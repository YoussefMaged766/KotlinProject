import Foundation
@preconcurrency import SharedLogic

/// One error, ready to draw full screen, in a toast or in a dialog.
struct ErrorUi: Equatable {
    let text: String
    let technicalDetails: String?
    let canRetry: Bool
}

extension ErrorMessage {
    func toUi(canRetry: Bool) -> ErrorUi {
        ErrorUi(text: serverText ?? key.toText(), technicalDetails: technicalDetails, canRetry: canRetry)
    }
}

/// The message table: the only place on iOS where error wording lives. Keep it in step with Android's strings.xml.
/// Kotlin exports enum entries to Swift in all lowercase: NoInternet is .nointernet.
extension MessageKey {
    func toText() -> String {
        switch self {
        case .nointernet:
            return String(localized: "error_no_internet", defaultValue: "No connection. Check your internet and try again.")
        case .connectionlost:
            return String(localized: "error_connection_lost", defaultValue: "The connection dropped. Check your internet and try again.")
        case .timeout:
            return String(localized: "error_timeout", defaultValue: "The server took too long to answer. Try again.")
        case .servererror:
            return String(localized: "error_server", defaultValue: "The server had a problem. Try again in a moment.")
        case .ratelimited:
            return String(localized: "error_rate_limited", defaultValue: "Too many requests. Wait a moment, then try again.")
        case .notallowed:
            return String(localized: "error_not_allowed", defaultValue: "You don't have access to this.")
        case .notfound:
            return String(localized: "error_not_found", defaultValue: "This is no longer available.")
        case .invalidinput:
            return String(localized: "error_invalid_input", defaultValue: "Some of the information isn't valid. Check it and try again.")
        case .outcomeunknown:
            return String(localized: "error_outcome_unknown", defaultValue: "We couldn't confirm whether this went through. Check before trying again.")
        default:
            return String(localized: "error_generic", defaultValue: "Something went wrong.")
        }
    }
}
