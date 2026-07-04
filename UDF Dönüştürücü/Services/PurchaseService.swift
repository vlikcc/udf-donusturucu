import Foundation
import StoreKit
import Combine
import os.log

private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "com.evrakdonus", category: "PurchaseService")

final class PurchaseService: ObservableObject {
    static let shared = PurchaseService()

    static let unlimitedProductID = "com.evrakdonus.unlimited"

    @Published var products: [Product] = []
    @Published var purchaseState: PurchaseState = .idle
    @Published var isUnlimitedPurchased: Bool = false
    @Published var productsLoaded = false
    @Published var debugLog: String = ""

    enum PurchaseState: Equatable {
        case idle
        case loading
        case purchased
        case failed(String)
    }

    private var transactionListener: Task<Void, Never>?

    private init() {
        let bundleID = Bundle.main.bundleIdentifier ?? "bilinmiyor"
        logger.info("PurchaseService başlatılıyor — Bundle ID: \(bundleID)")
        
        transactionListener = listenForTransactions()
        
        Task {
            // Önce entitlement kontrolü yap
            await checkEntitlements()
            // Sonra ürünleri yükle
            await loadProducts()
        }
    }

    deinit {
        transactionListener?.cancel()
    }

    @MainActor
    func loadProducts() async {
        do {
            purchaseState = .loading
            let bundleID = Bundle.main.bundleIdentifier ?? "bilinmiyor"
            logger.info("Ürünler yükleniyor — Bundle ID: \(bundleID), Product ID: \(Self.unlimitedProductID)")
            
            let storeProducts = try await Product.products(for: [Self.unlimitedProductID])
            products = storeProducts
            productsLoaded = true

            if storeProducts.isEmpty {
                logger.error("Ürün listesi BOŞ döndü — Bundle ID: \(bundleID), İstenen Product ID: \(Self.unlimitedProductID)")
                debugLog = "Ürün boş — Bundle: \(bundleID), ProductID: \(Self.unlimitedProductID)"
                purchaseState = .failed(
                    "Ürün bulunamadı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin."
                )
            } else {
                logger.info("Ürünler başarıyla yüklendi: \(storeProducts.map { $0.id })")
                for product in storeProducts {
                    logger.info("Ürün: \(product.id), Fiyat: \(product.displayPrice), Tür: \(product.type.rawValue)")
                }
                purchaseState = .idle
            }
        } catch let error as Product.StoreKitError {
            productsLoaded = true
            logger.error("StoreKit hatası: \(error.localizedDescription)")
            debugLog = "StoreKit Error: \(error)"
            purchaseState = .failed("Ürünler yüklenemedi. Lütfen internet bağlantınızı kontrol edip tekrar deneyin.")
        } catch {
            productsLoaded = true
            logger.error("Ürün yükleme hatası: \(error.localizedDescription) — Tam hata: \(String(describing: error))")
            debugLog = "Error: \(error)"
            purchaseState = .failed("Ürünler yüklenemedi: \(error.localizedDescription)")
        }
    }

    @MainActor
    func purchase() async {
        if products.isEmpty {
            await loadProducts()
        }

        guard let product = products.first else {
            purchaseState = .failed(
                "Ürün bulunamadı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin. Sorun devam ederse App Store Connect ayarlarını kontrol edin."
            )
            return
        }

        await purchaseWithProduct(product)
    }

    @MainActor
    private func purchaseWithProduct(_ product: Product) async {
        do {
            purchaseState = .loading
            let result = try await product.purchase()

            switch result {
            case .success(let verification):
                let transaction = try checkVerified(verification)
                await transaction.finish()
                isUnlimitedPurchased = true
                LimitService.shared.activatePremium()
                purchaseState = .purchased

            case .userCancelled:
                purchaseState = .idle

            case .pending:
                purchaseState = .failed("Satın alma işlemi onay bekliyor. Lütfen daha sonra tekrar kontrol edin.")

            @unknown default:
                purchaseState = .idle
            }
        } catch let error as StoreKitError {
            switch error {
            case .networkError:
                purchaseState = .failed("İnternet bağlantısı hatası. Lütfen bağlantınızı kontrol edip tekrar deneyin.")
            case .userCancelled:
                purchaseState = .idle
            default:
                purchaseState = .failed("Satın alma başarısız: \(error.localizedDescription)")
            }
        } catch {
            purchaseState = .failed("Satın alma başarısız: \(error.localizedDescription)")
        }
    }

    @MainActor
    func restorePurchases() async {
        purchaseState = .loading
        do {
            try await AppStore.sync()
            let found = await checkEntitlements()
            if found {
                purchaseState = .purchased
            } else {
                purchaseState = .failed("Bu Apple Kimliği ile ilişkili bir satın alma bulunamadı.")
            }
        } catch let error as StoreKitError {
            switch error {
            case .networkError:
                purchaseState = .failed("İnternet bağlantısı hatası. Lütfen bağlantınızı kontrol edip tekrar deneyin.")
            case .userCancelled:
                purchaseState = .idle
            default:
                purchaseState = .failed("Geri yükleme başarısız: \(error.localizedDescription)")
            }
        } catch {
            purchaseState = .failed("Geri yükleme başarısız: \(error.localizedDescription)")
        }
    }

    @MainActor
    @discardableResult
    private func checkEntitlements() async -> Bool {
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result {
                if transaction.productID == Self.unlimitedProductID {
                    isUnlimitedPurchased = true
                    LimitService.shared.activatePremium()
                    return true
                }
            }
        }
        return false
    }

    private func listenForTransactions() -> Task<Void, Never> {
        Task.detached {
            for await result in Transaction.updates {
                if case .verified(let transaction) = result {
                    await transaction.finish()
                    if transaction.productID == Self.unlimitedProductID {
                        await MainActor.run {
                            self.isUnlimitedPurchased = true
                            LimitService.shared.activatePremium()
                        }
                    }
                }
            }
        }
    }

    private func checkVerified<T>(_ result: VerificationResult<T>) throws -> T {
        switch result {
        case .unverified:
            throw StoreError.failedVerification
        case .verified(let safe):
            return safe
        }
    }
}

enum StoreError: LocalizedError {
    case failedVerification
    var errorDescription: String? { "Islem dogrulanamadi." }
}
