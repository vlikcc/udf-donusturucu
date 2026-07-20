import SwiftUI
import StoreKit

struct PaywallView: View {
    /// Paywall'ın hangi ekrandan açıldığı (analitik için): "onboarding", "limit_card", "limit_alert",
    /// "settings", "result_limit", "history".
    var source: String = "unknown"

    @ObservedObject var purchaseService = PurchaseService.shared
    @Environment(\.dismiss) private var dismiss

    @State private var selectedProductID: String?
    @State private var showTerms = false
    @State private var showPrivacy = false

    private var selectedProduct: Product? {
        purchaseService.sortedProducts.first { $0.id == selectedProductID }
            ?? purchaseService.sortedProducts.first { $0.id == PurchaseService.yearlyProductID }
            ?? purchaseService.sortedProducts.first
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    header

                    // Features
                    VStack(alignment: .leading, spacing: 16) {
                        featureRow(icon: "infinity", text: "Sınırsız ve toplu dönüştürme")
                        featureRow(icon: "rectangle.slash", text: "Tüm reklamlar kaldırılır")
                        featureRow(icon: "wrench.and.screwdriver.fill", text: "Birleştirme, sıkıştırma ve şifreleme araçları")
                        featureRow(icon: "text.viewfinder", text: "Taranmış belgeler için metin tanıma (OCR)")
                        featureRow(icon: "doc.badge.plus", text: "UDF düzenleme, şablonlar ve e-imza bilgisi")
                        featureRow(icon: "clock.arrow.circlepath", text: "30 günlük dönüşüm geçmişi")
                    }
                    .padding()
                    .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))

                    // Plans
                    if purchaseService.sortedProducts.isEmpty {
                        ProgressView()
                            .padding(.vertical, 24)
                    } else {
                        VStack(spacing: 10) {
                            ForEach(purchaseService.sortedProducts, id: \.id) { product in
                                planCard(for: product)
                            }
                        }
                    }

                    // Purchase button
                    Button {
                        guard let product = selectedProduct else { return }
                        AnalyticsService.logPaywallPlanSelected(productID: product.id, source: source)
                        Task { await purchaseService.purchase(product, source: source) }
                    } label: {
                        Group {
                            if purchaseService.purchaseState == .loading {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Text(ctaTitle)
                                    .font(.headline)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(
                            LinearGradient(
                                colors: [AppTheme.navy, AppTheme.navy.opacity(0.8)],
                                startPoint: .leading,
                                endPoint: .trailing
                            ),
                            in: RoundedRectangle(cornerRadius: 14)
                        )
                        .foregroundStyle(.white)
                    }
                    .shadow(color: AppTheme.navy.opacity(0.3), radius: 10, y: 5)
                    .disabled(purchaseService.purchaseState == .loading || selectedProduct == nil)

                    Text("Aboneliğinizi istediğiniz zaman App Store > Ayarlar bölümünden iptal edebilirsiniz.")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)

                    // Restore button
                    Button("Satın Alımı Geri Yükle") {
                        Task { await purchaseService.restorePurchases() }
                    }
                    .font(.subheadline)
                    .foregroundStyle(.secondary)

                    if case .failed(let message) = purchaseService.purchaseState {
                        VStack(spacing: 8) {
                            Text(message)
                                .font(.caption)
                                .foregroundStyle(.red)
                                .multilineTextAlignment(.center)

                            Button("Tekrar Dene") {
                                purchaseService.purchaseState = .idle
                                Task { await purchaseService.loadProducts() }
                            }
                            .font(.caption)
                            .foregroundStyle(AppTheme.navy)
                        }
                        .padding(.horizontal)
                    }

                    // Terms / Privacy (App Store abonelik sunumu için gereklidir)
                    HStack(spacing: 16) {
                        Button("Kullanım Koşulları") { showTerms = true }
                        Text("•").foregroundStyle(.secondary)
                        Button("Gizlilik Politikası") { showPrivacy = true }
                    }
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .padding(.top, 4)

                    Spacer()
                }
                .padding(.horizontal)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .sheet(isPresented: $showTerms) { NavigationStack { TermsView() } }
            .sheet(isPresented: $showPrivacy) { NavigationStack { PrivacyPolicyView() } }
        }
        .task {
            AnalyticsService.logPaywallShown(source: source)
            await purchaseService.loadProducts()
            if selectedProductID == nil {
                selectedProductID = purchaseService.sortedProducts
                    .first { $0.id == PurchaseService.yearlyProductID }?.id
                    ?? purchaseService.sortedProducts.first?.id
            }
        }
        .onChange(of: purchaseService.products) { _, _ in
            if selectedProductID == nil {
                selectedProductID = purchaseService.sortedProducts
                    .first { $0.id == PurchaseService.yearlyProductID }?.id
                    ?? purchaseService.sortedProducts.first?.id
            }
        }
    }

    // MARK: - Header

    private var header: some View {
        VStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(
                        LinearGradient(
                            colors: [Color.yellow.opacity(0.3), Color.orange.opacity(0.2)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .frame(width: 110, height: 110)
                Image(systemName: "crown.fill")
                    .font(.system(size: 48))
                    .foregroundStyle(
                        LinearGradient(
                            colors: [.yellow, .orange],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
            }

            Text("Sınırsız Dönüştürme")
                .font(.title).bold()

            Text("Günlük limit ve reklam olmadan tüm UDF dosyalarınızı dönüştürün")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(.top, 24)
    }

    // MARK: - Plan card

    private func planCard(for product: Product) -> some View {
        let isSelected = selectedProductID == product.id
        let isYearly = product.id == PurchaseService.yearlyProductID
        let hasFreeTrial = product.subscription?.introductoryOffer?.paymentMode == .freeTrial

        return Button {
            selectedProductID = product.id
        } label: {
            HStack(spacing: 12) {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .font(.title3)
                    .foregroundStyle(isSelected ? AppTheme.navy : Color.secondary.opacity(0.4))

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(planTitle(for: product))
                            .font(.subheadline).bold()
                        if isYearly {
                            Text("EN AVANTAJLI")
                                .font(.caption2).bold()
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.orange, in: Capsule())
                                .foregroundStyle(.white)
                        }
                    }
                    Text(planSubtitle(for: product, hasFreeTrial: hasFreeTrial))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                VStack(alignment: .trailing, spacing: 2) {
                    Text(product.displayPrice)
                        .font(.headline)
                    if let periodUnitText = periodUnitSuffix(for: product) {
                        Text(periodUnitText)
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding()
            .background(
                isSelected ? AppTheme.navy.opacity(0.08) : Color(.secondarySystemBackground),
                in: RoundedRectangle(cornerRadius: 14)
            )
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(isSelected ? AppTheme.navy : Color.clear, lineWidth: 2)
            )
        }
        .buttonStyle(.plain)
    }

    private func planTitle(for product: Product) -> String {
        switch product.id {
        case PurchaseService.weeklyProductID: return "Haftalık"
        case PurchaseService.yearlyProductID: return "Yıllık"
        case PurchaseService.unlimitedProductID: return "Ömür Boyu"
        default: return product.displayName
        }
    }

    private func planSubtitle(for product: Product, hasFreeTrial: Bool) -> String {
        switch product.id {
        case PurchaseService.weeklyProductID:
            return "Her hafta otomatik yenilenir"
        case PurchaseService.yearlyProductID:
            return hasFreeTrial ? "3 gün ücretsiz, sonra yıllık yenilenir" : "Yıllık otomatik yenilenir"
        case PurchaseService.unlimitedProductID:
            return "Tek seferlik ödeme — Abonelik yok"
        default:
            return ""
        }
    }

    private func periodUnitSuffix(for product: Product) -> String? {
        switch product.id {
        case PurchaseService.weeklyProductID: return "/hafta"
        case PurchaseService.yearlyProductID: return "/yıl"
        default: return nil
        }
    }

    private var ctaTitle: String {
        guard let product = selectedProduct else { return "Devam Et" }
        if product.subscription?.introductoryOffer?.paymentMode == .freeTrial {
            return "3 Gün Ücretsiz Dene"
        }
        if product.id == PurchaseService.unlimitedProductID {
            return "Satın Al"
        }
        return "Devam Et"
    }

    private func featureRow(icon: String, text: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundStyle(AppTheme.navy)
                .frame(width: 28)
            Text(text)
                .font(.subheadline)
        }
    }
}
