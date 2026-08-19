package com.example.rawaudiorecorder.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/*
 * Icons.Filled.PlayArrow, Delete, Edit, Check and Close all ship in
 * material-icons-core, but Pause and Stop do not — they live in
 * material-icons-extended. Drawing those two by hand keeps the icon set
 * consistent without pulling in an extra dependency.
 */

@Composable
fun PauseIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(modifier.size(24.dp)) {
        val barWidth = size.width * 0.22f
        val gap = size.width * 0.16f
        val barHeight = size.height * 0.62f
        val top = (size.height - barHeight) / 2f
        val radius = CornerRadius(barWidth * 0.28f, barWidth * 0.28f)

        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width / 2f - gap / 2f - barWidth, top),
            size = Size(barWidth, barHeight),
            cornerRadius = radius
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width / 2f + gap / 2f, top),
            size = Size(barWidth, barHeight),
            cornerRadius = radius
        )
    }
}

@Composable
fun StopIcon(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    Canvas(modifier.size(24.dp)) {
        val side = size.minDimension * 0.58f
        val offset = (size.minDimension - side) / 2f
        drawRoundRect(
            color = tint,
            topLeft = Offset(offset, offset),
            size = Size(side, side),
            cornerRadius = CornerRadius(side * 0.16f, side * 0.16f)
        )
    }
}
