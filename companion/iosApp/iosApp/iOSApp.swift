import SwiftUI

@main
struct iOSApp: App {
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
    }
}