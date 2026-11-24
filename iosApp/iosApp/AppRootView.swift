import SwiftUI
@preconcurrency import SharedLogic

/// The iOS counterpart of AppViewModel: the shared AppStatus as something SwiftUI can observe.
@MainActor
final class AppModel: ObservableObject {

    struct State: Equatable {
        var banner: Banner? = nil
        var block: Block? = nil
        var isSessionExpired = false
    }

    enum Banner { case offline, backOnline }
    enum Block { case upgradeRequired, maintenance }

    enum Intent {
        case retryAfterMaintenance
        case sessionExpiredConfirmed
    }

    @Published private(set) var state: State

    private let app: IosApp
    private var subscription: FlowSubscription?

    init(app: IosApp) {
        self.app = app
        state = app.initialState.toUi()
        subscription = app.watchState { [weak self] state in self?.state = state.toUi() }
    }

    func send(_ intent: Intent) {
        switch intent {
        case .retryAfterMaintenance: app.retryAfterMaintenance()
        case .sessionExpiredConfirmed: app.sessionExpiredConfirmed()
        }
    }
}

extension AppState {
    func toUi() -> AppModel.State {
        AppModel.State(
            banner: banner.map { $0 == NetworkBanner.offline ? AppModel.Banner.offline : AppModel.Banner.backOnline },
            block: blockedBy.map { $0 == AppBlock.upgraderequired ? AppModel.Block.upgradeRequired : AppModel.Block.maintenance },
            isSessionExpired: isSessionExpired
        )
    }
}

/// Wraps every screen. Nothing inside it needs to know about the banner or the global events.
struct AppRootView<Screen: View>: View {
    @StateObject private var model: AppModel
    private let screen: Screen

    init(app: @escaping @autoclosure () -> IosApp, @ViewBuilder screen: () -> Screen) {
        _model = StateObject(wrappedValue: AppModel(app: app()))
        self.screen = screen()
    }

    var body: some View {
        // The screen stays underneath a blocking screen: removing it would throw away its model and its data.
        ZStack {
            VStack(spacing: 0) {
                if let banner = model.state.banner {
                    NetworkBannerView(banner: banner).transition(.move(edge: .top).combined(with: .opacity))
                }
                screen
            }
            .animation(.default, value: model.state.banner)
            .accessibilityHidden(model.state.block != nil)

            if let block = model.state.block {
                BlockingView(block: block) { model.send(.retryAfterMaintenance) }
            }
        }
        // Interim: the app has no login yet. With one, confirming also clears the session and opens the login screen.
        .alert(
            String(localized: "session_expired_message", defaultValue: "Your session has expired. Please sign in again."),
            isPresented: Binding(
                get: { model.state.isSessionExpired },
                set: { isPresented in if !isPresented { model.send(.sessionExpiredConfirmed) } }
            )
        ) {
            Button(String(localized: "action_ok", defaultValue: "OK")) { model.send(.sessionExpiredConfirmed) }
        }
    }
}

struct NetworkBannerView: View {
    let banner: AppModel.Banner

    private var text: String {
        switch banner {
        case .offline: return String(localized: "banner_offline", defaultValue: "No internet connection")
        case .backOnline: return String(localized: "banner_back_online", defaultValue: "Back online")
        }
    }

    var body: some View {
        Text(text)
            .font(.subheadline.weight(.medium))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background((banner == .offline ? Color.red : Color.green).opacity(0.3), ignoresSafeAreaEdges: .top)
            .task(id: banner) { UIAccessibility.post(notification: .announcement, argument: text) }
    }
}

struct BlockingView: View {
    let block: AppModel.Block
    let onRetry: () -> Void

    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(spacing: 12) {
            switch block {
            case .upgradeRequired:
                Text(String(localized: "upgrade_title", defaultValue: "Update required")).font(.title2)
                Text(String(localized: "upgrade_message", defaultValue: "This version no longer works. Update the app to continue."))
                Button(String(localized: "action_update", defaultValue: "Update")) {
                    // The app has no App Store page yet, so this only opens the store.
                    if let store = URL(string: "itms-apps://apps.apple.com") { openURL(store) }
                }
                .buttonStyle(.borderedProminent)
            case .maintenance:
                Text(String(localized: "maintenance_title", defaultValue: "We'll be right back")).font(.title2)
                Text(String(localized: "maintenance_message", defaultValue: "We're doing some maintenance. Please try again in a few minutes."))
                Button(String(localized: "action_try_again", defaultValue: "Try again"), action: onRetry)
                    .buttonStyle(.borderedProminent)
            }
        }
        .multilineTextAlignment(.center)
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground).ignoresSafeArea())
    }
}
