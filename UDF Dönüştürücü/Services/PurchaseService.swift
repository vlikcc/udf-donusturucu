import Foundation
import StoreKit
import Combine
import os.log
import UIKit

private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "com.evrakdonus", category: "PurchaseService")

final class PurchaseService: ObservableObject {
    static let shared = PurchaseService()

    static let unlimitedProductID = "com.evrakdonus.unlimited"
    static let weeklyProductID = "com.evrakdonus.pro.weekly"
    static let yearlyProductID = "com.evrakdonus.pro.yearly"
    static let allProductIDs: Set<String> = [unlimitedProductID, weeklyProductID, yearlyProductID]

    @Published var products: [Product] = []
    @Published var purchaseState: PurchaseState = .idle
    @Published var isUnlimitedPurchased: Bool = false
    @Published var productsLoaded = false
    @Published var debugLog: String = ""

    /// Paywall'ın hangi ürünü öne çıkaracağını bilmesi için: fiyata göre sıralanmış, yıllık abonelik en üstte.
    var sortedProducts: [Product] {
        products.sorted { lhs, rhs in
            priority(for: lhs.id) < priority(for: rhs.id)
        }
    }

    private func priority(for productID: String) -> Int {
        switch productID {
        case Self.weeklyProductID: return 0
        case Self.yearlyProductID: return 1
        case Self.unlimitedProductID: return 2
        default: return 3
        }
    }

    enum PurchaseState: Equatable {
        case idle
        case loading
        case purchased
        case failed(String)
    }

    private var transactionListener: Task<Void, Never>?
    private var cancellables = Set<AnyCancellable>()

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

        // Abonelik süresi dolmuş/iptal edilmişse, uygulama ön plana her geldiğinde premium durumu güncellenir.
        NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)
            .sink { [weak self] _ in
                Task { await self?.checkEntitlements() }
            }
            .store(in: &cancellables)
    }

    deinit {
        transactionListener?.cancel()
    }

    @MainActor
    func loadProducts() async {
        do {
            purchaseState = .loading
            let bundleID = Bundle.main.bundleIdentifier ?? "bilinmiyor"
            logger.info("Ürünler yükleniyor — Bundle ID: \(bundleID)")

            let storeProducts = try await Product.products(for: Array(Self.allProductIDs))
            products = storeProducts
            productsLoaded = true

            if storeProducts.isEmpty {
                logger.error("Ürün listesi BOŞ döndü — Bundle ID: \(bundleID)")
                debugLog = "Ürün boş — Bundle: \(bundleID)"
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
        } catch let error as StoreKitError {
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

    /// Belirli bir ürünü satın alır. `source`, hangi ekrandan tetiklendiğini analitiğe iletir.
    @MainActor
    func purchase(_ product: Product, source: String) async {
        AnalyticsService.logPurchaseStarted(productID: product.id, source: source)
        await purchaseWithProduct(product, source: source)
    }

    /// Geriye dönük uyumluluk: parametre verilmezse en yüksek öncelikli (yıllık) ürünü satın almayı dener.
    @MainActor
    func purchase() async {
        if products.isEmpty {
            await loadProducts()
        }
        guard let product = sortedProducts.first else {
            purchaseState = .failed(
                "Ürün bulunamadı. Lütfen internet bağlantınızı kontrol edip tekrar deneyin. Sorun devam ederse App Store Connect ayarlarını kontrol edin."
            )
            return
        }
        await purchase(product, source: "unknown")
    }

    @MainActor
    private func purchaseWithProduct(_ product: Product, source: String) async {
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
                AnalyticsService.logPurchaseCompleted(
                    productID: product.id,
                    priceDisplay: product.displayPrice,
                    source: source
                )

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
                AnalyticsService.logPurchaseFailed(productID: product.id, reason: "network_error", source: source)
            case .userCancelled:
                purchaseState = .idle
            default:
                purchaseState = .failed("Satın alma başarısız: \(error.localizedDescription)")
                AnalyticsService.logPurchaseFailed(productID: product.id, reason: "storekit_error", source: source)
            }
        } catch {
            purchaseState = .failed("Satın alma başarısız: \(error.localizedDescription)")
            AnalyticsService.logPurchaseFailed(productID: product.id, reason: "unknown_error", source: source)
        }
    }

    @MainActor
    func restorePurchases() async {
        purchaseState = .loading
        do {
            try await AppStore.sync()
            let found = await checkEntitlements()
            AnalyticsService.logRestoreCompleted(found: found)
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

    /// Lifetime satın alma VEYA aktif bir abonelik varsa premium'u etkinleştirir.
    /// İkisi de yoksa (örn. abonelik süresi dolmuş/iptal edilmiş) premium'u düşürür.
    @MainActor
    @discardableResult
    private func checkEntitlements() async -> Bool {
        var hasEntitlement = false
        for await result in Transaction.currentEntitlements {
            if case .verified(let transaction) = result, Self.allProductIDs.contains(transaction.productID) {
                hasEntitlement = true
                break
            }
        }

        if hasEntitlement {
            isUnlimitedPurchased = true
            LimitService.shared.activatePremium()
        } else {
            isUnlimitedPurchased = false
            LimitService.shared.restorePremiumStatus(false)
        }
        return hasEntitlement
    }

    private func listenForTransactions() -> Task<Void, Never> {
        Task.detached { [weak self] in
            for await result in Transaction.updates {
                if case .verified(let transaction) = result {
                    await transaction.finish()
                    if PurchaseService.allProductIDs.contains(transaction.productID) {
                        await MainActor.run {
                            self?.isUnlimitedPurchased = true
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
