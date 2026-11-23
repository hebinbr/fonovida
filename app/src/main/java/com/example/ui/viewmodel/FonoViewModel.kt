package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FonoRepository
import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import com.example.data.model.AppointmentType
import com.example.data.model.EvolutionSession
import com.example.data.model.ExerciseCategory
import com.example.data.model.Patient
import com.example.data.model.PatientStatus
import com.example.data.model.TherapistProfile
import com.example.data.model.VoiceExercise
import com.example.notifications.ReminderScheduler
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class ClinicData(
    val profile: TherapistProfile?,
    val patients: List<Patient>,
    val exercises: List<VoiceExercise>,
    val sessions: List<EvolutionSession>,
    val appointments: List<Appointment>
)

class FonoViewModel(
    private val repository: FonoRepository,
    private val currentUserId: String,
    private val userDisplayName: String,
    private val userEmail: String
) : ViewModel() {

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureProfileExists(userDisplayName, userEmail)
                .onFailure { err ->
                    Log.w(TAG, "Profile initialization notice: ${err.message}")
                }
        }
    }

    private fun <T> Flow<T>.toUiState(): Flow<UiState<T>> =
        this.map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Stream error", rootError)
                emit(UiState.Error(rootError.localizedMessage ?: "Erro ao sincronizar dados clínicos."))
            }

    val clinicState: StateFlow<UiState<ClinicData>> = combine(
        repository.observeProfile(currentUserId),
        repository.observePatients(currentUserId),
        repository.observeExercises(currentUserId),
        repository.observeSessions(currentUserId),
        repository.observeAppointments(currentUserId)
    ) { profile, patients, exercises, sessions, appointments ->
        ClinicData(
            profile = profile,
            patients = patients,
            exercises = exercises,
            sessions = sessions,
            appointments = appointments
        )
    }
        .toUiState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun savePatient(
        context: Context,
        editingPatientId: String?,
        fullName: String,
        ageYears: Int,
        clinicalFocus: String,
        guardianName: String,
        guardianPhone: String,
        guardianEmail: String,
        status: PatientStatus,
        weeklyGoalMinutes: Int,
        initialNotes: String,
        dailyExerciseReminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int,
        onSuccess: (String) -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = if (editingPatientId == null) {
                repository.createPatient(
                    fullName = fullName,
                    ageYears = ageYears,
                    clinicalFocus = clinicalFocus,
                    guardianName = guardianName,
                    guardianPhone = guardianPhone,
                    guardianEmail = guardianEmail,
                    weeklyGoalMinutes = weeklyGoalMinutes,
                    initialNotes = initialNotes,
                    dailyExerciseReminderEnabled = dailyExerciseReminderEnabled,
                    reminderHour = reminderHour,
                    reminderMinute = reminderMinute
                )
            } else {
                repository.updatePatient(
                    patientId = editingPatientId,
                    fullName = fullName,
                    ageYears = ageYears,
                    clinicalFocus = clinicalFocus,
                    guardianName = guardianName,
                    guardianPhone = guardianPhone,
                    guardianEmail = guardianEmail,
                    status = status,
                    weeklyGoalMinutes = weeklyGoalMinutes,
                    initialNotes = initialNotes,
                    dailyExerciseReminderEnabled = dailyExerciseReminderEnabled,
                    reminderHour = reminderHour,
                    reminderMinute = reminderMinute
                ).map { editingPatientId }
            }
            _isBusy.value = false
            result.onSuccess { pid ->
                ReminderScheduler.scheduleDailyPatientExerciseReminder(
                    context = context,
                    patient = Patient(
                        id = pid,
                        fullName = fullName.trim(),
                        guardianName = guardianName.trim(),
                        dailyExerciseReminderEnabled = dailyExerciseReminderEnabled,
                        reminderHour = reminderHour.toLong(),
                        reminderMinute = reminderMinute.toLong()
                    )
                )
                _events.tryEmit(
                    if (editingPatientId == null) "Paciente cadastrado com sucesso."
                    else "Cadastro do paciente atualizado."
                )
                onSuccess(pid)
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Não foi possível salvar o paciente.")
            }
        }
    }

    fun deletePatient(patientId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.deletePatient(patientId)
            _isBusy.value = false
            result.onSuccess {
                _events.tryEmit("Paciente e registros vinculados removidos.")
                onDeleted()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Erro ao excluir paciente.")
            }
        }
    }

    fun prescribeExercise(
        patientId: String,
        title: String,
        category: ExerciseCategory,
        instructions: String,
        targetDurationMinutes: Int,
        frequencyPerWeek: Int,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createExercise(
                patientId = patientId,
                title = title,
                category = category,
                instructions = instructions,
                targetDurationMinutes = targetDurationMinutes,
                frequencyPerWeek = frequencyPerWeek
            )
            _isBusy.value = false
            result.onSuccess {
                _events.tryEmit("Exercício de voz prescrito.")
                onSuccess()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Erro ao prescrever exercício.")
            }
        }
    }

    fun toggleExerciseActive(exercise: VoiceExercise) {
        viewModelScope.launch {
            repository.toggleExerciseActive(exercise.id, !exercise.activeFlag)
                .onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Erro ao atualizar exercício.")
                }
        }
    }

    fun deleteExercise(exerciseId: String) {
        viewModelScope.launch {
            repository.deleteExercise(exerciseId)
                .onSuccess { _events.tryEmit("Exercício removido.") }
                .onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Erro ao remover exercício.")
                }
        }
    }

    fun logSession(
        patient: Patient,
        exerciseTitle: String,
        category: ExerciseCategory,
        durationMinutes: Int,
        vocalClarityScore: Int,
        stabilityScore: Int,
        comfortScore: Int,
        tmfSeconds: Double,
        clinicalNotes: String,
        homeGuidance: String,
        sessionDateMillis: Long = System.currentTimeMillis(),
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.logEvolutionSession(
                patientId = patient.id,
                patientName = patient.fullName,
                exerciseTitle = exerciseTitle,
                category = category,
                durationMinutes = durationMinutes,
                vocalClarityScore = vocalClarityScore,
                stabilityScore = stabilityScore,
                comfortScore = comfortScore,
                tmfSeconds = tmfSeconds,
                clinicalNotes = clinicalNotes,
                homeGuidance = homeGuidance,
                sessionDateMillis = sessionDateMillis
            )
            _isBusy.value = false
            result.onSuccess {
                _events.tryEmit("Sessão registrada no histórico de evolução.")
                onSuccess()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Erro ao registrar sessão.")
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
                .onSuccess { _events.tryEmit("Registro de sessão removido.") }
                .onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Erro ao remover sessão.")
                }
        }
    }

    fun scheduleAppointment(
        context: Context,
        patient: Patient,
        scheduledAtMillis: Long,
        durationMinutes: Int,
        sessionType: AppointmentType,
        notes: String,
        reminderEnabled: Boolean,
        reminderLeadMinutes: Long,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.createAppointment(
                patient = patient,
                scheduledAtMillis = scheduledAtMillis,
                durationMinutes = durationMinutes,
                sessionType = sessionType,
                notes = notes,
                reminderEnabled = reminderEnabled,
                reminderLeadMinutes = reminderLeadMinutes
            )
            _isBusy.value = false
            result.onSuccess { apptId ->
                ReminderScheduler.scheduleAppointmentReminder(
                    context = context,
                    appointment = Appointment(
                        id = apptId,
                        patientId = patient.id,
                        patientName = patient.fullName,
                        guardianName = patient.guardianName,
                        guardianPhone = patient.guardianPhone,
                        scheduledAtMillis = scheduledAtMillis,
                        durationMinutes = durationMinutes.toLong(),
                        sessionType = sessionType.name,
                        status = AppointmentStatus.AGENDADA.name,
                        notes = notes,
                        reminderEnabled = reminderEnabled,
                        reminderLeadMinutes = reminderLeadMinutes
                    )
                )
                _events.tryEmit(
                    if (reminderEnabled) "Consulta agendada com lembrete automático ativado."
                    else "Consulta agendada com sucesso."
                )
                onSuccess()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Erro ao agendar consulta.")
            }
        }
    }

    fun updateAppointmentStatus(
        context: Context,
        appointment: Appointment,
        newStatus: AppointmentStatus
    ) {
        viewModelScope.launch {
            repository.updateAppointmentStatus(appointment.id, newStatus)
                .onSuccess {
                    if (newStatus.isTerminal) {
                        ReminderScheduler.cancelAppointmentReminder(context, appointment.id)
                    }
                    _events.tryEmit("Consulta marcada como ${newStatus.label.lowercase()}.")
                }
                .onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Não foi possível atualizar a consulta.")
                }
        }
    }

    fun dispatchGuardianAppointmentReminder(
        context: Context,
        appointment: Appointment,
        therapistName: String
    ) {
        val message = ReminderScheduler.buildAppointmentGuardianMessage(appointment, therapistName)
        ReminderScheduler.launchGuardianReminderShare(context, appointment.guardianPhone, message)
        if (!appointment.appointmentStatus.isTerminal) {
            viewModelScope.launch {
                repository.markAppointmentReminderSent(appointment.id)
            }
        }
    }

    fun dispatchGuardianExerciseReminder(
        context: Context,
        patient: Patient,
        activeExercises: List<VoiceExercise>,
        therapistName: String
    ) {
        val summary = activeExercises.take(3).joinToString(" • ") { it.title }
        val message = ReminderScheduler.buildExerciseGuardianMessage(patient, summary, therapistName)
        ReminderScheduler.launchGuardianReminderShare(context, patient.guardianPhone, message)
        _events.tryEmit("Mensagem de lembrete preparada para o responsável.")
    }

    fun deleteAppointment(context: Context, appointmentId: String) {
        viewModelScope.launch {
            repository.deleteAppointment(appointmentId)
                .onSuccess {
                    ReminderScheduler.cancelAppointmentReminder(context, appointmentId)
                    _events.tryEmit("Agendamento removido.")
                }
                .onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Erro ao remover agendamento.")
                }
        }
    }

    fun updateTherapistProfile(
        displayName: String,
        crfa: String,
        clinicName: String,
        defaultReminderMinutes: Long,
        autoRemindersEnabled: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateProfile(
                displayName = displayName,
                crfa = crfa,
                clinicName = clinicName,
                defaultReminderMinutes = defaultReminderMinutes,
                autoRemindersEnabled = autoRemindersEnabled
            )
            _isBusy.value = false
            result.onSuccess {
                _events.tryEmit("Perfil profissional atualizado.")
                onSuccess()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Erro ao atualizar perfil.")
            }
        }
    }

    fun notifyMessage(msg: String) {
        _events.tryEmit(msg)
    }

    private companion object {
        const val TAG = "FonoVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
