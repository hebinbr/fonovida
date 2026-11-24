package com.example

import com.example.data.AppUserRole
import com.example.data.model.AccessCodeGenerator
import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import com.example.data.model.AppointmentType
import com.example.data.model.CuratedProtocolPresets
import com.example.data.model.CuratedVoiceExerciseTemplates
import com.example.data.model.CustomExerciseList
import com.example.data.model.EvolutionSession
import com.example.data.model.ExerciseCategory
import com.example.data.model.ExerciseListItem
import com.example.data.model.Patient
import com.example.data.model.PatientAccessCode
import com.example.data.model.PatientStatus
import com.example.data.model.ReportTimeframe
import com.example.data.model.SlotBlock
import com.example.data.model.TherapistProfile
import com.example.notifications.AppointmentReminderTemplateDefaults
import com.example.notifications.ReminderScheduler
import com.example.ui.components.VoiceChartMetric
import com.example.ui.components.buildDashboardVoiceProgressSeries
import com.example.ui.components.computeProgressDeltaPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun enumDeserializationFallsBackSafely() {
        assertEquals(PatientStatus.ATIVO, PatientStatus.fromRaw("UNKNOWN_STATUS"))
        assertEquals(ExerciseCategory.AQUECIMENTO, ExerciseCategory.fromRaw("INVALID"))
        assertEquals(AppointmentType.PRESENCIAL, AppointmentType.fromRaw(null))
        assertEquals(AppointmentStatus.AGENDADA, AppointmentStatus.fromRaw("OTHER"))
    }

    @Test
    fun appointmentTerminalStateLockingWorks() {
        assertTrue(AppointmentStatus.CONCLUIDA.isTerminal)
        assertTrue(AppointmentStatus.CANCELADA.isTerminal)
        assertFalse(AppointmentStatus.AGENDADA.isTerminal)
        assertFalse(AppointmentStatus.CONFIRMADA.isTerminal)
    }

    @Test
    fun evolutionSessionCompositeScoreCalculatesAverage() {
        val session = EvolutionSession(
            vocalClarityScore = 80L,
            stabilityScore = 70L,
            comfortScore = 90L
        )
        assertEquals(80, session.compositeScore)
    }

    @Test
    fun guardianReminderMessageFormatsDetails() {
        val appt = Appointment(
            id = "appt_1",
            patientId = "pat_1",
            patientName = "Sofia Mendes",
            guardianName = "Carlos Mendes",
            guardianPhone = "11988887777",
            scheduledAtMillis = System.currentTimeMillis() + 86_400_000L,
            durationMinutes = 45L,
            sessionType = AppointmentType.PRESENCIAL.name,
            status = AppointmentStatus.AGENDADA.name
        )
        val msg = ReminderScheduler.buildAppointmentGuardianMessage(appt, "Dra. Ana Paula")
        assertTrue(msg.contains("Sofia Mendes"))
        assertTrue(msg.contains("Carlos Mendes"))
        assertTrue(msg.contains("Presencial"))
        assertTrue(CuratedVoiceExerciseTemplates.templates.size >= 5)
    }

    @Test
    fun reportTimeframesAndSlotBlockDefaultsAreValid() {
        assertEquals(7, ReportTimeframe.SETE_DIAS.days)
        assertEquals(30, ReportTimeframe.TRINTA_DIAS.days)
        assertEquals(90, ReportTimeframe.NOVENTA_DIAS.days)
        assertNull(ReportTimeframe.TUDO.days)

        val block = SlotBlock(dateKey = "2026-11-24", hour = 12L, minute = 0L, durationMinutes = 60L, reason = "Almoço")
        assertEquals("2026-11-24", block.dateKey)
        assertEquals(12L, block.hour)
        assertEquals("Almoço", block.reason)

        val appt = Appointment()
        assertTrue(appt.reminderEnabled)
        assertTrue(appt.patientReminderEnabled)
        assertEquals(60L, appt.reminderLeadMinutes)
        assertEquals(1440L, appt.patientReminderLeadMinutes)
    }

    @Test
    fun customExerciseListWeeklyMinutesAndPresetsWork() {
        assertTrue(CuratedProtocolPresets.presets.size >= 4)
        val customList = CustomExerciseList(
            title = "Rotina Matinal",
            clinicalObjective = "Reduzir esforço laríngeo",
            items = listOf(
                ExerciseListItem(title = "Vibração", durationMinutes = 10L, frequencyPerWeek = 5L),
                ExerciseListItem(title = "Som Nasal", durationMinutes = 8L, frequencyPerWeek = 4L)
            )
        )
        assertEquals(82L, customList.totalWeeklyMinutes)

        val shareMsg = ReminderScheduler.buildExerciseListGuardianMessage(
            listTitle = customList.title,
            clinicalObjective = customList.clinicalObjective,
            itemsSummary = "1. Vibração (10 min • 5x/sem)",
            patientName = "Sofia Mendes",
            guardianName = "Carlos Mendes",
            therapistName = "Dra. Ana Paula"
        )
        assertTrue(shareMsg.contains("Rotina Matinal"))
        assertTrue(shareMsg.contains("Sofia Mendes"))
        assertTrue(shareMsg.contains("Carlos Mendes"))
        assertTrue(shareMsg.contains("1. Vibração"))
    }

    @Test
    fun patientAccessCodeGeneratorAndShareMessageWork() {
        val generated = AccessCodeGenerator.generate()
        assertEquals(8, generated.length)
        assertTrue(generated.startsWith("FV"))
        assertTrue(generated.all { it in 'A'..'Z' || it in '2'..'9' })

        assertEquals("FV8K4M2P", AccessCodeGenerator.normalize(" fv-8k4m 2p "))
        assertEquals(AppUserRole.PATIENT, AppUserRole.fromRaw("PATIENT"))
        assertEquals(AppUserRole.NONE, AppUserRole.fromRaw("UNKNOWN_ROLE"))

        val accessObj = PatientAccessCode()
        assertTrue(accessObj.active)
        assertEquals("", accessObj.redeemedByUid)
        assertEquals(0L, accessObj.nextAppointmentMillis)
        assertTrue(accessObj.dailyReminderEnabled)
        assertEquals(18L, accessObj.reminderHour)
        assertEquals(0L, accessObj.reminderMinute)

        val pat = Patient(fullName = "Sofia Mendes", guardianName = "Carlos Mendes")
        val codeMsg = ReminderScheduler.buildPatientAccessCodeMessage(
            patient = pat,
            code = generated,
            therapistName = "Dra. Ana Paula",
            clinicName = "Clínica FonoVida"
        )
        assertTrue(codeMsg.contains(generated))
        assertTrue(codeMsg.contains("Sofia Mendes"))
        assertTrue(codeMsg.contains("Sou Paciente"))
    }

    @Test
    fun workManagerDailyTriggerDelayCalculatesSameDayAndNextDayCorrectly() {
        val baseCal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 24, 14, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val nowMillis = baseCal.timeInMillis

        // Same day at 18:00 -> 4 hours (14_400_000 ms)
        val sameDayDelay = ReminderScheduler.computeNextDailyTriggerDelayMillis(
            hour = 18,
            minute = 0,
            nowMillis = nowMillis
        )
        assertEquals(4 * 3_600_000L, sameDayDelay)

        // Past time at 09:00 -> tomorrow at 09:00 -> 19 hours (68_400_000 ms)
        val nextDayDelay = ReminderScheduler.computeNextDailyTriggerDelayMillis(
            hour = 9,
            minute = 0,
            nowMillis = nowMillis
        )
        assertEquals(19 * 3_600_000L, nextDayDelay)

        // Exact current minute at 14:00 -> rolls to next day (24 hours)
        val exactSameTimeDelay = ReminderScheduler.computeNextDailyTriggerDelayMillis(
            hour = 14,
            minute = 0,
            nowMillis = nowMillis
        )
        assertEquals(24 * 3_600_000L, exactSameTimeDelay)
    }

    @Test
    fun whatsAppUrlFormattingForGuardianConfirmationWorks() {
        assertEquals("5511988887777", ReminderScheduler.normalizePhoneForWhatsApp("(11) 98888-7777"))
        assertEquals("5511988887777", ReminderScheduler.normalizePhoneForWhatsApp("011 98888-7777"))
        assertEquals("5511988887777", ReminderScheduler.normalizePhoneForWhatsApp("+55 (11) 98888-7777"))
        assertEquals("", ReminderScheduler.normalizePhoneForWhatsApp("123"))

        val appt = Appointment(
            id = "appt_wa",
            patientId = "pat_1",
            patientName = "Lucas Lima",
            guardianName = "Mariana Lima",
            guardianPhone = "(21) 99999-1234",
            scheduledAtMillis = System.currentTimeMillis() + 86_400_000L,
            durationMinutes = 45L,
            sessionType = AppointmentType.PRESENCIAL.name,
            status = AppointmentStatus.CONFIRMADA.name
        )
        val confirmedMsg = ReminderScheduler.buildAppointmentConfirmedMessage(appt, "Dra. Ana Paula")
        assertTrue(confirmedMsg.contains("Mariana Lima"))
        assertTrue(confirmedMsg.contains("Lucas Lima"))
        assertTrue(confirmedMsg.contains("Confirmamos a consulta"))

        val waUrlWithPhone = ReminderScheduler.buildWhatsAppUrlString(appt.guardianPhone, confirmedMsg)
        assertTrue(waUrlWithPhone.startsWith("https://wa.me/5521999991234?text="))

        val waUrlNoPhone = ReminderScheduler.buildWhatsAppUrlString("", confirmedMsg)
        assertTrue(waUrlNoPhone.startsWith("https://wa.me/?text="))
    }

    @Test
    fun customAppointmentReminderTemplateRendersVariablesCorrectly() {
        val profile = TherapistProfile()
        assertEquals("", profile.customReminderTemplate)
        assertTrue(AppointmentReminderTemplateDefaults.availableVariables.any { it.first == "{paciente}" })
        assertTrue(AppointmentReminderTemplateDefaults.availableVariables.any { it.first == "{data_hora}" })

        val cal = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.NOVEMBER, 25, 14, 30, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val appt = Appointment(
            id = "appt_custom",
            patientId = "pat_custom",
            patientName = "Sofia Mendes",
            guardianName = "Carlos Mendes",
            guardianPhone = "11988887777",
            scheduledAtMillis = cal.timeInMillis,
            durationMinutes = 45L,
            sessionType = AppointmentType.TELEFONOAUDIOLOGIA.name,
            status = AppointmentStatus.AGENDADA.name,
            notes = ""
        )

        val template = "Oi, {responsavel}! Lembrete de {clinica}: sessão de {paciente} ({tipo}) em {data_hora} com {terapeuta}.\n{observacoes}"
        val rendered = ReminderScheduler.buildAppointmentGuardianMessage(
            appointment = appt,
            therapistName = "Dra. Ana Paula",
            customTemplate = template,
            clinicName = "Clínica Voz Viva"
        )

        assertTrue(rendered.contains("Carlos Mendes"))
        assertTrue(rendered.contains("Sofia Mendes"))
        assertTrue(rendered.contains("25/11/2026 às 14:30"))
        assertTrue(rendered.contains("Telefonoaudiologia"))
        assertTrue(rendered.contains("Dra. Ana Paula"))
        assertTrue(rendered.contains("Clínica Voz Viva"))
        assertFalse(rendered.contains("{paciente}"))
        assertFalse(rendered.contains("{data_hora}"))
        assertFalse(rendered.contains("{observacoes}"))

        val preview = ReminderScheduler.renderAppointmentTemplatePreview(
            template = AppointmentReminderTemplateDefaults.DEFAULT_TEMPLATE,
            therapistName = "Dra. Ana Paula",
            clinicName = "Clínica Voz Viva"
        )
        assertTrue(preview.contains("Sofia Mendes"))
        assertTrue(preview.contains("25/11/2026 às 14:30"))
    }

    @Test
    fun dashboardLineChartSeriesFiltersAndComputesDeltaCorrectly() {
        val now = 1_764_000_000_000L
        val day = 86_400_000L
        val sessions = listOf(
            EvolutionSession(
                id = "s_old",
                patientId = "pat_1",
                patientName = "Sofia Mendes",
                exerciseTitle = "Vibração Labial",
                vocalClarityScore = 50L,
                stabilityScore = 50L,
                comfortScore = 50L,
                tmfSeconds = 8.0,
                sessionDateMillis = now - (45 * day)
            ),
            EvolutionSession(
                id = "s_1",
                patientId = "pat_1",
                patientName = "Sofia Mendes",
                exerciseTitle = "Som Nasal",
                vocalClarityScore = 60L,
                stabilityScore = 66L,
                comfortScore = 72L,
                tmfSeconds = 11.2,
                sessionDateMillis = now - (10 * day)
            ),
            EvolutionSession(
                id = "s_2",
                patientId = "pat_2",
                patientName = "Lucas Lima",
                exerciseTitle = "Ressonância",
                vocalClarityScore = 84L,
                stabilityScore = 80L,
                comfortScore = 88L,
                tmfSeconds = 14.5,
                sessionDateMillis = now - (2 * day)
            ),
            EvolutionSession(
                id = "s_3",
                patientId = "pat_1",
                patientName = "Sofia Mendes",
                exerciseTitle = "Projeção Vocal",
                vocalClarityScore = 90L,
                stabilityScore = 84L,
                comfortScore = 90L,
                tmfSeconds = 15.0,
                sessionDateMillis = now - day
            )
        )

        val thirtyDaysAll = buildDashboardVoiceProgressSeries(
            sessions = sessions,
            selectedPatientId = null,
            timeframe = ReportTimeframe.TRINTA_DIAS,
            nowMillis = now
        )
        assertEquals(3, thirtyDaysAll.size)
        assertEquals("s_1", thirtyDaysAll.first().sessionId)
        assertEquals("s_3", thirtyDaysAll.last().sessionId)

        val sofiaOnly = buildDashboardVoiceProgressSeries(
            sessions = sessions,
            selectedPatientId = "pat_1",
            timeframe = ReportTimeframe.TRINTA_DIAS,
            nowMillis = now
        )
        assertEquals(2, sofiaOnly.size)
        assertEquals(66, sofiaOnly.first().compositeScore)
        assertEquals(88, sofiaOnly.last().compositeScore)
        assertEquals(22, computeProgressDeltaPercent(sofiaOnly, VoiceChartMetric.GERAL))
        assertEquals(30, computeProgressDeltaPercent(sofiaOnly, VoiceChartMetric.CLAREZA))
    }

    @Test
    fun patientClinicalSheetDefaultsAndCompletionFlagWork() {
        val emptyPatient = Patient(fullName = "Ana Clara")
        assertEquals("", emptyPatient.medicalHistory)
        assertEquals("", emptyPatient.mainComplaints)
        assertEquals("", emptyPatient.therapeuticGoals)
        assertFalse(emptyPatient.hasClinicalSheetContent)

        val filledPatient = emptyPatient.copy(
            medicalHistory = "Videolaringoscopia em 10/2026 sem lesões estruturais.",
            mainComplaints = "Fadiga vocal ao final do dia.",
            therapeuticGoals = "Aumentar TMF e coordenação pneumofonoarticulatória."
        )
        assertTrue(filledPatient.hasClinicalSheetContent)
    }
}
