import Foundation
import Combine
import UIKit

final class LimitService: ObservableObject {
    static let shared = LimitService()

    private let dailyLimitKey = "dailyConversionCount"
    private let lastResetDateKey = "lastResetDate"
    private let premiumKey = "isPremiumUser"
    private let maxFreeConversions = 3
    private let bonusConversionsKey = "bonusConversions"

    @Published var remainingConversions: Int = 3
    @Published var isPremium: Bool = false

    private var cancellables = Set<AnyCancellable>()

    private init() {
        isPremium = UserDefaults.standard.bool(forKey: premiumKey)
        resetIfNewDay()
        updateRemaining()

        // Uygulama ön plana geldiğinde günü kontrol et
        NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)
            .sink { [weak self] _ in
                self?.resetIfNewDay()
                self?.updateRemaining()
            }
            .store(in: &cancellables)
    }

    var canConvert: Bool {
        isPremium || remainingConversions > 0
    }

    func useConversion(count: Int = 1) -> Bool {
        guard canConvert else { return false }
        if isPremium { return true }

        resetIfNewDay()
        let used = UserDefaults.standard.integer(forKey: dailyLimitKey)
        let bonus = UserDefaults.standard.integer(forKey: bonusConversionsKey)
        let totalAllowed = maxFreeConversions + bonus

        guard used + count <= totalAllowed else { return false }

        UserDefaults.standard.set(used + count, forKey: dailyLimitKey)
        updateRemaining()
        return true
    }

    func addBonusConversions(_ count: Int) {
        let current = UserDefaults.standard.integer(forKey: bonusConversionsKey)
        UserDefaults.standard.set(current + count, forKey: bonusConversionsKey)
        updateRemaining()
    }

    func activatePremium() {
        UserDefaults.standard.set(true, forKey: premiumKey)
        isPremium = true
        remainingConversions = Int.max
    }

    func restorePremiumStatus(_ isPremium: Bool) {
        UserDefaults.standard.set(isPremium, forKey: premiumKey)
        self.isPremium = isPremium
        updateRemaining()
    }

    private func resetIfNewDay() {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())

        if let lastReset = UserDefaults.standard.object(forKey: lastResetDateKey) as? Date {
            let lastResetDay = calendar.startOfDay(for: lastReset)
            if today > lastResetDay {
                UserDefaults.standard.set(0, forKey: dailyLimitKey)
                UserDefaults.standard.set(0, forKey: bonusConversionsKey)
                UserDefaults.standard.set(today, forKey: lastResetDateKey)
            }
        } else {
            UserDefaults.standard.set(today, forKey: lastResetDateKey)
        }
    }

    private func updateRemaining() {
        if isPremium {
            remainingConversions = Int.max
            return
        }
        resetIfNewDay()
        let used = UserDefaults.standard.integer(forKey: dailyLimitKey)
        let bonus = UserDefaults.standard.integer(forKey: bonusConversionsKey)
        remainingConversions = max(0, maxFreeConversions + bonus - used)
    }
}
