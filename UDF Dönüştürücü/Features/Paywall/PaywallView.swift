import SwiftUI
import StoreKit

struct PaywallView: View {
    @ObservedObject var purchaseService = PurchaseService.shared
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 24) {
                    // Header
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

                        Text("Günlük limit olmadan tüm UDF dosyalarınızı dönüştürün")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                            .multilineTextAlignment(.center)
                    }
                    .padding(.top, 24)

                    // Features
                    VStack(alignment: .leading, spacing: 16) {
                        featureRow(icon: "infinity", text: "Sınırsız belge dönüştürme")
                        featureRow(icon: "clock.arrow.circlepath", text: "30 günlük dönüşüm geçmişi")
                        featureRow(icon: "bolt.fill", text: "Toplu dönüştürme desteği")
                    }
                    .padding()
                    .background(Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 16))

                    // Price
                    VStack(spacing: 8) {
                        if let product = purchaseService.products.first {
                            Text(product.displayPrice)
                                .font(.system(size: 36, weight: .bold))
                            Text("Tek seferlik ödeme — Abonelik yok")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        } else {
                            Text("₺499")
                                .font(.system(size: 36, weight: .bold))
                            Text("Tek seferlik ödeme — Abonelik yok")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }

                    // Purchase button
                    Button {
                        Task { await purchaseService.purchase() }
                    } label: {
                        Group {
                            if purchaseService.purchaseState == .loading {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Text("Satın Al")
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
                    .disabled(purchaseService.purchaseState == .loading)

                    // Reklam izle — bonus çeviri
                    Button {
                        if let vc = UIApplication.topViewController() {
                            AdsManager.shared.showRewarded(from: vc)
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "play.rectangle.fill")
                            Text("Reklam İzle, +1 Çeviri Kazan")
                        }
                        .font(.subheadline)
                        .foregroundStyle(AppTheme.navy)
                    }

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
        }
        .task {
            await purchaseService.loadProducts()
        }
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
