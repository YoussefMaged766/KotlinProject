import Foundation
@preconcurrency import SharedLogic

/// The Kotlin domain model. A bare `Category` clashes with the Objective-C runtime's
/// `typedef struct objc_category *Category`, so we name the module explicitly.
typealias DomainCategory = SharedLogic.Category

/// What a category row renders. Text is already decided here, so views make no decisions.
struct CategoryUi: Identifiable, Equatable {
    let id: String
    let name: String
    let subtitle: String?
}

/// Domain -> UI, one item. The iOS counterpart of Android's CategoryUiMapper.
struct CategoryUiMapper {
    func map(_ input: DomainCategory) -> CategoryUi {
        CategoryUi(
            id: input.id,
            name: input.name,
            subtitle: input.isAll
                ? String(localized: "categories_all_subtitle", defaultValue: "Shows every restaurant")
                : nil
        )
    }
}

/// Domain -> UI, whole list. Delegates to the item mapper.
struct CategoryUiListMapper {
    private let itemMapper = CategoryUiMapper()

    func map(_ input: [DomainCategory]) -> [CategoryUi] {
        input.map(itemMapper.map)
    }
}

/// DataError -> the message the user sees.
struct ErrorUiMapper {
    func map(_ error: any DataError) -> String {
        switch error {
        case is DataErrorNoInternet:
            return String(localized: "error_no_internet", defaultValue: "No connection. Check your internet and try again.")
        case is DataErrorTimeout:
            return String(localized: "error_timeout", defaultValue: "The server took too long to answer. Try again.")
        case let server as DataErrorServer:
            let format = String(localized: "error_server", defaultValue: "Server error %@: %@")
            return String(format: format, String(server.code), server.message)
        default:
            return String(localized: "error_bad_response", defaultValue: "The server sent something the app couldn't read.")
        }
    }
}
