package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AccessCodeGenerator
import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import com.example.data.model.AppointmentType
import com.example.data.model.CustomExerciseList
import com.example.data.model.EvolutionSession
import com.example.data.model.ExerciseCategory
import com.example.data.model.ExerciseListItem
import com.example.data.model.Patient
import com.example.data.model.PatientAccessCode
import com.example.data.model.PatientExerciseSnapshotItem
import com.example.data.model.PatientStatus
import com.example.data.model.SlotBlock
import com.example.data.model.TherapistProfile
import com.example.data.model.VoiceExercise
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FonoRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireValidDocId(id: String, message: String = "Identificador inválido."): String {
        val trimmed = id.trim()
        require(VALID_DOC_ID_REGEX.matches(trimmed)) { message }
        return trimmed
    }

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("A fonoaudióloga precisa estar autenticada com Google.")
        return requireValidDocId(uid, "Identificador de usuário inválido.")
    }

    private fun <T> Flow<T>.withFirestoreErrorLogging(
        uid: String,
        operationType: OperationType,
        path: String
    ): Flow<T> = catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
        throw error
    }

    fun observeProfile(userId: String): Flow<TherapistProfile?> = flow {
        val safeUid = requireValidDocId(userId, "ID de usuário inválido.")
        val path = "users/$safeUid"
        emitAll(
            db.collection("users").document(safeUid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            TherapistProfile::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .withFirestoreErrorLogging(userId, OperationType.GET, path)
        )
    }

    fun observePatients(userId: String): Flow<List<Patient>> = flow {
        val safeUid = requireValidDocId(userId, "ID de usuário inválido.")
        val path = "users/$safeUid/patients"
        emitAll(
            db.collection("users").document(safeUid).collection("patients")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Patient::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.fullName.lowercase() }
                }
                .withFirestoreErrorLogging(safeUid, OperationType.LIST, path)
        )
    }

    fun observeExercises(userId: String): Flow<List<VoiceExercise>> = flow {
        val safeUid = requireValidDocId(userId, "ID de usuário inválido.")
        val path = "users/$safeUid/exercises"
        emitAll(
            db.collection("users").document(safeUid).collection("exercises")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VoiceExercise::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .withFirestoreErrorLogging(safeUid, OperationType.LIST, path)
        )
    }

    fun observeSessions(userId: String): Flow<List<EvolutionSession>> = flow {
        val safeUid = requireValidDocId(userId, "ID de usuário inválido.")
        val path = "users/$safeUid/sessions"
        emitAll(
            db.collection("users").document(safeUid).collection("sessions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        EvolutionSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.sessionDateMillis }
                }
                .withFirestoreErrorLogging(safeUid, OperationType.LIST, path)
        )
    }

    fun observeAppointments(userId: String): Flow<List<Appointment>> = flow {
        val safeUid = requireValidDocId(userId, "ID de usuário inválido.")
        val path = "users/$safeUid/appointments"
        emitAll(
            db.collection("users").document(safeUid).collection("appointments")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Appointment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.scheduledAtMillis }
                }
                .withFirestoreErrorLogging(safeUid, OperationType.LIST, path)
        )
    }

    fun observeSlotBlocks(userId: String): Flow<List<SlotBlock>> = flow {
        val safeUid = requireValidDocId(userId, "ID de usuário inválido.")
        val path = "users/$safeUid/slotBlocks"
        emitAll(
            db.collection("users").document(safeUid).collection("slotBlocks")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        SlotBlock::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedWith(compareBy({ it.dateKey }, { it.hour }, { it.minute }))
                }
                .withFirestoreErrorLogging(safeUid, OperationType.LIST, path)
        )
    }

    fun observeExerciseLists(userId: String): Flow<List<CustomExerciseList>> = flow {
        val safeUid = requireValidDocId(userId, "ID de usuário inválido.")
        val path = "users/$safeUid/exerciseLists"
        emitAll(
            db.collection("users").document(safeUid).collection("exerciseLists")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CustomExerciseList::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .withFirestoreErrorLogging(safeUid, OperationType.LIST, path)
        )
    }

    fun observePatientAccessCodes(userId: String): Flow<List<PatientAccessCode>> = flow {
        val safeUid = requireValidDocId(userId, "ID de usuário inválido.")
        val path = "patientAccessCodes"
        emitAll(
            db.collection("patientAccessCodes")
                .whereEqualTo("therapistId", safeUid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        PatientAccessCode::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .withFirestoreErrorLogging(safeUid, OperationType.LIST, path)
        )
    }

    suspend fun ensureProfileExists(
        displayName: String,
        email: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val safeName = displayName.trim().ifBlank { "Dra. Fonoaudióloga" }.take(100)
                val safeEmail = email.trim().take(120)
                val payload = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to safeEmail,
                    "crfa" to "",
                    "clinicName" to "Consultório de Fonoaudiologia",
                    "defaultReminderMinutes" to 60L,
                    "autoRemindersEnabled" to true,
                    "customReminderTemplate" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        crfa: String,
        clinicName: String,
        defaultReminderMinutes: Long,
        autoRemindersEnabled: Boolean,
        customReminderTemplate: String = ""
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val cleanName = displayName.trim().take(100)
            require(cleanName.isNotEmpty()) { "Informe seu nome profissional." }
            val safeLead = defaultReminderMinutes.coerceIn(5L, 10080L)
            val safeTemplate = customReminderTemplate.trim().take(1000)
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "displayName" to cleanName,
                    "crfa" to crfa.trim().take(40),
                    "clinicName" to clinicName.trim().take(100),
                    "defaultReminderMinutes" to safeLead,
                    "autoRemindersEnabled" to autoRemindersEnabled,
                    "customReminderTemplate" to safeTemplate,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createPatient(
        fullName: String,
        ageYears: Int,
        clinicalFocus: String,
        guardianName: String,
        guardianPhone: String,
        guardianEmail: String,
        weeklyGoalMinutes: Int,
        weeklyExerciseFrequencyGoal: Int = 5,
        initialNotes: String,
        dailyExerciseReminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/patients"
        return try {
            val uid = requireUserId()
            val cleanName = fullName.trim().take(100)
            val cleanFocus = clinicalFocus.trim().take(120)
            val cleanGuardian = guardianName.trim().take(100)
            require(cleanName.length >= 2) { "O nome do paciente deve ter ao menos 2 caracteres." }
            require(cleanFocus.length >= 2) { "Informe o foco clínico / diagnóstico." }
            require(cleanGuardian.length >= 2) { "Informe o nome do responsável." }

            val docRef = db.collection("users").document(uid).collection("patients").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "fullName" to cleanName,
                "ageYears" to ageYears.coerceIn(1, 120).toLong(),
                "clinicalFocus" to cleanFocus,
                "guardianName" to cleanGuardian,
                "guardianPhone" to guardianPhone.trim().take(30),
                "guardianEmail" to guardianEmail.trim().take(120),
                "status" to PatientStatus.ATIVO.name,
                "weeklyGoalMinutes" to weeklyGoalMinutes.coerceIn(10, 1000).toLong(),
                "weeklyExerciseFrequencyGoal" to weeklyExerciseFrequencyGoal.coerceIn(1, 21).toLong(),
                "initialNotes" to initialNotes.trim().take(1000),
                "medicalHistory" to "",
                "mainComplaints" to "",
                "therapeuticGoals" to "",
                "dailyExerciseReminderEnabled" to dailyExerciseReminderEnabled,
                "reminderHour" to reminderHour.coerceIn(0, 23).toLong(),
                "reminderMinute" to reminderMinute.coerceIn(0, 59).toLong(),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updatePatient(
        patientId: String,
        fullName: String,
        ageYears: Int,
        clinicalFocus: String,
        guardianName: String,
        guardianPhone: String,
        guardianEmail: String,
        status: PatientStatus,
        weeklyGoalMinutes: Int,
        weeklyExerciseFrequencyGoal: Int = 5,
        initialNotes: String,
        dailyExerciseReminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/patients/$patientId"
        return try {
            val uid = requireUserId()
            val safePatientId = requireValidDocId(patientId, "ID do paciente inválido.")
            val cleanName = fullName.trim().take(100)
            val cleanFocus = clinicalFocus.trim().take(120)
            val cleanGuardian = guardianName.trim().take(100)
            require(cleanName.length >= 2) { "O nome do paciente deve ter ao menos 2 caracteres." }
            require(cleanFocus.length >= 2) { "Informe o foco clínico." }
            require(cleanGuardian.length >= 2) { "Informe o nome do responsável." }

            val docRef = db.collection("users").document(uid)
                .collection("patients").document(safePatientId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "fullName" to cleanName,
                    "ageYears" to ageYears.coerceIn(1, 120).toLong(),
                    "clinicalFocus" to cleanFocus,
                    "guardianName" to cleanGuardian,
                    "guardianPhone" to guardianPhone.trim().take(30),
                    "guardianEmail" to guardianEmail.trim().take(120),
                    "status" to status.name,
                    "weeklyGoalMinutes" to weeklyGoalMinutes.coerceIn(10, 1000).toLong(),
                    "weeklyExerciseFrequencyGoal" to weeklyExerciseFrequencyGoal.coerceIn(1, 21).toLong(),
                    "initialNotes" to initialNotes.trim().take(1000),
                    "dailyExerciseReminderEnabled" to dailyExerciseReminderEnabled,
                    "reminderHour" to reminderHour.coerceIn(0, 23).toLong(),
                    "reminderMinute" to reminderMinute.coerceIn(0, 59).toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updatePatientWeeklyFrequencyGoal(
        patientId: String,
        frequencyGoal: Int
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/patients/$patientId"
        return try {
            val uid = requireUserId()
            val safePatientId = requireValidDocId(patientId, "ID do paciente inválido.")
            val docRef = db.collection("users").document(uid)
                .collection("patients").document(safePatientId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "weeklyExerciseFrequencyGoal" to frequencyGoal.coerceIn(1, 21).toLong(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updatePatientClinicalSheet(
        patientId: String,
        medicalHistory: String,
        mainComplaints: String,
        therapeuticGoals: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/patients/$patientId"
        return try {
            val uid = requireUserId()
            val safePatientId = requireValidDocId(patientId, "ID do paciente inválido.")
            val docRef = db.collection("users").document(uid)
                .collection("patients").document(safePatientId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "medicalHistory" to medicalHistory.trim().take(2000),
                    "mainComplaints" to mainComplaints.trim().take(2000),
                    "therapeuticGoals" to therapeuticGoals.trim().take(2000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deletePatient(patientId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/patients/$patientId"
        return try {
            val uid = requireUserId()
            val safePatientId = requireValidDocId(patientId, "ID do paciente inválido.")
            val userRef = db.collection("users").document(uid)
            docPath = userRef.collection("patients").document(safePatientId).path

            val exercises = userRef.collection("exercises").whereEqualTo("patientId", safePatientId).get().await()
            val sessions = userRef.collection("sessions").whereEqualTo("patientId", safePatientId).get().await()
            val appointments = userRef.collection("appointments").whereEqualTo("patientId", safePatientId).get().await()
            val lists = userRef.collection("exerciseLists").whereEqualTo("patientId", safePatientId).get().await()
            val accessCodes = db.collection("patientAccessCodes")
                .whereEqualTo("therapistId", uid)
                .whereEqualTo("patientId", safePatientId)
                .get()
                .await()

            val batch = db.batch()
            exercises.documents.forEach { batch.delete(it.reference) }
            sessions.documents.forEach { batch.delete(it.reference) }
            appointments.documents.forEach { batch.delete(it.reference) }
            lists.documents.forEach { batch.delete(it.reference) }
            accessCodes.documents.forEach { batch.delete(it.reference) }
            batch.delete(userRef.collection("patients").document(safePatientId))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createExercise(
        patientId: String,
        title: String,
        category: ExerciseCategory,
        instructions: String,
        targetDurationMinutes: Int,
        frequencyPerWeek: Int
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/exercises"
        return try {
            val uid = requireUserId()
            val safePatientId = requireValidDocId(patientId, "Paciente não selecionado.")
            val cleanTitle = title.trim().take(100)
            val cleanInstructions = instructions.trim().take(1000)
            require(cleanTitle.length >= 2) { "O nome do exercício deve ter ao menos 2 caracteres." }
            require(cleanInstructions.length >= 2) { "Informe as instruções do exercício." }

            val docRef = db.collection("users").document(uid).collection("exercises").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "patientId" to safePatientId,
                "title" to cleanTitle,
                "category" to category.name,
                "instructions" to cleanInstructions,
                "targetDurationMinutes" to targetDurationMinutes.coerceIn(1, 120).toLong(),
                "frequencyPerWeek" to frequencyPerWeek.coerceIn(1, 21).toLong(),
                "activeFlag" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleExerciseActive(exerciseId: String, activeFlag: Boolean): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/exercises/$exerciseId"
        return try {
            val uid = requireUserId()
            val safeExerciseId = requireValidDocId(exerciseId, "ID do exercício inválido.")
            val docRef = db.collection("users").document(uid)
                .collection("exercises").document(safeExerciseId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "activeFlag" to activeFlag,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteExercise(exerciseId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/exercises/$exerciseId"
        return try {
            val uid = requireUserId()
            val safeExerciseId = requireValidDocId(exerciseId, "ID do exercício inválido.")
            val docRef = db.collection("users").document(uid)
                .collection("exercises").document(safeExerciseId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun logEvolutionSession(
        patientId: String,
        patientName: String,
        exerciseTitle: String,
        category: ExerciseCategory,
        durationMinutes: Int,
        vocalClarityScore: Int,
        stabilityScore: Int,
        comfortScore: Int,
        tmfSeconds: Double,
        clinicalNotes: String,
        homeGuidance: String,
        sessionDateMillis: Long
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/sessions"
        return try {
            val uid = requireUserId()
            val safePatientId = requireValidDocId(patientId, "Paciente inválido.")
            val cleanTitle = exerciseTitle.trim().take(120)
            require(cleanTitle.length >= 2) { "Informe o exercício realizado." }
            val safeDateMillis = if (sessionDateMillis > 0L) sessionDateMillis else System.currentTimeMillis()

            val docRef = db.collection("users").document(uid).collection("sessions").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "patientId" to safePatientId,
                "patientName" to patientName.trim().take(100).ifBlank { "Paciente" },
                "exerciseTitle" to cleanTitle,
                "category" to category.name,
                "durationMinutes" to durationMinutes.coerceIn(1, 240).toLong(),
                "vocalClarityScore" to vocalClarityScore.coerceIn(0, 100).toLong(),
                "stabilityScore" to stabilityScore.coerceIn(0, 100).toLong(),
                "comfortScore" to comfortScore.coerceIn(0, 100).toLong(),
                "tmfSeconds" to tmfSeconds.coerceIn(0.0, 120.0),
                "clinicalNotes" to clinicalNotes.trim().take(1500),
                "homeGuidance" to homeGuidance.trim().take(1000),
                "sessionDateMillis" to safeDateMillis,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSession(sessionId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/sessions/$sessionId"
        return try {
            val uid = requireUserId()
            val safeSessionId = requireValidDocId(sessionId, "ID da sessão inválido.")
            val docRef = db.collection("users").document(uid)
                .collection("sessions").document(safeSessionId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createAppointment(
        patient: Patient,
        scheduledAtMillis: Long,
        durationMinutes: Int,
        sessionType: AppointmentType,
        notes: String,
        reminderEnabled: Boolean,
        reminderLeadMinutes: Long,
        patientReminderEnabled: Boolean,
        patientReminderLeadMinutes: Long
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/appointments"
        return try {
            val uid = requireUserId()
            val safePatientId = requireValidDocId(patient.id, "Selecione um paciente.")
            require(scheduledAtMillis > 0L) { "Horário inválido." }

            val docRef = db.collection("users").document(uid).collection("appointments").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "patientId" to safePatientId,
                "patientName" to patient.fullName.trim().take(100),
                "guardianName" to patient.guardianName.trim().take(100),
                "guardianPhone" to patient.guardianPhone.trim().take(30),
                "scheduledAtMillis" to scheduledAtMillis,
                "durationMinutes" to durationMinutes.coerceIn(15, 180).toLong(),
                "sessionType" to sessionType.name,
                "status" to AppointmentStatus.AGENDADA.name,
                "notes" to notes.trim().take(1000),
                "reminderEnabled" to reminderEnabled,
                "reminderLeadMinutes" to reminderLeadMinutes.coerceIn(5L, 10080L),
                "patientReminderEnabled" to patientReminderEnabled,
                "patientReminderLeadMinutes" to patientReminderLeadMinutes.coerceIn(5L, 10080L),
                "reminderSentMillis" to 0L,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun blockSlot(
        dateKey: String,
        hour: Int,
        minute: Int,
        durationMinutes: Int,
        reason: String
    ): Result<String> {
        val docId = "${dateKey}_${hour}_${minute}"
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/slotBlocks/$docId"
        return try {
            val uid = requireUserId()
            require(Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(dateKey)) { "Data inválida." }
            val safeDocId = requireValidDocId(docId, "Identificador de bloqueio inválido.")
            val docRef = db.collection("users").document(uid)
                .collection("slotBlocks").document(safeDocId)
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "dateKey" to dateKey,
                "hour" to hour.coerceIn(0, 23).toLong(),
                "minute" to minute.coerceIn(0, 59).toLong(),
                "durationMinutes" to durationMinutes.coerceIn(15, 240).toLong(),
                "reason" to reason.trim().ifBlank { "Horário ocupado" }.take(120),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(safeDocId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun unblockSlot(blockId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/slotBlocks/$blockId"
        return try {
            val uid = requireUserId()
            val safeBlockId = requireValidDocId(blockId, "ID do bloqueio inválido.")
            val docRef = db.collection("users").document(uid)
                .collection("slotBlocks").document(safeBlockId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateAppointmentStatus(
        appointmentId: String,
        newStatus: AppointmentStatus
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/appointments/$appointmentId"
        return try {
            val uid = requireUserId()
            val safeApptId = requireValidDocId(appointmentId, "ID do agendamento inválido.")
            val docRef = db.collection("users").document(uid)
                .collection("appointments").document(safeApptId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to newStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun markAppointmentReminderSent(
        appointmentId: String,
        sentAtMillis: Long = System.currentTimeMillis()
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/appointments/$appointmentId"
        return try {
            val uid = requireUserId()
            val safeApptId = requireValidDocId(appointmentId, "ID do agendamento inválido.")
            val docRef = db.collection("users").document(uid)
                .collection("appointments").document(safeApptId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "reminderSentMillis" to sentAtMillis.coerceAtLeast(0L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAppointment(appointmentId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/appointments/$appointmentId"
        return try {
            val uid = requireUserId()
            val safeApptId = requireValidDocId(appointmentId, "ID do agendamento inválido.")
            val docRef = db.collection("users").document(uid)
                .collection("appointments").document(safeApptId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveExerciseList(
        editingListId: String?,
        title: String,
        clinicalObjective: String,
        patientId: String,
        patientName: String,
        items: List<ExerciseListItem>
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/exerciseLists"
        return try {
            val uid = requireUserId()
            val cleanTitle = title.trim().take(120)
            require(cleanTitle.length >= 2) { "O título da lista deve ter ao menos 2 caracteres." }
            require(items.isNotEmpty()) { "Adicione ao menos 1 exercício à lista." }
            val safeItems = items.take(20).map { item ->
                mapOf(
                    "title" to item.title.trim().take(100).ifBlank { "Exercício de Voz" },
                    "category" to item.exerciseCategory.name,
                    "instructions" to item.instructions.trim().take(1000).ifBlank { "Executar conforme orientação em consultório." },
                    "durationMinutes" to item.durationMinutes.coerceIn(1L, 120L),
                    "frequencyPerWeek" to item.frequencyPerWeek.coerceIn(1L, 21L)
                )
            }
            val cleanPatientId = if (patientId.isBlank()) {
                ""
            } else {
                requireValidDocId(patientId, "ID do paciente inválido.")
            }
            val cleanPatientName = if (cleanPatientId.isBlank()) "" else patientName.trim().take(100)
            val cleanObjective = clinicalObjective.trim().take(400)

            val collectionRef = db.collection("users").document(uid).collection("exerciseLists")
            if (editingListId.isNullOrBlank()) {
                val docRef = collectionRef.document()
                docPath = docRef.path
                val payload = mapOf(
                    "userId" to uid,
                    "title" to cleanTitle,
                    "clinicalObjective" to cleanObjective,
                    "patientId" to cleanPatientId,
                    "patientName" to cleanPatientName,
                    "items" to safeItems,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
                Result.success(docRef.id)
            } else {
                val safeListId = requireValidDocId(editingListId, "ID da lista inválido.")
                val docRef = collectionRef.document(safeListId)
                docPath = docRef.path
                docRef.update(
                    mapOf(
                        "title" to cleanTitle,
                        "clinicalObjective" to cleanObjective,
                        "patientId" to cleanPatientId,
                        "patientName" to cleanPatientName,
                        "items" to safeItems,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                Result.success(safeListId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val op = if (editingListId.isNullOrBlank()) OperationType.CREATE else OperationType.UPDATE
            handleFirestoreError(e, op, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteExerciseList(listId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/exerciseLists/$listId"
        return try {
            val uid = requireUserId()
            val safeListId = requireValidDocId(listId, "ID da lista inválido.")
            val docRef = db.collection("users").document(uid)
                .collection("exerciseLists").document(safeListId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun applyExerciseListToPatient(
        patientId: String,
        items: List<ExerciseListItem>
    ): Result<Int> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/exercises"
        return try {
            val uid = requireUserId()
            val safePatientId = requireValidDocId(patientId, "Paciente não selecionado.")
            require(items.isNotEmpty()) { "A lista não possui exercícios." }

            val exercisesCol = db.collection("users").document(uid).collection("exercises")
            val batch = db.batch()
            val bounded = items.take(20)
            bounded.forEach { item ->
                val docRef = exercisesCol.document()
                docPath = docRef.path
                val payload = mapOf(
                    "userId" to uid,
                    "patientId" to safePatientId,
                    "title" to item.title.trim().take(100).ifBlank { "Exercício de Voz" },
                    "category" to item.exerciseCategory.name,
                    "instructions" to item.instructions.trim().take(1000).ifBlank { "Executar conforme orientação clínica." },
                    "targetDurationMinutes" to item.durationMinutes.coerceIn(1L, 120L),
                    "frequencyPerWeek" to item.frequencyPerWeek.coerceIn(1L, 21L),
                    "activeFlag" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                batch.set(docRef, payload)
            }
            batch.commit().await()
            Result.success(bounded.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    private fun buildExerciseSnapshotMaps(
        exercises: List<VoiceExercise>,
        patientLists: List<CustomExerciseList>
    ): List<Map<String, Any>> {
        val seenKeys = mutableSetOf<String>()
        val result = mutableListOf<Map<String, Any>>()

        exercises.filter { it.activeFlag }.forEach { ex ->
            val key = "${ex.title.trim().lowercase()}|${ex.exerciseCategory.name}"
            if (seenKeys.add(key) && result.size < 40) {
                result.add(
                    mapOf(
                        "title" to ex.title.trim().take(100),
                        "category" to ex.exerciseCategory.name,
                        "instructions" to ex.instructions.trim().take(1000),
                        "durationMinutes" to ex.targetDurationMinutes.coerceIn(1L, 120L),
                        "frequencyPerWeek" to ex.frequencyPerWeek.coerceIn(1L, 21L),
                        "sourceLabel" to "Plano Ativo"
                    )
                )
            }
        }

        patientLists.forEach { list ->
            list.items.forEach { item ->
                val key = "${item.title.trim().lowercase()}|${item.exerciseCategory.name}"
                if (seenKeys.add(key) && result.size < 40) {
                    result.add(
                        mapOf(
                            "title" to item.title.trim().take(100),
                            "category" to item.exerciseCategory.name,
                            "instructions" to item.instructions.trim().take(1000),
                            "durationMinutes" to item.durationMinutes.coerceIn(1L, 120L),
                            "frequencyPerWeek" to item.frequencyPerWeek.coerceIn(1L, 21L),
                            "sourceLabel" to "Lista: ${list.title.take(32)}"
                        )
                    )
                }
            }
        }
        return result
    }

    private fun findNextAppointmentMillis(appointments: List<Appointment>): Long {
        val now = System.currentTimeMillis()
        return appointments
            .filter { !it.appointmentStatus.isTerminal && it.scheduledAtMillis >= now - 15 * 60_000L }
            .minOfOrNull { it.scheduledAtMillis }
            ?: 0L
    }

    private fun buildNextAppointmentSummary(appointments: List<Appointment>): String {
        val now = System.currentTimeMillis()
        val next = appointments
            .filter { !it.appointmentStatus.isTerminal && it.scheduledAtMillis >= now - 15 * 60_000L }
            .minByOrNull { it.scheduledAtMillis }
            ?: return ""
        val fmt = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
        return "${fmt.format(Date(next.scheduledAtMillis))} • ${next.appointmentType.label}".take(200)
    }

    private fun buildRecentProgressSummary(sessions: List<EvolutionSession>): String {
        val latest = sessions.maxByOrNull { it.sessionDateMillis } ?: return ""
        val tmfFmt = String.format(Locale("pt", "BR"), "%.1fs", latest.tmfSeconds)
        return "Último registro (${sessions.size} sessões): Clareza ${latest.vocalClarityScore}% • TMF $tmfFmt".take(300)
    }

    suspend fun generatePatientAccessCode(
        patient: Patient,
        therapistName: String,
        clinicName: String,
        exercises: List<VoiceExercise>,
        patientLists: List<CustomExerciseList>,
        appointments: List<Appointment>,
        sessions: List<EvolutionSession>
    ): Result<String> {
        var docPath = "patientAccessCodes"
        return try {
            val uid = requireUserId()
            val safePatientId = requireValidDocId(patient.id, "Paciente inválido.")

            val newCode = AccessCodeGenerator.generate()
            val codeRef = db.collection("patientAccessCodes").document(newCode)
            docPath = codeRef.path

            val existingActive = db.collection("patientAccessCodes")
                .whereEqualTo("therapistId", uid)
                .whereEqualTo("patientId", safePatientId)
                .get()
                .await()

            val batch = db.batch()
            existingActive.documents.forEach { doc ->
                if (doc.getBoolean("active") == true) {
                    batch.update(
                        doc.reference,
                        mapOf(
                            "active" to false,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }

            val payload = mapOf(
                "code" to newCode,
                "therapistId" to uid,
                "therapistName" to therapistName.trim().ifBlank { "Fonoaudióloga" }.take(100),
                "clinicName" to clinicName.trim().take(100),
                "patientId" to safePatientId,
                "patientName" to patient.fullName.trim().ifBlank { "Paciente" }.take(100),
                "clinicalFocus" to patient.clinicalFocus.trim().take(120),
                "weeklyGoalMinutes" to patient.weeklyGoalMinutes.coerceIn(10L, 1000L),
                "active" to true,
                "redeemedByUid" to "",
                "redeemedAtMillis" to 0L,
                "exercisesSnapshot" to buildExerciseSnapshotMaps(exercises, patientLists),
                "nextAppointmentSummary" to buildNextAppointmentSummary(appointments),
                "nextAppointmentMillis" to findNextAppointmentMillis(appointments).coerceAtLeast(0L),
                "recentProgressSummary" to buildRecentProgressSummary(sessions),
                "dailyReminderEnabled" to patient.dailyExerciseReminderEnabled,
                "reminderHour" to patient.reminderHour.coerceIn(0L, 23L),
                "reminderMinute" to patient.reminderMinute.coerceIn(0L, 59L),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            batch.set(codeRef, payload)
            batch.commit().await()
            Result.success(newCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun revokePatientAccessCode(code: String): Result<Unit> {
        val normalized = AccessCodeGenerator.normalize(code)
        var docPath = "patientAccessCodes/$normalized"
        return try {
            requireUserId()
            val safeCode = requireValidDocId(normalized, "Código de acesso inválido.")
            val docRef = db.collection("patientAccessCodes").document(safeCode)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "active" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncActiveAccessCodeForPatient(
        patient: Patient,
        therapistName: String,
        clinicName: String,
        exercises: List<VoiceExercise>,
        patientLists: List<CustomExerciseList>,
        appointments: List<Appointment>,
        sessions: List<EvolutionSession>
    ) {
        try {
            val uid = auth.currentUser?.uid ?: return
            if (patient.id.isBlank()) return
            val activeDocs = db.collection("patientAccessCodes")
                .whereEqualTo("therapistId", uid)
                .whereEqualTo("patientId", patient.id)
                .get()
                .await()
                .documents
                .filter { it.getBoolean("active") == true }
            if (activeDocs.isEmpty()) return

            val updates = mapOf(
                "therapistName" to therapistName.trim().ifBlank { "Fonoaudióloga" }.take(100),
                "clinicName" to clinicName.trim().take(100),
                "patientName" to patient.fullName.trim().ifBlank { "Paciente" }.take(100),
                "clinicalFocus" to patient.clinicalFocus.trim().take(120),
                "weeklyGoalMinutes" to patient.weeklyGoalMinutes.coerceIn(10L, 1000L),
                "exercisesSnapshot" to buildExerciseSnapshotMaps(exercises, patientLists),
                "nextAppointmentSummary" to buildNextAppointmentSummary(appointments),
                "nextAppointmentMillis" to findNextAppointmentMillis(appointments).coerceAtLeast(0L),
                "recentProgressSummary" to buildRecentProgressSummary(sessions),
                "dailyReminderEnabled" to patient.dailyExerciseReminderEnabled,
                "reminderHour" to patient.reminderHour.coerceIn(0L, 23L),
                "reminderMinute" to patient.reminderMinute.coerceIn(0L, 59L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val batch = db.batch()
            activeDocs.forEach { batch.update(it.reference, updates) }
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Best-effort sync for patient portal mirror
        }
    }

    suspend fun redeemPatientAccessCode(rawCode: String): Result<PatientAccessCode> {
        val normalized = AccessCodeGenerator.normalize(rawCode)
        var docPath = "patientAccessCodes/$normalized"
        return try {
            val uid = auth.currentUser?.uid
                ?: throw IllegalStateException("Conecte sua conta Google antes de validar o código.")
            require(normalized.length in 6..16 && VALID_DOC_ID_REGEX.matches(normalized)) {
                "Digite um código válido com 6 a 16 caracteres (ex.: FV8K4M2P)."
            }

            val docRef = db.collection("patientAccessCodes").document(normalized)
            docPath = docRef.path
            val snap = docRef.get().await()
            if (!snap.exists()) {
                return Result.failure(IllegalArgumentException("Código não encontrado. Confira com sua fonoaudióloga."))
            }
            val codeObj = snap.toObject(
                PatientAccessCode::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: return Result.failure(IllegalStateException("Não foi possível ler os dados do código."))

            if (!codeObj.active) {
                return Result.failure(IllegalStateException("Este código foi desativado pela fonoaudióloga. Solicite um novo código."))
            }
            if (codeObj.redeemedByUid.isNotBlank() && codeObj.redeemedByUid != uid) {
                return Result.failure(IllegalStateException("Este código já está vinculado a outra conta."))
            }

            val nowMillis = System.currentTimeMillis()
            docRef.update(
                mapOf(
                    "redeemedByUid" to uid,
                    "redeemedAtMillis" to nowMillis,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(
                codeObj.copy(
                    id = normalized,
                    redeemedByUid = uid,
                    redeemedAtMillis = nowMillis
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            handleFirestoreError(e, OperationType.GET, docPath)
            val friendlyMsg = if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                "Código inválido, desativado ou já utilizado por outra conta."
            } else {
                e.localizedMessage ?: "Erro ao validar o código."
            }
            Result.failure(IllegalStateException(friendlyMsg, e))
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    fun observeRedeemedPatientAccessCode(rawCode: String): Flow<PatientAccessCode?> = flow {
        val normalized = AccessCodeGenerator.normalize(rawCode)
        val uid = auth.currentUser?.uid
        if (normalized.isBlank() || uid.isNullOrBlank() || !VALID_DOC_ID_REGEX.matches(normalized)) {
            emit(null)
            return@flow
        }
        val docRef = db.collection("patientAccessCodes").document(normalized)
        emitAll(
            docRef.snapshots()
                .map { snapshot ->
                    if (!snapshot.exists()) {
                        null
                    } else {
                        val obj = snapshot.toObject(
                            PatientAccessCode::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                        if (obj != null && obj.active && (obj.redeemedByUid.isBlank() || obj.redeemedByUid == uid)) {
                            obj
                        } else {
                            null
                        }
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (firestoreError?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        emit(null)
                    } else {
                        throw error
                    }
                }
        )
    }

    private companion object {
        val VALID_DOC_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")
    }
}
