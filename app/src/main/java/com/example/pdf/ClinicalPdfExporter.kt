package com.example.pdf

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.CustomExerciseList
import com.example.data.model.EvolutionSession
import com.example.data.model.ExerciseCategory
import com.example.data.model.Patient
import com.example.data.model.TherapistProfile
import com.example.data.model.VoiceExercise
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ClinicalPdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val REPORT_CACHE_TTL_MS = 24L * 3_600_000L

    private fun pruneStaleReports(reportsDir: File, nowMillis: Long = System.currentTimeMillis()) {
        reportsDir.listFiles()?.forEach { candidate ->
            if (candidate.isFile && (nowMillis - candidate.lastModified()) > REPORT_CACHE_TTL_MS) {
                candidate.delete()
            }
        }
    }

    fun generateAndShareReport(
        context: Context,
        patient: Patient,
        sessions: List<EvolutionSession>,
        exercises: List<VoiceExercise>,
        profile: TherapistProfile?,
        periodLabel: String = "Todo o histórico"
    ): Result<Uri> {
        return try {
            val pdfFile = generateReportFile(context, patient, sessions, exercises, profile, periodLabel)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Relatório Fonoaudiológico - ${patient.fullName}"
                )
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Olá, ${patient.guardianName}! Segue o relatório detalhado de evolução dos exercícios de voz de ${patient.fullName} ($periodLabel)."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Compartilhar Relatório PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun openPdfPreview(
        context: Context,
        patient: Patient,
        sessions: List<EvolutionSession>,
        exercises: List<VoiceExercise>,
        profile: TherapistProfile?,
        periodLabel: String = "Todo o histórico"
    ): Result<Uri> {
        return try {
            val pdfFile = generateReportFile(context, patient, sessions, exercises, profile, periodLabel)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(viewIntent, "Visualizar Relatório PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun generateReportFile(
        context: Context,
        patient: Patient,
        sessions: List<EvolutionSession>,
        exercises: List<VoiceExercise>,
        profile: TherapistProfile?,
        periodLabel: String = "Todo o histórico"
    ): File {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        pruneStaleReports(reportsDir)
        val safePatientSlug = patient.fullName
            .lowercase(Locale("pt", "BR"))
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(40)
            .ifBlank { "paciente" }
        val file = File(reportsDir, "relatorio_fonovida_${safePatientSlug}_${System.currentTimeMillis()}.pdf")

        val document = PdfDocument()
        val orderedSessions = sessions.sortedBy { it.sessionDateMillis }
        val recentSessionsDesc = sessions.sortedByDescending { it.sessionDateMillis }

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        val dateTimeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

        val tealColor = Color.rgb(14, 114, 104)
        val coralColor = Color.rgb(217, 101, 75)
        val slateColor = Color.rgb(46, 107, 142)
        val ivoryCardBg = Color.rgb(246, 244, 239)
        val darkInk = Color.rgb(26, 34, 32)
        val mutedInk = Color.rgb(90, 101, 98)
        val lineColor = Color.rgb(218, 212, 202)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(210, 240, 236)
            textSize = 10f
        }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tealColor
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkInk
            textSize = 10.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkInk
            textSize = 10f
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedInk
            textSize = 9f
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = lineColor
        }

        var pageNumber = 1
        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = 0f

        fun drawFooter(c: Canvas, pageNum: Int) {
            c.drawLine(MARGIN, PAGE_HEIGHT - 42f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 42f, strokePaint)
            c.drawText(
                "Relatório gerado por FonoVida • Documento Clínico para Responsáveis",
                MARGIN,
                PAGE_HEIGHT - 24f,
                mutedPaint
            )
            c.drawText(
                "Página $pageNum",
                PAGE_WIDTH - MARGIN - 45f,
                PAGE_HEIGHT - 24f,
                mutedPaint
            )
        }

        fun startNewPage() {
            drawFooter(canvas, pageNumber)
            document.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN
        }

        // --- HEADER BANNER ---
        fillPaint.color = tealColor
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 92f, fillPaint)
        canvas.drawText("FonoVida • Relatório Detalhado de Evolução Vocal", MARGIN, 36f, titlePaint)
        val therapistLine = buildString {
            append(profile?.displayName?.ifBlank { "Fonoaudióloga Responsável" } ?: "Fonoaudióloga Responsável")
            if (!profile?.crfa.isNullOrBlank()) append(" • ${profile?.crfa}")
            if (!profile?.clinicName.isNullOrBlank()) append(" • ${profile?.clinicName}")
        }
        canvas.drawText(therapistLine, MARGIN, 56f, subtitlePaint)
        canvas.drawText("Período: $periodLabel  •  Emitido em ${dateTimeFormat.format(Date())}", MARGIN, 72f, subtitlePaint)

        y = 110f

        // --- PATIENT & GUARDIAN CARD ---
        fillPaint.color = ivoryCardBg
        val patientCardRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 84f)
        canvas.drawRoundRect(patientCardRect, 10f, 10f, fillPaint)
        canvas.drawRoundRect(patientCardRect, 10f, 10f, strokePaint)

        canvas.drawText("Paciente: ${patient.fullName} (${patient.ageYears} anos)", MARGIN + 14f, y + 22f, bodyBoldPaint)
        canvas.drawText("Foco Clínico: ${patient.clinicalFocus}", MARGIN + 14f, y + 39f, bodyPaint)
        canvas.drawText("Responsável: ${patient.guardianName}  |  Contato: ${patient.guardianPhone.ifBlank { "Não informado" }}", MARGIN + 14f, y + 56f, bodyPaint)
        canvas.drawText("Status: ${patient.patientStatus.label}  •  Meta Semanal: ${patient.weeklyGoalMinutes} min", MARGIN + 14f, y + 72f, mutedPaint)

        y += 102f

        // --- SUMMARY KPIs ---
        canvas.drawText("Resumo Clínico e Evolução no Período", MARGIN, y, sectionPaint)
        y += 12f

        val avgClarity = if (sessions.isEmpty()) 0 else sessions.map { it.vocalClarityScore }.average().toInt()
        val avgStability = if (sessions.isEmpty()) 0 else sessions.map { it.stabilityScore }.average().toInt()
        val latestTmf = orderedSessions.lastOrNull()?.tmfSeconds ?: 0.0
        val firstSession = orderedSessions.firstOrNull()
        val lastSession = orderedSessions.lastOrNull()
        val clarityDelta = if (firstSession != null && lastSession != null) (lastSession.vocalClarityScore - firstSession.vocalClarityScore).toInt() else 0
        val totalMinutes = sessions.sumOf { it.durationMinutes }

        val cardWidth = ((PAGE_WIDTH - MARGIN * 2) - 24f) / 4f
        val kpis = listOf(
            Triple("Sessões", "${sessions.size} (${totalMinutes}m)", tealColor),
            Triple("Clareza Média", "$avgClarity% (${if (clarityDelta >= 0) "+$clarityDelta" else "$clarityDelta"})", tealColor),
            Triple("Estabilidade", "$avgStability%", slateColor),
            Triple("TMF Atual", String.format(Locale("pt", "BR"), "%.1fs", latestTmf), coralColor)
        )

        kpis.forEachIndexed { index, (label, value, accent) ->
            val left = MARGIN + index * (cardWidth + 8f)
            val rect = RectF(left, y, left + cardWidth, y + 54f)
            fillPaint.color = Color.WHITE
            canvas.drawRoundRect(rect, 8f, 8f, fillPaint)
            canvas.drawRoundRect(rect, 8f, 8f, strokePaint)

            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                textSize = 13.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(label, left + 10f, y + 18f, mutedPaint)
            canvas.drawText(value, left + 10f, y + 40f, valuePaint)
        }

        y += 70f

        // --- DUAL CHARTS ON PDF CANVAS ---
        if (orderedSessions.isNotEmpty()) {
            canvas.drawText("Gráficos de Desempenho Vocal (Clareza, Estabilidade e TMF)", MARGIN, y, sectionPaint)
            y += 10f

            val points = orderedSessions.takeLast(12)
            val halfWidth = (PAGE_WIDTH - MARGIN * 2 - 12f) / 2f

            // Left chart: Clarity / Stability / Comfort
            val leftRect = RectF(MARGIN, y, MARGIN + halfWidth, y + 135f)
            fillPaint.color = Color.WHITE
            canvas.drawRoundRect(leftRect, 8f, 8f, fillPaint)
            canvas.drawRoundRect(leftRect, 8f, 8f, strokePaint)

            val plotLeft = leftRect.left + 28f
            val plotRight = leftRect.right - 12f
            val plotTop = leftRect.top + 22f
            val plotBottom = leftRect.bottom - 22f
            val plotHeight = plotBottom - plotTop
            val plotWidth = plotRight - plotLeft

            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(232, 228, 220)
                strokeWidth = 0.8f
            }
            listOf(0, 50, 100).forEach { pct ->
                val py = plotBottom - (pct / 100f) * plotHeight
                canvas.drawLine(plotLeft, py, plotRight, py, gridPaint)
                canvas.drawText("$pct", leftRect.left + 6f, py + 3f, mutedPaint)
            }

            val clarityPath = Path()
            val stabilityPath = Path()
            val comfortPath = Path()

            points.forEachIndexed { i, session ->
                val px = if (points.size == 1) plotLeft + plotWidth / 2f
                else plotLeft + (i.toFloat() / (points.size - 1)) * plotWidth
                val cy = plotBottom - (session.vocalClarityScore.coerceIn(0, 100) / 100f) * plotHeight
                val sy = plotBottom - (session.stabilityScore.coerceIn(0, 100) / 100f) * plotHeight
                val coy = plotBottom - (session.comfortScore.coerceIn(0, 100) / 100f) * plotHeight

                if (i == 0) {
                    clarityPath.moveTo(px, cy)
                    stabilityPath.moveTo(px, sy)
                    comfortPath.moveTo(px, coy)
                } else {
                    clarityPath.lineTo(px, cy)
                    stabilityPath.lineTo(px, sy)
                    comfortPath.lineTo(px, coy)
                }
            }

            canvas.drawPath(comfortPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = slateColor; style = Paint.Style.STROKE; strokeWidth = 1.6f
            })
            canvas.drawPath(stabilityPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = coralColor; style = Paint.Style.STROKE; strokeWidth = 2.0f
            })
            canvas.drawPath(clarityPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tealColor; style = Paint.Style.STROKE; strokeWidth = 2.5f
            })

            canvas.drawText("Clareza / Estab. / Conforto (%)", leftRect.left + 10f, leftRect.top + 13f, mutedPaint)

            // Colored legend dots on top-right of left chart
            val legendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7.5f; color = mutedInk }
            val legStart = leftRect.right - 98f
            fillPaint.color = tealColor
            canvas.drawCircle(legStart, leftRect.top + 10f, 2.5f, fillPaint)
            canvas.drawText("Clar.", legStart + 5f, leftRect.top + 13f, legendPaint)
            fillPaint.color = coralColor
            canvas.drawCircle(legStart + 32f, leftRect.top + 10f, 2.5f, fillPaint)
            canvas.drawText("Est.", legStart + 37f, leftRect.top + 13f, legendPaint)
            fillPaint.color = slateColor
            canvas.drawCircle(legStart + 62f, leftRect.top + 10f, 2.5f, fillPaint)
            canvas.drawText("Conf.", legStart + 67f, leftRect.top + 13f, legendPaint)

            // X-axis start and end dates on left chart
            val shortDateFmt = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
            if (points.isNotEmpty()) {
                canvas.drawText(
                    shortDateFmt.format(Date(points.first().sessionDateMillis)),
                    plotLeft,
                    leftRect.bottom - 7f,
                    legendPaint
                )
                if (points.size > 1) {
                    val endStr = shortDateFmt.format(Date(points.last().sessionDateMillis))
                    canvas.drawText(endStr, plotRight - 22f, leftRect.bottom - 7f, legendPaint)
                }
            }

            // Right chart: TMF Bar Chart
            val rightRect = RectF(MARGIN + halfWidth + 12f, y, PAGE_WIDTH - MARGIN, y + 135f)
            fillPaint.color = Color.WHITE
            canvas.drawRoundRect(rightRect, 8f, 8f, fillPaint)
            canvas.drawRoundRect(rightRect, 8f, 8f, strokePaint)
            canvas.drawText("Tempo Máximo de Fonação (TMF)", rightRect.left + 10f, rightRect.top + 13f, mutedPaint)

            val tmfPlotLeft = rightRect.left + 16f
            val tmfPlotRight = rightRect.right - 14f
            val tmfPlotTop = rightRect.top + 26f
            val tmfPlotBottom = rightRect.bottom - 22f
            val tmfPlotW = tmfPlotRight - tmfPlotLeft
            val tmfPlotH = tmfPlotBottom - tmfPlotTop
            val maxTmf = (points.maxOfOrNull { it.tmfSeconds } ?: 15.0).coerceAtLeast(10.0)
            val slotW = tmfPlotW / points.size.coerceAtLeast(1)
            val barW = (slotW * 0.55f).coerceIn(8f, 20f)

            canvas.drawLine(tmfPlotLeft, tmfPlotBottom, tmfPlotRight, tmfPlotBottom, gridPaint)
            points.forEachIndexed { i, session ->
                val frac = (session.tmfSeconds / maxTmf).toFloat().coerceIn(0.05f, 1f)
                val bh = tmfPlotH * frac
                val bx = tmfPlotLeft + i * slotW + (slotW - barW) / 2f
                val by = tmfPlotBottom - bh
                fillPaint.color = if (i == points.lastIndex) tealColor else slateColor
                canvas.drawRoundRect(RectF(bx, by, bx + barW, tmfPlotBottom), 3f, 3f, fillPaint)
                if (points.size <= 6 || i == 0 || i == points.lastIndex) {
                    canvas.drawText(
                        String.format(Locale("pt", "BR"), "%.0fs", session.tmfSeconds),
                        bx,
                        rightRect.bottom - 8f,
                        mutedPaint
                    )
                }
            }

            y += 155f

            // --- CATEGORY PERFORMANCE BREAKDOWN ---
            val catSummary = ExerciseCategory.entries.mapNotNull { cat ->
                val catSessions = orderedSessions.filter { it.exerciseCategory == cat }
                if (catSessions.isEmpty()) null
                else Triple(
                    cat.label,
                    catSessions.size,
                    catSessions.map { it.compositeScore }.average().toInt()
                )
            }
            if (catSummary.isNotEmpty()) {
                if (y > PAGE_HEIGHT - 130f) startNewPage()
                canvas.drawText("Desempenho Médio por Categoria de Exercício", MARGIN, y, sectionPaint)
                y += 12f
                catSummary.forEach { (catLabel, count, avgScore) ->
                    if (y > PAGE_HEIGHT - 85f) startNewPage()
                    canvas.drawText("$catLabel ($count sessões)", MARGIN + 4f, y, bodyPaint)
                    canvas.drawText("$avgScore%", PAGE_WIDTH - MARGIN - 34f, y, bodyBoldPaint)
                    val barStart = MARGIN + 195f
                    val barMaxW = (PAGE_WIDTH - MARGIN - 42f) - barStart
                    if (barMaxW > 40f) {
                        fillPaint.color = Color.rgb(232, 228, 220)
                        canvas.drawRoundRect(RectF(barStart, y - 7f, barStart + barMaxW, y), 3f, 3f, fillPaint)
                        fillPaint.color = tealColor
                        val filledW = barMaxW * (avgScore.coerceIn(0, 100) / 100f)
                        canvas.drawRoundRect(RectF(barStart, y - 7f, barStart + filledW, y), 3f, 3f, fillPaint)
                    }
                    y += 16f
                }
                y += 8f
            }
        }

        // --- ACTIVE PRESCRIBED EXERCISES ---
        val activeExercises = exercises.filter { it.activeFlag }
        if (activeExercises.isNotEmpty()) {
            if (y > PAGE_HEIGHT - 130f) startNewPage()
            canvas.drawText("Plano de Exercícios Prescritos", MARGIN, y, sectionPaint)
            y += 14f
            activeExercises.take(5).forEach { ex ->
                if (y > PAGE_HEIGHT - 90f) startNewPage()
                canvas.drawText(
                    "• ${ex.title} (${ex.exerciseCategory.label}) — ${ex.targetDurationMinutes} min, ${ex.frequencyPerWeek}x/semana",
                    MARGIN + 4f,
                    y,
                    bodyBoldPaint
                )
                y += 14f
                val wrapped = wrapText(ex.instructions, bodyPaint, PAGE_WIDTH - MARGIN * 2 - 16f)
                wrapped.take(2).forEach { line ->
                    canvas.drawText(line, MARGIN + 12f, y, mutedPaint)
                    y += 12f
                }
                y += 4f
            }
            y += 10f
        }

        // --- CHRONOLOGICAL EVOLUTION HISTORY ---
        if (y > PAGE_HEIGHT - 120f) startNewPage()
        canvas.drawText("Histórico Detalhado de Sessões", MARGIN, y, sectionPaint)
        y += 14f

        if (recentSessionsDesc.isEmpty()) {
            canvas.drawText("Nenhuma sessão registrada no período selecionado.", MARGIN, y, bodyPaint)
            y += 20f
        } else {
            recentSessionsDesc.forEach { session ->
                val noteLines = if (session.clinicalNotes.isNotBlank()) {
                    wrapText("Evolução: ${session.clinicalNotes}", bodyPaint, PAGE_WIDTH - MARGIN * 2 - 20f)
                } else emptyList()
                val guidanceLines = if (session.homeGuidance.isNotBlank()) {
                    wrapText("Em casa: ${session.homeGuidance}", mutedPaint, PAGE_WIDTH - MARGIN * 2 - 20f)
                } else emptyList()

                val neededHeight = 46f + (noteLines.size * 13f) + (guidanceLines.size * 12f) + 12f
                if (y + neededHeight > PAGE_HEIGHT - 75f) {
                    startNewPage()
                    canvas.drawText("Histórico Detalhado (continuação)", MARGIN, y, sectionPaint)
                    y += 16f
                }

                val boxRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + neededHeight - 6f)
                fillPaint.color = ivoryCardBg
                canvas.drawRoundRect(boxRect, 8f, 8f, fillPaint)
                canvas.drawRoundRect(boxRect, 8f, 8f, strokePaint)

                val headerStr = "${dateFormat.format(Date(session.sessionDateMillis))} • ${session.exerciseTitle}"
                canvas.drawText(headerStr.take(65), MARGIN + 10f, y + 17f, bodyBoldPaint)

                val metricsStr = "Clareza: ${session.vocalClarityScore}%  |  Estabilidade: ${session.stabilityScore}%  |  Conforto: ${session.comfortScore}%  |  TMF: ${String.format(Locale("pt", "BR"), "%.1fs", session.tmfSeconds)}  |  ${session.durationMinutes} min"
                canvas.drawText(metricsStr, MARGIN + 10f, y + 32f, mutedPaint)

                var textY = y + 46f
                noteLines.forEach { line ->
                    canvas.drawText(line, MARGIN + 10f, textY, bodyPaint)
                    textY += 13f
                }
                guidanceLines.forEach { line ->
                    canvas.drawText(line, MARGIN + 10f, textY, mutedPaint)
                    textY += 12f
                }
                y += neededHeight
            }
        }

        // Signature block at end of report
        if (y > PAGE_HEIGHT - 95f) startNewPage()
        y += 18f
        canvas.drawLine(MARGIN, y + 22f, MARGIN + 220f, y + 22f, strokePaint)
        canvas.drawText(
            profile?.displayName?.ifBlank { "Fonoaudióloga" } ?: "Fonoaudióloga",
            MARGIN,
            y + 35f,
            bodyBoldPaint
        )
        if (!profile?.crfa.isNullOrBlank()) {
            canvas.drawText(profile?.crfa ?: "", MARGIN, y + 47f, mutedPaint)
        }

        drawFooter(canvas, pageNumber)
        document.finishPage(page)

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
    }

    fun generateAndShareExerciseListPdf(
        context: Context,
        list: CustomExerciseList,
        patient: Patient?,
        profile: TherapistProfile?
    ): Result<Uri> {
        return try {
            val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
            pruneStaleReports(reportsDir)
            val safeSlug = list.title
                .lowercase(Locale("pt", "BR"))
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .take(40)
                .ifBlank { "lista" }
            val file = File(reportsDir, "lista_exercicios_${safeSlug}_${System.currentTimeMillis()}.pdf")

            val document = PdfDocument()
            val tealColor = Color.rgb(14, 114, 104)
            val ivoryCardBg = Color.rgb(246, 244, 239)
            val darkInk = Color.rgb(26, 34, 32)
            val mutedInk = Color.rgb(90, 101, 98)
            val lineColor = Color.rgb(218, 212, 202)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(210, 240, 236)
                textSize = 10f
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tealColor
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyBoldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = darkInk
                textSize = 10.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = darkInk
                textSize = 10f
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mutedInk
                textSize = 9f
            }
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = lineColor
            }

            var pageNumber = 1
            var page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            var canvas = page.canvas

            fun drawFooter(c: Canvas, num: Int) {
                c.drawLine(MARGIN, PAGE_HEIGHT - 42f, PAGE_WIDTH - MARGIN, PAGE_HEIGHT - 42f, strokePaint)
                c.drawText("Roteiro Terapêutico FonoVida • Uso sob orientação fonoaudiológica", MARGIN, PAGE_HEIGHT - 24f, mutedPaint)
                c.drawText("Página $num", PAGE_WIDTH - MARGIN - 45f, PAGE_HEIGHT - 24f, mutedPaint)
            }

            fillPaint.color = tealColor
            canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 88f, fillPaint)
            canvas.drawText("FonoVida • Lista Personalizada de Exercícios", MARGIN, 35f, titlePaint)
            val therapistLine = buildString {
                append(profile?.displayName?.ifBlank { "Fonoaudióloga Responsável" } ?: "Fonoaudióloga Responsável")
                if (!profile?.crfa.isNullOrBlank()) append(" • ${profile?.crfa}")
                if (!profile?.clinicName.isNullOrBlank()) append(" • ${profile?.clinicName}")
            }
            canvas.drawText(therapistLine, MARGIN, 55f, subtitlePaint)
            val targetName = patient?.fullName ?: list.patientName.ifBlank { "Protocolo Clínico Geral" }
            canvas.drawText("Indicação: $targetName  •  ${list.items.size} exercício(s)", MARGIN, 71f, subtitlePaint)

            var y = 108f
            canvas.drawText(list.title, MARGIN, y, sectionPaint)
            y += 16f
            if (list.clinicalObjective.isNotBlank()) {
                val objLines = wrapText("Objetivo: ${list.clinicalObjective}", bodyPaint, PAGE_WIDTH - MARGIN * 2)
                objLines.forEach { line ->
                    canvas.drawText(line, MARGIN, y, bodyPaint)
                    y += 13f
                }
                y += 8f
            }

            list.items.forEachIndexed { idx, item ->
                val instLines = wrapText(item.instructions, bodyPaint, PAGE_WIDTH - MARGIN * 2 - 22f)
                val neededH = 44f + (instLines.size * 13f) + 10f
                if (y + neededH > PAGE_HEIGHT - 75f) {
                    drawFooter(canvas, pageNumber)
                    document.finishPage(page)
                    pageNumber++
                    page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                    canvas = page.canvas
                    y = MARGIN
                }
                val cardRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + neededH - 6f)
                fillPaint.color = ivoryCardBg
                canvas.drawRoundRect(cardRect, 8f, 8f, fillPaint)
                canvas.drawRoundRect(cardRect, 8f, 8f, strokePaint)

                canvas.drawText("${idx + 1}. ${item.title}", MARGIN + 10f, y + 18f, bodyBoldPaint)
                canvas.drawText(
                    "${item.exerciseCategory.label}  |  Duração: ${item.durationMinutes} min  |  Frequência: ${item.frequencyPerWeek}x/semana",
                    MARGIN + 10f,
                    y + 33f,
                    mutedPaint
                )
                var textY = y + 48f
                instLines.forEach { line ->
                    canvas.drawText(line, MARGIN + 10f, textY, bodyPaint)
                    textY += 13f
                }
                y += neededH
            }

            drawFooter(canvas, pageNumber)
            document.finishPage(page)
            FileOutputStream(file).use { out -> document.writeTo(out) }
            document.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Exercícios de Voz - ${list.title}")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Segue a lista personalizada de exercícios vocais: ${list.title}."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Compartilhar Lista em PDF").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.trim().split(Regex("\\s+"))
        if (words.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = StringBuilder(candidate)
            } else {
                if (current.isNotEmpty()) lines.add(current.toString())
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines.add(current.toString())
        return lines
    }
}
