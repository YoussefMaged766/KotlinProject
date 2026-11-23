package org.example.project

import android.app.Application
import java.util.Locale
import org.example.project.categories.di.categoriesPresentationModule
import org.example.project.di.initKoin
import org.example.project.network.ApiConfig
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class KotlinProjectApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initKoin(
            config = ApiConfig(
                baseUrl = ApiConfig.DEV_BASE_URL,
                platform = "android",
                appVersion = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0",
                languageTag = Locale.getDefault().language,
            ),
        ) {
            androidLogger()                           // prints Koin's resolution log to Logcat
            androidContext(this@KotlinProjectApp)     // lets definitions call androidContext() later
            modules(categoriesPresentationModule)     // Android-only modules, added to the shared ones
        }
    }
}
