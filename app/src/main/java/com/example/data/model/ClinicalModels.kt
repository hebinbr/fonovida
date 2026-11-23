package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class PatientStatus(val label: String) {
    ATIVO("Em Acompanhamento"),
    PAUSA("Em Pausa"),
    ALTA("Alta Clínica");

    companion object {
        fun fromRaw(raw: String?): PatientStatus =
            entries.firstOrNull { it.name == raw } ?: ATIVO
    }
}

enum class ExerciseCategory(val label: String) {
    AQUECIMENTO("Aquecimento Vocal"),
    RESPIRACAO("Controle Respiratório"),
    RESSONANCIA("Ressonância e Projeção"),
    ARTICULACAO("Articulação e Dicção"),
    PROSODIA("Prosódia e Expressão");

    companion object {
        fun fromRaw(raw: String?): ExerciseCategory =
            entries.firstOrNull { it.name == raw } ?: AQUECIMENTO
    }
}

enum class AppointmentType(val label: String) {
    PRESENCIAL("Presencial"),
    TELEFONOAUDIOLOGIA("Telefonoaudiologia"),
    AVALIACAO("Avaliação Inicial / Reavaliação");

    companion object {
        fun fromRaw(raw: String?): AppointmentType =
            entries.firstOrNull { it.name == raw } ?: PRESENCIAL
    }
}

enum class AppointmentStatus(val label: String) {
    AGENDADA("Agendada"),
    CONFIRMADA("Confirmada"),
    CONCLUIDA("Concluída"),
    CANCELADA("Cancelada");

    val isTerminal: Boolean
        get() = this == CONCLUIDA || this == CANCELADA

    companion object {
        fun fromRaw(raw: String?): AppointmentStatus =
            entries.firstOrNull { it.name == raw } ?: AGENDADA
    }
}

@Keep
@IgnoreExtraProperties
data class TherapistProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val crfa: String = "",
    val clinicName: String = "",
    val defaultReminderMinutes: Long = 60L,
    val autoRemindersEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Patient(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fullName: String = "",
    val ageYears: Long = 8L,
    val clinicalFocus: String = "",
    val guardianName: String = "",
    val guardianPhone: String = "",
    val guardianEmail: String = "",
    val status: String = PatientStatus.ATIVO.name,
    val weeklyGoalMinutes: Long = 60L,
    val initialNotes: String = "",
    val dailyExerciseReminderEnabled: Boolean = true,
    val reminderHour: Long = 18L,
    val reminderMinute: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val patientStatus: PatientStatus
        get() = PatientStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class VoiceExercise(
    @DocumentId val id: String = "",
    val userId: String = "",
    val patientId: String = "",
    val title: String = "",
    val category: String = ExerciseCategory.AQUECIMENTO.name,
    val instructions: String = "",
    val targetDurationMinutes: Long = 10L,
    val frequencyPerWeek: Long = 5L,
    val activeFlag: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val exerciseCategory: ExerciseCategory
        get() = ExerciseCategory.fromRaw(category)
}

@Keep
@IgnoreExtraProperties
data class EvolutionSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val exerciseTitle: String = "",
    val category: String = ExerciseCategory.AQUECIMENTO.name,
    val durationMinutes: Long = 15L,
    val vocalClarityScore: Long = 75L,
    val stabilityScore: Long = 75L,
    val comfortScore: Long = 80L,
    val tmfSeconds: Double = 10.0,
    val clinicalNotes: String = "",
    val homeGuidance: String = "",
    val sessionDateMillis: Long = System.currentTimeMillis(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val exerciseCategory: ExerciseCategory
        get() = ExerciseCategory.fromRaw(category)

    val compositeScore: Int
        get() = ((vocalClarityScore + stabilityScore + comfortScore) / 3.0).toInt()
}

@Keep
@IgnoreExtraProperties
data class Appointment(
    @DocumentId val id: String = "",
    val userId: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val guardianName: String = "",
    val guardianPhone: String = "",
    val scheduledAtMillis: Long = System.currentTimeMillis(),
    val durationMinutes: Long = 45L,
    val sessionType: String = AppointmentType.PRESENCIAL.name,
    val status: String = AppointmentStatus.AGENDADA.name,
    val notes: String = "",
    val reminderEnabled: Boolean = true,
    val reminderLeadMinutes: Long = 60L,
    val reminderSentMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val appointmentType: AppointmentType
        get() = AppointmentType.fromRaw(sessionType)

    val appointmentStatus: AppointmentStatus
        get() = AppointmentStatus.fromRaw(status)
}

data class VoiceExerciseTemplate(
    val title: String,
    val category: ExerciseCategory,
    val instructions: String,
    val targetDurationMinutes: Int,
    val frequencyPerWeek: Int
)

object CuratedVoiceExerciseTemplates {
    val templates = listOf(
        VoiceExerciseTemplate(
            title = "Vibração de Lábios e Língua (/br/ e /tr/)",
            category = ExerciseCategory.AQUECIMENTO,
            instructions = "Inspirar suavemente pelo nariz e soltar o ar vibrando lábios ou língua de forma contínua, sem esforço na garganta. Realizar 3 séries de 8 emissões.",
            targetDurationMinutes = 10,
            frequencyPerWeek = 5
        ),
        VoiceExerciseTemplate(
            title = "Som Nasal Sustentado (/m/ mastigado)",
            category = ExerciseCategory.RESSONANCIA,
            instructions = "Emitir o fonema /m/ contínuo associando movimentos amplos e suaves de mastigação para direcionar a ressonância para a máscara facial.",
            targetDurationMinutes = 12,
            frequencyPerWeek = 5
        ),
        VoiceExerciseTemplate(
            title = "Fricativa Sonora /z/ com Controle Costodiafragmático",
            category = ExerciseCategory.RESPIRACAO,
            instructions = "Manter postura alinhada, inspirar expandindo a base costal e emitir /z/ estável cronometrando o Tempo Máximo de Fonação (TMF).",
            targetDurationMinutes = 10,
            frequencyPerWeek = 6
        ),
        VoiceExerciseTemplate(
            title = "Sobrearticulação de Sequências Vocálicas e Travas",
            category = ExerciseCategory.ARTICULACAO,
            instructions = "Ler frases curtas em ritmo pausado exagerando a precisão dos movimentos de lábios, língua e mandíbula sem aumentar a intensidade.",
            targetDurationMinutes = 15,
            frequencyPerWeek = 4
        ),
        VoiceExerciseTemplate(
            title = "Glissando Ascendente e Descendente em /u/",
            category = ExerciseCategory.PROSODIA,
            instructions = "Deslizar a voz do grave ao agudo e retornar suavemente na vogal /u/ com fluxo aéreo constante, evitando quebras na passagem de registro.",
            targetDurationMinutes = 8,
            frequencyPerWeek = 5
        )
    )
}
