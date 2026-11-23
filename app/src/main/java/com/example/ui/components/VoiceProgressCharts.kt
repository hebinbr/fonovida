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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.data.model.ExerciseCategory
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusDangerBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
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
        sessions.sortedBy { it.sessionDateMillis }.takeLast(12)
    }
    var selectedIndex by remember(ordered.size) {
        mutableIntStateOf((ordered.size - 1).coerceAtLeast(0))
    }

    val clarityColor = MaterialTheme.colorScheme.primary
    val stabilityColor = MaterialTheme.colorScheme.secondary
    val comfortColor = MaterialTheme.colorScheme.tertiary
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendDot(color = clarityColor, label = "Clar.")
                    LegendDot(color = stabilityColor, label = "Estab.")
                    LegendDot(color = comfortColor, label = "Conf.")
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScorePill("Clar.", "${selectedSession.vocalClarityScore}%", clarityColor)
                            ScorePill("Estab.", "${selectedSession.stabilityScore}%", stabilityColor)
                            ScorePill("Conf.", "${selectedSession.comfortScore}%", comfortColor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .pointerInput(ordered) {
                            detectTapGestures { offset ->
                                val leftPad = 24.dp.toPx()
                                val rightPad = 12.dp.toPx()
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

                    fun buildPoints(extract: (EvolutionSession) -> Long): List<Offset> =
                        ordered.mapIndexed { idx, item ->
                            val x = if (ordered.size == 1) leftPad + plotW / 2f
                            else leftPad + (idx.toFloat() / (ordered.size - 1)) * plotW
                            val y = topPad + plotH * (1f - (extract(item).coerceIn(0, 100) / 100f))
                            Offset(x, y)
                        }

                    val clarityPts = buildPoints { it.vocalClarityScore }
                    val stabilityPts = buildPoints { it.stabilityScore }
                    val comfortPts = buildPoints { it.comfortScore }

                    fun drawSmoothCurve(pts: List<Offset>, color: Color, widthDp: Float) {
                        if (pts.size <= 1) return
                        val path = Path().apply {
                            moveTo(pts.first().x, pts.first().y)
                            for (i in 0 until pts.lastIndex) {
                                val p0 = pts[i]
                                val p1 = pts[i + 1]
                                val cx = (p0.x + p1.x) / 2f
                                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(
                                width = widthDp.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
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
                        drawSmoothCurve(comfortPts, comfortColor, 2.0f)
                        drawSmoothCurve(stabilityPts, stabilityColor, 2.4f)
                        drawSmoothCurve(clarityPts, clarityColor, 3.2f)
                    }

                    val selX = clarityPts[selectedIndex.coerceIn(0, clarityPts.lastIndex)].x
                    drawLine(
                        color = clarityColor.copy(alpha = 0.35f),
                        start = Offset(selX, topPad),
                        end = Offset(selX, topPad + plotH),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    listOf(
                        comfortPts to comfortColor,
                        stabilityPts to stabilityColor,
                        clarityPts to clarityColor
                    ).forEach { (pts, col) ->
                        pts.forEachIndexed { idx, pt ->
                            val isSel = idx == selectedIndex
                            drawCircle(
                                color = surfaceColor,
                                radius = if (isSel) 5.5.dp.toPx() else 3.8.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = col,
                                radius = if (isSel) 4.2.dp.toPx() else 2.8.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }

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
fun TmfProgressionChartCard(
    sessions: List<EvolutionSession>,
    modifier: Modifier = Modifier
) {
    val ordered = remember(sessions) {
        sessions.sortedBy { it.sessionDateMillis }.takeLast(10)
    }
    val barColor = MaterialTheme.colorScheme.tertiary
    val peakColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maxTmf = remember(ordered) {
        (ordered.maxOfOrNull { it.tmfSeconds } ?: 15.0).coerceAtLeast(10.0)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        text = "Tempo Máximo de Fonação (TMF)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Sustentação em segundos por sessão",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (ordered.isNotEmpty()) {
                    val best = ordered.maxOf { it.tmfSeconds }
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = String.format(Locale("pt", "BR"), "Pico: %.1fs", best),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (ordered.isEmpty()) {
                Text(
                    text = "Nenhuma medição de TMF registrada no período.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    val leftPad = 8.dp.toPx()
                    val rightPad = 8.dp.toPx()
                    val topPad = 10.dp.toPx()
                    val bottomPad = 8.dp.toPx()
                    val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                    drawLine(
                        color = gridColor,
                        start = Offset(leftPad, topPad + plotH),
                        end = Offset(size.width - rightPad, topPad + plotH),
                        strokeWidth = 1.dp.toPx()
                    )

                    val slotW = plotW / ordered.size.coerceAtLeast(1)
                    val barW = (slotW * 0.56f).coerceIn(10.dp.toPx(), 28.dp.toPx())
                    val bestVal = ordered.maxOf { it.tmfSeconds }

                    ordered.forEachIndexed { idx, item ->
                        val frac = (item.tmfSeconds / maxTmf).toFloat().coerceIn(0.05f, 1f)
                        val bh = plotH * frac
                        val bx = leftPad + idx * slotW + (slotW - barW) / 2f
                        val by = topPad + (plotH - bh)
                        val isPeak = item.tmfSeconds == bestVal
                        drawRoundRect(
                            color = if (isPeak) peakColor else barColor.copy(alpha = 0.72f),
                            topLeft = Offset(bx, by),
                            size = Size(barW, bh),
                            cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ordered.forEach { s ->
                        Text(
                            text = String.format(Locale("pt", "BR"), "%.1fs", s.tmfSeconds),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryPerformanceCard(
    sessions: List<EvolutionSession>,
    modifier: Modifier = Modifier
) {
    val breakdown = remember(sessions) {
        ExerciseCategory.entries.mapNotNull { cat ->
            val items = sessions.filter { it.exerciseCategory == cat }
            if (items.isEmpty()) null
            else Triple(
                cat,
                items.size to items.sumOf { it.durationMinutes }.toInt(),
                items.map { it.compositeScore }.average().toInt()
            )
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Desempenho por Categoria Vocal",
                style = MaterialTheme.typography.titleMedium
            )
            if (breakdown.isEmpty()) {
                Text(
                    text = "Sem sessões neste período para agrupar por categoria.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                breakdown.forEach { (cat, countAndMin, avgScore) ->
                    val (count, minutes) = countAndMin
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = cat.label,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "$avgScore% • $count sessões ($minutes min)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (avgScore / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(50))
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProgressDeltaCard(
    sessions: List<EvolutionSession>,
    modifier: Modifier = Modifier
) {
    val ordered = remember(sessions) { sessions.sortedBy { it.sessionDateMillis } }
    val first = ordered.firstOrNull()
    val last = ordered.lastOrNull()

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Comparativo Inicial vs. Atual",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (first == null || last == null) {
                Text(
                    text = "Registre sessões para acompanhar o ganho clínico ao longo do tempo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val clarityDelta = (last.vocalClarityScore - first.vocalClarityScore).toInt()
                val stabilityDelta = (last.stabilityScore - first.stabilityScore).toInt()
                val tmfDelta = last.tmfSeconds - first.tmfSeconds

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeltaMetricTile(
                        title = "Clareza",
                        current = "${last.vocalClarityScore}%",
                        deltaText = formatIntDelta(clarityDelta, "%"),
                        positive = clarityDelta >= 0,
                        modifier = Modifier.weight(1f)
                    )
                    DeltaMetricTile(
                        title = "Estabilidade",
                        current = "${last.stabilityScore}%",
                        deltaText = formatIntDelta(stabilityDelta, "%"),
                        positive = stabilityDelta >= 0,
                        modifier = Modifier.weight(1f)
                    )
                    DeltaMetricTile(
                        title = "TMF",
                        current = String.format(Locale("pt", "BR"), "%.1fs", last.tmfSeconds),
                        deltaText = String.format(Locale("pt", "BR"), "%+.1fs", tmfDelta),
                        positive = tmfDelta >= 0.0,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun formatIntDelta(value: Int, suffix: String): String =
    if (value >= 0) "+$value$suffix" else "$value$suffix"

@Composable
private fun DeltaMetricTile(
    title: String,
    current: String,
    deltaText: String,
    positive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = current,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Surface(
                color = if (positive) StatusSuccessBg else StatusDangerBg,
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = deltaText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (positive) StatusSuccess else StatusDanger,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
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
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
