import SwiftUI

struct OnboardingView: View {
    @Binding var hasCompletedOnboarding: Bool

    @State private var currentPage = 0

    private let pages: [(icon: String, title: String, description: String)] = [
        ("doc.zipper", "UYAP UDF Dosyalarını Açın",
         "UYAP sisteminden indirdiğiniz .udf uzantılı belgeleri doğrudan iPhone'unuzda açın ve dönüştürün."),
        ("lock.shield", "Gizliliğiniz Bizim İçin Öncelik",
         "Tüm dönüştürme işlemleri cihazınızda gerçekleşir. Belgeleriniz hiçbir sunucuya gönderilmez."),
        ("arrow.triangle.2.circlepath", "PDF veya Word Formatında",
         "UDF dosyalarınızı tek dokunuşla PDF veya Microsoft Word (.docx) formatına çevirin ve paylaşın.")
    ]

    var body: some View {
        VStack(spacing: 0) {
            TabView(selection: $currentPage) {
                ForEach(0..<pages.count, id: \.self) { index in
                    onboardingPage(pages[index])
                        .tag(index)
                }
            }
            .tabViewStyle(.page(indexDisplayMode: .always))

            VStack(spacing: 12) {
                Button {
                    if currentPage < pages.count - 1 {
                        withAnimation { currentPage += 1 }
                    } else {
                        completeOnboarding()
                    }
                } label: {
                    Text(currentPage < pages.count - 1 ? "Devam" : "Başla")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(AppTheme.navy, in: RoundedRectangle(cornerRadius: 14))
                        .foregroundStyle(.white)
                }

                if currentPage < pages.count - 1 {
                    Button("Atla") {
                        completeOnboarding()
                    }
                    .foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 32)
            .padding(.bottom, 40)
        }
    }

    private func onboardingPage(_ page: (icon: String, title: String, description: String)) -> some View {
        VStack(spacing: 24) {
            Spacer()

            ZStack {
                Circle()
                    .fill(AppTheme.navy.opacity(0.12))
                    .frame(width: 140, height: 140)
                Image(systemName: page.icon)
                    .font(.system(size: 60))
                    .foregroundStyle(AppTheme.navy)
            }
            .padding(.bottom, 8)

            Text(page.title)
                .font(.title2).bold()
                .multilineTextAlignment(.center)

            Text(page.description)
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 32)

            Spacer()
            Spacer()
        }
    }

    private func completeOnboarding() {
        UserDefaults.standard.set(true, forKey: "hasCompletedOnboarding")
        withAnimation { hasCompletedOnboarding = true }
    }
}
