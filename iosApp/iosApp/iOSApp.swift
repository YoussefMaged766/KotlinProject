import SwiftUI
@preconcurrency import SharedLogic

@main
struct iOSApp: App {

    init() {
        // Exactly once per launch, before any view asks for a dependency.
        // Study project: dev server only for now.
        IosKoin.shared.start(
            environment: .dev,
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0",
            languageTag: Locale.current.language.languageCode?.identifier ?? "en"
        )
    }

    var body: some Scene {
        WindowGroup {
            AppRootView(app: IosKoin.shared.app()) {
                CategoriesView(screen: IosKoin.shared.categoriesScreen())
            }
        }
    }
}
