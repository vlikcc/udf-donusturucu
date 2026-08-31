import SwiftUI
import StoreKit

// MARK: - PaywallView

struct PaywallView: View {
    /// Paywall'ın hangi ekrandan açıldığı (analitik): "onboarding", "limit_card", "limit_alert",
    /// "settings", "result_limit", "history", "tools".
    var source: String = "unknown"

    @ObservedObject private var purchaseService = PurchaseService.shared
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var selectedProductID: String?
    @State private var showTerms = false
    @State private var showPrivacy = false

    private var selectedProduct: Product? {
        purchaseService.sortedProducts.first { $0.id == selectedProductID }
            ?? purchaseService.sortedProducts.first { $0.id == PurchaseService.yearlyProductID }
            ?? purchaseService.sortedProducts.first
    }

    /// Yıllık planın "üstü çizili" referansı ve tasarruf oranı, canlı aylık fiyattan türetilir.
    /// Aylık ürün yüklenmediyse `nil` → ne üstü çizili fiyat ne de indirim rozeti gösterilir.
    private var monthlyProduct: Product? {
        purchaseService.products.first { $0.id == PurchaseService.monthlyProductID }
    }

    var body: some View {
        NavigationStack {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 16) {
                    compactHeroSection
                    plansSection
                    compactFeaturesSection
                    legalInlineSection
                }
                .padding(.horizontal, 20)
                .padding(.top, 4)
                .padding(.bottom, 12)
            }
            .safeAreaInset(edge: .bottom, spacing: 0) {
                bottomBar
            }
            .background(paywallBackground.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button { dismiss() } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title3)
                            .symbolRenderingMode(.hierarchical)
                            .foregroundStyle(.secondary)
                    }
                    .accessibilityLabel("Kapat")
                }
            }
            .sheet(isPresented: $showTerms) { NavigationStack { TermsView() } }
            .sheet(isPresented: $showPrivacy) { NavigationStack { PrivacyPolicyView() } }
        }
        .task {
            AnalyticsService.logPaywallShown(source: source)
            await purchaseService.loadProducts()
            selectDefaultPlan()
        }
        .onChange(of: purchaseService.products) { _, _ in
            if selectedProductID == nil { selectDefaultPlan() }
        }
        .onChange(of: purchaseService.purchaseState) { _, newState in
            if newState == .purchased { dismiss() }
        }
    }

    // MARK: - Background

    private var paywallBackground: some View {
        ZStack {
            AppTheme.pageBg
            LinearGradient(
                colors: [
                    AppTheme.navy.opacity(colorScheme == .dark ? 0.35 : 0.12),
                    Color.clear
                ],
                startPoint: .top,
                endPoint: .center
            )
        }
    }

    // MARK: - Hero (compact)

    private var compactHeroSection: some View {
        HStack(spacing: 14) {
            ZStack {
                Circle()
                    .fill(AppTheme.navy.opacity(0.12))
                    .frame(width: 52, height: 52)
                Image(systemName: "crown.fill")
                    .font(.title3)
                    .foregroundStyle(
                        LinearGradient(
                            colors: [Color(red: 1, green: 0.82, blue: 0.4), .orange],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                    )
            }

            VStack(alignment: .leading, spacing: 4) {
                Text("Evrak Dönüştürücü Pro")
                    .font(.headline.bold())

                Text("Sınırsız dönüştürme, reklamsız deneyim ve tüm Pro araçlar.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)
            }

            Spacer(minLength: 0)
        }
    }

    // MARK: - Features (compact grid)

    private var compactFeaturesSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Pro ile neler var?")
                .font(.subheadline.bold())
                .foregroundStyle(.secondary)

            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                featureChip(icon: "infinity", text: "Sınırsız dönüştürme")
                featureChip(icon: "rectangle.slash", text: "Reklamsız")
                featureChip(icon: "folder.fill", text: "Dava dosyaları")
                featureChip(icon: "clock.arrow.circlepath", text: "30 gün geçmiş")
                featureChip(icon: "doc.on.doc", text: "Toplu dönüştürme")
                featureChip(icon: "scissors", text: "Belge bölme")
                featureChip(icon: "wrench.and.screwdriver", text: "Pro Araçlar")
                featureChip(icon: "pencil.and.outline", text: "UDF düzenleme")
            }
        }
        .padding(14)
        .background(AppTheme.cardBg, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .stroke(Color.primary.opacity(colorScheme == .dark ? 0.12 : 0.06), lineWidth: 1)
        )
    }

    private func featureChip(icon: String, text: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.caption.bold())
                .foregroundStyle(AppTheme.navy)
                .frame(width: 18)

            Text(text)
                .font(.caption)
                .lineLimit(2)
                .minimumScaleFactor(0.85)

            Spacer(minLength: 0)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 6)
        .background(AppTheme.elevatedCardBg, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    // MARK: - Plans

    @ViewBuilder
    private var plansSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Plan Seçin")
                .font(.subheadline.bold())

            if purchaseService.sortedProducts.isEmpty {
                HStack {
                    Spacer()
                    ProgressView("Planlar yükleniyor…")
                    Spacer()
                }
                .padding(.vertical, 20)
            } else {
                VStack(spacing: 8) {
                    ForEach(purchaseService.sortedProducts, id: \.id) { product in
                        planCard(for: product)
                    }
                }
            }
        }
    }

    private func planCard(for product: Product) -> some View {
        let isSelected = selectedProductID == product.id
        let isYearly = product.id == PurchaseService.yearlyProductID
        let isLifetime = product.id == PurchaseService.unlimitedProductID
        let savings = savingsPercent(for: product)

        return Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                selectedProductID = product.id
            }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: isSelected ? "largecircle.fill.circle" : "circle")
                    .font(.body)
                    .foregroundStyle(isSelected ? AppTheme.navy : Color.secondary.opacity(0.35))

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 5) {
                        Text(planTitle(for: product))
                            .font(.subheadline.bold())
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)

                        if isYearly {
                            badge("EN AVANTAJLI", color: Color.orange)
                        } else if isLifetime {
                            badge("TEK SEFER", color: AppTheme.navy)
                        }

                        if let savings, savings > 0 {
                            badge("-%\(savings)", color: Color.red.opacity(0.9))
                        }
                    }

                    Text(planSubtitle(for: product))
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(3)
                        .multilineTextAlignment(.leading)
                }

                Spacer(minLength: 4)

                planPriceColumn(for: product, isYearly: isYearly)
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .fill(isSelected ? AppTheme.navy.opacity(0.08) : AppTheme.elevatedCardBg)
            }
            .overlay {
                RoundedRectangle(cornerRadius: 16, style: .continuous)
                    .stroke(isSelected ? AppTheme.navy : Color.primary.opacity(0.08), lineWidth: isSelected ? 2 : 1)
            }
            .scaleEffect(isSelected ? 1.01 : 1)
        }
        .buttonStyle(.plain)
    }

    private func planPriceColumn(for product: Product, isYearly: Bool) -> some View {
        VStack(alignment: .trailing, spacing: 1) {
            if isYearly, let reference = yearlyReferencePrice {
                Text(reference.formatted(product.priceFormatStyle))
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .strikethrough(true, color: .secondary)
            }

            Text(product.displayPrice)
                .font(.subheadline.bold())

            if let suffix = periodSuffix(for: product) {
                Text(suffix)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }

            // Apple 3.1.2: "price per unit if appropriate" — yıllık planın aylık karşılığı.
            if isYearly {
                Text("≈ \((product.price / 12).formatted(product.priceFormatStyle)) / ay")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
        }
    }

    private func badge(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 9, weight: .heavy))
            .padding(.horizontal, 6)
            .padding(.vertical, 3)
            .background(color, in: Capsule())
            .foregroundStyle(.white)
    }

    // MARK: - Bottom bar

    private var bottomBar: some View {
        VStack(spacing: 8) {
            if case .failed(let message) = purchaseService.purchaseState {
                VStack(spacing: 4) {
                    Text(message)
                        .font(.caption2)
                        .foregroundStyle(.red)
                        .multilineTextAlignment(.center)

                    Button("Tekrar Dene") {
                        purchaseService.purchaseState = .idle
                        Task { await purchaseService.loadProducts() }
                    }
                    .font(.caption2.bold())
                    .foregroundStyle(AppTheme.navy)
                }
            }

            Button {
                guard let product = selectedProduct else { return }
                AnalyticsService.logPaywallPlanSelected(productID: product.id, source: source)
                Task { await purchaseService.purchase(product, source: source) }
            } label: {
                Group {
                    if purchaseService.purchaseState == .loading {
                        ProgressView().tint(.white)
                    } else {
                        Text(ctaTitle)
                            .font(.headline)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
            }
            .buttonStyle(.plain)
            .background(ctaGradient, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .foregroundStyle(.white)
            .disabled(purchaseService.purchaseState == .loading || selectedProduct == nil)

            if let footnote = ctaFootnote {
                Text(footnote)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
            }

            HStack(spacing: 8) {
                Button("Geri Yükle") {
                    Task { await purchaseService.restorePurchases() }
                }

                Text("•").foregroundStyle(.tertiary)

                Button("Koşullar") { showTerms = true }

                Text("•").foregroundStyle(.tertiary)

                Button("Gizlilik") { showPrivacy = true }

                Text("•").foregroundStyle(.tertiary)

                Link("EULA", destination: AppLinks.appleEULA)
            }
            .font(.caption2)
            .foregroundStyle(.secondary)
        }
        .padding(.horizontal, 20)
        .padding(.top, 10)
        .padding(.bottom, 8)
        .background {
            Rectangle()
                .fill(.ultraThinMaterial)
                .ignoresSafeArea(edges: .bottom)
        }
    }

    private var ctaGradient: LinearGradient {
        LinearGradient(
            colors: [AppTheme.navy, AppTheme.navy.opacity(0.85)],
            startPoint: .leading,
            endPoint: .trailing
        )
    }

    // MARK: - Legal (scroll area)

    private var legalInlineSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Abonelik Koşulları")
                .font(.caption2.bold())
                .foregroundStyle(.secondary)

            Text("""
            Pro Aylık Abonelik 1 ay, Pro Yıllık Abonelik 1 yıl sürelidir ve otomatik olarak yenilenir. \
            Ömür Boyu Pro tek seferlik bir satın almadır, abonelik değildir.

            Ödeme, satın alma onayında Apple Kimliği hesabınıza yansıtılır. Abonelik, mevcut dönemin \
            bitiminden en az 24 saat önce kapatılmadığı sürece otomatik olarak yenilenir ve yenileme \
            ücreti dönem bitiminden önceki 24 saat içinde tahsil edilir. Aboneliğinizi App Store > \
            Apple Kimliği > Abonelikler bölümünden yönetebilir veya iptal edebilirsiniz.
            """)
            .font(.caption2)
            .foregroundStyle(.tertiary)
            .multilineTextAlignment(.leading)
            .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.bottom, 8)
    }

    // MARK: - Copy helpers

    private func planTitle(for product: Product) -> String {
        switch product.id {
        case PurchaseService.monthlyProductID: return "Pro Aylık Abonelik"
        case PurchaseService.yearlyProductID: return "Pro Yıllık Abonelik"
        case PurchaseService.unlimitedProductID: return "Ömür Boyu Pro"
        default: return product.displayName
        }
    }

    /// Apple 3.1.2: abonelik süresi paywall'da açıkça yazmalı. Süre sabit metinden değil,
    /// ürünün gerçek `subscriptionPeriod`'undan türetilir; varsa tanıtım teklifi de eklenir.
    private func planSubtitle(for product: Product) -> String {
        guard let period = product.subscription?.subscriptionPeriod else {
            return product.id == PurchaseService.unlimitedProductID
                ? "Tek ödeme — abonelik yok, kalıcı erişim"
                : product.description
        }

        var parts = ["\(period.paywallDescription) · otomatik yenilenir"]

        if let intro = product.introOfferDescription {
            parts.append(intro)
        }

        if product.id == PurchaseService.yearlyProductID {
            parts.append("12 aylık pakete göre tasarruf")
        } else {
            parts.append("istediğiniz zaman iptal")
        }

        return parts.joined(separator: " · ")
    }

    private func periodSuffix(for product: Product) -> String? {
        guard let period = product.subscription?.subscriptionPeriod else { return nil }
        return "/ \(period.priceSuffixDescription)"
    }

    private var ctaTitle: String {
        guard let product = selectedProduct else { return "Devam Et" }
        if product.id == PurchaseService.unlimitedProductID {
            return "Ömür Boyu Satın Al"
        }
        return "Pro'ya Geç"
    }

    private var ctaFootnote: String? {
        guard let product = selectedProduct else { return nil }
        if product.type == .autoRenewable {
            return "App Store > Abonelikler bölümünden istediğiniz zaman iptal edebilirsiniz."
        }
        return nil
    }

    private func selectDefaultPlan() {
        selectedProductID = purchaseService.sortedProducts
            .first { $0.id == PurchaseService.yearlyProductID }?.id
            ?? purchaseService.sortedProducts.first?.id
    }

    /// 12 × canlı aylık fiyat. Aylık ürün henüz yüklenmediyse referans gösterilmez.
    private var yearlyReferencePrice: Decimal? {
        guard let monthlyProduct else { return nil }
        return monthlyProduct.price * 12
    }

    private func savingsPercent(for product: Product) -> Int? {
        guard product.id == PurchaseService.yearlyProductID else { return nil }
        guard let reference = yearlyReferencePrice, reference > product.price else { return nil }

        let ratio = (reference - product.price) / reference
        return Int(truncating: (ratio * 100) as NSDecimalNumber)
    }
}

// MARK: - StoreKit helpers

private extension Product {
    var introOfferDescription: String? {
        guard let offer = subscription?.introductoryOffer else { return nil }

        switch offer.paymentMode {
        case .freeTrial:
            return "\(offer.period.paywallDescription) ücretsiz deneme"
        case .payAsYouGo:
            return "\(offer.displayPrice) / \(offer.period.paywallDescription)"
        case .payUpFront:
            return "\(offer.displayPrice) peşin"
        default:
            return nil
        }
    }
}

private extension Product.SubscriptionPeriod {
    /// Süreyi sayıyla birlikte yazar: "1 ay", "1 yıl". Paywall'da abonelik uzunluğunu
    /// açıkça göstermek için kullanılır (App Review 3.1.2).
    var paywallDescription: String {
        switch unit {
        case .day: return "\(value) gün"
        case .week: return "\(value) hafta"
        case .month: return "\(value) ay"
        case .year: return "\(value) yıl"
        @unknown default: return "\(value) dönem"
        }
    }

    /// Fiyatın yanındaki kısa ek: tek dönemde birim adı ("/ ay"), çoklu dönemde sayıyla ("/ 3 ay").
    var priceSuffixDescription: String {
        guard value == 1 else { return paywallDescription }

        switch unit {
        case .day: return "gün"
        case .week: return "hafta"
        case .month: return "ay"
        case .year: return "yıl"
        @unknown default: return "dönem"
        }
    }
}
