package org.example.project.core.presentation.root

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import org.example.project.core.app.AppState
import org.example.project.core.app.AppStatus

@Immutable
sealed interface AppIntent {
    data object RetryAfterMaintenance : AppIntent
    data object SessionExpiredConfirmed : AppIntent
}

/** What the app shows whichever screen is open: the offline banner and the reactions to global events. */
class AppViewModel(private val appStatus: AppStatus) : ViewModel() {

    val state: StateFlow<AppState> =
        appStatus.state.stateIn(viewModelScope, SharingStarted.Eagerly, appStatus.initialState)

    fun onIntent(intent: AppIntent) = when (intent) {
        AppIntent.RetryAfterMaintenance -> appStatus.retryAfterMaintenance()
        AppIntent.SessionExpiredConfirmed -> appStatus.sessionExpiredConfirmed()
    }
}
