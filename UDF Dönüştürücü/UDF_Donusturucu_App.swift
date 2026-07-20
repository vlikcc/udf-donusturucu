import SwiftUI
import FirebaseCore

@main
struct UDF_Donusturucu_App: App {
    @State private var hasCompletedOnboarding = UserDefaults.standard.bool(forKey: "hasCompletedOnboarding")
    @State private var showIntroPaywall = false

    private static let hasSeenIntroPaywallKey = "hasSeenIntroPaywall"

    init() {
        // GoogleService-Info.plist projeye eklenmeden configure() çağrılırsa uygulama çöker.
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
        }
        AdsManager.shared.configure()
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if hasCompletedOnboarding {
                    MainTabView()
                } else {
                    OnboardingView(hasCompletedOnboarding: $hasCompletedOnboarding)
                }
            }
            .fullScreenCover(isPresented: $showIntroPaywall) {
                PaywallView(source: "onboarding")
            }
            .onChange(of: hasCompletedOnboarding) { _, completed in
                // Onboarding'i yeni bitiren kullanıcıya paywall'ı bir kez göster.
                if completed { presentIntroPaywallIfNeeded() }
            }
            .onOpenURL { url in
                IncomingFileRouter.shared.handle(url: url)
            }
            .task {
                await AdsManager.shared.requestATTIfNeeded()
                // Onboarding'i daha önce tamamlamış (mevcut) kullanıcılar yeni paywall'ı bir kez görür.
                if hasCompletedOnboarding { presentIntroPaywallIfNeeded() }
            }
        }
    }

    private func presentIntroPaywallIfNeeded() {
        guard !UserDefaults.standard.bool(forKey: Self.hasSeenIntroPaywallKey),
              !LimitService.shared.isPremium else { return }
        UserDefaults.standard.set(true, forKey: Self.hasSeenIntroPaywallKey)
        showIntroPaywall = true
    }
}

struct MainTabView: View {
    @ObservedObject private var fileRouter = IncomingFileRouter.shared
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack {
                ContentView()
            }
            .tabItem {
                Image(systemName: "house.fill")
                Text("ANA SAYFA")
            }
            .tag(0)

            NavigationStack {
                ToolsView()
            }
            .tabItem {
                Image(systemName: "wrench.and.screwdriver.fill")
                Text("ARAÇLAR")
            }
            .tag(1)

            NavigationStack {
                HistoryView()
            }
            .tabItem {
                Image(systemName: "clock.arrow.circlepath")
                Text("GEÇMİŞ")
            }
            .tag(2)

            NavigationStack {
                SettingsView()
            }
            .tabItem {
                Image(systemName: "gearshape.fill")
                Text("AYARLAR")
            }
            .tag(3)
        }
        .tint(Color("AccentNavy"))
        .onChange(of: fileRouter.incomingFile) { _, url in
            // Dışarıdan dosya geldiğinde ana sayfaya geç — ContentView dosyayı listeye ekler.
            if url != nil { selectedTab = 0 }
        }
    }
}
