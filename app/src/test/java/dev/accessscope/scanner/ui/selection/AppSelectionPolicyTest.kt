package dev.accessscope.scanner.ui.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSelectionPolicyTest {

    @Test
    fun addFirstApp_succeeds() {
        val result = AppSelectionPolicy.toggleSelection(emptySet(), "com.example.a")
        assertTrue(result is AppSelectionPolicy.ToggleResult.Updated)
        assertEquals(setOf("com.example.a"), (result as AppSelectionPolicy.ToggleResult.Updated).selected)
    }

    @Test
    fun removeSelectedApp_succeeds() {
        val result = AppSelectionPolicy.toggleSelection(setOf("com.example.a"), "com.example.a")
        assertTrue(result is AppSelectionPolicy.ToggleResult.Updated)
        assertEquals(emptySet<String>(), (result as AppSelectionPolicy.ToggleResult.Updated).selected)
    }

    @Test
    fun addSecondApp_blockedWithoutReplace() {
        val result = AppSelectionPolicy.toggleSelection(setOf("com.example.a"), "com.example.b")
        assertTrue(result is AppSelectionPolicy.ToggleResult.LimitReached)
        val blocked = result as AppSelectionPolicy.ToggleResult.LimitReached
        assertEquals(setOf("com.example.a"), blocked.selected)
        assertEquals("com.example.b", blocked.blockedPackage)
    }

    @Test
    fun addSecondApp_replacesWhenAutoLaunch() {
        val result = AppSelectionPolicy.toggleSelection(
            setOf("com.example.a"),
            "com.example.b",
            replaceOnLimit = true,
        )
        assertTrue(result is AppSelectionPolicy.ToggleResult.Updated)
        assertEquals(setOf("com.example.b"), (result as AppSelectionPolicy.ToggleResult.Updated).selected)
    }

    @Test
    fun enforceMax_trimsExtraPackages() {
        val trimmed = AppSelectionPolicy.enforceMax(setOf("a", "b"))
        assertEquals(setOf("a"), trimmed)
    }

    @Test
    fun favoriteAdded_selectsPackage() {
        assertEquals(
            setOf("com.example.b"),
            AppSelectionPolicy.selectOnFavoriteAdded("com.example.b"),
        )
    }

    @Test
    fun favoriteRemoved_deselectsPackage() {
        assertEquals(
            emptySet<String>(),
            AppSelectionPolicy.selectOnFavoriteRemoved(setOf("com.example.a"), "com.example.a"),
        )
    }

    @Test
    fun favoriteProtectedFromManualDeselect() {
        assertTrue(
            AppSelectionPolicy.isFavoriteProtectedFromDeselect(
                "com.example.a",
                setOf("com.example.a"),
            ),
        )
    }

    @Test
    fun restoreSelectionFromFavorites_whenEmpty() {
        assertEquals(
            setOf("com.example.a"),
            AppSelectionPolicy.restoreSelectionFromFavorites(
                current = emptySet(),
                favorites = setOf("com.example.a", "com.example.b"),
                preferredPrimary = "com.example.a",
            ),
        )
    }

    @Test
    fun sanitizeAgainstInstalled_removesStalePackages() {
        val installed = setOf("com.example.b")
        val (favorites, selected) = AppSelectionPolicy.sanitizeAgainstInstalled(
            selected = setOf("com.example.a"),
            favorites = setOf("com.example.a", "com.example.b"),
            installed = installed,
            preferredPrimary = "com.example.b",
        )
        assertEquals(setOf("com.example.b"), favorites)
        assertEquals(setOf("com.example.b"), selected)
    }

    @Test
    fun sanitizeAgainstInstalled_emptyWhenNothingInstalled() {
        val (favorites, selected) = AppSelectionPolicy.sanitizeAgainstInstalled(
            selected = setOf("com.example.a"),
            favorites = setOf("com.example.a"),
            installed = emptySet(),
        )
        assertEquals(emptySet<String>(), favorites)
        assertEquals(emptySet<String>(), selected)
    }
}
