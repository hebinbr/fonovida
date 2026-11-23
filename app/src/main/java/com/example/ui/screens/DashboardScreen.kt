package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PeopleOutline
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import com.example.data.model.EvolutionSession
import com.example.data.model.PatientStatus
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.viewmodel.ClinicData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    data: ClinicData,
    fallbackName: String,
    onOpenPatient: (String) -> Unit,
    onOpenPatientsTab: () -> Unit,
    onOpenAgendaTab: () -> Unit,
    onAddPatientClick: () -> Unit,
    onScheduleAppointmentClick: () -> Unit,
    onSendAppointmentReminder: (Appointment) -> Unit,
    onConfirmAppointment: (Appointment) -> Unit
) {
    val therapistName = data.profile?.displayName?.ifBlank { fallbackName } ?: fallbackName
    val activePatients = remember(data.patients) {
        data.patients.count { it.patientStatus == PatientStatus.ATIVO }
    }
    val upcomingAppointments = remember(data.appointments) {
        val now = System.currentTimeMillis() - 3600_000L
        data.appointments.filter {
            !it.appointmentStatus.isTerminal && it.scheduledAtMillis >= now
        }.sortedBy { it.scheduledAtMillis }
    }
    val avgClarity = remember(data.sessions) {
        if (data.sessions.isEmpty()) 0 else data.sessions.map { it.vocalClarityScore }.average().toInt()
    }
    val recentSessions = remember(data.sessions) {
        data.sessions.sortedByDescending { it.sessionDateMillis }.take(4)
    }

    val dateTimeFmt = remember { SimpleDateFormat("dd/MM 'às' HH:mm", Locale("pt", "BR")) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                // Hero Greeting Card
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(
                                text = "Olá, $therapistName",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = data.profile?.clinicName?.ifBlank { "Painel Clínico FonoVida" }
                                    ?: "Painel Clínico FonoVida",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = onAddPatientClick,
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onPrimary,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("dashboard_add_patient_btn")
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Novo Paciente", maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = onScheduleAppointmentClick,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f).testTag("dashboard_schedule_btn")
                                ) {
                                    Icon(Icons.Rounded.CalendarToday, contentDescription = null, modifier = Modifier.size(17.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Agendar", maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            // KPI Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DashboardKpiCard(
                        icon = Icons.Rounded.PeopleOutline,
                        label = "Pacientes Ativos",
                        value = "$activePatients",
                        accent = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenPatientsTab
                    )
                    DashboardKpiCard(
                        icon = Icons.Rounded.CalendarToday,
                        label = "Próximas Consultas",
                        value = "${upcomingAppointments.size}",
                        accent = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenAgendaTab
                    )
                    DashboardKpiCard(
                        icon = Icons.Rounded.GraphicEq,
                        label = "Clareza Média",
                        value = if (data.sessions.isEmpty()) "—" else "$avgClarity%",
                        accent = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.weight(1f),
                        onClick = onOpenPatientsTab
                    )
                }
            }

            // Upcoming Appointments Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Próximos Atendimentos",
                        style = MaterialTheme.typography.titleLarge
                    )
                    if (upcomingAppointments.isNotEmpty()) {
                        Text(
                            text = "Ver agenda",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { onOpenAgendaTab() }.padding(4.dp)
                        )
                    }
                }
            }

            if (upcomingAppointments.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Nenhuma consulta agendada",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Agende consultas para ativar lembretes automáticos para você e enviar avisos aos responsáveis.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = onScheduleAppointmentClick) {
                                Text("Agendar primeira consulta")
                            }
                        }
                    }
                }
            } else {
                items(upcomingAppointments.take(3), key = { it.id }) { appt ->
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenPatient(appt.patientId) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = appt.patientName,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "${dateTimeFmt.format(Date(appt.scheduledAtMillis))} • ${appt.appointmentType.label}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                StatusChip(status = appt.appointmentStatus)
                            }

                            if (appt.reminderEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Lembrete automático armado (${appt.reminderLeadMinutes} min antes)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { onSendAppointmentReminder(appt) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Lembrar Resp.", maxLines = 1)
                                }
                                if (appt.appointmentStatus == AppointmentStatus.AGENDADA) {
                                    FilledTonalButton(
                                        onClick = { onConfirmAppointment(appt) },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Confirmar", maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Recent Clinical Sessions
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Evoluções Recentes",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            if (recentSessions.isEmpty()) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "As sessões de exercícios de voz registradas nos prontuários dos pacientes aparecerão aqui.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(recentSessions, key = { it.id }) { session ->
                    RecentSessionCard(
                        session = session,
                        dateLabel = dateTimeFmt.format(Date(session.sessionDateMillis)),
                        onClick = { onOpenPatient(session.patientId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardKpiCard(
    icon: ImageVector,
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
fun StatusChip(status: AppointmentStatus) {
    val (bg, fg) = when (status) {
        AppointmentStatus.CONFIRMADA -> StatusSuccessBg to StatusSuccess
        AppointmentStatus.AGENDADA -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        AppointmentStatus.CONCLUIDA -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        AppointmentStatus.CANCELADA -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    Surface(color = bg, shape = RoundedCornerShape(50)) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun RecentSessionCard(
    session: EvolutionSession,
    dateLabel: String,
    onClick: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(46.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${session.vocalClarityScore}%",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(session.patientName, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "${session.exerciseTitle} • $dateLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = "Abrir prontuário",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
