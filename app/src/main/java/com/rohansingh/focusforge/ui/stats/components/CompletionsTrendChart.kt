package com.rohansingh.focusforge.ui.stats.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rohansingh.focusforge.domain.models.ChartDataPoint
import kotlin.math.max

@Composable
fun CompletionsTrendChart(
    dataPoints: List<ChartDataPoint>,
    modifier: Modifier = Modifier
) {
    val barColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Goal Completions Trend",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (dataPoints.isEmpty() || dataPoints.all { it.value == 0.0 }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No goal completions in this period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val maxVal = max(dataPoints.maxOfOrNull { it.value } ?: 1.0, 5.0)

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val bottomPadding = 26.dp.toPx()
                    val chartHeight = height - bottomPadding
                    val count = dataPoints.size
                    val groupWidth = width / count

                    // Grid lines
                    val gridLines = 3
                    for (i in 0..gridLines) {
                        val y = chartHeight * (i.toFloat() / gridLines)
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val maxBarWidth = 20.dp.toPx()
                    val barWidth = (groupWidth * 0.5f).coerceAtMost(maxBarWidth)
                    val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                    dataPoints.forEachIndexed { index, point ->
                        val centerX = (index * groupWidth) + (groupWidth / 2f)
                        val barHeight = ((point.value / maxVal) * chartHeight).toFloat().coerceAtLeast(0f)

                        if (barHeight > 0f) {
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(centerX - (barWidth / 2f), chartHeight - barHeight),
                                size = Size(barWidth, barHeight),
                                cornerRadius = cornerRadius
                            )
                        }

                        val shouldDrawLabel = count <= 7 || index % (count / 5).coerceAtLeast(1) == 0 || index == count - 1
                        if (shouldDrawLabel) {
                            drawContext.canvas.nativeCanvas.apply {
                                val paint = android.graphics.Paint().apply {
                                    color = android.graphics.Color.GRAY
                                    textSize = 10.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isAntiAlias = true
                                }
                                drawText(
                                    point.dateLabel,
                                    centerX,
                                    height - 4.dp.toPx(),
                                    paint
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
