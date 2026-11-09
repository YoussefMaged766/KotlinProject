import SwiftUI
@preconcurrency import SharedLogic

@main
struct iOSApp: App {
    // One AppGraph (and so one HttpClient) for the life of the app, like KotlinProjectApp on Android.
    private let graph = AppGraph(config: ApiConfig(
        baseUrl: ApiConfig.companion.DEV_BASE_URL,
        platform: "ios",
        appVersion: Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0",
        languageTag: Locale.current.language.languageCode?.identifier ?? "en"
    ))

    var body: some Scene {
        WindowGroup {
            CategoriesView(getCategoriesUseCase: graph.getCategoriesUseCase)
        }
    }
}
