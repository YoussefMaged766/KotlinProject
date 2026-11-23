import SwiftUI
@preconcurrency import SharedLogic

@main
struct iOSApp: App {

    init() {
        // Exactly once per launch, before any view asks for a dependency.
        IosKoin.shared.start(config: ApiConfig(
            baseUrl: ApiConfig.companion.DEV_BASE_URL,
            platform: "ios",
            appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0",
            languageTag: Locale.current.language.languageCode?.identifier ?? "en"
        ))
    }

    var body: some Scene {
        WindowGroup {
            CategoriesView(getCategoriesUseCase: IosKoin.shared.getCategoriesUseCase())
        }
    }
}
