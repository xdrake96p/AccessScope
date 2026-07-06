package dev.accessscope.scanner

import android.app.Application
import dev.accessscope.scanner.data.ScanSessionRepository
import dev.accessscope.scanner.util.FavoriteAppsStore

class AccessScopeApp : Application() {
    val scanRepository: ScanSessionRepository by lazy { ScanSessionRepository() }
    val favoriteAppsStore: FavoriteAppsStore by lazy { FavoriteAppsStore(this) }
}
