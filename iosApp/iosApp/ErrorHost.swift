import SwiftUI
@preconcurrency import SharedLogic

/// Draws the errors that aren't part of a screen's state: toasts and dialogs. One per screen, like Android's ErrorHostState.
@MainActor
final class ErrorHost: ObservableObject {

    struct Toast: Equatable {
        let id = UUID()
        let error: ErrorUi
    }

    /// The newest toast replaces the one on screen, so they never queue up.
    @Published private(set) var toast: Toast?
    @Published var dialog: ErrorUi?

    func show(_ effect: any LoadEffect) {
        if let showError = effect as? LoadEffectShowError {
            show(showError.display)
        }
    }

    private func show(_ display: any ErrorDisplay) {
        if let toast = display as? ErrorDisplayToast {
            self.toast = Toast(error: toast.message.toUi(canRetry: toast.canRetry))
        } else if let dialog = display as? ErrorDisplayDialog {
            self.dialog = dialog.message.toUi(canRetry: dialog.canRetry)
        }
        // Full screen, inline, silent and app actions are drawn by the screen, its fields or the app root.
    }

    func dismissToast() { toast = nil }
}

extension View {
    /// Place once per screen.
    func errorHost(_ host: ErrorHost, onRetry: @escaping () -> Void) -> some View {
        ErrorHostView(host: host, onRetry: onRetry, screen: self)
    }
}

/// A view rather than a ViewModifier: that protocol needs the name Content, which SharedLogic also exports.
private struct ErrorHostView<Screen: View>: View {
    @ObservedObject var host: ErrorHost
    let onRetry: () -> Void
    let screen: Screen

    var body: some View {
        screen
            .overlay(alignment: .bottom) {
                if let toast = host.toast {
                    ToastView(error: toast.error) {
                        host.dismissToast()
                        onRetry()
                    }
                    .task(id: toast.id) {
                        UIAccessibility.post(notification: .announcement, argument: toast.error.text)
                        try? await Task.sleep(for: .seconds(10))
                        if !Task.isCancelled { host.dismissToast() }
                    }
                }
            }
            .alert(
                host.dialog?.text ?? "",
                isPresented: Binding(
                    get: { host.dialog != nil },
                    set: { isPresented in if !isPresented { host.dialog = nil } }
                ),
                presenting: host.dialog
            ) { dialog in
                if dialog.canRetry {
                    Button(String(localized: "action_try_again", defaultValue: "Try again"), action: onRetry)
                    Button(String(localized: "action_cancel", defaultValue: "Cancel"), role: .cancel) {}
                } else {
                    Button(String(localized: "action_ok", defaultValue: "OK")) {}
                }
            } message: { dialog in
                if let details = dialog.technicalDetails { Text(details) }
            }
    }
}

private struct ToastView: View {
    let error: ErrorUi
    let onRetry: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(error.text)
                if let details = error.technicalDetails { Text(details).font(.caption) }
            }
            Spacer(minLength: 0)
            if error.canRetry {
                Button(String(localized: "action_try_again", defaultValue: "Try again"), action: onRetry)
            }
        }
        .padding(16)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 12))
        .padding(16)
    }
}

struct FullScreenErrorView: View {
    let error: ErrorUi
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Text(error.text).multilineTextAlignment(.center)
            if let details = error.technicalDetails {
                Text(details).font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.center)
            }
            if error.canRetry {
                Button(String(localized: "action_try_again", defaultValue: "Try again"), action: onRetry)
            }
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Shown while the state says a load is slow.
struct SlowConnectionHint: View {
    private let text = String(localized: "slow_connection", defaultValue: "Slow connection, still trying…")

    var body: some View {
        Text(text)
            .font(.subheadline)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(Color.yellow.opacity(0.2))
            .onAppear { UIAccessibility.post(notification: .announcement, argument: text) }
    }
}
