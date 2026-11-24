package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PatientAccessCode
import com.example.notifications.ReminderScheduler
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.viewmodel.PatientReminderSettings
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientPortalScreen(
    accessCode: PatientAccessCode,
    completedToday: Set<String>,
    reminderSettings: PatientReminderSettings,
    onToggleExerciseDone: (String) -> Unit,
    onUpdateReminderSettings: (Boolean, Int, Int) -> Unit,
    onSendTestReminder: () -> Unit,
    onChangeCode: () -> Unit,
    onSwitchRole: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasNotifPermission by remember {
        mutableStateOf(ReminderScheduler.hasNotificationPermission(context))
    }
    var pendingTestAfterPermission by remember { mutableStateOf(false) }
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotifPermission = granted
        if (granted) {
            onUpdateReminderSettings(
                true,
                reminderSettings.hour,
                reminderSettings.minute
            )
            if (pendingTestAfterPermission) {
                pendingTestAfterPermission = false
                onSendTestReminder()
                scope.launch {
                    snackbarHostState.showSnackbar("Lembrete agendado via WorkManager! Chegará em ~3s mesmo com o app fechado.")
                }
            } else {
                scope.launch {
                    snackbarHostState.showSnackbar("Notificações ativadas! Lembretes sincronizados.")
                }
            }
        } else {
            pendingTestAfterPermission = false
            scope.launch {
                snackbarHostState.showSnackbar("Permissão de notificação negada. Ative nas configurações do aparelho.")
            }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ReminderScheduler.hasNotificationPermission(context)
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val exercises = accessCode.exercisesSnapshot
    val doneCount = exercises.count { item ->
        val key = "${item.title.trim().lowercase()}|${item.category}"
        key in completedToday
    }
    val progress = if (exercises.isEmpty()) 0f else doneCount.toFloat() / exercises.size.toFloat()

    val formattedTime = String.format(
        Locale("pt", "BR"),
        "%02d:%02d",
        reminderSettings.hour,
        reminderSettings.minute
    )
    val hasFutureAppointment = accessCode.nextAppointmentMillis > System.currentTimeMillis()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "FonoVida • Paciente",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1
                        )
                        val subtitle = buildString {
                            append(accessCode.therapistName.ifBlank { "Fonoaudióloga" })
                            if (accessCode.clinicName.isNotBlank()) {
                                append(" • ${accessCode.clinicName}")
                            }
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onChangeCode,
                        modifier = Modifier.testTag("patient_portal_change_code_btn")
                    ) {
                        Icon(
                            Icons.Rounded.VpnKey,
                            contentDescription = "Trocar código de acesso"
                        )
                    }
                    IconButton(
                        onClick = onSignOut,
                        modifier = Modifier.testTag("patient_portal_sign_out_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Sair da conta",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxSize(),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.VerifiedUser,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = "Código ${accessCode.code}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(50)
                                ) {
                                    Text(
                                        text = "Meta: ${accessCode.weeklyGoalMinutes} min/sem",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Olá, ${accessCode.patientName.ifBlank { "Paciente" }}!",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (accessCode.clinicalFocus.isNotBlank()) {
                                Text(
                                    text = "Foco terapêutico: ${accessCode.clinicalFocus}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }

                if (accessCode.nextAppointmentSummary.isNotBlank()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(10.dp).size(22.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Próxima Consulta",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = accessCode.nextAppointmentSummary,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (hasFutureAppointment) {
                                        Text(
                                            text = "Alertas automáticos programados (24h e 1h antes)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (accessCode.recentProgressSummary.isNotBlank()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.GraphicEq,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(10.dp).size(22.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Evolução Clínica Recente",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = accessCode.recentProgressSummary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("patient_portal_reminders_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.NotificationsActive,
                                            contentDescription = "Lembretes automáticos",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(10.dp).size(20.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Lembretes Automáticos",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (reminderSettings.enabled) {
                                                "Aviso diário às $formattedTime (funciona com app fechado)"
                                            } else {
                                                "Lembrete diário de exercícios desativado"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = reminderSettings.enabled,
                                    onCheckedChange = { checked ->
                                        if (checked && !hasNotifPermission &&
                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                        ) {
                                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            onUpdateReminderSettings(
                                                checked,
                                                reminderSettings.hour,
                                                reminderSettings.minute
                                            )
                                            scope.launch {
                                                snackbarHostState.showSnackbar(
                                                    if (checked) "Lembrete diário programado para $formattedTime."
                                                    else "Lembrete diário pausado."
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("patient_portal_daily_reminder_switch")
                                )
                            }

                            if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                FilledTonalButton(
                                    onClick = {
                                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("patient_portal_grant_notif_btn")
                                ) {
                                    Text("Permitir Notificações neste Aparelho")
                                }
                            }

                            if (reminderSettings.enabled) {
                                Text(
                                    text = "Escolha o melhor horário para praticar:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf(8 to 0, 10 to 0, 14 to 0, 18 to 0, 20 to 0).forEach { (h, m) ->
                                        val isSelected = reminderSettings.hour == h && reminderSettings.minute == m
                                        val label = String.format(Locale("pt", "BR"), "%02d:%02d", h, m)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                onUpdateReminderSettings(true, h, m)
                                                scope.launch {
                                                    snackbarHostState.showSnackbar("Horário diário atualizado para $label.")
                                                }
                                            },
                                            label = { Text(label) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Rounded.Schedule,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                    FilterChip(
                                        selected = listOf(8, 10, 14, 18, 20).none {
                                            it == reminderSettings.hour && reminderSettings.minute == 0
                                        },
                                        onClick = { showTimePickerDialog = true },
                                        label = { Text("Outro ($formattedTime)") },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Rounded.Alarm,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("patient_portal_custom_time_chip")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (hasFutureAppointment) {
                                        "Consultas: avisos ativos (24h e 1h antes)"
                                    } else {
                                        "Consultas: aguardando novo agendamento"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        if (!hasNotifPermission &&
                                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                        ) {
                                            pendingTestAfterPermission = true
                                            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        } else {
                                            onSendTestReminder()
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Lembrete agendado via WorkManager! Chegará em ~3s mesmo com o app fechado.")
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .testTag("patient_portal_test_notif_btn")
                                ) {
                                    Text("Testar Lembrete")
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Prática Vocal de Hoje",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$doneCount de ${exercises.size} concluídos",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50))
                            )
                        }
                    }
                }

                item {
                    Text(
                        text = "Seus Exercícios de Voz (${exercises.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (exercises.isEmpty()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    text = "Nenhum exercício ativo no momento",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Assim que sua fonoaudióloga prescrever ou atualizar seus exercícios, eles aparecerão automaticamente aqui.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(
                        items = exercises,
                        key = { index, item -> "${index}_${item.title}_${item.category}" }
                    ) { index, item ->
                        val exKey = "${item.title.trim().lowercase()}|${item.category}"
                        val isDone = exKey in completedToday

                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDone) StatusSuccessBg.copy(alpha = 0.45f)
                                else MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(50)
                                    ) {
                                        Text(
                                            text = item.exerciseCategory.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = "${item.durationMinutes} min • ${item.frequencyPerWeek}x/sem",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                if (item.sourceLabel.isNotBlank()) {
                                    Text(
                                        text = item.sourceLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text(
                                    text = item.instructions,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (isDone) {
                                    Button(
                                        onClick = { onToggleExerciseDone(exKey) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = StatusSuccess,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("patient_exercise_done_$index")
                                    ) {
                                        Icon(
                                            Icons.Rounded.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Concluído hoje")
                                    }
                                } else {
                                    FilledTonalButton(
                                        onClick = { onToggleExerciseDone(exKey) },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("patient_exercise_done_$index")
                                    ) {
                                        Icon(
                                            Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Marcar como feito hoje")
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onChangeCode,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Inserir Outro Código")
                        }
                        OutlinedButton(
                            onClick = onSwitchRole,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text("Trocar Perfil")
                        }
                    }
                }
            }
        }

        if (showTimePickerDialog) {
            val timePickerState = rememberTimePickerState(
                initialHour = reminderSettings.hour,
                initialMinute = reminderSettings.minute,
                is24Hour = true
            )
            AlertDialog(
                onDismissRequest = { showTimePickerDialog = false },
                title = { Text("Horário do Lembrete Diário") },
                text = {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        TimePicker(state = timePickerState)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onUpdateReminderSettings(
                                true,
                                timePickerState.hour,
                                timePickerState.minute
                            )
                            val newLabel = String.format(
                                Locale("pt", "BR"),
                                "%02d:%02d",
                                timePickerState.hour,
                                timePickerState.minute
                            )
                            showTimePickerDialog = false
                            scope.launch {
                                snackbarHostState.showSnackbar("Lembrete diário programado para $newLabel.")
                            }
                        }
                    ) {
                        Text("Salvar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTimePickerDialog = false }) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}
