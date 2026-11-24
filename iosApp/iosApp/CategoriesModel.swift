import Foundation
@preconcurrency import SharedLogic

/// The iOS counterpart of CategoriesViewModel: State + Intent + a single `send(_:)` entry point.
@MainActor
final class CategoriesModel: ObservableObject {

    struct State {
        var isLoading = true
        var categories: [CategoryUi] = []
        var error: ErrorUi? = nil
    }

    enum Intent {
        case loadCategories
        case retry
        case categoryTapped(id: String)
    }

    @Published private(set) var state = State()

    /// Effect: one-off navigation, handled by whoever owns the view.
    var onOpenCategory: (String) -> Void = { categoryId in print("Open \(categoryId)") }

    private let getCategoriesUseCase: GetCategoriesUseCase
    private let errorPresenter: ErrorPresenter
    private var loadTask: Task<Void, Never>?

    init(getCategoriesUseCase: GetCategoriesUseCase, errorPresenter: ErrorPresenter) {
        self.getCategoriesUseCase = getCategoriesUseCase
        self.errorPresenter = errorPresenter
    }

    /// The only entry point, like onIntent on Android.
    func send(_ intent: Intent) {
        switch intent {
        case .loadCategories, .retry:
            loadTask?.cancel()
            loadTask = Task { await loadCategories() }
        case .categoryTapped(let id):
            onOpenCategory(id)
        }
    }

    private func loadCategories() async {
        state.isLoading = true
        state.error = nil

        let result = await fetchCategories()
        if let success = result as? ApiResultSuccess<NSArray> {
            state.categories = (success.data as? [DomainCategory] ?? []).toUi()
        } else {
            let error: any DataError = (result as? ApiResultFailure)?.error ?? DataErrorUnknown(details: nil)
            state.error = errorPresenter.present(error: error, kind: .initialLoad).toErrorUi()
        }
        state.isLoading = false
    }

    /// Kotlin suspend functions must be started on the main thread. We are on the main actor here,
    /// so we call the completion-handler form directly instead of Swift's `async` import.
    private func fetchCategories() async -> (any ApiResult)? {
        let box = await withCheckedContinuation { (continuation: CheckedContinuation<KotlinResultBox, Never>) in
            getCategoriesUseCase.invoke { result, _ in
                continuation.resume(returning: KotlinResultBox(value: result))
            }
        }
        return box.value
    }
}

/// Kotlin objects aren't marked Sendable, so Swift 6 refuses to pass one through a continuation.
/// Kotlin calls the completion handler on the main thread, and we read it on the main actor,
/// so crossing here is safe; the box just tells the compiler so.
private struct KotlinResultBox: @unchecked Sendable {
    let value: (any ApiResult)?
}
