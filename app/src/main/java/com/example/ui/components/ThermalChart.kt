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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.max
import kotlin.math.min
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ThermalChart(
    records: List<ThermalRecord>,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xFF06B6D4),
    fillColorStart: Color = Color(0x6606B6D4),
    fillColorEnd: Color = Color(0x0006B6D4)
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = max(1f, min(100f, scale * zoom))
                    offsetX += pan.x
                }
            }
            .padding(top = 24.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (records.isEmpty()) return@Canvas
            
            val minTemp = (records.minOfOrNull { it.batteryTemp } ?: 0f) - 2f
            val maxTemp = (records.maxOfOrNull { it.batteryTemp } ?: 100f) + 2f
            val timeStart = records.first().timestamp
            val timeEnd = records.last().timestamp
            
            val tempRange = max(maxTemp - minTemp, 1f)
            val timeRange = max(timeEnd - timeStart, 1L).toFloat()

            val chartBottom = size.height - 30.dp.toPx()
            val totalWidth = size.width * scale
            val maxOffsetX = 0f
            val minOffsetX = size.width - totalWidth
            
            offsetX = max(minOffsetX, min(maxOffsetX, offsetX))

            val tempToY = { t: Float ->
                val normalizedY = (t - minTemp) / tempRange
                chartBottom - (normalizedY * chartBottom)
            }

            val drawZone = { upperTemp: Float, lowerTemp: Float, color: Color ->
                val topY = max(0f, tempToY(upperTemp))
                val bottomY = min(chartBottom, tempToY(lowerTemp))
                if (bottomY > topY) {
                    drawRect(
                        color = color.copy(alpha = 0.08f),
                        topLeft = Offset(0f, topY),
                        size = androidx.compose.ui.geometry.Size(size.width, bottomY - topY)
                    )
                }
            }

            // Draw temperature zones
            drawZone(maxTemp, 40f, AuraRose) // Critical
            drawZone(40f, 35f, AuraOrange)   // Elevated
            drawZone(35f, 20f, AuraGreen)    // Normal
            drawZone(20f, minTemp, AuraCyan) // Optimal

            val path = Path()
            val fillPath = Path()
            
            var firstPoint = true
            var prevX = 0f
            var prevY = 0f

            records.forEachIndexed { index, record ->
                val normalizedX = (record.timestamp - timeStart) / timeRange
                val x = (normalizedX * totalWidth) + offsetX
                
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
                
                if (index == records.size - 1) {
                    fillPath.lineTo(x, chartBottom)
                    fillPath.close()
                }
            }

            // Draw grid lines and Y-axis labels
            val gridLines = 3
            for (i in 0..gridLines) {
                val y = chartBottom * (i.toFloat() / gridLines)
                drawLine(
                    color = Color.White.copy(alpha = 0.05f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.dp.toPx()
                )
                // Y-axis label
                val tempLabel = maxTemp - (tempRange * (i.toFloat() / gridLines))
                drawText(
                    textMeasurer = textMeasurer,
                    text = String.format("%.1f°", tempLabel),
                    topLeft = Offset(5f, y - 20f),
                    style = TextStyle(color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                )
            }

            // Draw X-axis labels
            val xLabelsCount = 4
            val df = SimpleDateFormat("HH:mm", Locale.getDefault())
            if (timeRange > 24 * 60 * 60 * 1000L) { // If > 24h, use MM-dd
                df.applyPattern("MM-dd")
            }
            
            for (i in 0..xLabelsCount) {
                // Determine label position visually
                val xPos = (size.width / xLabelsCount) * i
                // Map screen X back to timestamp
                val unscaledX = (xPos - offsetX) / scale
                val t = timeStart + ((unscaledX / size.width) * timeRange).toLong()
                if (t in timeStart..timeEnd) {
                    val timeString = df.format(Date(t))
                    drawText(
                        textMeasurer = textMeasurer,
                        text = timeString,
                        topLeft = Offset(xPos, chartBottom + 10f),
                        style = TextStyle(color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                    )
                    // Tick mark
                    drawLine(
                        color = Color.White.copy(alpha = 0.1f),
                        start = Offset(xPos, chartBottom),
                        end = Offset(xPos, chartBottom + 10f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            // Draw Gradient Fill
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(fillColorStart, fillColorEnd),
                    startY = 0f,
                    endY = chartBottom
                )
            )

            // Draw Outer Glow for the line
            drawPath(
                path = path,
                color = lineColor.copy(alpha = 0.25f),
                style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            // Draw Main High-Tech Line
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}
