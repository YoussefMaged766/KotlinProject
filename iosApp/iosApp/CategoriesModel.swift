import Foundation
@preconcurrency import SharedLogic

/// The iOS counterpart of CategoriesViewModel: State + Intent + a single `send(_:)` entry point.
/// Loading follows the shared rules in ScreenLoader; only what is specific to this screen lives here.
@MainActor
final class CategoriesModel: ObservableObject {

    struct State: Equatable {
        enum Content: Equatable {
            case loading
            case loaded([CategoryUi])
            case empty
            case failed(ErrorUi)
        }

        var content = Content.loading
        var isRefreshing = false
        var isSlow = false
    }

    enum Intent {
        case appeared
        case disappeared
        case retry
        case refresh
        case categoryTapped(id: String)
    }

    @Published private(set) var state = State()

    let errorHost = ErrorHost()

    /// Effect: one-off navigation, handled by whoever owns the view.
    var onOpenCategory: (String) -> Void = { categoryId in print("Open \(categoryId)") }

    // Only used on the main actor; unsafe just lets deinit, which belongs to no actor, close it.
    private nonisolated(unsafe) let screen: IosScreen<NSArray>
    private var stateSubscription: FlowSubscription?
    private var effectSubscription: FlowSubscription?

    init(screen: IosScreen<NSArray>) {
        self.screen = screen
        screen.load()
    }

    deinit { screen.close() }

    /// The only entry point, like onIntent on Android.
    func send(_ intent: Intent) {
        switch intent {
        case .appeared: startWatching()
        case .disappeared: stopWatching()
        case .retry, .refresh: screen.load()
        case .categoryTapped(let id): onOpenCategory(id)
        }
    }

    /// For pull to refresh: SwiftUI shows its spinner until this returns.
    func refresh() async {
        send(.refresh)
        repeat {
            try? await Task.sleep(for: .milliseconds(100))
        } while state.isRefreshing && !Task.isCancelled
    }

    /// Only while visible: ScreenLoader auto reloads the screens that are being watched.
    private func startWatching() {
        guard stateSubscription == nil else { return }
        stateSubscription = screen.watchState { [weak self] state in self?.state = toCategoriesState(state) }
        effectSubscription = screen.watchEffects { [weak self] effect in self?.errorHost.show(effect) }
    }

    private func stopWatching() {
        stateSubscription?.cancel()
        stateSubscription = nil
        effectSubscription?.cancel()
        effectSubscription = nil
    }
}

/// Shared state -> what the view renders. A function, because Swift can't extend a generic Kotlin class for one type
/// argument. Swift sees Kotlin's List as NSArray, hence the casts.
private func toCategoriesState(_ shared: ScreenState<NSArray>) -> CategoriesModel.State {
    let content: CategoriesModel.State.Content
    if let loaded = shared.content as? ContentLoaded<NSArray> {
        content = .loaded((loaded.data as? [DomainCategory] ?? []).toUi())
    } else if let failed = shared.content as? ContentFailed {
        content = .failed(failed.display.message.toUi(canRetry: failed.display.canRetry))
    } else if shared.content is ContentEmpty {
        content = .empty
    } else {
        content = .loading
    }
    return CategoriesModel.State(content: content, isRefreshing: shared.isRefreshing, isSlow: shared.isSlow)
}
