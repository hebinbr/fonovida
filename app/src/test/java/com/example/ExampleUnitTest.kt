package com.example

import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import com.example.data.model.AppointmentType
import com.example.data.model.CuratedVoiceExerciseTemplates
import com.example.data.model.EvolutionSession
import com.example.data.model.ExerciseCategory
import com.example.data.model.PatientStatus
import com.example.notifications.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
