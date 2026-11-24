package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.FonoRepository
import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import com.example.data.model.AppointmentType
import com.example.data.model.CustomExerciseList
import com.example.data.model.EvolutionSession
import com.example.data.model.ExerciseCategory
import com.example.data.model.ExerciseListItem
import com.example.data.model.Patient
import com.example.data.model.PatientAccessCode
import com.example.data.model.PatientStatus
import com.example.data.model.SlotBlock
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

import kotlinx.coroutines.delay

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
    val appointments: List<Appointment>,
    val slotBlocks: List<SlotBlock>,
    val exerciseLists: List<CustomExerciseList>,
    val accessCodes: List<PatientAccessCode> = emptyList()
)

private data class ClinicLeftBundle(
    val profile: TherapistProfile?,
    val patients: List<Patient>,
    val exercises: List<VoiceExercise>,
    val sessions: List<EvolutionSession>
)

private data class ClinicRightBundle(
    val appointments: List<Appointment>,
    val slotBlocks: List<SlotBlock>,
    val exerciseLists: List<CustomExerciseList>,
    val accessCodes: List<PatientAccessCode>
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
        combine(
            repository.observeProfile(currentUserId),
            repository.observePatients(currentUserId),
            repository.observeExercises(currentUserId),
            repository.observeSessions(currentUserId)
        ) { profile, patients, exercises, sessions ->
            ClinicLeftBundle(profile, patients, exercises, sessions)
        },
        combine(
            repository.observeAppointments(currentUserId),
            repository.observeSlotBlocks(currentUserId),
            repository.observeExerciseLists(currentUserId),
            repository.observePatientAccessCodes(currentUserId)
        ) { appointments, blocks, lists, codes ->
            ClinicRightBundle(appointments, blocks, lists, codes)
        }
    ) { left, right ->
        ClinicData(
            profile = left.profile,
            patients = left.patients,
            exercises = left.exercises,
            sessions = left.sessions,
            appointments = right.appointments,
            slotBlocks = right.slotBlocks,
            exerciseLists = right.exerciseLists,
            accessCodes = right.accessCodes
        )
    }
        .toUiState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    private fun schedulePortalMirrorSync(patientId: String) {
        if (patientId.isBlank()) return
        viewModelScope.launch {
            delay(300L)
            val data = (clinicState.value as? UiState.Success<ClinicData>)?.data ?: return@launch
            val patient = data.patients.firstOrNull { it.id == patientId } ?: return@launch
            if (data.accessCodes.none { it.patientId == patientId && it.active }) return@launch
            val therapistName = data.profile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName
            val clinicName = data.profile?.clinicName ?: ""
            repository.syncActiveAccessCodeForPatient(
                patient = patient,
                therapistName = therapistName,
                clinicName = clinicName,
                exercises = data.exercises.filter { it.patientId == patientId },
                patientLists = data.exerciseLists.filter { it.patientId == patientId },
                appointments = data.appointments.filter { it.patientId == patientId },
                sessions = data.sessions.filter { it.patientId == patientId }
            )
        }
    }

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
                if (editingPatientId != null) {
                    schedulePortalMirrorSync(pid)
                }
                onSuccess(pid)
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Não foi possível salvar o paciente.")
            }
        }
    }

    fun saveClinicalSheet(
        patientId: String,
        medicalHistory: String,
        mainComplaints: String,
        therapeuticGoals: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updatePatientClinicalSheet(
                patientId = patientId,
                medicalHistory = medicalHistory,
                mainComplaints = mainComplaints,
                therapeuticGoals = therapeuticGoals
            )
            _isBusy.value = false
            result.onSuccess {
                _events.tryEmit("Ficha clínica salva com sucesso no Firebase Firestore.")
                onSuccess()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Não foi possível salvar a ficha clínica.")
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
                schedulePortalMirrorSync(patientId)
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
                .onSuccess { schedulePortalMirrorSync(exercise.patientId) }
                .onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Erro ao atualizar exercício.")
                }
        }
    }

    fun deleteExercise(exerciseId: String, patientId: String = "") {
        viewModelScope.launch {
            repository.deleteExercise(exerciseId)
                .onSuccess {
                    if (patientId.isNotBlank()) schedulePortalMirrorSync(patientId)
                    _events.tryEmit("Exercício removido.")
                }
                .onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Erro ao remover exercício.")
                }
        }
    }

    fun saveExerciseList(
        editingListId: String?,
        title: String,
        clinicalObjective: String,
        patient: Patient?,
        items: List<ExerciseListItem>,
        applyToPatientNow: Boolean,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.saveExerciseList(
                editingListId = editingListId,
                title = title,
                clinicalObjective = clinicalObjective,
                patientId = patient?.id ?: "",
                patientName = patient?.fullName ?: "",
                items = items
            )
            if (result.isSuccess && applyToPatientNow && patient != null) {
                repository.applyExerciseListToPatient(patient.id, items)
                    .onSuccess { count ->
                        _isBusy.value = false
                        schedulePortalMirrorSync(patient.id)
                        _events.tryEmit("Lista salva e $count exercício(s) aplicado(s) a ${patient.fullName}.")
                        onSuccess()
                    }
                    .onFailure { err ->
                        _isBusy.value = false
                        _events.tryEmit(err.localizedMessage ?: "Lista salva, mas erro ao aplicar exercícios.")
                        onSuccess()
                    }
            } else {
                _isBusy.value = false
                result.onSuccess {
                    if (patient != null) schedulePortalMirrorSync(patient.id)
                    _events.tryEmit(
                        if (editingListId == null) "Lista personalizada criada com sucesso."
                        else "Lista personalizada atualizada."
                    )
                    onSuccess()
                }.onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Erro ao salvar lista de exercícios.")
                }
            }
        }
    }

    fun applyExerciseListToPatient(
        patient: Patient,
        list: CustomExerciseList,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.applyExerciseListToPatient(patient.id, list.items)
            _isBusy.value = false
            result.onSuccess { count ->
                schedulePortalMirrorSync(patient.id)
                _events.tryEmit("$count exercício(s) da lista \"${list.title}\" aplicado(s) a ${patient.fullName}.")
                onSuccess()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Erro ao aplicar lista ao paciente.")
            }
        }
    }

    fun deleteExerciseList(listId: String) {
        viewModelScope.launch {
            repository.deleteExerciseList(listId)
                .onSuccess { _events.tryEmit("Lista de exercícios removida.") }
                .onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Erro ao remover lista.")
                }
        }
    }

    fun dispatchExerciseListShare(
        context: Context,
        patient: Patient?,
        list: CustomExerciseList,
        therapistName: String
    ) {
        val itemsSummary = list.items.mapIndexed { idx, item ->
            "${idx + 1}. ${item.title} (${item.durationMinutes} min • ${item.frequencyPerWeek}x/sem)\n   ${item.instructions}"
        }.joinToString("\n")
        val msg = ReminderScheduler.buildExerciseListGuardianMessage(
            listTitle = list.title,
            clinicalObjective = list.clinicalObjective,
            itemsSummary = itemsSummary,
            patientName = patient?.fullName ?: list.patientName.ifBlank { null },
            guardianName = patient?.guardianName,
            therapistName = therapistName
        )
        ReminderScheduler.launchGuardianReminderShare(context, patient?.guardianPhone ?: "", msg)
        _events.tryEmit("Lista formatada para compartilhamento.")
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
                schedulePortalMirrorSync(patient.id)
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
        patientReminderEnabled: Boolean,
        patientReminderLeadMinutes: Long,
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
                reminderLeadMinutes = reminderLeadMinutes,
                patientReminderEnabled = patientReminderEnabled,
                patientReminderLeadMinutes = patientReminderLeadMinutes
            )
            _isBusy.value = false
            result.onSuccess { apptId ->
                val profile = (clinicState.value as? UiState.Success<ClinicData>)?.data?.profile
                val therapistName = profile?.displayName?.ifBlank { userDisplayName } ?: userDisplayName
                val clinicName = profile?.clinicName.orEmpty()
                val customTemplate = profile?.customReminderTemplate.orEmpty()
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
                        reminderLeadMinutes = reminderLeadMinutes,
                        patientReminderEnabled = patientReminderEnabled,
                        patientReminderLeadMinutes = patientReminderLeadMinutes
                    ),
                    therapistName = therapistName,
                    customReminderTemplate = customTemplate,
                    clinicName = clinicName
                )
                schedulePortalMirrorSync(patient.id)
                _events.tryEmit(
                    if (reminderEnabled || patientReminderEnabled)
                        "Consulta agendada com lembretes automáticos ativados."
                    else "Consulta agendada com sucesso."
                )
                onSuccess()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Erro ao agendar consulta.")
            }
        }
    }

    fun blockTimeSlot(
        dateKey: String,
        hour: Int,
        minute: Int,
        durationMinutes: Int = 60,
        reason: String = "Horário ocupado"
    ) {
        viewModelScope.launch {
            repository.blockSlot(dateKey, hour, minute, durationMinutes, reason)
                .onSuccess { _events.tryEmit("Horário marcado como ocupado.") }
                .onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Não foi possível bloquear o horário.")
                }
        }
    }

    fun unblockTimeSlot(blockId: String) {
        viewModelScope.launch {
            repository.unblockSlot(blockId)
                .onSuccess { _events.tryEmit("Horário marcado como disponível.") }
                .onFailure { err ->
                    _events.tryEmit(err.localizedMessage ?: "Não foi possível liberar o horário.")
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
                    schedulePortalMirrorSync(appointment.patientId)
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
        therapistName: String,
        customTemplate: String? = null,
        clinicName: String? = null
    ) {
        val profile = (clinicState.value as? UiState.Success<ClinicData>)?.data?.profile
        val effectiveTemplate = customTemplate ?: profile?.customReminderTemplate.orEmpty()
        val effectiveClinic = clinicName ?: profile?.clinicName.orEmpty()
        val message = ReminderScheduler.buildAppointmentGuardianMessage(
            appointment = appointment,
            therapistName = therapistName,
            customTemplate = effectiveTemplate,
            clinicName = effectiveClinic
        )
        ReminderScheduler.launchGuardianReminderShare(context, appointment.guardianPhone, message)
        _events.tryEmit("Abrindo WhatsApp (wa.me) para enviar lembrete de confirmação.")
        if (!appointment.appointmentStatus.isTerminal) {
            viewModelScope.launch {
                repository.markAppointmentReminderSent(appointment.id)
            }
        }
    }

    fun dispatchGuardianConfirmationNotice(
        context: Context,
        appointment: Appointment,
        therapistName: String
    ) {
        val message = ReminderScheduler.buildAppointmentConfirmedMessage(appointment, therapistName)
        ReminderScheduler.launchGuardianReminderShare(context, appointment.guardianPhone, message)
        _events.tryEmit("Abrindo WhatsApp (wa.me) para confirmar consulta com o responsável.")
        viewModelScope.launch {
            repository.markAppointmentReminderSent(appointment.id)
        }
    }

    fun markAppointmentReminderSent(appointmentId: String) {
        if (appointmentId.isBlank()) return
        viewModelScope.launch {
            repository.markAppointmentReminderSent(appointmentId)
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
            val patientId = (clinicState.value as? UiState.Success<ClinicData>)
                ?.data
                ?.appointments
                ?.firstOrNull { it.id == appointmentId }
                ?.patientId
                .orEmpty()
            repository.deleteAppointment(appointmentId)
                .onSuccess {
                    ReminderScheduler.cancelAppointmentReminder(context, appointmentId)
                    if (patientId.isNotBlank()) {
                        schedulePortalMirrorSync(patientId)
                    }
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
        customReminderTemplate: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.updateProfile(
                displayName = displayName,
                crfa = crfa,
                clinicName = clinicName,
                defaultReminderMinutes = defaultReminderMinutes,
                autoRemindersEnabled = autoRemindersEnabled,
                customReminderTemplate = customReminderTemplate
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

    fun generatePatientAccessCode(
        patient: Patient,
        data: ClinicData,
        fallbackName: String,
        onSuccess: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            val therapistName = data.profile?.displayName?.ifBlank { fallbackName } ?: fallbackName
            val clinicName = data.profile?.clinicName ?: ""
            val result = repository.generatePatientAccessCode(
                patient = patient,
                therapistName = therapistName,
                clinicName = clinicName,
                exercises = data.exercises.filter { it.patientId == patient.id },
                patientLists = data.exerciseLists.filter { it.patientId == patient.id },
                appointments = data.appointments.filter { it.patientId == patient.id },
                sessions = data.sessions.filter { it.patientId == patient.id }
            )
            _isBusy.value = false
            result.onSuccess { newCode ->
                _events.tryEmit("Código $newCode gerado para ${patient.fullName}.")
                onSuccess(newCode)
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Não foi possível gerar o código de acesso.")
            }
        }
    }

    fun revokePatientAccessCode(code: String) {
        viewModelScope.launch {
            _isBusy.value = true
            val result = repository.revokePatientAccessCode(code)
            _isBusy.value = false
            result.onSuccess {
                _events.tryEmit("Código $code desativado. O paciente precisará de um novo código.")
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Não foi possível desativar o código.")
            }
        }
    }

    fun dispatchAccessCodeShare(
        context: Context,
        patient: Patient,
        code: String,
        therapistName: String,
        clinicName: String
    ) {
        val message = ReminderScheduler.buildPatientAccessCodeMessage(
            patient = patient,
            code = code,
            therapistName = therapistName,
            clinicName = clinicName
        )
        ReminderScheduler.launchGuardianReminderShare(context, patient.guardianPhone, message)
        _events.tryEmit("Instruções com o código $code prontas para envio.")
    }

    fun notifyMessage(msg: String) {
        _events.tryEmit(msg)
    }

    private companion object {
        const val TAG = "FonoVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
