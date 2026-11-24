package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.model.Appointment
import com.example.data.model.Patient
import com.example.data.model.PatientAccessCode
import com.example.data.model.PatientStatus
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object ReminderNotificationChannels {
    const val CHANNEL_ID = "fonovida_reminders"
    const val CHANNEL_EXERCISES = "fonovida_exercises"
    const val CHANNEL_APPOINTMENTS = "fonovida_appointments"
}

object AppointmentReminderTemplateDefaults {
    const val DEFAULT_TEMPLATE =
        "Olá, {responsavel}! Tudo bem?\n" +
            "Aqui é {terapeuta} ({clinica}).\n" +
            "Passando para lembrar da consulta de {paciente} agendada para {data_hora} ({tipo}).\n" +
            "Por favor, confirme o comparecimento respondendo esta mensagem. Obrigada!"

    val availableVariables: List<Pair<String, String>> = listOf(
        "{paciente}" to "Paciente",
        "{data_hora}" to "Data e hora",
        "{responsavel}" to "Responsável",
        "{tipo}" to "Modalidade",
        "{terapeuta}" to "Fonoaudióloga",
        "{clinica}" to "Clínica",
        "{observacoes}" to "Observações"
    )
}

class ClinicalReminderWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: "Lembrete FonoVida"
        val message = inputData.getString(KEY_MESSAGE)
            ?: "Você possui um acompanhamento fonoaudiológico programado."
        val notificationId = inputData.getInt(KEY_NOTIFICATION_ID, 1001)
        val channelId = inputData.getString(KEY_CHANNEL_ID)
            ?: ReminderNotificationChannels.CHANNEL_ID
        val whatsappPhone = inputData.getString(KEY_WHATSAPP_PHONE)
        val whatsappMessage = inputData.getString(KEY_WHATSAPP_MESSAGE)
        val whatsappAppointmentId = inputData.getString(KEY_WHATSAPP_APPT_ID)

        ReminderScheduler.showImmediateNotification(
            context = applicationContext,
            title = title,
            message = message,
            notificationId = notificationId,
            channelId = channelId,
            whatsappPhone = whatsappPhone,
            whatsappMessage = whatsappMessage,
            whatsappAppointmentId = whatsappAppointmentId
        )
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_NOTIFICATION_ID = "notification_id"
        const val KEY_CHANNEL_ID = "channel_id"
        const val KEY_WHATSAPP_PHONE = "whatsapp_phone"
        const val KEY_WHATSAPP_MESSAGE = "whatsapp_message"
        const val KEY_WHATSAPP_APPT_ID = "whatsapp_appt_id"
    }
}

object ReminderScheduler {

    const val EXTRA_WHATSAPP_URI = "extra_whatsapp_uri"
    const val EXTRA_WHATSAPP_APPT_ID = "extra_whatsapp_appt_id"
    const val EXTRA_WHATSAPP_TOKEN = "extra_whatsapp_token"

    private const val INTERNAL_SECRET_PREFS = "fonovida_internal_secrets"
    private const val KEY_INTENT_HMAC_SECRET = "intent_hmac_secret_b64"
    private val VALID_WA_PATH_REGEX = Regex("^/(?:[0-9]{10,15})?$")
    private val VALID_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")

    fun isValidSafeDocumentId(id: String?): Boolean =
        !id.isNullOrBlank() && VALID_ID_REGEX.matches(id)

    fun isAllowedWhatsAppUri(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank() || rawUrl.length > 4096) return false
        if (rawUrl.any { it.isWhitespace() || it.code < 0x20 || it.code == 0x7F }) return false
        return try {
            val parsed = URI(rawUrl)
            parsed.scheme.equals("https", ignoreCase = true) &&
                parsed.host.equals("wa.me", ignoreCase = true) &&
                parsed.rawUserInfo == null &&
                parsed.userInfo == null &&
                parsed.port == -1 &&
                VALID_WA_PATH_REGEX.matches(parsed.rawPath.orEmpty())
        } catch (_: Exception) {
            false
        }
    }

    private fun getOrCreateInternalHmacKey(context: Context): ByteArray {
        val prefs = context.applicationContext.getSharedPreferences(
            INTERNAL_SECRET_PREFS,
            Context.MODE_PRIVATE
        )
        val existingB64 = prefs.getString(KEY_INTENT_HMAC_SECRET, null)
        if (!existingB64.isNullOrBlank()) {
            try {
                val decoded = Base64.getDecoder().decode(existingB64)
                if (decoded.size == 32) return decoded
            } catch (_: Exception) {
                // Regenerate corrupted secret
            }
        }
        val secret = ByteArray(32)
        SecureRandom().nextBytes(secret)
        val encoded = Base64.getEncoder().encodeToString(secret)
        prefs.edit().putString(KEY_INTENT_HMAC_SECRET, encoded).apply()
        return secret
    }

    fun computeIntentDispatchToken(
        context: Context,
        waUrl: String,
        appointmentId: String
    ): String {
        val keyBytes = getOrCreateInternalHmacKey(context)
        val payload = "${waUrl}\n${appointmentId}".toByteArray(StandardCharsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(keyBytes, "HmacSHA256"))
        }
        val digest = mac.doFinal(payload)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    fun verifyIntentDispatchToken(
        context: Context,
        waUrl: String,
        appointmentId: String,
        providedToken: String?
    ): Boolean {
        if (providedToken.isNullOrBlank()) return false
        val expected = computeIntentDispatchToken(context, waUrl, appointmentId)
        return MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.UTF_8),
            providedToken.toByteArray(StandardCharsets.UTF_8)
        )
    }

    fun normalizePhoneForWhatsApp(phone: String): String {
        val clean = phone.filter { it.isDigit() }.trimStart('0')
        if (clean.length < 10) return ""
        return if (clean.startsWith("55") && clean.length >= 12) clean else "55$clean"
    }

    fun buildWhatsAppUrlString(phone: String, message: String): String {
        val normalized = normalizePhoneForWhatsApp(phone)
        val encodedText = URLEncoder.encode(message, StandardCharsets.UTF_8.name())
            .replace("+", "%20")
        return if (normalized.isNotEmpty()) {
            "https://wa.me/$normalized?text=$encodedText"
        } else {
            "https://wa.me/?text=$encodedText"
        }
    }

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channels = listOf(
                Triple(
                    ReminderNotificationChannels.CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    context.getString(R.string.notification_channel_desc)
                ),
                Triple(
                    ReminderNotificationChannels.CHANNEL_EXERCISES,
                    context.getString(R.string.notification_channel_exercises_name),
                    context.getString(R.string.notification_channel_exercises_desc)
                ),
                Triple(
                    ReminderNotificationChannels.CHANNEL_APPOINTMENTS,
                    context.getString(R.string.notification_channel_appointments_name),
                    context.getString(R.string.notification_channel_appointments_desc)
                )
            )

            channels.forEach { (id, name, desc) ->
                if (manager.getNotificationChannel(id) == null) {
                    val channel = NotificationChannel(
                        id,
                        name,
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = desc
                        enableVibration(true)
                    }
                    manager.createNotificationChannel(channel)
                }
            }
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun computeNextDailyTriggerDelayMillis(
        hour: Int,
        minute: Int,
        nowMillis: Long = System.currentTimeMillis()
    ): Long {
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        return (target.timeInMillis - nowMillis).coerceAtLeast(60_000L)
    }

    fun showImmediateNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
        channelId: String = ReminderNotificationChannels.CHANNEL_ID,
        whatsappPhone: String? = null,
        whatsappMessage: String? = null,
        whatsappAppointmentId: String? = null
    ): Boolean {
        ensureNotificationChannel(context)
        if (!hasNotificationPermission(context)) return false

        val waUrl = whatsappMessage?.takeIf { it.isNotBlank() }?.let { msg ->
            buildWhatsAppUrlString(whatsappPhone.orEmpty(), msg)
        }?.takeIf { isAllowedWhatsAppUri(it) }

        val safeApptId = whatsappAppointmentId?.takeIf { isValidSafeDocumentId(it) }.orEmpty()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            if (waUrl != null) {
                putExtra(EXTRA_WHATSAPP_URI, waUrl)
                if (safeApptId.isNotEmpty()) {
                    putExtra(EXTRA_WHATSAPP_APPT_ID, safeApptId)
                }
                putExtra(
                    EXTRA_WHATSAPP_TOKEN,
                    computeIntentDispatchToken(context, waUrl, safeApptId)
                )
            }
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_voice)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (waUrl != null) {
            val waIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val waPendingIntent = PendingIntent.getActivity(
                context,
                notificationId + 31,
                waIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_notification_voice,
                context.getString(R.string.notification_action_whatsapp),
                waPendingIntent
            )
        }

        val notification = builder.build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun syncTherapistReminders(
        context: Context,
        patients: List<Patient>,
        appointments: List<Appointment>,
        therapistName: String = "Fonoaudióloga",
        customReminderTemplate: String = "",
        clinicName: String = ""
    ) {
        val appContext = context.applicationContext
        ensureNotificationChannel(appContext)
        patients.forEach { patient ->
            if (patient.patientStatus == PatientStatus.ATIVO && patient.dailyExerciseReminderEnabled) {
                scheduleDailyPatientExerciseReminder(appContext, patient)
            } else {
                WorkManager.getInstance(appContext).cancelUniqueWork("patient_daily_${patient.id}")
            }
        }
        appointments.forEach { appointment ->
            scheduleAppointmentReminder(
                context = appContext,
                appointment = appointment,
                therapistName = therapistName,
                customReminderTemplate = customReminderTemplate,
                clinicName = clinicName
            )
        }
    }

    fun scheduleAppointmentReminder(
        context: Context,
        appointment: Appointment,
        therapistName: String = "Fonoaudióloga",
        customReminderTemplate: String = "",
        clinicName: String = ""
    ) {
        val therapistWorkName = "appt_reminder_${appointment.id}"
        val patientWorkName = "appt_patient_reminder_${appointment.id}"
        val workManager = WorkManager.getInstance(context.applicationContext)

        if (appointment.appointmentStatus.isTerminal || appointment.scheduledAtMillis <= System.currentTimeMillis()) {
            workManager.cancelUniqueWork(therapistWorkName)
            workManager.cancelUniqueWork(patientWorkName)
            return
        }

        val timeText = SimpleDateFormat("dd/MM 'às' HH:mm", Locale("pt", "BR"))
            .format(Date(appointment.scheduledAtMillis))

        // 1. Lembrete automático para a fonoaudióloga
        if (appointment.reminderEnabled) {
            val triggerAtMillis = appointment.scheduledAtMillis - (appointment.reminderLeadMinutes * 60_000L)
            val delayMillis = triggerAtMillis - System.currentTimeMillis()
            if (delayMillis > 0L) {
                val data = Data.Builder()
                    .putString(
                        ClinicalReminderWorker.KEY_TITLE,
                        "Sua consulta em breve: ${appointment.patientName}"
                    )
                    .putString(
                        ClinicalReminderWorker.KEY_MESSAGE,
                        "Atendimento (${appointment.appointmentType.label}) marcado para $timeText."
                    )
                    .putInt(ClinicalReminderWorker.KEY_NOTIFICATION_ID, appointment.id.hashCode())
                    .putString(
                        ClinicalReminderWorker.KEY_CHANNEL_ID,
                        ReminderNotificationChannels.CHANNEL_APPOINTMENTS
                    )
                    .build()

                val request = OneTimeWorkRequestBuilder<ClinicalReminderWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .build()

                workManager.enqueueUniqueWork(therapistWorkName, ExistingWorkPolicy.REPLACE, request)
            } else {
                workManager.cancelUniqueWork(therapistWorkName)
            }
        } else {
            workManager.cancelUniqueWork(therapistWorkName)
        }

        // 2. Lembrete automático de confirmação para o responsável (Notificação + WhatsApp wa.me)
        if (appointment.patientReminderEnabled) {
            val triggerAtMillis = appointment.scheduledAtMillis - (appointment.patientReminderLeadMinutes * 60_000L)
            val delayMillis = triggerAtMillis - System.currentTimeMillis()
            if (delayMillis > 0L) {
                val guardian = appointment.guardianName.ifBlank { "o responsável" }
                val waMessage = buildAppointmentGuardianMessage(
                    appointment = appointment,
                    therapistName = therapistName,
                    customTemplate = customReminderTemplate,
                    clinicName = clinicName
                )
                val data = Data.Builder()
                    .putString(
                        ClinicalReminderWorker.KEY_TITLE,
                        "Confirmar consulta via WhatsApp: ${appointment.patientName}"
                    )
                    .putString(
                        ClinicalReminderWorker.KEY_MESSAGE,
                        "Lembrete de $timeText para $guardian:\n${waMessage.take(260)}"
                    )
                    .putInt(ClinicalReminderWorker.KEY_NOTIFICATION_ID, appointment.id.hashCode() + 7)
                    .putString(
                        ClinicalReminderWorker.KEY_CHANNEL_ID,
                        ReminderNotificationChannels.CHANNEL_APPOINTMENTS
                    )
                    .putString(ClinicalReminderWorker.KEY_WHATSAPP_PHONE, appointment.guardianPhone)
                    .putString(ClinicalReminderWorker.KEY_WHATSAPP_MESSAGE, waMessage)
                    .putString(ClinicalReminderWorker.KEY_WHATSAPP_APPT_ID, appointment.id)
                    .build()

                val request = OneTimeWorkRequestBuilder<ClinicalReminderWorker>()
                    .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                    .setInputData(data)
                    .build()

                workManager.enqueueUniqueWork(patientWorkName, ExistingWorkPolicy.REPLACE, request)
            } else {
                workManager.cancelUniqueWork(patientWorkName)
            }
        } else {
            workManager.cancelUniqueWork(patientWorkName)
        }
    }

    fun cancelAppointmentReminder(context: Context, appointmentId: String) {
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork("appt_reminder_$appointmentId")
        workManager.cancelUniqueWork("appt_patient_reminder_$appointmentId")
    }

    fun scheduleDailyPatientExerciseReminder(
        context: Context,
        patient: Patient
    ) {
        val workName = "patient_daily_${patient.id}"
        val workManager = WorkManager.getInstance(context.applicationContext)

        if (!patient.dailyExerciseReminderEnabled) {
            workManager.cancelUniqueWork(workName)
            return
        }

        val initialDelayMillis = computeNextDailyTriggerDelayMillis(
            hour = patient.reminderHour.toInt(),
            minute = patient.reminderMinute.toInt()
        )

        val data = Data.Builder()
            .putString(
                ClinicalReminderWorker.KEY_TITLE,
                "Hora do treino vocal: ${patient.fullName}"
            )
            .putString(
                ClinicalReminderWorker.KEY_MESSAGE,
                "Lembrete automático diário: enviar incentivo para os exercícios de voz de ${patient.fullName} (Resp.: ${patient.guardianName})."
            )
            .putInt(ClinicalReminderWorker.KEY_NOTIFICATION_ID, patient.id.hashCode())
            .putString(
                ClinicalReminderWorker.KEY_CHANNEL_ID,
                ReminderNotificationChannels.CHANNEL_EXERCISES
            )
            .build()

        val periodicRequest = PeriodicWorkRequestBuilder<ClinicalReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        workManager.enqueueUniquePeriodicWork(
            workName,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicRequest
        )
    }

    fun syncPatientPortalReminders(
        context: Context,
        accessCode: PatientAccessCode,
        reminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int
    ) {
        val code = accessCode.code.trim().uppercase()
        if (code.isBlank()) return
        val appContext = context.applicationContext
        ensureNotificationChannel(appContext)
        val workManager = WorkManager.getInstance(appContext)

        val dailyWorkName = "portal_patient_daily_$code"
        val apptWork24h = "portal_appt_24h_$code"
        val apptWork1h = "portal_appt_1h_$code"
        val apptWorkSoon = "portal_appt_soon_$code"

        val firstName = accessCode.patientName.trim().split(" ").firstOrNull().orEmpty()
            .ifBlank { "Paciente" }

        // 1. Lembrete diário de exercícios no aparelho do paciente (funciona com o app fechado)
        if (!reminderEnabled) {
            workManager.cancelUniqueWork(dailyWorkName)
        } else {
            val initialDelayMillis = computeNextDailyTriggerDelayMillis(reminderHour, reminderMinute)
            val exerciseNames = accessCode.exercisesSnapshot.take(2).joinToString(" • ") { it.title }
            val body = if (exerciseNames.isNotBlank()) {
                "Seus exercícios de voz de hoje esperam por você: $exerciseNames."
            } else {
                "Separe alguns minutos hoje para sua rotina vocal orientada por ${accessCode.therapistName.ifBlank { "sua fonoaudióloga" }}."
            }

            val dailyData = Data.Builder()
                .putString(
                    ClinicalReminderWorker.KEY_TITLE,
                    "Hora do treino vocal, $firstName!"
                )
                .putString(ClinicalReminderWorker.KEY_MESSAGE, body)
                .putInt(ClinicalReminderWorker.KEY_NOTIFICATION_ID, ("daily_$code").hashCode())
                .putString(
                    ClinicalReminderWorker.KEY_CHANNEL_ID,
                    ReminderNotificationChannels.CHANNEL_EXERCISES
                )
                .build()

            val dailyRequest = PeriodicWorkRequestBuilder<ClinicalReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
                .setInputData(dailyData)
                .build()

            workManager.enqueueUniquePeriodicWork(
                dailyWorkName,
                ExistingPeriodicWorkPolicy.UPDATE,
                dailyRequest
            )
        }

        // 2. Lembretes automáticos da próxima consulta agendada no aparelho do paciente
        val apptMillis = accessCode.nextAppointmentMillis
        val now = System.currentTimeMillis()
        if (apptMillis <= now) {
            workManager.cancelUniqueWork(apptWork24h)
            workManager.cancelUniqueWork(apptWork1h)
            workManager.cancelUniqueWork(apptWorkSoon)
            return
        }

        val apptLabel = accessCode.nextAppointmentSummary.ifBlank {
            SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")).format(Date(apptMillis))
        }
        val therapistLabel = accessCode.therapistName.ifBlank { "sua fonoaudióloga" }

        val delay24h = apptMillis - 24 * 3_600_000L - now
        if (delay24h > 0L) {
            val data24h = Data.Builder()
                .putString(
                    ClinicalReminderWorker.KEY_TITLE,
                    "Consulta de Fonoaudiologia amanhã"
                )
                .putString(
                    ClinicalReminderWorker.KEY_MESSAGE,
                    "Olá, $firstName! Lembrando da sua consulta com $therapistLabel: $apptLabel."
                )
                .putInt(ClinicalReminderWorker.KEY_NOTIFICATION_ID, ("appt24_$code").hashCode())
                .putString(
                    ClinicalReminderWorker.KEY_CHANNEL_ID,
                    ReminderNotificationChannels.CHANNEL_APPOINTMENTS
                )
                .build()
            val req24h = OneTimeWorkRequestBuilder<ClinicalReminderWorker>()
                .setInitialDelay(delay24h, TimeUnit.MILLISECONDS)
                .setInputData(data24h)
                .build()
            workManager.enqueueUniqueWork(apptWork24h, ExistingWorkPolicy.REPLACE, req24h)
        } else {
            workManager.cancelUniqueWork(apptWork24h)
        }

        val delay1h = apptMillis - 3_600_000L - now
        if (delay1h > 0L) {
            val data1h = Data.Builder()
                .putString(
                    ClinicalReminderWorker.KEY_TITLE,
                    "Sua consulta começa em 1 hora"
                )
                .putString(
                    ClinicalReminderWorker.KEY_MESSAGE,
                    "Prepare-se para o atendimento com $therapistLabel ($apptLabel)."
                )
                .putInt(ClinicalReminderWorker.KEY_NOTIFICATION_ID, ("appt1h_$code").hashCode())
                .putString(
                    ClinicalReminderWorker.KEY_CHANNEL_ID,
                    ReminderNotificationChannels.CHANNEL_APPOINTMENTS
                )
                .build()
            val req1h = OneTimeWorkRequestBuilder<ClinicalReminderWorker>()
                .setInitialDelay(delay1h, TimeUnit.MILLISECONDS)
                .setInputData(data1h)
                .build()
            workManager.enqueueUniqueWork(apptWork1h, ExistingWorkPolicy.REPLACE, req1h)
            workManager.cancelUniqueWork(apptWorkSoon)
        } else {
            workManager.cancelUniqueWork(apptWork1h)
            val remainingMillis = apptMillis - now
            if (remainingMillis > 120_000L) {
                val soonDelay = (apptMillis - 15 * 60_000L - now).coerceAtLeast(15_000L)
                val dataSoon = Data.Builder()
                    .putString(
                        ClinicalReminderWorker.KEY_TITLE,
                        "Consulta de Fonoaudiologia hoje"
                    )
                    .putString(
                        ClinicalReminderWorker.KEY_MESSAGE,
                        "Seu atendimento com $therapistLabel está próximo: $apptLabel."
                    )
                    .putInt(ClinicalReminderWorker.KEY_NOTIFICATION_ID, ("apptSoon_$code").hashCode())
                    .putString(
                        ClinicalReminderWorker.KEY_CHANNEL_ID,
                        ReminderNotificationChannels.CHANNEL_APPOINTMENTS
                    )
                    .build()
                val reqSoon = OneTimeWorkRequestBuilder<ClinicalReminderWorker>()
                    .setInitialDelay(soonDelay, TimeUnit.MILLISECONDS)
                    .setInputData(dataSoon)
                    .build()
                workManager.enqueueUniqueWork(apptWorkSoon, ExistingWorkPolicy.REPLACE, reqSoon)
            } else {
                workManager.cancelUniqueWork(apptWorkSoon)
            }
        }
    }

    fun cancelPatientPortalReminders(context: Context, code: String) {
        val norm = code.trim().uppercase()
        if (norm.isBlank()) return
        val workManager = WorkManager.getInstance(context.applicationContext)
        workManager.cancelUniqueWork("portal_patient_daily_$norm")
        workManager.cancelUniqueWork("portal_appt_24h_$norm")
        workManager.cancelUniqueWork("portal_appt_1h_$norm")
        workManager.cancelUniqueWork("portal_appt_soon_$norm")
    }

    fun enqueueImmediateWorkReminderTest(
        context: Context,
        patientName: String
    ) {
        val appContext = context.applicationContext
        ensureNotificationChannel(appContext)
        val firstName = patientName.trim().split(" ").firstOrNull().orEmpty().ifBlank { "Paciente" }
        val data = Data.Builder()
            .putString(
                ClinicalReminderWorker.KEY_TITLE,
                "Lembrete FonoVida ativo, $firstName!"
            )
            .putString(
                ClinicalReminderWorker.KEY_MESSAGE,
                "Notificação disparada em segundo plano via WorkManager. Seus avisos de exercícios e consultas funcionarão mesmo com o app fechado."
            )
            .putInt(ClinicalReminderWorker.KEY_NOTIFICATION_ID, 9021)
            .putString(
                ClinicalReminderWorker.KEY_CHANNEL_ID,
                ReminderNotificationChannels.CHANNEL_EXERCISES
            )
            .build()

        val request = OneTimeWorkRequestBuilder<ClinicalReminderWorker>()
            .setInitialDelay(3, TimeUnit.SECONDS)
            .setInputData(data)
            .build()

        WorkManager.getInstance(appContext).enqueueUniqueWork(
            "portal_test_reminder",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun renderAppointmentTemplate(
        template: String,
        patientName: String,
        guardianName: String,
        dateTimeStr: String,
        appointmentTypeLabel: String,
        therapistName: String,
        clinicName: String = "",
        notes: String = ""
    ): String {
        val cleanTemplate = template.trim()
        val safeGuardian = guardianName.trim().ifBlank { "Responsável" }
        val safeTherapist = therapistName.trim().ifBlank { "Fonoaudióloga" }
        val safeClinic = clinicName.trim().ifBlank { "Fonoaudiologia - FonoVida" }
        val safeNotes = notes.trim()

        if (cleanTemplate.isBlank()) {
            return buildString {
                appendLine("Olá, $safeGuardian! Tudo bem?")
                appendLine("Aqui é $safeTherapist ($safeClinic).")
                appendLine("Passando para lembrar da consulta de $patientName agendada para $dateTimeStr ($appointmentTypeLabel).")
                if (safeNotes.isNotBlank()) {
                    appendLine("Observação: $safeNotes")
                }
                append("Por favor, confirme o comparecimento respondendo esta mensagem. Obrigada!")
            }
        }

        val rendered = cleanTemplate
            .replace("{paciente}", patientName, ignoreCase = true)
            .replace("{responsavel}", safeGuardian, ignoreCase = true)
            .replace("{data_hora}", dateTimeStr, ignoreCase = true)
            .replace("{tipo}", appointmentTypeLabel, ignoreCase = true)
            .replace("{terapeuta}", safeTherapist, ignoreCase = true)
            .replace("{clinica}", safeClinic, ignoreCase = true)
            .replace("{observacoes}", safeNotes, ignoreCase = true)

        return rendered
            .lines()
            .filterIndexed { index, line ->
                // Preserve intentional spacing except lines that became blank solely from an empty {observacoes}
                line.isNotBlank() || (index > 0 && !cleanTemplate.lines().getOrElse(index) { "" }.trim().equals("{observacoes}", ignoreCase = true))
            }
            .joinToString("\n")
            .trim()
    }

    fun renderAppointmentTemplatePreview(
        template: String,
        therapistName: String,
        clinicName: String = ""
    ): String {
        return renderAppointmentTemplate(
            template = template,
            patientName = "Sofia Mendes",
            guardianName = "Carlos Mendes",
            dateTimeStr = "25/11/2026 às 14:30",
            appointmentTypeLabel = "Presencial",
            therapistName = therapistName,
            clinicName = clinicName,
            notes = "Trazer caderno de exercícios vocais"
        )
    }

    fun buildAppointmentGuardianMessage(
        appointment: Appointment,
        therapistName: String,
        customTemplate: String = "",
        clinicName: String = ""
    ): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
        val dateStr = formatter.format(Date(appointment.scheduledAtMillis))
        return renderAppointmentTemplate(
            template = customTemplate,
            patientName = appointment.patientName,
            guardianName = appointment.guardianName,
            dateTimeStr = dateStr,
            appointmentTypeLabel = appointment.appointmentType.label,
            therapistName = therapistName,
            clinicName = clinicName,
            notes = appointment.notes
        )
    }

    fun buildExerciseGuardianMessage(
        patient: Patient,
        exerciseSummary: String,
        therapistName: String
    ): String {
        val guardian = patient.guardianName.ifBlank { "Responsável" }
        return buildString {
            appendLine("Olá, $guardian!")
            appendLine("Lembrete gentil de $therapistName (FonoVida) para os exercícios de voz de ${patient.fullName}:")
            if (exerciseSummary.isNotBlank()) {
                appendLine("• $exerciseSummary")
            }
            appendLine("A prática diária ajuda muito na evolução da clareza e conforto vocal.")
            append("Qualquer dúvida, estou à disposição!")
        }
    }

    fun buildExerciseListGuardianMessage(
        listTitle: String,
        clinicalObjective: String,
        itemsSummary: String,
        patientName: String?,
        guardianName: String?,
        therapistName: String
    ): String {
        val guardian = guardianName?.ifBlank { null } ?: "Responsável"
        return buildString {
            appendLine("Olá, $guardian!")
            appendLine("Aqui é $therapistName (Fonoaudiologia - FonoVida).")
            if (!patientName.isNullOrBlank()) {
                appendLine("Segue a lista personalizada de exercícios de voz para $patientName:")
            } else {
                appendLine("Segue a lista personalizada de exercícios de voz:")
            }
            appendLine("*$listTitle*")
            if (clinicalObjective.isNotBlank()) {
                appendLine("Objetivo: $clinicalObjective")
            }
            appendLine()
            appendLine(itemsSummary)
            append("Em caso de desconforto vocal, suspenda o exercício e me avise.")
        }
    }

    fun buildPatientAccessCodeMessage(
        patient: Patient,
        code: String,
        therapistName: String,
        clinicName: String
    ): String {
        val guardian = patient.guardianName.ifBlank { "Olá" }
        val clinicLabel = clinicName.ifBlank { "FonoVida" }
        return buildString {
            appendLine("Olá, $guardian! Tudo bem?")
            appendLine("Aqui é $therapistName ($clinicLabel).")
            appendLine("Gerei o código exclusivo para liberar o acesso de ${patient.fullName} aos exercícios de voz no aplicativo FonoVida:")
            appendLine()
            appendLine("Código de acesso: *$code*")
            appendLine()
            appendLine("Como usar:")
            appendLine("1. Abra o app FonoVida")
            appendLine("2. Selecione \"Sou Paciente\"")
            appendLine("3. Conecte sua conta Google e digite o código *$code*")
            append("Qualquer dúvida, estou à disposição!")
        }
    }

    fun buildAppointmentConfirmedMessage(
        appointment: Appointment,
        therapistName: String
    ): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
        val dateStr = formatter.format(Date(appointment.scheduledAtMillis))
        val guardian = appointment.guardianName.ifBlank { "Responsável" }
        return buildString {
            appendLine("Olá, $guardian! Tudo bem?")
            appendLine("Aqui é $therapistName (Fonoaudiologia - FonoVida).")
            appendLine("Confirmamos a consulta de ${appointment.patientName} marcada para $dateStr (${appointment.appointmentType.label}).")
            if (appointment.notes.isNotBlank()) {
                appendLine("Observação: ${appointment.notes}")
            }
            append("Qualquer necessidade de reagendamento, estou à disposição pelo WhatsApp. Até breve!")
        }
    }

    fun launchWhatsAppUrl(
        context: Context,
        waUrl: String,
        fallbackMessage: String = ""
    ): Boolean {
        if (!isAllowedWhatsAppUri(waUrl)) {
            return false
        }
        val waIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(waUrl)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(waIntent)
            true
        } catch (_: Exception) {
            if (fallbackMessage.isNotBlank()) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, fallbackMessage)
                }
                val chooser = Intent.createChooser(shareIntent, "Enviar lembrete ao responsável").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
            false
        }
    }

    fun launchGuardianReminderShare(
        context: Context,
        phone: String,
        message: String
    ): Boolean {
        val waUrl = buildWhatsAppUrlString(phone, message)
        return launchWhatsAppUrl(context, waUrl, message)
    }
}
