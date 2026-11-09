import SwiftUI
@preconcurrency import SharedLogic

/// Thin assembler: picks which pre-computed state to show.
struct CategoriesView: View {
    @StateObject private var model: CategoriesModel

    init(getCategoriesUseCase: GetCategoriesUseCase) {
        _model = StateObject(wrappedValue: CategoriesModel(getCategoriesUseCase: getCategoriesUseCase))
    }

    var body: some View {
        NavigationStack {
            Group {
                if model.state.isLoading {
                    CategoriesShimmerList()
                } else if let message = model.state.errorMessage {
                    CategoriesErrorSection(message: message) { model.send(.retry) }
                } else {
                    CategoriesList(categories: model.state.categories) { model.send(.categoryTapped(id: $0)) }
                }
            }
            .navigationTitle(String(localized: "categories_title", defaultValue: "Categories"))
        }
        .onAppear { model.send(.loadCategories) }
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

struct CategoriesErrorSection: View {
    let message: String
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Text(message).multilineTextAlignment(.center)
            Button(String(localized: "action_try_again", defaultValue: "Try again"), action: onRetry)
        }
        .padding(24)
    }
}
