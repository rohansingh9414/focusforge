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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.rohansingh.focusforge.domain.models.DualChartDataPoint
import kotlin.math.max

@Composable
fun CreditsTrendChart(
    dataPoints: List<DualChartDataPoint>,
    modifier: Modifier = Modifier
) {
    val earnedColor = Color(0xFF4CAF50)
    val spentColor = Color(0xFFFF7043)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

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
                    text = "Credits: Earned vs Spent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                // Legend
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = CircleShape, color = earnedColor, modifier = Modifier.size(8.dp)) {}
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Earned", style = MaterialTheme.typography.labelSmall, color = labelColor)
                    Spacer(modifier = Modifier.width(12.dp))
                    Surface(shape = CircleShape, color = spentColor, modifier = Modifier.size(8.dp)) {}
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Spent", style = MaterialTheme.typography.labelSmall, color = labelColor)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (dataPoints.isEmpty() || (dataPoints.all { it.earnedValue == 0.0 && it.spentValue == 0.0 })) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No credit activity in this period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val maxVal = max(
                    dataPoints.maxOfOrNull { max(it.earnedValue, it.spentValue) } ?: 1.0,
                    10.0
                )

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                ) {
                    val width = size.width
                    val height = size.height
                    val bottomPadding = 30.dp.toPx()
                    val chartHeight = height - bottomPadding
                    val count = dataPoints.size
                    val groupWidth = width / count

                    // Draw 3 horizontal grid lines
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

                    // Bar properties
                    val maxBarWidth = 14.dp.toPx()
                    val barWidth = (groupWidth * 0.3f).coerceAtMost(maxBarWidth)
                    val cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())

                    dataPoints.forEachIndexed { index, point ->
                        val groupCenterX = (index * groupWidth) + (groupWidth / 2f)

                        // Earned bar (left of center)
                        val earnedHeight = ((point.earnedValue / maxVal) * chartHeight).toFloat().coerceAtLeast(0f)
                        if (earnedHeight > 0f) {
                            drawRoundRect(
                                color = earnedColor,
                                topLeft = Offset(groupCenterX - barWidth - 2.dp.toPx(), chartHeight - earnedHeight),
                                size = Size(barWidth, earnedHeight),
                                cornerRadius = cornerRadius
                            )
                        }

                        // Spent bar (right of center)
                        val spentHeight = ((point.spentValue / maxVal) * chartHeight).toFloat().coerceAtLeast(0f)
                        if (spentHeight > 0f) {
                            drawRoundRect(
                                color = spentColor,
                                topLeft = Offset(groupCenterX + 2.dp.toPx(), chartHeight - spentHeight),
                                size = Size(barWidth, spentHeight),
                                cornerRadius = cornerRadius
                            )
                        }

                        // Date label below (skip some labels if more than 10 points)
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
                                    groupCenterX,
                                    height - 6.dp.toPx(),
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
