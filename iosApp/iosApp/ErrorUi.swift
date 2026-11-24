import Foundation
@preconcurrency import SharedLogic

/// What an error section renders. The Swift twin of Android's ErrorUi.
struct ErrorUi: Equatable {
    let text: String
    let technicalDetails: String?
    let canRetry: Bool
}

extension ErrorDisplay {
    /// Interim until phase 9's error host: this screen only has room for a full-screen error, so every other
    /// display falls back to a generic one instead of leaving the screen empty.
    func toErrorUi() -> ErrorUi {
        guard let fullScreen = self as? ErrorDisplayFullScreen else {
            return ErrorUi(text: MessageKey.generic.toText(), technicalDetails: nil, canRetry: true)
        }
        return ErrorUi(
            text: fullScreen.message.serverText ?? fullScreen.message.key.toText(),
            technicalDetails: fullScreen.message.technicalDetails,
            canRetry: fullScreen.canRetry
        )
    }
}

/// The message table: the only place on iOS where error wording lives. Keep it in step with Android's strings.xml.
extension MessageKey {
    func toText() -> String {
        switch self {
        case .noInternet:
            return String(localized: "error_no_internet", defaultValue: "No connection. Check your internet and try again.")
        case .connectionLost:
            return String(localized: "error_connection_lost", defaultValue: "The connection dropped. Check your internet and try again.")
        case .timeout:
            return String(localized: "error_timeout", defaultValue: "The server took too long to answer. Try again.")
        case .serverError:
            return String(localized: "error_server", defaultValue: "The server had a problem. Try again in a moment.")
        case .rateLimited:
            return String(localized: "error_rate_limited", defaultValue: "Too many requests. Wait a moment, then try again.")
        case .notAllowed:
            return String(localized: "error_not_allowed", defaultValue: "You don't have access to this.")
        case .notFound:
            return String(localized: "error_not_found", defaultValue: "This is no longer available.")
        case .invalidInput:
            return String(localized: "error_invalid_input", defaultValue: "Some of the information isn't valid. Check it and try again.")
        case .outcomeUnknown:
            return String(localized: "error_outcome_unknown", defaultValue: "We couldn't confirm whether this went through. Check before trying again.")
        default:
            return String(localized: "error_generic", defaultValue: "Something went wrong.")
        }
    }
}
