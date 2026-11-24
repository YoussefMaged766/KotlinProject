package org.example.project.core.events

import org.example.project.core.result.DataError

/** Something the whole app must react to, whichever screen's request discovered it. */
sealed interface GlobalEvent {
    /** The server no longer accepts our session: log out and show the login screen (M1). */
    data object SessionExpired : GlobalEvent

    /** This app version is blocked: show the update screen (M3). */
    data object UpgradeRequired : GlobalEvent

    /** The server is down on purpose: show the maintenance screen (M2). */
    data object Maintenance : GlobalEvent
}

/** The app-wide meaning of an error, or null when it only concerns the screen that asked. */
fun DataError.toGlobalEvent(): GlobalEvent? = when (this) {
    is DataError.Unauthorized -> if (hadSession) GlobalEvent.SessionExpired else null
    is DataError.UpgradeRequired -> GlobalEvent.UpgradeRequired
    is DataError.Maintenance -> GlobalEvent.Maintenance
    else -> null
}
