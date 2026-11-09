package org.example.project

import android.app.Application
import java.util.Locale
import org.example.project.network.ApiConfig

class KotlinProjectApp : Application() {

    /** One graph (and so one HttpClient) for the life of the process. */
    val graph: AppGraph by lazy {
        AppGraph(
            ApiConfig(
                baseUrl = ApiConfig.DEV_BASE_URL,
                platform = "android",
                appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0",
                languageTag = Locale.getDefault().language,
            )
        )
    }
}
