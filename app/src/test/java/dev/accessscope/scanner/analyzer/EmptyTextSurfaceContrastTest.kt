package dev.accessscope.scanner.analyzer

import android.graphics.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class EmptyTextSurfaceContrastTest {

    @Test
    fun emptyClickableTextView_skipsContrast() {
        val snap = textSnap(text = null, clickable = true)
        assertTrue(PrecisionRules.isEmptyTextSurfaceWithoutContent(snap))
        assertTrue(PrecisionRules.shouldSkipContrastCheck(snap, emptyList()))
    }

    @Test
    fun textViewWithText_notSkipped() {
        val snap = textSnap(text = "Ciao mondo", clickable = false)
        assertFalse(PrecisionRules.isEmptyTextSurfaceWithoutContent(snap))
        assertFalse(PrecisionRules.shouldSkipContrastCheck(snap, emptyList()))
    }

    @Test
    fun editTextWithHintOnly_notEmptySurface() {
        val snap = textSnap(
            text = null,
            className = "android.widget.EditText",
            hint = "Inserisci email",
            editable = true,
        )
        assertFalse(PrecisionRules.isEmptyTextSurfaceWithoutContent(snap))
        assertFalse(PrecisionRules.shouldSkipContrastCheck(snap, emptyList()))
    }

    @Test
    fun textViewWithContentDescription_notSkipped() {
        val snap = textSnap(text = null, contentDescription = "Badge stato")
        assertFalse(PrecisionRules.isEmptyTextSurfaceWithoutContent(snap))
    }

    private fun textSnap(
        text: String?,
        className: String = "android.widget.TextView",
        hint: String? = null,
        contentDescription: String? = null,
        clickable: Boolean = false,
        editable: Boolean = false,
    ) = NodeSnapshot(
        className = className,
        bounds = Rect(10, 10, 200, 60),
        viewId = "it.example:id/empty_bg",
        text = text,
        contentDescription = contentDescription,
        hintText = hint,
        tooltipText = null,
        isClickable = clickable,
        isLongClickable = false,
        isFocusable = clickable,
        isEditable = editable,
        isCheckable = false,
        isChecked = false,
        isScrollable = false,
        isEnabled = true,
        isPassword = false,
        isHeading = false,
        headingLevel = 0,
        hasLabeledBy = false,
        hasLabelFor = false,
        errorText = null,
        stateDescription = null,
        isExpanded = null,
        collectionRow = -1,
        collectionColumn = -1,
        childCount = 0,
        isAccessibilityExcluded = false,
        isLikelyDecorative = false,
        traversalIndex = 0,
        rangeCurrent = null,
        rangeMin = null,
        rangeMax = null,
        unlabeledActionCount = 0,
        minTextHeightPx = 42,
        minTouchTargetPx = 126,
        textSizeSp = null,
        sectionTitle = null,
    )
}
