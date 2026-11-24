package org.example.project.core.presentation.root

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
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
import org.example.project.core.app.AppBlock
import org.example.project.core.app.AppState
import org.example.project.core.app.NetworkBanner
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

        null -> ScreenWithBanner(banner = state.banner, content = content)
    }

    if (state.isSessionExpired) SessionExpiredDialog(onConfirm = { onIntent(AppIntent.SessionExpiredConfirmed) })
}

/**
 * The status bar's space belongs to this column, coloured like the banner while there is one, and not to the screen
 * below. So the banner can grow and shrink without the screen's own insets changing under it.
 */
@Composable
private fun ScreenWithBanner(banner: NetworkBanner?, content: @Composable () -> Unit) {
    val statusBarColor = animateColorAsState(banner.containerColor(), label = "statusBarColor")
    Column(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(statusBarColor.value) }      // read while drawing: no recomposition per frame
            .statusBarsPadding(),
    ) {
        AnimatedContent(
            targetState = banner,
            transitionSpec = { fadeIn() togetherWith fadeOut() using SizeTransform() },
            label = "networkBanner",
        ) { shown ->
            if (shown != null) NetworkBannerStrip(shown) else Spacer(Modifier.fillMaxWidth())
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun NetworkBannerStrip(banner: NetworkBanner) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(banner.containerColor())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },      // TalkBack says it when it appears
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(
                when (banner) {
                    NetworkBanner.Offline -> R.string.banner_offline
                    NetworkBanner.BackOnline -> R.string.banner_back_online
                }
            ),
            style = MaterialTheme.typography.labelLarge,
            color = when (banner) {
                NetworkBanner.Offline -> MaterialTheme.colorScheme.onErrorContainer
                NetworkBanner.BackOnline -> OnGreenContainer
            },
        )
    }
}

// Material 3 has no "success" colours. Light, so the status bar's dark icons stay readable on it.
private val GreenContainer = Color(0xFFA5D6A7)
private val OnGreenContainer = Color(0xFF0B3D0E)

@Composable
@ReadOnlyComposable
private fun NetworkBanner?.containerColor(): Color = when (this) {
    NetworkBanner.Offline -> MaterialTheme.colorScheme.errorContainer
    NetworkBanner.BackOnline -> GreenContainer
    null -> MaterialTheme.colorScheme.surface
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
