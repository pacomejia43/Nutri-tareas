package com.nutritareas.app.ui.chat.components

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Egg
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.LocalFlorist
import androidx.compose.material.icons.outlined.RiceBowl
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/**
 * A faint, tiled pattern of nutrition-themed glyphs behind the chat - purely decorative, so it
 * reuses the existing brand color at very low alpha instead of introducing a new one.
 */
@Composable
fun NutritionBackdrop(modifier: Modifier = Modifier) {
    val painters = listOf(
        rememberVectorPainter(Icons.Outlined.Grass),
        rememberVectorPainter(Icons.Outlined.LocalDining),
        rememberVectorPainter(Icons.Outlined.WaterDrop),
        rememberVectorPainter(Icons.Outlined.RiceBowl),
        rememberVectorPainter(Icons.Outlined.Spa),
        rememberVectorPainter(Icons.Outlined.LocalFlorist),
        rememberVectorPainter(Icons.Outlined.Egg),
    )
    val tint = ColorFilter.tint(MaterialTheme.colorScheme.outline.copy(alpha = 0.07f))
    val cellSize = 96.dp
    val iconSize = 26.dp

    Canvas(modifier = modifier) {
        val cellPx = cellSize.toPx()
        val iconPx = iconSize.toPx()
        var row = 0
        var y = -cellPx / 2f
        while (y < size.height + cellPx) {
            val rowOffset = if (row % 2 == 0) 0f else cellPx / 2f
            var col = 0
            var x = -cellPx / 2f + rowOffset
            while (x < size.width + cellPx) {
                val index = (row * 5 + col).mod(painters.size)
                val painter = painters[index]
                val angle = ((row * 47 + col * 83) % 360).toFloat()
                translate(left = x, top = y) {
                    rotate(degrees = angle, pivot = Offset(iconPx / 2f, iconPx / 2f)) {
                        with(painter) {
                            draw(size = Size(iconPx, iconPx), colorFilter = tint)
                        }
                    }
                }
                x += cellPx
                col++
            }
            y += cellPx
            row++
        }
    }
}
