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
    val customReminderTemplate: String = "",
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
    val medicalHistory: String = "",
    val mainComplaints: String = "",
    val therapeuticGoals: String = "",
    val dailyExerciseReminderEnabled: Boolean = true,
    val reminderHour: Long = 18L,
    val reminderMinute: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val patientStatus: PatientStatus
        get() = PatientStatus.fromRaw(status)

    val hasClinicalSheetContent: Boolean
        get() = medicalHistory.isNotBlank() || mainComplaints.isNotBlank() || therapeuticGoals.isNotBlank()
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
    val patientReminderEnabled: Boolean = true,
    val patientReminderLeadMinutes: Long = 1440L,
    val reminderSentMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val appointmentType: AppointmentType
        get() = AppointmentType.fromRaw(sessionType)

    val appointmentStatus: AppointmentStatus
        get() = AppointmentStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class SlotBlock(
    @DocumentId val id: String = "",
    val userId: String = "",
    val dateKey: String = "",
    val hour: Long = 9L,
    val minute: Long = 0L,
    val durationMinutes: Long = 60L,
    val reason: String = "Horário ocupado",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class ReportTimeframe(val label: String, val days: Int?) {
    SETE_DIAS("7 dias", 7),
    TRINTA_DIAS("30 dias", 30),
    NOVENTA_DIAS("90 dias", 90),
    TUDO("Todo histórico", null)
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

@Keep
@IgnoreExtraProperties
data class ExerciseListItem(
    val title: String = "",
    val category: String = ExerciseCategory.AQUECIMENTO.name,
    val instructions: String = "",
    val durationMinutes: Long = 10L,
    val frequencyPerWeek: Long = 5L
) {
    val exerciseCategory: ExerciseCategory
        get() = ExerciseCategory.fromRaw(category)
}

@Keep
@IgnoreExtraProperties
data class CustomExerciseList(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val clinicalObjective: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val items: List<ExerciseListItem> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val totalWeeklyMinutes: Long
        get() = items.sumOf { it.durationMinutes * it.frequencyPerWeek }
}

data class ClinicalProtocolPreset(
    val id: String,
    val label: String,
    val defaultTitle: String,
    val objective: String,
    val items: List<ExerciseListItem>
)

object CuratedProtocolPresets {
    val presets = listOf(
        ClinicalProtocolPreset(
            id = "disfonia",
            label = "Disfonia / Fadiga Vocal",
            defaultTitle = "Protocolo de Equilíbrio e Conforto Vocal",
            objective = "Reduzir tensão laríngea, equilibrar ressonância e estabilizar emissão sem esforço.",
            items = listOf(
                ExerciseListItem(
                    title = "Vibração de Lábios e Língua (/br/ e /tr/)",
                    category = ExerciseCategory.AQUECIMENTO.name,
                    instructions = "Inspirar suavemente pelo nariz e soltar o ar vibrando lábios ou língua em 3 séries de 8 emissões confortáveis.",
                    durationMinutes = 8L,
                    frequencyPerWeek = 6L
                ),
                ExerciseListItem(
                    title = "Som Nasal Sustentado (/m/ mastigado)",
                    category = ExerciseCategory.RESSONANCIA.name,
                    instructions = "Emitir /m/ contínuo com movimentos suaves de mastigação para direcionar a vibração à máscara facial.",
                    durationMinutes = 10L,
                    frequencyPerWeek = 5L
                ),
                ExerciseListItem(
                    title = "Fricativa Sonora /z/ com Controle Costodiafragmático",
                    category = ExerciseCategory.RESPIRACAO.name,
                    instructions = "Manter postura alinhada, inspirar expandindo a base costal e emitir /z/ contínuo monitorando o conforto.",
                    durationMinutes = 10L,
                    frequencyPerWeek = 5L
                )
            )
        ),
        ClinicalProtocolPreset(
            id = "projecao",
            label = "Projeção e Ressonância",
            defaultTitle = "Lista de Projeção e Brilho Vocal",
            objective = "Ampliar a projeção na máscara facial, suavizar transições e dar expressividade à fala.",
            items = listOf(
                ExerciseListItem(
                    title = "Som Nasal Sustentado (/m/ mastigado)",
                    category = ExerciseCategory.RESSONANCIA.name,
                    instructions = "Emitir /m/ com sensação vibratória em lábios e região nasal, alternando com vogais abertas (/ma-me-mi-mo-mu/).",
                    durationMinutes = 10L,
                    frequencyPerWeek = 5L
                ),
                ExerciseListItem(
                    title = "Glissando Ascendente e Descendente em /u/",
                    category = ExerciseCategory.PROSODIA.name,
                    instructions = "Deslizar a voz do grave ao agudo e retornar suavemente na vogal /u/ sem quebras na passagem de registro.",
                    durationMinutes = 8L,
                    frequencyPerWeek = 5L
                ),
                ExerciseListItem(
                    title = "Sobrearticulação de Sequências Vocálicas",
                    category = ExerciseCategory.ARTICULACAO.name,
                    instructions = "Ler 5 frases curtas com articulação ampla e precisa sem elevar o esforço laríngeo.",
                    durationMinutes = 10L,
                    frequencyPerWeek = 4L
                )
            )
        ),
        ClinicalProtocolPreset(
            id = "respiracao_tmf",
            label = "Controle Respiratório e TMF",
            defaultTitle = "Treino de Coordenação Pneumofonoarticulatória",
            objective = "Aumentar o Tempo Máximo de Fonação (TMF) e o controle do fluxo aéreo expiratório.",
            items = listOf(
                ExerciseListItem(
                    title = "Fricativa Sonora /z/ Cronometrada",
                    category = ExerciseCategory.RESPIRACAO.name,
                    instructions = "Inspirar pelo nariz por 3 segundos e sustentar /z/ em intensidade moderada por 5 repetições, anotando o tempo.",
                    durationMinutes = 10L,
                    frequencyPerWeek = 6L
                ),
                ExerciseListItem(
                    title = "Sopros Contínuos com Fricativa /s/ e /z/",
                    category = ExerciseCategory.RESPIRACAO.name,
                    instructions = "Alternar 5 emissões de /s/ e 5 de /z/ buscando duração semelhante entre ambas (relação s/z equilibrada).",
                    durationMinutes = 10L,
                    frequencyPerWeek = 5L
                ),
                ExerciseListItem(
                    title = "Vibração Contínua em Escala Leve",
                    category = ExerciseCategory.AQUECIMENTO.name,
                    instructions = "Executar vibração labial sustentada mantendo apoio abdominal leve e estável.",
                    durationMinutes = 8L,
                    frequencyPerWeek = 5L
                )
            )
        ),
        ClinicalProtocolPreset(
            id = "articulacao",
            label = "Dicção e Articulação",
            defaultTitle = "Rotina de Clareza Articulatória e Prosódia",
            objective = "Melhorar precisão articulatória, ritmo de fala e inteligibilidade em voz conversacional.",
            items = listOf(
                ExerciseListItem(
                    title = "Aquecimento Articulatório de Lábios e Língua",
                    category = ExerciseCategory.AQUECIMENTO.name,
                    instructions = "Realizar estalos de língua, projeção/retração labial e varredura de palato em 2 séries lentas.",
                    durationMinutes = 6L,
                    frequencyPerWeek = 5L
                ),
                ExerciseListItem(
                    title = "Sobrearticulação de Sequências Vocálicas e Travas",
                    category = ExerciseCategory.ARTICULACAO.name,
                    instructions = "Repetir sequências silábicas (/pa-ta-ka/) e travas-língua em velocidade progressiva mantendo nitidez.",
                    durationMinutes = 12L,
                    frequencyPerWeek = 5L
                ),
                ExerciseListItem(
                    title = "Leitura Expressiva com Pausas e Entonação",
                    category = ExerciseCategory.PROSODIA.name,
                    instructions = "Ler um parágrafo curto marcando pausas respiratórias e variações melódicas naturais.",
                    durationMinutes = 10L,
                    frequencyPerWeek = 4L
                )
            )
        )
    )
}

@Keep
@IgnoreExtraProperties
data class PatientExerciseSnapshotItem(
    val title: String = "",
    val category: String = ExerciseCategory.AQUECIMENTO.name,
    val instructions: String = "",
    val durationMinutes: Long = 10L,
    val frequencyPerWeek: Long = 5L,
    val sourceLabel: String = ""
) {
    val exerciseCategory: ExerciseCategory
        get() = ExerciseCategory.fromRaw(category)
}

@Keep
@IgnoreExtraProperties
data class PatientAccessCode(
    @DocumentId val id: String = "",
    val code: String = "",
    val therapistId: String = "",
    val therapistName: String = "",
    val clinicName: String = "",
    val patientId: String = "",
    val patientName: String = "",
    val clinicalFocus: String = "",
    val weeklyGoalMinutes: Long = 60L,
    val active: Boolean = true,
    val redeemedByUid: String = "",
    val redeemedAtMillis: Long = 0L,
    val exercisesSnapshot: List<PatientExerciseSnapshotItem> = emptyList(),
    val nextAppointmentSummary: String = "",
    val nextAppointmentMillis: Long = 0L,
    val recentProgressSummary: String = "",
    val dailyReminderEnabled: Boolean = true,
    val reminderHour: Long = 18L,
    val reminderMinute: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

object AccessCodeGenerator {
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = java.security.SecureRandom()

    fun generate(): String {
        val suffix = buildString {
            repeat(6) {
                append(ALPHABET[random.nextInt(ALPHABET.length)])
            }
        }
        return "FV$suffix"
    }

    fun normalize(raw: String): String =
        raw.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
}


