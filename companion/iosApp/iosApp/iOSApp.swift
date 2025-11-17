import SwiftUI

@main
struct iOSApp: App {
    @Environment(\.scenePhase) private var scenePhase

    init() {
        // Disable idle timer to keep display on while app is running
        // This prevents the screen from auto-locking and turning off
        // Useful for development/testing and when actively monitoring heart rate
        UIApplication.shared.isIdleTimerDisabled = true
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
        .onChange(of: scenePhase) { newPhase in
            // Re-enable screen wake when app becomes active
            // This ensures the setting persists after backgrounding
            if newPhase == .active {
                UIApplication.shared.isIdleTimerDisabled = true
            }
        }
    }
}