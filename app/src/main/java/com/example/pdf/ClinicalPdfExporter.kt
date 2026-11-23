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
import com.example.data.model.EvolutionSession
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

    fun generateAndShareReport(
        context: Context,
        patient: Patient,
        sessions: List<EvolutionSession>,
        exercises: List<VoiceExercise>,
        profile: TherapistProfile?
    ): Result<Uri> {
        return try {
            val pdfFile = generateReportFile(context, patient, sessions, exercises, profile)
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
                    "Olá, ${patient.guardianName}! Segue o relatório detalhado de evolução dos exercícios de voz de ${patient.fullName}."
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

    fun generateReportFile(
        context: Context,
        patient: Patient,
        sessions: List<EvolutionSession>,
        exercises: List<VoiceExercise>,
        profile: TherapistProfile?
    ): File {
        val reportsDir = File(context.cacheDir, "reports").apply { mkdirs() }
        val safePatientSlug = patient.fullName
            .lowercase(Locale("pt", "BR"))
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
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
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 88f, fillPaint)
        canvas.drawText("FonoVida • Relatório de Evolução Vocal", MARGIN, 36f, titlePaint)
        val therapistLine = buildString {
            append(profile?.displayName?.ifBlank { "Fonoaudióloga Responsável" } ?: "Fonoaudióloga Responsável")
            if (!profile?.crfa.isNullOrBlank()) append(" • ${profile?.crfa}")
            if (!profile?.clinicName.isNullOrBlank()) append(" • ${profile?.clinicName}")
        }
        canvas.drawText(therapistLine, MARGIN, 56f, subtitlePaint)
        canvas.drawText("Emitido em ${dateTimeFormat.format(Date())}", MARGIN, 72f, subtitlePaint)

        y = 108f

        // --- PATIENT & GUARDIAN CARD ---
        fillPaint.color = ivoryCardBg
        val patientCardRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 84f)
        canvas.drawRoundRect(patientCardRect, 10f, 10f, fillPaint)
        canvas.drawRoundRect(patientCardRect, 10f, 10f, strokePaint)

        canvas.drawText("Paciente: ${patient.fullName} (${patient.ageYears} anos)", MARGIN + 14f, y + 22f, bodyBoldPaint)
        canvas.drawText("Foco Clínico: ${patient.clinicalFocus}", MARGIN + 14f, y + 39f, bodyPaint)
        canvas.drawText("Responsável: ${patient.guardianName}  |  Contato: ${patient.guardianPhone.ifBlank { "Não informado" }}", MARGIN + 14f, y + 56f, bodyPaint)
        canvas.drawText("Status: ${patient.patientStatus.label}  •  Meta Semanal: ${patient.weeklyGoalMinutes} min", MARGIN + 14f, y + 72f, mutedPaint)

        y += 104f

        // --- SUMMARY KPIs ---
        canvas.drawText("Resumo Clínico de Desempenho", MARGIN, y, sectionPaint)
        y += 12f

        val avgClarity = if (sessions.isEmpty()) 0 else sessions.map { it.vocalClarityScore }.average().toInt()
        val avgStability = if (sessions.isEmpty()) 0 else sessions.map { it.stabilityScore }.average().toInt()
        val latestTmf = orderedSessions.lastOrNull()?.tmfSeconds ?: 0.0
        val totalMinutes = sessions.sumOf { it.durationMinutes }

        val cardWidth = ((PAGE_WIDTH - MARGIN * 2) - 24f) / 4f
        val kpis = listOf(
            Triple("Sessões", "${sessions.size}", tealColor),
            Triple("Clareza Média", "$avgClarity%", tealColor),
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
                textSize = 16f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(label, left + 10f, y + 18f, mutedPaint)
            canvas.drawText(value, left + 10f, y + 40f, valuePaint)
        }

        y += 66f
        canvas.drawText("Total acumulado em terapia: $totalMinutes minutos registrados", MARGIN, y, mutedPaint)
        y += 22f

        // --- EVOLUTION CHART (VECTOR ON PDF CANVAS) ---
        if (orderedSessions.isNotEmpty()) {
            canvas.drawText("Curva de Evolução (Clareza Vocal vs. Estabilidade)", MARGIN, y, sectionPaint)
            y += 10f

            val chartRect = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 135f)
            fillPaint.color = Color.WHITE
            canvas.drawRoundRect(chartRect, 8f, 8f, fillPaint)
            canvas.drawRoundRect(chartRect, 8f, 8f, strokePaint)

            val plotLeft = chartRect.left + 36f
            val plotRight = chartRect.right - 18f
            val plotTop = chartRect.top + 18f
            val plotBottom = chartRect.bottom - 26f
            val plotHeight = plotBottom - plotTop
            val plotWidth = plotRight - plotLeft

            val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(232, 228, 220)
                strokeWidth = 0.8f
            }
            listOf(0, 50, 100).forEach { pct ->
                val py = plotBottom - (pct / 100f) * plotHeight
                canvas.drawLine(plotLeft, py, plotRight, py, gridPaint)
                canvas.drawText("$pct%", chartRect.left + 8f, py + 3f, mutedPaint)
            }

            val points = orderedSessions.takeLast(12)
            val clarityPath = Path()
            val stabilityPath = Path()
            val clarityLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tealColor
                style = Paint.Style.STROKE
                strokeWidth = 2.4f
            }
            val stabilityLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = coralColor
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }

            points.forEachIndexed { i, session ->
                val px = if (points.size == 1) plotLeft + plotWidth / 2f
                else plotLeft + (i.toFloat() / (points.size - 1).toFloat()) * plotWidth
                val cy = plotBottom - (session.vocalClarityScore.coerceIn(0, 100) / 100f) * plotHeight
                val sy = plotBottom - (session.stabilityScore.coerceIn(0, 100) / 100f) * plotHeight

                if (i == 0) {
                    clarityPath.moveTo(px, cy)
                    stabilityPath.moveTo(px, sy)
                } else {
                    clarityPath.lineTo(px, cy)
                    stabilityPath.lineTo(px, sy)
                }
            }
            canvas.drawPath(stabilityPath, stabilityLinePaint)
            canvas.drawPath(clarityPath, clarityLinePaint)

            points.forEachIndexed { i, session ->
                val px = if (points.size == 1) plotLeft + plotWidth / 2f
                else plotLeft + (i.toFloat() / (points.size - 1).toFloat()) * plotWidth
                val cy = plotBottom - (session.vocalClarityScore.coerceIn(0, 100) / 100f) * plotHeight
                val sy = plotBottom - (session.stabilityScore.coerceIn(0, 100) / 100f) * plotHeight

                fillPaint.color = tealColor
                canvas.drawCircle(px, cy, 3.5f, fillPaint)
                fillPaint.color = coralColor
                canvas.drawCircle(px, sy, 3f, fillPaint)

                if (i == 0 || i == points.lastIndex || points.size <= 6) {
                    val shortDate = SimpleDateFormat("dd/MM", Locale("pt", "BR"))
                        .format(Date(session.sessionDateMillis))
                    canvas.drawText(shortDate, px - 12f, chartRect.bottom - 8f, mutedPaint)
                }
            }

            // Legend
            fillPaint.color = tealColor
            canvas.drawCircle(plotRight - 140f, chartRect.top + 10f, 3.5f, fillPaint)
            canvas.drawText("Clareza Vocal", plotRight - 132f, chartRect.top + 13f, mutedPaint)
            fillPaint.color = coralColor
            canvas.drawCircle(plotRight - 65f, chartRect.top + 10f, 3.5f, fillPaint)
            canvas.drawText("Estabilidade", plotRight - 57f, chartRect.top + 13f, mutedPaint)

            y += 155f
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
            canvas.drawText("Nenhuma sessão registrada até o momento.", MARGIN, y, bodyPaint)
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
                if (y + neededHeight > PAGE_HEIGHT - 55f) {
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

        drawFooter(canvas, pageNumber)
        document.finishPage(page)

        FileOutputStream(file).use { out ->
            document.writeTo(out)
        }
        document.close()
        return file
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
