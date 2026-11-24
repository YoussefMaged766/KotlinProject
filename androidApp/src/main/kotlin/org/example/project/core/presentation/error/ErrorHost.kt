package org.example.project.core.presentation.error

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.example.project.R
import org.example.project.core.error.ErrorDisplay
import org.example.project.core.error.ErrorMessage
import org.example.project.core.error.MessageKey

/**
 * Draws the errors that aren't part of a screen's state: toasts (as snackbars, which can carry a Retry) and dialogs.
 * One per screen. Full-screen errors are state instead: see [FullScreenError].
 */
@Stable
class ErrorHostState internal constructor(
    private val resources: Resources,
    internal val onRetry: () -> Unit,
    dialog: ErrorDisplay.Dialog? = null,
) {
    val snackbar = SnackbarHostState()

    internal var dialog by mutableStateOf(dialog)
        private set

    /** Suspends while a snackbar is on screen. Other displays return at once. */
    suspend fun show(display: ErrorDisplay) {
        when (display) {
            is ErrorDisplay.Toast -> {
                val result = showSnackbar(display.message.text(), resources.getString(R.string.action_try_again).takeIf { display.canRetry }, SnackbarDuration.Long)
                if (result == SnackbarResult.ActionPerformed) onRetry()
            }

            is ErrorDisplay.Dialog -> dialog = display

            // The screen's own state, its fields, or the app root shows these.
            is ErrorDisplay.FullScreen, is ErrorDisplay.Inline, is ErrorDisplay.AppAction, ErrorDisplay.Silent -> Unit
        }
    }

    suspend fun showNotice(text: String) {
        showSnackbar(text, actionLabel = null, SnackbarDuration.Short)
    }

    internal fun dismissDialog() {
        dialog = null
    }

    /** The newest message replaces the one on screen, so they never queue up (M6). */
    private suspend fun showSnackbar(text: String, actionLabel: String?, duration: SnackbarDuration): SnackbarResult {
        snackbar.currentSnackbarData?.dismiss()
        return snackbar.showSnackbar(text, actionLabel, duration = duration)
    }

    private fun ErrorMessage.text(): String =
        listOfNotNull(serverText ?: resources.getString(key.toStringRes()), technicalDetails).joinToString("\n")
}

/** An open dialog survives rotation and process death: the user may not have read it yet. */
@Composable
fun rememberErrorHostState(onRetry: () -> Unit): ErrorHostState {
    val resources = LocalResources.current
    val currentOnRetry by rememberUpdatedState(onRetry)
    return rememberSaveable(
        saver = listSaver(
            save = { host -> host.dialog?.toSaveable().orEmpty() },
            restore = { saved -> ErrorHostState(resources, { currentOnRetry() }, saved.toDialog()) },
        ),
    ) { ErrorHostState(resources, { currentOnRetry() }) }
}

private fun ErrorDisplay.Dialog.toSaveable(): List<Any?> =
    listOf(message.key.name, message.serverText, message.technicalDetails, canRetry)

private fun List<Any?>.toDialog() = ErrorDisplay.Dialog(
    message = ErrorMessage(
        key = MessageKey.valueOf(this[0] as String),
        serverText = this[1] as String?,
        technicalDetails = this[2] as String?,
    ),
    canRetry = this[3] as Boolean,
)

/** Place once per screen; the screen's Scaffold shows [ErrorHostState.snackbar]. */
@Composable
fun ErrorDialogHost(state: ErrorHostState) {
    val display = state.dialog ?: return
    AlertDialog(
        onDismissRequest = state::dismissDialog,
        text = { ErrorText(display.message) },
        confirmButton = {
            if (display.canRetry) {
                TextButton(onClick = { state.dismissDialog(); state.onRetry() }) { Text(stringResource(R.string.action_try_again)) }
            } else {
                TextButton(onClick = state::dismissDialog) { Text(stringResource(R.string.action_ok)) }
            }
        },
        dismissButton = {
            if (display.canRetry) {
                TextButton(onClick = state::dismissDialog) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
fun FullScreenError(display: ErrorDisplay.FullScreen, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ErrorText(display.message)
        if (display.canRetry) {
            Button(onClick = onRetry) { Text(stringResource(R.string.action_try_again)) }
        }
    }
}

@Composable
private fun ErrorText(message: ErrorMessage) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message.serverText ?: stringResource(message.key.toStringRes()), textAlign = TextAlign.Center)
        message.technicalDetails?.let { details ->
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
