package dev.accessscope.scanner.util

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.provider.Settings
import dev.accessscope.scanner.service.AccessScopeAccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PermissionHelperTest {

    /** Registra un resolver fittizio per [action], così `resolveActivity` non torna null. */
    private fun Context.makeResolvable(action: String) {
        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.android.settings"
                name = "FakeAccessibilityDetailsActivity"
            }
        }
        shadowOf(packageManager).addResolveInfoForIntent(Intent(action), resolveInfo)
    }

    @Test
    @Config(sdk = [32])
    fun accessibilityServiceIntent_api32_usesGeneralList() {
        val context = RuntimeEnvironment.getApplication()
        val intent = PermissionHelper.accessibilityServiceIntent(
            context,
            AccessScopeAccessibilityService::class.java,
        )
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
    }

    @Test
    @Config(sdk = [33])
    fun accessibilityServiceIntent_api33_usesDetails() {
        val context = RuntimeEnvironment.getApplication()
        context.makeResolvable("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
        val intent = PermissionHelper.accessibilityServiceIntent(
            context,
            AccessScopeAccessibilityService::class.java,
        )
        assertEquals("android.settings.ACCESSIBILITY_DETAILS_SETTINGS", intent.action)
        val component = intent.getStringExtra(Intent.EXTRA_COMPONENT_NAME).orEmpty()
        assert(component.contains("AccessScopeAccessibilityService")) {
            "Expected service component, got: $component"
        }
    }

    @Test
    @Config(sdk = [34])
    fun accessibilityServiceIntent_api34_usesDetails() {
        val context = RuntimeEnvironment.getApplication()
        context.makeResolvable("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
        val intent = PermissionHelper.accessibilityServiceIntent(
            context,
            AccessScopeAccessibilityService::class.java,
        )
        assertEquals("android.settings.ACCESSIBILITY_DETAILS_SETTINGS", intent.action)
        val component = intent.getStringExtra(Intent.EXTRA_COMPONENT_NAME).orEmpty()
        assert(component.contains("AccessScopeAccessibilityService")) {
            "Expected service component, got: $component"
        }
    }

    @Test
    @Config(sdk = [34])
    fun accessibilityServiceIntent_api34_fallsBackToGeneralList_whenDetailsUnresolvable() {
        val context = RuntimeEnvironment.getApplication()
        // Nessun resolver registrato per ACCESSIBILITY_DETAILS_SETTINGS: simula OEM (es. Samsung)
        // dove l'activity di dettaglio non esiste.
        val intent = PermissionHelper.accessibilityServiceIntent(
            context,
            AccessScopeAccessibilityService::class.java,
        )
        assertEquals(Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
    }
}
