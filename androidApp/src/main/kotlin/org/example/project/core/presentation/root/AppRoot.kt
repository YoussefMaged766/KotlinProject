package org.example.project.core.presentation.root

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.example.project.R
import org.koin.androidx.compose.koinViewModel

/** Wraps every screen. Nothing inside it needs to know about the banner or the global events. */
@Composable
fun AppRoot(viewModel: AppViewModel = koinViewModel(), content: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AppRootContent(state = state, onIntent = viewModel::onIntent, content = content)
}

@Composable
fun AppRootContent(state: AppState, onIntent: (AppIntent) -> Unit, content: @Composable () -> Unit) {
    when (state.blockedBy) {
        AppBlock.UpgradeRequired -> {
            val context = LocalContext.current
            BlockingScreen(
                title = stringResource(R.string.upgrade_title),
                message = stringResource(R.string.upgrade_message),
                action = stringResource(R.string.action_update),
                onAction = { context.openStorePage() },
            )
        }

        AppBlock.Maintenance -> BlockingScreen(
            title = stringResource(R.string.maintenance_title),
            message = stringResource(R.string.maintenance_message),
            action = stringResource(R.string.action_try_again),
            onAction = { onIntent(AppIntent.RetryAfterMaintenance) },
        )

        null -> Column(Modifier.fillMaxSize()) {
            if (state.isOffline) OfflineBanner()
            // While the banner covers the status bar, the screen below must not leave room for it again. Not animated:
            // the inset changes at once, so a banner shrinking slowly would make the screen jump.
            val insets = if (state.isOffline) Modifier.consumeWindowInsets(WindowInsets.statusBars) else Modifier
            Box(Modifier.weight(1f).then(insets)) { content() }
        }
    }

    if (state.isSessionExpired) SessionExpiredDialog(onConfirm = { onIntent(AppIntent.SessionExpiredConfirmed) })
}

@Composable
private fun OfflineBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)   // light, so the status bar's icons stay readable
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },      // TalkBack says it when it appears
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.banner_offline),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}

@Composable
private fun BlockingScreen(title: String, message: String, action: String, onAction: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(message, textAlign = TextAlign.Center)
            Button(onClick = onAction) { Text(action) }
        }
    }
}

/** Interim: the app has no login yet. With one, this also clears the session and opens the login screen. */
@Composable
private fun SessionExpiredDialog(onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onConfirm,
        text = { Text(stringResource(R.string.session_expired_message)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_ok)) } },
    )
}

private fun Context.openStorePage() {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()))
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri()))
        } catch (_: ActivityNotFoundException) {
            // No store and no browser: the message already tells the user what to do.
        }
    }
}
