package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.EvolutionSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun VoiceEvolutionChartCard(
    sessions: List<EvolutionSession>,
    modifier: Modifier = Modifier
) {
    val ordered = remember(sessions) {
        sessions.sortedBy { it.sessionDateMillis }.takeLast(10)
    }
    var selectedIndex by remember(ordered.size) {
        mutableIntStateOf((ordered.size - 1).coerceAtLeast(0))
    }

    val clarityColor = MaterialTheme.colorScheme.primary
    val stabilityColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surfaceColor = MaterialTheme.colorScheme.surface

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Curva de Evolução Vocal",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (ordered.isEmpty()) "Sem sessões para plotar"
                        else "Últimas ${ordered.size} sessões • toque nos pontos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendDot(color = clarityColor, label = "Clareza")
                    LegendDot(color = stabilityColor, label = "Estab.")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (ordered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Registre a primeira sessão de exercícios para visualizar o gráfico de evolução.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                val selectedSession = ordered.getOrNull(selectedIndex) ?: ordered.last()
                val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = dateFmt.format(Date(selectedSession.sessionDateMillis)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = selectedSession.exerciseTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ScorePill(
                                label = "Clar.",
                                value = "${selectedSession.vocalClarityScore}%",
                                color = clarityColor
                            )
                            ScorePill(
                                label = "Estab.",
                                value = "${selectedSession.stabilityScore}%",
                                color = stabilityColor
                            )
                            ScorePill(
                                label = "TMF",
                                value = String.format(Locale("pt", "BR"), "%.1fs", selectedSession.tmfSeconds),
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(165.dp)
                        .pointerInput(ordered) {
                            detectTapGestures { offset ->
                                val leftPad = 36.dp.toPx()
                                val rightPad = 14.dp.toPx()
                                val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                                if (ordered.size == 1) {
                                    selectedIndex = 0
                                } else {
                                    val step = plotW / (ordered.size - 1)
                                    val idx = ordered.indices.minByOrNull { i ->
                                        abs((leftPad + i * step) - offset.x)
                                    } ?: 0
                                    selectedIndex = idx
                                }
                            }
                        }
                ) {
                    val leftPad = 24.dp.toPx()
                    val rightPad = 12.dp.toPx()
                    val topPad = 12.dp.toPx()
                    val bottomPad = 18.dp.toPx()
                    val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    listOf(0f, 0.5f, 1f).forEach { frac ->
                        val py = topPad + plotH * (1f - frac)
                        drawLine(
                            color = gridColor,
                            start = Offset(leftPad, py),
                            end = Offset(size.width - rightPad, py),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = if (frac > 0f) dashEffect else null
                        )
                    }

                    val clarityPts = ordered.mapIndexed { idx, item ->
                        val x = if (ordered.size == 1) leftPad + plotW / 2f
                        else leftPad + (idx.toFloat() / (ordered.size - 1)) * plotW
                        val y = topPad + plotH * (1f - (item.vocalClarityScore.coerceIn(0, 100) / 100f))
                        Offset(x, y)
                    }
                    val stabilityPts = ordered.mapIndexed { idx, item ->
                        val x = if (ordered.size == 1) leftPad + plotW / 2f
                        else leftPad + (idx.toFloat() / (ordered.size - 1)) * plotW
                        val y = topPad + plotH * (1f - (item.stabilityScore.coerceIn(0, 100) / 100f))
                        Offset(x, y)
                    }

                    if (clarityPts.size > 1) {
                        val areaPath = Path().apply {
                            moveTo(clarityPts.first().x, topPad + plotH)
                            clarityPts.forEach { lineTo(it.x, it.y) }
                            lineTo(clarityPts.last().x, topPad + plotH)
                            close()
                        }
                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    clarityColor.copy(alpha = 0.22f),
                                    clarityColor.copy(alpha = 0.01f)
                                ),
                                startY = topPad,
                                endY = topPad + plotH
                            )
                        )

                        val stabPath = Path().apply {
                            moveTo(stabilityPts.first().x, stabilityPts.first().y)
                            for (i in 0 until stabilityPts.lastIndex) {
                                val p0 = stabilityPts[i]
                                val p1 = stabilityPts[i + 1]
                                val cx = (p0.x + p1.x) / 2f
                                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                            }
                        }
                        drawPath(
                            path = stabPath,
                            color = stabilityColor,
                            style = Stroke(
                                width = 2.4.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        val clarPath = Path().apply {
                            moveTo(clarityPts.first().x, clarityPts.first().y)
                            for (i in 0 until clarityPts.lastIndex) {
                                val p0 = clarityPts[i]
                                val p1 = clarityPts[i + 1]
                                val cx = (p0.x + p1.x) / 2f
                                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                            }
                        }
                        drawPath(
                            path = clarPath,
                            color = clarityColor,
                            style = Stroke(
                                width = 3.2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    // Selected vertical guide
                    val selX = clarityPts[selectedIndex.coerceIn(0, clarityPts.lastIndex)].x
                    drawLine(
                        color = clarityColor.copy(alpha = 0.35f),
                        start = Offset(selX, topPad),
                        end = Offset(selX, topPad + plotH),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    stabilityPts.forEachIndexed { idx, pt ->
                        val isSel = idx == selectedIndex
                        drawCircle(
                            color = surfaceColor,
                            radius = if (isSel) 5.5.dp.toPx() else 4.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = stabilityColor,
                            radius = if (isSel) 4.dp.toPx() else 2.8.dp.toPx(),
                            center = pt
                        )
                    }

                    clarityPts.forEachIndexed { idx, pt ->
                        val isSel = idx == selectedIndex
                        drawCircle(
                            color = surfaceColor,
                            radius = if (isSel) 7.dp.toPx() else 4.5.dp.toPx(),
                            center = pt
                        )
                        drawCircle(
                            color = clarityColor,
                            radius = if (isSel) 5.dp.toPx() else 3.2.dp.toPx(),
                            center = pt
                        )
                    }
                }

                // Bottom date labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val shortFmt = remember { SimpleDateFormat("dd/MM", Locale("pt", "BR")) }
                    ordered.forEachIndexed { idx, s ->
                        val showLabel = ordered.size <= 5 || idx == 0 || idx == ordered.lastIndex || idx == selectedIndex
                        Text(
                            text = if (showLabel) shortFmt.format(Date(s.sessionDateMillis)) else "",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (idx == selectedIndex) FontWeight.Bold else FontWeight.Normal,
                            color = if (idx == selectedIndex) clarityColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { selectedIndex = idx }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScorePill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
