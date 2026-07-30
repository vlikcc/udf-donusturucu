import SwiftUI

struct SettingsView: View {
    @ObservedObject var limitService = LimitService.shared
    @ObservedObject var purchaseService = PurchaseService.shared
    @State private var showPaywall = false

    var body: some View {
        List {
            // Subscription status
            Section("Hesap Durumu") {
                HStack {
                    Image(systemName: limitService.isPremium ? "crown.fill" : "person.circle")
                        .foregroundStyle(limitService.isPremium ? Color.orange : .gray)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(limitService.isPremium ? "Premium Kullanıcı" : "Ücretsiz Kullanıcı")
                            .font(.subheadline).bold()
                        if !limitService.isPremium {
                            Text("Günlük \(limitService.remainingConversions) dönüşüm hakkı kaldı")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }

                if !limitService.isPremium {
                    Button {
                        showPaywall = true
                    } label: {
                        Label("Premium'a Yükselt", systemImage: "star.fill")
                    }
                }
            }

            // App info
            Section("Uygulama") {
                HStack {
                    Text("Sürüm")
                    Spacer()
                    Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0")
                        .foregroundStyle(.secondary)
                }

                HStack {
                    Text("Geliştirici")
                    Spacer()
                    Text("Veli KEÇECİ")
                        .foregroundStyle(.secondary)
                }
            }

            // Privacy
            Section("Gizlilik ve Yasal") {
                NavigationLink {
                    PrivacyPolicyView()
                } label: {
                    Label("Gizlilik Politikası", systemImage: "hand.raised")
                }

                NavigationLink {
                    TermsView()
                } label: {
                    Label("Kullanım Koşulları", systemImage: "doc.text")
                }

                Link(destination: AppLinks.appleEULA) {
                    Label("Lisans Sözleşmesi (EULA)", systemImage: "doc.badge.gearshape")
                }
            }

            // Support
            Section("Destek") {
                Button {
                    if let url = URL(string: "mailto:info@velikececi.com") {
                        UIApplication.shared.open(url)
                    }
                } label: {
                    Label("Bize Yazın", systemImage: "envelope")
                }

                Button {
                    requestAppReview()
                } label: {
                    Label("Uygulamayı Değerlendirin", systemImage: "star")
                }
            }

            // Restore
            Section {
                Button {
                    Task { await purchaseService.restorePurchases() }
                } label: {
                    Label("Satın Alımları Geri Yükle", systemImage: "arrow.clockwise")
                }
            }
        }
        .navigationTitle("Ayarlar")
        .sheet(isPresented: $showPaywall) {
            PaywallView(source: "settings")
        }
    }

    private func requestAppReview() {
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
        SKStoreReviewController.requestReview(in: scene)
    }
}

import StoreKit

// MARK: - Privacy Policy

struct PrivacyPolicyView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Gizlilik Politikası")
                    .font(.title).bold()

                Text("Son güncelleme: Temmuz 2026")
                    .foregroundStyle(.secondary)

                Group {
                    Text("Veri Toplama").font(.headline)
                    Text("Evrak Dönüştürücü uygulaması, yüklediğiniz UDF dosyalarının içeriğini hiçbir sunucuya göndermez. Tüm dönüştürme işlemleri tamamen cihazınız üzerinde gerçekleştirilir. Belgeleriniz üçüncü taraflarla paylaşılmaz.")

                    Text("Yerel Depolama").font(.headline)
                    Text("Dönüştürme geçmişi (yalnızca dosya adı ve tarih bilgisi) cihazınızda yerel olarak saklanır. Bu bilgiler üçüncü taraflarla paylaşılmaz ve yalnızca uygulamanın geçmiş ekranında görülebilir.")

                    Text("Uygulama İçi Satın Alma").font(.headline)
                    Text("Premium özellikler, otomatik yenilenen Pro Aylık (1 ay) veya Pro Yıllık (1 yıl) abonelikle ya da tek seferlik Ömür Boyu Pro satın almasıyla edinilebilir. Satın alma işlemleri Apple App Store üzerinden gerçekleştirilir. Ödeme ve işlem bilgileri yalnızca Apple tarafından yönetilir; uygulama bu bilgilere erişemez.")

                    Text("Analitik Veriler").font(.headline)
                    Text("Uygulamayı geliştirmek amacıyla anonim kullanım istatistikleri (örn. ekran görüntülenme ve satın alma olayları) Google Firebase Analytics aracılığıyla toplanabilir. Bu veriler kimliğinizi tanımlamaz ve belge içeriklerinizle ilişkilendirilmez.")

                    Text("KVKK Uyumu").font(.headline)
                    Text("Evrak Dönüştürücü, 6698 sayılı Kişisel Verilerin Korunması Kanunu'na uygun olarak çalışır. Kişisel veri toplanmaz ve işlenmez. Dosyalarınız tamamen cihazınızda kalır.")

                    Text("İletişim").font(.headline)
                    Text("Gizlilik politikamız hakkındaki sorularınız için bizimle iletişime geçebilirsiniz.")
                    Text("E-posta: info@velikececi.com")
                }

                Link(destination: AppLinks.privacyPolicy) {
                    Label("Gizlilik politikasını web'de görüntüle", systemImage: "safari")
                        .font(.subheadline)
                }
                .padding(.top, 4)
            }
            .padding()
        }
        .navigationTitle("Gizlilik")
        .navigationBarTitleDisplayMode(.inline)
    }
}

// MARK: - Terms

struct TermsView: View {
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Kullanım Koşulları")
                    .font(.title).bold()

                Text("Son güncelleme: Temmuz 2026")
                    .foregroundStyle(.secondary)

                Group {
                    Text("Hizmet Tanımı").font(.headline)
                    Text("Evrak Dönüştürücü, UYAP UDF formatındaki dosyaları PDF ve Microsoft Word (.docx) formatlarına dönüştürme hizmeti sunan bir iOS uygulamasıdır.")
                    Text("Önemli: Bu uygulama resmi bir devlet hizmeti değildir ve UYAP ile doğrudan bağlantılı değildir. Bağımsız bir üçüncü taraf uygulamasıdır.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    Text("Desteklenen Dönüştürme Yönleri").font(.headline)
                    Text("• UDF → PDF\n• UDF → Microsoft Word (.docx)\n• PDF → UDF\n• Word (.docx) → UDF")

                    Text("Sorumluluk Sınırı").font(.headline)
                    Text("Dönüştürme işlemi sırasında oluşabilecek biçimlendirme farklılıklarından dolayı sorumluluk kabul edilmez. Hukuki işlemlerde orijinal belgenin kullanılması tavsiye edilir.")
                    Text("Uygulama \"olduğu gibi\" sunulmaktadır. Dönüştürme sonuçlarının doğruluğu veya eksiksizliği için garanti verilmez.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                Group {
                    Text("Ücretsiz Kullanım ve Limitler").font(.headline)
                    Text("• Ücretsiz kullanıcılar günlük 1 dönüştürme hakkına sahiptir.\n• Reklam izleyerek günde en fazla 2 ek dönüştürme hakkı kazanılabilir.\n• Limit her gün gece yarısı sıfırlanır.\n• Dönüştürme geçmişi 7 gün boyunca saklanır.")

                    Text("Pro Üyelik Planları").font(.headline)
                    Text("• Pro Aylık Abonelik — 1 ay süreli, otomatik yenilenen abonelik.\n• Pro Yıllık Abonelik — 1 yıl süreli, otomatik yenilenen abonelik.\n• Ömür Boyu Pro — tek seferlik satın alma, abonelik değildir.")
                    Text("Güncel fiyatlar bulunduğunuz ülkenin App Store fiyatlandırmasına göre değişir ve uygulama içindeki Pro ekranında satın alma öncesinde gösterilir.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Text("Pro üyelik; sınırsız dönüştürme sağlar, tüm reklamları kaldırır, toplu dönüştürme ve Pro Araçlar'ı açar ve dönüştürme geçmişini 30 güne uzatır.")
                }

                Group {
                    Text("Aboneliğin Yenilenmesi ve İptali").font(.headline)
                    Text("• Ödeme, satın alma onayında Apple Kimliği hesabınıza yansıtılır.\n• Abonelik, mevcut dönemin bitiminden en az 24 saat önce kapatılmadığı sürece otomatik olarak yenilenir.\n• Yenileme ücreti, dönem bitiminden önceki 24 saat içinde tahsil edilir.\n• Aboneliğinizi App Store > Apple Kimliği > Abonelikler bölümünden yönetebilir veya iptal edebilirsiniz. Mevcut dönem içinde iptal edilen abonelikler dönem sonuna kadar geçerli kalır.")
                    Text("Satın alma işlemleri Apple App Store üzerinden gerçekleştirilir ve Apple'ın standart iade politikalarına tabidir. İade talepleri için doğrudan Apple ile iletişime geçmeniz gerekmektedir.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    Text("Gizlilik").font(.headline)
                    Text("Tüm dönüştürme işlemleri cihazınız üzerinde gerçekleşir. Belgeleriniz hiçbir sunucuya gönderilmez.")

                    Text("Değişiklikler").font(.headline)
                    Text("Bu kullanım koşulları zaman zaman güncellenebilir. Önemli değişiklikler uygulama içinden bildirilecektir. Uygulamayı kullanmaya devam etmeniz, güncel koşulları kabul ettiğiniz anlamına gelir.")

                    Text("İletişim").font(.headline)
                    Text("Kullanım koşulları hakkındaki sorularınız için:")
                    Text("E-posta: info@velikececi.com")
                }

                VStack(alignment: .leading, spacing: 8) {
                    Link(destination: AppLinks.termsOfUse) {
                        Label("Kullanım koşullarını web'de görüntüle", systemImage: "safari")
                            .font(.subheadline)
                    }

                    Link(destination: AppLinks.appleEULA) {
                        Label("Apple Standart Lisans Sözleşmesi (EULA)", systemImage: "doc.badge.gearshape")
                            .font(.subheadline)
                    }
                }
                .padding(.top, 4)
            }
            .padding()
        }
        .navigationTitle("Koşullar")
        .navigationBarTitleDisplayMode(.inline)
    }
}
