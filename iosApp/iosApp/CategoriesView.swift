import SwiftUI
@preconcurrency import SharedLogic

/// Thin assembler: picks which pre-computed state to show.
struct CategoriesView: View {
    @StateObject private var model: CategoriesModel

    init(screen: @escaping @autoclosure () -> IosScreen<NSArray>) {
        // wrappedValue is an autoclosure too: the screen is built once, not on every redraw of the parent.
        _model = StateObject(wrappedValue: CategoriesModel(screen: screen()))
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if model.state.isSlow { SlowConnectionHint() }
                content
            }
            .navigationTitle(String(localized: "categories_title", defaultValue: "Categories"))
        }
        .errorHost(model.errorHost) { model.send(.retry) }
        .onAppear { model.send(.appeared) }
        .onDisappear { model.send(.disappeared) }
    }

    @ViewBuilder
    private var content: some View {
        switch model.state.content {
        case .loading:
            CategoriesShimmerList()
        case .failed(let error):
            FullScreenErrorView(error: error) { model.send(.retry) }
        case .loaded(let categories):
            CategoriesList(categories: categories) { model.send(.categoryTapped(id: $0)) }
                .refreshable { await model.refresh() }
        case .empty:
            CategoriesEmptyList()
                .refreshable { await model.refresh() }
        }
    }
}

struct CategoriesList: View {
    let categories: [CategoryUi]
    let onCategoryTapped: (String) -> Void

    var body: some View {
        List(categories) { category in
            CategoryRow(category: category)
                .contentShape(Rectangle())
                .onTapGesture { onCategoryTapped(category.id) }
        }
    }
}

/// One item: plain data in. No list logic.
struct CategoryRow: View {
    let category: CategoryUi

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(category.name)
            if let subtitle = category.subtitle {
                Text(subtitle).font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

/// A list, so pulling to refresh works here too.
struct CategoriesEmptyList: View {
    var body: some View {
        List {
            Text(String(localized: "categories_empty", defaultValue: "No categories yet."))
                .frame(maxWidth: .infinity)
                .listRowBackground(Color.clear)
        }
    }
}

/// Same shape as CategoryRow, redacted while loading.
struct CategoriesShimmerList: View {
    var body: some View {
        List(0..<6, id: \.self) { _ in
            CategoryRow(category: CategoryUi(id: "", name: "Placeholder category", subtitle: nil))
                .redacted(reason: .placeholder)
        }
        .disabled(true)
    }
}
