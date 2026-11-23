package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AgendaScreen(
    appointments: List<Appointment>,
    hasPatients: Boolean,
    onScheduleClick: () -> Unit,
    onOpenPatient: (String) -> Unit,
    onUpdateStatus: (Appointment, AppointmentStatus) -> Unit,
    onSendGuardianReminder: (Appointment) -> Unit,
    onDeleteAppointment: (String) -> Unit
) {
    var filterStatus by remember { mutableStateOf<AppointmentStatus?>(null) }
    val filtered = remember(appointments, filterStatus) {
        val list = if (filterStatus == null) appointments else appointments.filter { it.appointmentStatus == filterStatus }
        list.sortedBy { it.scheduledAtMillis }
    }
    val dateFmt = remember { SimpleDateFormat("EEE, dd/MM • HH:mm", Locale("pt", "BR")) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onScheduleClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_schedule_appointment")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Agendar consulta")
            }
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
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text("Agenda de Consultas", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "Gerencie sessões, lembretes automáticos e confirmações com os responsáveis.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = filterStatus == null,
                            onClick = { filterStatus = null },
                            label = { Text("Todas (${appointments.size})") }
                        )
                        AppointmentStatus.entries.forEach { st ->
                            val count = appointments.count { it.appointmentStatus == st }
                            FilterChip(
                                selected = filterStatus == st,
                                onClick = { filterStatus = if (filterStatus == st) null else st },
                                label = { Text("${st.label} ($count)") }
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item {
                        Card(
                            shape = MaterialTheme.shapes.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(44.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    text = if (!hasPatients) "Cadastre um paciente primeiro"
                                    else "Nenhuma consulta nesta visualização",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = if (!hasPatients)
                                        "Para agendar uma consulta e enviar lembretes aos responsáveis, cadastre um paciente na aba Pacientes."
                                    else "Toque no botão + para marcar um novo atendimento.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (hasPatients) {
                                    Spacer(Modifier.height(14.dp))
                                    Button(onClick = onScheduleClick) {
                                        Text("Agendar Consulta")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { appt ->
                        AppointmentCard(
                            appointment = appt,
                            dateLabel = dateFmt.format(Date(appt.scheduledAtMillis)),
                            onOpenPatient = { onOpenPatient(appt.patientId) },
                            onConfirm = { onUpdateStatus(appt, AppointmentStatus.CONFIRMADA) },
                            onComplete = { onUpdateStatus(appt, AppointmentStatus.CONCLUIDA) },
                            onCancel = { onUpdateStatus(appt, AppointmentStatus.CANCELADA) },
                            onSendReminder = { onSendGuardianReminder(appt) },
                            onDelete = { onDeleteAppointment(appt.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppointmentCard(
    appointment: Appointment,
    dateLabel: String,
    onOpenPatient: () -> Unit,
    onConfirm: () -> Unit,
    onComplete: () -> Unit,
    onCancel: () -> Unit,
    onSendReminder: () -> Unit,
    onDelete: () -> Unit
) {
    val sentFmt = remember { SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenPatient() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appointment.patientName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$dateLabel • ${appointment.durationMinutes} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${appointment.appointmentType.label} • Resp.: ${appointment.guardianName.ifBlank { "—" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(status = appointment.appointmentStatus)
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Remover consulta",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            if (appointment.notes.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = appointment.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (appointment.reminderEnabled && !appointment.appointmentStatus.isTerminal) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Lembrete automático (${appointment.reminderLeadMinutes} min antes)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (appointment.reminderSentMillis > 0L) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Aviso enviado ao responsável em ${sentFmt.format(Date(appointment.reminderSentMillis))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!appointment.appointmentStatus.isTerminal) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSendReminder,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Lembrar", maxLines = 1)
                    }

                    if (appointment.appointmentStatus == AppointmentStatus.AGENDADA) {
                        FilledTonalButton(
                            onClick = onConfirm,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.CheckCircleOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Confirmar", maxLines = 1)
                        }
                    } else {
                        FilledTonalButton(
                            onClick = onComplete,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Concluir", maxLines = 1)
                        }
                    }

                    IconButton(onClick = onCancel) {
                        Icon(
                            Icons.Rounded.Cancel,
                            contentDescription = "Cancelar consulta",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
