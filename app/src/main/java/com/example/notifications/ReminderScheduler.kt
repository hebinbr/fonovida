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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object ReminderNotificationChannels {
    const val CHANNEL_ID = "fonovida_reminders"
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

        ReminderScheduler.showImmediateNotification(
            context = applicationContext,
            title = title,
            message = message,
            notificationId = notificationId
        )
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_MESSAGE = "message"
        const val KEY_NOTIFICATION_ID = "notification_id"
    }
}

object ReminderScheduler {

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val existing = manager.getNotificationChannel(ReminderNotificationChannels.CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    ReminderNotificationChannels.CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notification_channel_desc)
                }
                manager.createNotificationChannel(channel)
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

    fun showImmediateNotification(
        context: Context,
        title: String,
        message: String,
        notificationId: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    ): Boolean {
        ensureNotificationChannel(context)
        if (!hasNotificationPermission(context)) return false

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, ReminderNotificationChannels.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun scheduleAppointmentReminder(
        context: Context,
        appointment: Appointment
    ) {
        val workName = "appt_reminder_${appointment.id}"
        val workManager = WorkManager.getInstance(context.applicationContext)

        if (!appointment.reminderEnabled || appointment.appointmentStatus.isTerminal) {
            workManager.cancelUniqueWork(workName)
            return
        }

        val triggerAtMillis = appointment.scheduledAtMillis - (appointment.reminderLeadMinutes * 60_000L)
        val delayMillis = triggerAtMillis - System.currentTimeMillis()
        if (delayMillis <= 0L) {
            return
        }

        val timeText = SimpleDateFormat("dd/MM 'às' HH:mm", Locale("pt", "BR"))
            .format(Date(appointment.scheduledAtMillis))

        val data = Data.Builder()
            .putString(
                ClinicalReminderWorker.KEY_TITLE,
                "Consulta em breve: ${appointment.patientName}"
            )
            .putString(
                ClinicalReminderWorker.KEY_MESSAGE,
                "Sessão (${appointment.appointmentType.label}) marcada para $timeText. Lembre-se de confirmar com ${appointment.guardianName.ifBlank { "o responsável" }}."
            )
            .putInt(ClinicalReminderWorker.KEY_NOTIFICATION_ID, appointment.id.hashCode())
            .build()

        val request = OneTimeWorkRequestBuilder<ClinicalReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()

        workManager.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelAppointmentReminder(context: Context, appointmentId: String) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork("appt_reminder_$appointmentId")
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

        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, patient.reminderHour.toInt().coerceIn(0, 23))
            set(Calendar.MINUTE, patient.reminderMinute.toInt().coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        val initialDelayMillis = (target.timeInMillis - now.timeInMillis).coerceAtLeast(60_000L)

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

    fun buildAppointmentGuardianMessage(
        appointment: Appointment,
        therapistName: String
    ): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR"))
        val dateStr = formatter.format(Date(appointment.scheduledAtMillis))
        val guardian = appointment.guardianName.ifBlank { "Responsável" }
        return buildString {
            appendLine("Olá, $guardian! Tudo bem?")
            appendLine("Aqui é $therapistName (Fonoaudiologia - FonoVida).")
            appendLine("Passando para lembrar da consulta de ${appointment.patientName} agendada para $dateStr (${appointment.appointmentType.label}).")
            if (appointment.notes.isNotBlank()) {
                appendLine("Observação: ${appointment.notes}")
            }
            append("Por favor, confirme o comparecimento respondendo esta mensagem. Obrigada!")
        }
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

    fun launchGuardianReminderShare(
        context: Context,
        phone: String,
        message: String
    ) {
        val digits = phone.filter { it.isDigit() }
        if (digits.length >= 10) {
            val normalized = if (digits.startsWith("55")) digits else "55$digits"
            val waIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/$normalized?text=${Uri.encode(message)}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(waIntent)
                return
            } catch (_: Exception) {
                // Fallback to general share sheet
            }
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        val chooser = Intent.createChooser(shareIntent, "Enviar lembrete ao responsável").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
