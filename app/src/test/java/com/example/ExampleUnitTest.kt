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
import com.example.notifications.ReminderScheduler
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
}
