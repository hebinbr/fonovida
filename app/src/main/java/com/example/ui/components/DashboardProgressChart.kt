package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EvolutionSession
import com.example.data.model.Patient
import com.example.data.model.ReportTimeframe
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusDangerBg
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class VoiceChartMetric(val label: String) {
    GERAL("Geral"),
    CLAREZA("Clareza"),
    ESTABILIDADE("Estabilidade"),
    CONFORTO("Conforto")
}

data class VoiceProgressChartPoint(
    val sessionId: String,
    val patientId: String,
    val patientName: String,
    val exerciseTitle: String,
    val timestampMillis: Long,
    val compositeScore: Int,
    val clarityScore: Int,
    val stabilityScore: Int,
    val comfortScore: Int,
    val tmfSeconds: Double
) {
    fun scoreFor(metric: VoiceChartMetric): Int = when (metric) {
        VoiceChartMetric.GERAL -> compositeScore
        VoiceChartMetric.CLAREZA -> clarityScore
        VoiceChartMetric.ESTABILIDADE -> stabilityScore
        VoiceChartMetric.CONFORTO -> comfortScore
    }
}

fun buildDashboardVoiceProgressSeries(
    sessions: List<EvolutionSession>,
    selectedPatientId: String?,
    timeframe: ReportTimeframe,
    nowMillis: Long = System.currentTimeMillis(),
    maxPoints: Int = 16
): List<VoiceProgressChartPoint> {
    val patientFiltered = if (selectedPatientId.isNullOrBlank()) {
        sessions
    } else {
        sessions.filter { it.patientId == selectedPatientId }
    }

    val days = timeframe.days
    val timeFiltered = if (days == null) {
        patientFiltered
    } else {
        val cutoff = nowMillis - (days.toLong() * 86_400_000L)
        patientFiltered.filter { it.sessionDateMillis >= cutoff }
    }

    return timeFiltered
        .sortedBy { it.sessionDateMillis }
        .takeLast(maxPoints.coerceAtLeast(1))
        .map { session ->
            VoiceProgressChartPoint(
                sessionId = session.id,
                patientId = session.patientId,
                patientName = session.patientName.ifBlank { "Paciente" },
                exerciseTitle = session.exerciseTitle.ifBlank { "Exercício Vocal" },
                timestampMillis = session.sessionDateMillis,
                compositeScore = session.compositeScore.coerceIn(0, 100),
                clarityScore = session.vocalClarityScore.toInt().coerceIn(0, 100),
                stabilityScore = session.stabilityScore.toInt().coerceIn(0, 100),
                comfortScore = session.comfortScore.toInt().coerceIn(0, 100),
                tmfSeconds = session.tmfSeconds
            )
        }
}

fun computeProgressDeltaPercent(
    points: List<VoiceProgressChartPoint>,
    metric: VoiceChartMetric = VoiceChartMetric.GERAL
): Int? {
    if (points.size < 2) return null
    return points.last().scoreFor(metric) - points.first().scoreFor(metric)
}

@Composable
fun DashboardVoiceProgressChartCard(
    patients: List<Patient>,
    sessions: List<EvolutionSession>,
    onOpenPatient: (String) -> Unit,
    onOpenReportsTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPatientId by remember { mutableStateOf<String?>(null) }
    var timeframe by remember { mutableStateOf(ReportTimeframe.TRINTA_DIAS) }
    var activeMetrics by remember {
        mutableStateOf(
            setOf(
                VoiceChartMetric.GERAL,
                VoiceChartMetric.CLAREZA,
                VoiceChartMetric.ESTABILIDADE
            )
        )
    }

    // Fall back to ALL if the selected patient was deleted
    val effectivePatientId = remember(patients, selectedPatientId) {
        if (selectedPatientId != null && patients.any { it.id == selectedPatientId }) {
            selectedPatientId
        } else {
            null
        }
    }

    val chartPoints = remember(sessions, effectivePatientId, timeframe) {
        buildDashboardVoiceProgressSeries(
            sessions = sessions,
            selectedPatientId = effectivePatientId,
            timeframe = timeframe
        )
    }

    var selectedIndex by remember(chartPoints.size, effectivePatientId, timeframe) {
        mutableIntStateOf((chartPoints.size - 1).coerceAtLeast(0))
    }

    val revealProgress by animateFloatAsState(
        targetValue = if (chartPoints.isEmpty()) 0f else 1f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "dashboard_chart_reveal"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val clarityColor = MaterialTheme.colorScheme.tertiary
    val stabilityColor = MaterialTheme.colorScheme.secondary
    val comfortColor = StatusSuccess
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val axisLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val textMeasurer = rememberTextMeasurer()

    fun colorForMetric(metric: VoiceChartMetric): Color = when (metric) {
        VoiceChartMetric.GERAL -> primaryColor
        VoiceChartMetric.CLAREZA -> clarityColor
        VoiceChartMetric.ESTABILIDADE -> stabilityColor
        VoiceChartMetric.CONFORTO -> comfortColor
    }

    val primaryDelta = remember(chartPoints, activeMetrics) {
        val headlineMetric = when {
            VoiceChartMetric.GERAL in activeMetrics -> VoiceChartMetric.GERAL
            else -> activeMetrics.firstOrNull() ?: VoiceChartMetric.GERAL
        }
        computeProgressDeltaPercent(chartPoints, headlineMetric)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("dashboard_voice_progress_chart_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ShowChart,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Evolução dos Exercícios Vocais",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (chartPoints.isEmpty()) {
                            "Desempenho longitudinal dos pacientes"
                        } else {
                            "${chartPoints.size} sessão(ões) no período • toque ou arraste"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (primaryDelta != null) {
                        val isPositive = primaryDelta >= 0
                        Surface(
                            color = if (isPositive) StatusSuccessBg else StatusDangerBg,
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isPositive) {
                                        Icons.AutoMirrored.Rounded.TrendingUp
                                    } else {
                                        Icons.AutoMirrored.Rounded.TrendingDown
                                    },
                                    contentDescription = null,
                                    tint = if (isPositive) StatusSuccess else StatusDanger,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = if (primaryDelta >= 0) "+$primaryDelta%" else "$primaryDelta%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPositive) StatusSuccess else StatusDanger
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = onOpenReportsTab,
                        modifier = Modifier.testTag("dashboard_chart_open_reports_btn")
                    ) {
                        Text("Relatórios", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Abrir relatórios completos",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Patient Filter Strip
            if (patients.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = effectivePatientId == null,
                        onClick = { selectedPatientId = null },
                        label = { Text("Todos os pacientes") },
                        modifier = Modifier.testTag("dashboard_chart_patient_all")
                    )
                    patients.forEach { pat ->
                        FilterChip(
                            selected = effectivePatientId == pat.id,
                            onClick = { selectedPatientId = pat.id },
                            label = { Text(pat.fullName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.testTag("dashboard_chart_patient_${pat.id}")
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
            }

            // Timeframe Filter Strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ReportTimeframe.entries.forEach { tf ->
                    FilterChip(
                        selected = timeframe == tf,
                        onClick = { timeframe = tf },
                        label = { Text(tf.label) },
                        modifier = Modifier.testTag("dashboard_chart_timeframe_${tf.name.lowercase()}")
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Toggleable Series Legend (Recharts-style interactive legend)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VoiceChartMetric.entries.forEach { metric ->
                    val selected = metric in activeMetrics
                    val dotColor = colorForMetric(metric)
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (selected && activeMetrics.size > 1) {
                                activeMetrics = activeMetrics - metric
                            } else if (!selected) {
                                activeMetrics = activeMetrics + metric
                            }
                        },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (selected) dotColor else axisLabelColor.copy(alpha = 0.35f))
                            )
                        },
                        label = { Text(metric.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = dotColor.copy(alpha = 0.14f),
                            selectedLabelColor = dotColor
                        ),
                        modifier = Modifier.testTag("dashboard_chart_metric_${metric.name.lowercase()}")
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            if (chartPoints.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (sessions.isEmpty()) {
                                "Nenhuma sessão de exercício vocal registrada ainda."
                            } else {
                                "Nenhuma sessão encontrada para o filtro selecionado."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (sessions.isNotEmpty() && (effectivePatientId != null || timeframe != ReportTimeframe.TUDO)) {
                            TextButton(
                                onClick = {
                                    selectedPatientId = null
                                    timeframe = ReportTimeframe.TUDO
                                },
                                modifier = Modifier.testTag("dashboard_chart_reset_filters_btn")
                            ) {
                                Text("Mostrar todo o histórico")
                            }
                        }
                    }
                }
            } else {
                val safeIdx = selectedIndex.coerceIn(0, chartPoints.lastIndex)
                val activePoint = chartPoints[safeIdx]
                val fullDateFmt = remember { SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")) }

                // Interactive Crosshair Tooltip Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dashboard_chart_tooltip")
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${activePoint.patientName} • ${fullDateFmt.format(Date(activePoint.timestampMillis))}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = primaryColor,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = String.format(
                                        Locale("pt", "BR"),
                                        "%s • TMF %.1fs",
                                        activePoint.exerciseTitle,
                                        activePoint.tmfSeconds
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "Prontuário",
                                style = MaterialTheme.typography.labelMedium,
                                color = primaryColor,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onOpenPatient(activePoint.patientId) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                                    .testTag("dashboard_chart_open_patient_btn")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            VoiceChartMetric.entries.forEach { metric ->
                                if (metric in activeMetrics) {
                                    TooltipMetricChip(
                                        label = metric.label,
                                        value = "${activePoint.scoreFor(metric)}%",
                                        color = colorForMetric(metric),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Line Chart Canvas with Y-Axis, Cartesian Grid, Smooth Curves, Crosshair & Active Dots
                val axisTextStyle = remember(axisLabelColor) {
                    TextStyle(
                        color = axisLabelColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .pointerInput(chartPoints) {
                            detectTapGestures { offset ->
                                val leftPad = 36.dp.toPx()
                                val rightPad = 14.dp.toPx()
                                val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                                if (chartPoints.size == 1) {
                                    selectedIndex = 0
                                } else {
                                    val step = plotW / (chartPoints.size - 1)
                                    val nearest = chartPoints.indices.minByOrNull { i ->
                                        abs((leftPad + i * step) - offset.x)
                                    } ?: 0
                                    selectedIndex = nearest
                                }
                            }
                        }
                        .pointerInput(chartPoints) {
                            detectHorizontalDragGestures { change, _ ->
                                change.consume()
                                val leftPad = 36.dp.toPx()
                                val rightPad = 14.dp.toPx()
                                val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                                if (chartPoints.size == 1) {
                                    selectedIndex = 0
                                } else {
                                    val step = plotW / (chartPoints.size - 1)
                                    val nearest = chartPoints.indices.minByOrNull { i ->
                                        abs((leftPad + i * step) - change.position.x)
                                    } ?: 0
                                    selectedIndex = nearest
                                }
                            }
                        }
                        .testTag("dashboard_line_chart_canvas")
                ) {
                    val leftPad = 36.dp.toPx()
                    val rightPad = 14.dp.toPx()
                    val topPad = 12.dp.toPx()
                    val bottomPad = 16.dp.toPx()
                    val plotW = (size.width - leftPad - rightPad).coerceAtLeast(1f)
                    val plotH = (size.height - topPad - bottomPad).coerceAtLeast(1f)

                    // Horizontal CartesianGrid + Y-Axis Ticks (0%, 25%, 50%, 75%, 100%)
                    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    listOf(0, 25, 50, 75, 100).forEach { pct ->
                        val frac = pct / 100f
                        val py = topPad + plotH * (1f - frac)
                        drawLine(
                            color = gridColor,
                            start = Offset(leftPad, py),
                            end = Offset(size.width - rightPad, py),
                            strokeWidth = if (pct == 0) 1.4.dp.toPx() else 1.dp.toPx(),
                            pathEffect = if (pct > 0) dashEffect else null
                        )
                        val measured = textMeasurer.measure(text = "$pct%", style = axisTextStyle)
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = (leftPad - measured.size.width - 6.dp.toPx()).coerceAtLeast(0f),
                                y = py - (measured.size.height / 2f)
                            )
                        )
                    }

                    // Helper to build plotted coordinates
                    fun buildPoints(metric: VoiceChartMetric): List<Offset> =
                        chartPoints.mapIndexed { idx, pt ->
                            val x = if (chartPoints.size == 1) {
                                leftPad + plotW / 2f
                            } else {
                                leftPad + (idx.toFloat() / (chartPoints.size - 1)) * plotW
                            }
                            val y = topPad + plotH * (1f - (pt.scoreFor(metric).coerceIn(0, 100) / 100f))
                            Offset(x, y)
                        }

                    fun buildSmoothPath(pts: List<Offset>): Path = Path().apply {
                        if (pts.isEmpty()) return@apply
                        moveTo(pts.first().x, pts.first().y)
                        for (i in 0 until pts.lastIndex) {
                            val p0 = pts[i]
                            val p1 = pts[i + 1]
                            val cx = (p0.x + p1.x) / 2f
                            cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                        }
                    }

                    val drawOrder = listOf(
                        VoiceChartMetric.CONFORTO,
                        VoiceChartMetric.ESTABILIDADE,
                        VoiceChartMetric.CLAREZA,
                        VoiceChartMetric.GERAL
                    ).filter { it in activeMetrics }

                    val areaMetric = if (VoiceChartMetric.GERAL in activeMetrics) {
                        VoiceChartMetric.GERAL
                    } else {
                        drawOrder.lastOrNull()
                    }

                    // Draw animated smooth curves + area fill
                    val clipWidth = leftPad + (plotW * revealProgress.coerceIn(0f, 1f)) + rightPad
                    clipRect(left = 0f, top = 0f, right = clipWidth, bottom = size.height) {
                        drawOrder.forEach { metric ->
                            val pts = buildPoints(metric)
                            val lineColor = colorForMetric(metric)

                            if (pts.size > 1) {
                                val smoothPath = buildSmoothPath(pts)
                                if (metric == areaMetric) {
                                    val areaPath = Path().apply {
                                        addPath(smoothPath)
                                        lineTo(pts.last().x, topPad + plotH)
                                        lineTo(pts.first().x, topPad + plotH)
                                        close()
                                    }
                                    drawPath(
                                        path = areaPath,
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                lineColor.copy(alpha = 0.22f),
                                                lineColor.copy(alpha = 0.01f)
                                            ),
                                            startY = topPad,
                                            endY = topPad + plotH
                                        )
                                    )
                                }
                                val strokeWidthDp = if (metric == VoiceChartMetric.GERAL) 3.2f else 2.3f
                                drawPath(
                                    path = smoothPath,
                                    color = lineColor,
                                    style = Stroke(
                                        width = strokeWidthDp.dp.toPx(),
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }

                    // Active Vertical Crosshair
                    val crosshairX = if (chartPoints.size == 1) {
                        leftPad + plotW / 2f
                    } else {
                        leftPad + (safeIdx.toFloat() / (chartPoints.size - 1)) * plotW
                    }
                    drawLine(
                        color = primaryColor.copy(alpha = 0.35f),
                        start = Offset(crosshairX, topPad),
                        end = Offset(crosshairX, topPad + plotH),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Dots & Active Halo
                    drawOrder.forEach { metric ->
                        val pts = buildPoints(metric)
                        val lineColor = colorForMetric(metric)
                        pts.forEachIndexed { idx, pt ->
                            val isSelected = idx == safeIdx
                            if (isSelected) {
                                drawCircle(
                                    color = lineColor.copy(alpha = 0.22f),
                                    radius = 8.5.dp.toPx(),
                                    center = pt
                                )
                            }
                            drawCircle(
                                color = surfaceColor,
                                radius = if (isSelected) 5.5.dp.toPx() else 3.8.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = lineColor,
                                radius = if (isSelected) 4.2.dp.toPx() else 2.7.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }

                // X-Axis Date Labels
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 32.dp, end = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val shortFmt = remember { SimpleDateFormat("dd/MM", Locale("pt", "BR")) }
                    chartPoints.forEachIndexed { idx, point ->
                        val showLabel = chartPoints.size <= 5 ||
                            idx == 0 ||
                            idx == chartPoints.lastIndex ||
                            idx == safeIdx
                        Text(
                            text = if (showLabel) shortFmt.format(Date(point.timestampMillis)) else "",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (idx == safeIdx) FontWeight.Bold else FontWeight.Normal,
                            color = if (idx == safeIdx) primaryColor else axisLabelColor,
                            modifier = Modifier.clickable { selectedIndex = idx }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TooltipMetricChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
