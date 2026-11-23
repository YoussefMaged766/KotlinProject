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

/// Domain -> UI, the Swift twin of Android's Category.toUi().
extension DomainCategory {
    func toUi() -> CategoryUi {
        CategoryUi(
            id: id,
            name: name,
            subtitle: isAll
                ? String(localized: "categories_all_subtitle", defaultValue: "Shows every restaurant")
                : nil
        )
    }
}

extension Array where Element == DomainCategory {
    func toUi() -> [CategoryUi] { map { $0.toUi() } }
}

/// DataError -> the message the user sees, the Swift twin of Android's DataError.toErrorUi().
extension DataError {
    func toMessage() -> String {
        switch self {
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
