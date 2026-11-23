package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import com.example.data.model.AppointmentType
import com.example.data.model.EvolutionSession
import com.example.data.model.ExerciseCategory
import com.example.data.model.Patient
import com.example.data.model.PatientStatus
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

class FonoRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("A fonoaudióloga precisa estar autenticada com Google.")
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
        val path = "users/$userId"
        emitAll(
            db.collection("users").document(userId)
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
        val path = "users/$userId/patients"
        emitAll(
            db.collection("users").document(userId).collection("patients")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Patient::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.fullName.lowercase() }
                }
                .withFirestoreErrorLogging(userId, OperationType.LIST, path)
        )
    }

    fun observeExercises(userId: String): Flow<List<VoiceExercise>> = flow {
        val path = "users/$userId/exercises"
        emitAll(
            db.collection("users").document(userId).collection("exercises")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        VoiceExercise::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .withFirestoreErrorLogging(userId, OperationType.LIST, path)
        )
    }

    fun observeSessions(userId: String): Flow<List<EvolutionSession>> = flow {
        val path = "users/$userId/sessions"
        emitAll(
            db.collection("users").document(userId).collection("sessions")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        EvolutionSession::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.sessionDateMillis }
                }
                .withFirestoreErrorLogging(userId, OperationType.LIST, path)
        )
    }

    fun observeAppointments(userId: String): Flow<List<Appointment>> = flow {
        val path = "users/$userId/appointments"
        emitAll(
            db.collection("users").document(userId).collection("appointments")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Appointment::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.scheduledAtMillis }
                }
                .withFirestoreErrorLogging(userId, OperationType.LIST, path)
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
        autoRemindersEnabled: Boolean
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val cleanName = displayName.trim().take(100)
            require(cleanName.isNotEmpty()) { "Informe seu nome profissional." }
            val safeLead = defaultReminderMinutes.coerceIn(5L, 10080L)
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "displayName" to cleanName,
                    "crfa" to crfa.trim().take(40),
                    "clinicName" to clinicName.trim().take(100),
                    "defaultReminderMinutes" to safeLead,
                    "autoRemindersEnabled" to autoRemindersEnabled,
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
                "initialNotes" to initialNotes.trim().take(1000),
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
        initialNotes: String,
        dailyExerciseReminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/patients/$patientId"
        return try {
            val uid = requireUserId()
            require(patientId.isNotBlank()) { "ID do paciente inválido." }
            val cleanName = fullName.trim().take(100)
            val cleanFocus = clinicalFocus.trim().take(120)
            val cleanGuardian = guardianName.trim().take(100)
            require(cleanName.length >= 2) { "O nome do paciente deve ter ao menos 2 caracteres." }
            require(cleanFocus.length >= 2) { "Informe o foco clínico." }
            require(cleanGuardian.length >= 2) { "Informe o nome do responsável." }

            val docRef = db.collection("users").document(uid)
                .collection("patients").document(patientId)
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

    suspend fun deletePatient(patientId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/patients/$patientId"
        return try {
            val uid = requireUserId()
            require(patientId.isNotBlank()) { "ID do paciente inválido." }
            val userRef = db.collection("users").document(uid)
            docPath = userRef.collection("patients").document(patientId).path

            val exercises = userRef.collection("exercises").whereEqualTo("patientId", patientId).get().await()
            val sessions = userRef.collection("sessions").whereEqualTo("patientId", patientId).get().await()
            val appointments = userRef.collection("appointments").whereEqualTo("patientId", patientId).get().await()

            val batch = db.batch()
            exercises.documents.forEach { batch.delete(it.reference) }
            sessions.documents.forEach { batch.delete(it.reference) }
            appointments.documents.forEach { batch.delete(it.reference) }
            batch.delete(userRef.collection("patients").document(patientId))
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
            require(patientId.isNotBlank()) { "Paciente não selecionado." }
            val cleanTitle = title.trim().take(100)
            val cleanInstructions = instructions.trim().take(1000)
            require(cleanTitle.length >= 2) { "O nome do exercício deve ter ao menos 2 caracteres." }
            require(cleanInstructions.length >= 2) { "Informe as instruções do exercício." }

            val docRef = db.collection("users").document(uid).collection("exercises").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "patientId" to patientId,
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
            require(exerciseId.isNotBlank())
            val docRef = db.collection("users").document(uid)
                .collection("exercises").document(exerciseId)
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
            require(exerciseId.isNotBlank())
            val docRef = db.collection("users").document(uid)
                .collection("exercises").document(exerciseId)
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
            require(patientId.isNotBlank()) { "Paciente inválido." }
            val cleanTitle = exerciseTitle.trim().take(120)
            require(cleanTitle.length >= 2) { "Informe o exercício realizado." }
            val safeDateMillis = if (sessionDateMillis > 0L) sessionDateMillis else System.currentTimeMillis()

            val docRef = db.collection("users").document(uid).collection("sessions").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "patientId" to patientId,
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
            require(sessionId.isNotBlank())
            val docRef = db.collection("users").document(uid)
                .collection("sessions").document(sessionId)
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
        reminderLeadMinutes: Long
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/appointments"
        return try {
            val uid = requireUserId()
            require(patient.id.isNotBlank()) { "Selecione um paciente." }
            require(scheduledAtMillis > 0L) { "Horário inválido." }

            val docRef = db.collection("users").document(uid).collection("appointments").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "patientId" to patient.id,
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

    suspend fun updateAppointmentStatus(
        appointmentId: String,
        newStatus: AppointmentStatus
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/appointments/$appointmentId"
        return try {
            val uid = requireUserId()
            require(appointmentId.isNotBlank())
            val docRef = db.collection("users").document(uid)
                .collection("appointments").document(appointmentId)
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
            require(appointmentId.isNotBlank())
            val docRef = db.collection("users").document(uid)
                .collection("appointments").document(appointmentId)
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
            require(appointmentId.isNotBlank())
            val docRef = db.collection("users").document(uid)
                .collection("appointments").document(appointmentId)
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
}
