/**
 * Filmstrip orizzontale delle schermate visitate nel report dinamico.
 */
package dev.accessscope.scanner.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.accessscope.scanner.report.DynamicScreenFrame
import dev.accessscope.scanner.ui.theme.BrandPrimary
import dev.accessscope.scanner.ui.theme.CompactShape
import dev.accessscope.scanner.ui.theme.Success
import dev.accessscope.scanner.ui.theme.contentSecondary

/**
 * Carousel di miniature delle schermate visitate.
 *
 * @param frames Elenco frame del report dinamico.
 * @param selectedIndex Indice della schermata selezionata.
 * @param thumbnails Mappa fingerprint → bitmap per le miniature.
 * @param onSelect Callback alla selezione di una schermata.
 */
@Composable
fun ScreenFilmstrip(
    frames: List<DynamicScreenFrame>,
    selectedIndex: Int,
    thumbnails: Map<String, Bitmap?>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(frames, key = { _, frame -> frame.fingerprint }) { index, frame ->
            val isSelected = index == selectedIndex
            val issueCount = frame.violations.size + frame.talkBackFindings.size
            val thumb = thumbnails[frame.fingerprint]

            Column(
                modifier = Modifier
                    .width(108.dp)
                    .clip(CompactShape)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) BrandPrimary else MaterialTheme.colorScheme.outlineVariant,
                        shape = CompactShape,
                    )
                    .clickable { onSelect(index) }
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (thumb != null) {
                        val imageBitmap = remember(thumb) { thumb.asImageBitmap() }
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = frame.title,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = contentSecondary(),
                        )
                    }
                    if (issueCount > 0) {
                        Badge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                            containerColor = BrandPrimary,
                        ) {
                            Text("$issueCount")
                        }
                    } else {
                        Badge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                            containerColor = Success,
                        ) {
                            Text("OK")
                        }
                    }
                }
                Text(
                    frame.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
