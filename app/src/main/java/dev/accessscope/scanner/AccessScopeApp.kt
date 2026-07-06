package dev.accessscope.scanner

import android.app.Application
import dev.accessscope.scanner.data.ScanSessionRepository

class AccessScopeApp : Application() {
    val scanRepository: ScanSessionRepository by lazy { ScanSessionRepository() }
}
