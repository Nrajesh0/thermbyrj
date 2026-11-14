package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ThermalRecord
import com.example.ui.theme.AuraCyan
import com.example.ui.theme.AuraGreen
import com.example.ui.theme.AuraOrange
import com.example.ui.theme.AuraRose
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun ThermalChart(
    records: List<ThermalRecord>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF06B6D4),
    fillColorStart: Color = Color(0x6606B6D4),
    fillColorEnd: Color = Color(0x0006B6D4),
    warningTemp: Float = 35f,
    criticalTemp: Float = 40f
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var canvasWidthPx by remember { mutableFloatStateOf(1f) }
    val textMeasurer = rememberTextMeasurer()

    val sortedRecords = remember(records) {
        val sorted = records.sortedBy { it.timestamp }
        val maxPoints = 240
        if (sorted.size <= maxPoints) {
            sorted
        } else {
            val step = (sorted.size - 1).toDouble() / (maxPoints - 1)
            List(maxPoints) { idx ->
                val srcIdx = if (idx == maxPoints - 1) sorted.lastIndex else (idx * step).toInt()
                sorted[srcIdx]
            }
        }
    }

    val timeRangeLongRemembered = remember(sortedRecords) {
        if (sortedRecords.size >= 2) max(sortedRecords.last().timestamp - sortedRecords.first().timestamp, 1L) else 1L
    }
    val datePattern = if (timeRangeLongRemembered > 24L * 60L * 60L * 1000L) "MM-dd" else "HH:mm"
    val dateFormatter = remember(datePattern) { SimpleDateFormat(datePattern, Locale.getDefault()) }

    Box(
        modifier = modifier
            .onSizeChanged { size ->
                canvasWidthPx = max(1f, size.width.toFloat())
                val minOffset = canvasWidthPx - (canvasWidthPx * scale)
                offsetX = offsetX.coerceIn(minOffset, 0f)
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = max(1f, min(20f, scale * zoom))
                    scale = newScale
                    val minOffset = canvasWidthPx - (canvasWidthPx * newScale)
                    offsetX = (offsetX + pan.x).coerceIn(minOffset, 0f)
                }
            }
            .clipToBounds()
            .padding(top = 8.dp, bottom = 8.dp, start = 4.dp, end = 4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (sortedRecords.isEmpty()) return@Canvas

            val topPadding = 12.dp.toPx()
            val bottomLabelHeight = 24.dp.toPx()
            val chartHeight = max(1f, size.height - topPadding - bottomLabelHeight)
            val chartBottom = topPadding + chartHeight

            val minTemp = (sortedRecords.minOfOrNull { it.batteryTemp } ?: 0f) - 2f
            val maxTemp = (sortedRecords.maxOfOrNull { it.batteryTemp } ?: 100f) + 2f
            val timeStart = sortedRecords.first().timestamp
            val timeEnd = sortedRecords.last().timestamp

            val tempRange = max(maxTemp - minTemp, 1f)
            val timeRangeLong = max(timeEnd - timeStart, 1L)
            val timeRange = timeRangeLong.toFloat()

            val totalWidth = size.width * scale
            val clampedOffsetX = offsetX.coerceIn(size.width - totalWidth, 0f)

            val tempToY = { t: Float ->
                val normalizedY = (t - minTemp) / tempRange
                chartBottom - (normalizedY * chartHeight)
            }

            val drawZone = { upperTemp: Float, lowerTemp: Float, color: Color ->
                val topY = max(topPadding, tempToY(upperTemp))
                val bottomY = min(chartBottom, tempToY(lowerTemp))
                if (bottomY > topY) {
                    drawRect(
                        color = color.copy(alpha = 0.08f),
                        topLeft = Offset(0f, topY),
                        size = Size(size.width, bottomY - topY)
                    )
                }
            }

            // Draw temperature zones
            drawZone(maxTemp, criticalTemp, AuraRose)
            drawZone(criticalTemp, warningTemp, AuraOrange)
            drawZone(warningTemp, 20f, AuraGreen)
            drawZone(20f, minTemp, AuraCyan)

            val path = Path()
            val fillPath = Path()

            var firstPoint = true
            var prevX = 0f
            var prevY = 0f

            sortedRecords.forEachIndexed { index, record ->
                val normalizedX = if (timeEnd == timeStart) {
                    if (sortedRecords.size <= 1) 0.5f else index.toFloat() / (sortedRecords.size - 1)
                } else {
                    (record.timestamp - timeStart) / timeRange
                }
                val x = (normalizedX * totalWidth) + clampedOffsetX
                val y = tempToY(record.batteryTemp)

                if (firstPoint) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, chartBottom)
                    fillPath.lineTo(x, y)
                    firstPoint = false
                } else {
                    val controlX = (prevX + x) / 2f
                    path.cubicTo(controlX, prevY, controlX, y, x, y)
                    fillPath.cubicTo(controlX, prevY, controlX, y, x, y)
                }

                prevX = x
                prevY = y

                if (index == sortedRecords.size - 1) {
                    fillPath.lineTo(x, chartBottom)
                    fillPath.close()
                }
            }

            // Draw grid lines and Y-axis labels
            val gridLines = 3
            val labelStyle = TextStyle(color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
            for (i in 0..gridLines) {
                val y = topPadding + chartHeight * (i.toFloat() / gridLines)
                drawLine(
                    color = Color.White.copy(alpha = 0.06f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                val tempLabel = maxTemp - (tempRange * (i.toFloat() / gridLines))
                val measured = textMeasurer.measure(String.format(Locale.US, "%.1f°", tempLabel), labelStyle)
                val labelY = (y - measured.size.height - 2.dp.toPx()).coerceAtLeast(0f)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(4.dp.toPx(), labelY)
                )
            }

            // Draw X-axis labels
            val xLabelsCount = 4

            for (i in 0..xLabelsCount) {
                val xPos = (size.width / xLabelsCount) * i
                val unscaledX = (xPos - clampedOffsetX) / scale
                val t = timeStart + ((unscaledX / size.width) * timeRange).toLong()
                if (t in timeStart..timeEnd) {
                    val timeString = dateFormatter.format(Date(t))
                    val measured = textMeasurer.measure(timeString, labelStyle)
                    val labelX = (xPos - measured.size.width / 2f)
                        .coerceIn(0f, max(0f, size.width - measured.size.width.toFloat()))
                    drawText(
                        textLayoutResult = measured,
                        topLeft = Offset(labelX, chartBottom + 6.dp.toPx())
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.12f),
                        start = Offset(xPos, chartBottom),
                        end = Offset(xPos, chartBottom + 4.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            // Clip curves cleanly to the chart plotting area
            clipRect(left = 0f, top = topPadding, right = size.width, bottom = chartBottom) {
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(fillColorStart, fillColorEnd),
                        startY = topPadding,
                        endY = chartBottom
                    )
                )

                drawPath(
                    path = path,
                    color = lineColor.copy(alpha = 0.25f),
                    style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
