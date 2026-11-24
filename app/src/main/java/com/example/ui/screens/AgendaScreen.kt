package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.List
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.Appointment
import com.example.data.model.AppointmentStatus
import com.example.data.model.SlotBlock
import com.example.ui.components.BlockSlotDialog
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class AgendaViewMode {
    CALENDAR,
    LIST
}

@Composable
fun AgendaScreen(
    appointments: List<Appointment>,
    slotBlocks: List<SlotBlock>,
    hasPatients: Boolean,
    isBusy: Boolean,
    onScheduleClick: () -> Unit,
    onScheduleAtSlot: (dateMillis: Long, hour: Int, minute: Int) -> Unit,
    onBlockSlot: (dateKey: String, hour: Int, minute: Int, durationMinutes: Int, reason: String) -> Unit,
    onUnblockSlot: (blockId: String) -> Unit,
    onOpenPatient: (String) -> Unit,
    onUpdateStatus: (Appointment, AppointmentStatus) -> Unit,
    onSendGuardianReminder: (Appointment) -> Unit,
    onDeleteAppointment: (String) -> Unit
) {
    var viewMode by remember { mutableStateOf(AgendaViewMode.CALENDAR) }
    var filterStatus by remember { mutableStateOf<AppointmentStatus?>(null) }

    val dateKeyFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val selectedDayFmt = remember { SimpleDateFormat("EEEE, dd 'de' MMMM", Locale("pt", "BR")) }
    val monthYearFmt = remember { SimpleDateFormat("MMMM 'de' yyyy", Locale("pt", "BR")) }
    val dateFmt = remember { SimpleDateFormat("EEE, dd/MM • HH:mm", Locale("pt", "BR")) }

    val todayKey = remember { dateKeyFmt.format(Date()) }
    var currentMonthStartMillis by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }
    var selectedDateMillis by remember {
        mutableStateOf(
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        )
    }

    var blockDialogTarget by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val selectedDateKey = remember(selectedDateMillis) {
        dateKeyFmt.format(Date(selectedDateMillis))
    }

    val activeAppointmentsByDay = remember(appointments) {
        appointments
            .filter { it.appointmentStatus != AppointmentStatus.CANCELADA }
            .groupBy { dateKeyFmt.format(Date(it.scheduledAtMillis)) }
    }
    val blocksByDay = remember(slotBlocks) {
        slotBlocks.groupBy { it.dateKey }
    }

    val dayAppointments = remember(appointments, selectedDateKey) {
        appointments
            .filter { dateKeyFmt.format(Date(it.scheduledAtMillis)) == selectedDateKey }
            .sortedBy { it.scheduledAtMillis }
    }
    val dayBlocks = remember(blocksByDay, selectedDateKey) {
        blocksByDay[selectedDateKey].orEmpty()
    }

    val filteredListAppointments = remember(appointments, filterStatus) {
        val list = if (filterStatus == null) appointments else appointments.filter { it.appointmentStatus == filterStatus }
        list.sortedBy { it.scheduledAtMillis }
    }

    val dailyHours = remember { (8..19).toList() }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (viewMode == AgendaViewMode.CALENDAR) {
                        onScheduleAtSlot(selectedDateMillis, 10, 0)
                    } else {
                        onScheduleClick()
                    }
                },
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
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Agenda Integrada", style = MaterialTheme.typography.headlineMedium)
                        Text(
                            text = "Visualize o calendário, bloqueie horários ocupados e programe lembretes automáticos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = viewMode == AgendaViewMode.CALENDAR,
                                onClick = { viewMode = AgendaViewMode.CALENDAR },
                                leadingIcon = {
                                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("Calendário e Horários") },
                                modifier = Modifier.testTag("agenda_view_calendar")
                            )
                            FilterChip(
                                selected = viewMode == AgendaViewMode.LIST,
                                onClick = { viewMode = AgendaViewMode.LIST },
                                leadingIcon = {
                                    Icon(Icons.Rounded.List, contentDescription = null, modifier = Modifier.size(16.dp))
                                },
                                label = { Text("Lista (${appointments.size})") },
                                modifier = Modifier.testTag("agenda_view_list")
                            )
                        }
                    }
                }

                if (viewMode == AgendaViewMode.CALENDAR) {
                    item {
                        MonthlyCalendarCard(
                            monthStartMillis = currentMonthStartMillis,
                            selectedDateKey = selectedDateKey,
                            todayKey = todayKey,
                            monthTitle = monthYearFmt.format(Date(currentMonthStartMillis))
                                .replaceFirstChar { it.titlecase(Locale("pt", "BR")) },
                            appointmentDays = activeAppointmentsByDay.keys,
                            blockedDays = blocksByDay.keys,
                            dateKeyFmt = dateKeyFmt,
                            onPrevMonth = {
                                currentMonthStartMillis = Calendar.getInstance().apply {
                                    timeInMillis = currentMonthStartMillis
                                    add(Calendar.MONTH, -1)
                                }.timeInMillis
                            },
                            onNextMonth = {
                                currentMonthStartMillis = Calendar.getInstance().apply {
                                    timeInMillis = currentMonthStartMillis
                                    add(Calendar.MONTH, 1)
                                }.timeInMillis
                            },
                            onSelectDate = { millis ->
                                selectedDateMillis = millis
                            }
                        )
                    }

                    item {
                        val activeCount = dayAppointments.count { it.appointmentStatus != AppointmentStatus.CANCELADA }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedDayFmt.format(Date(selectedDateMillis))
                                        .replaceFirstChar { it.titlecase(Locale("pt", "BR")) },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$activeCount consulta(s) • ${dayBlocks.size} horário(s) ocupado(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Out-of-hours appointments (before 08:00 or >= 20:00)
                    val outOfRangeAppts = dayAppointments.filter { appt ->
                        val h = Calendar.getInstance().apply { timeInMillis = appt.scheduledAtMillis }.get(Calendar.HOUR_OF_DAY)
                        h !in 8..19
                    }
                    if (outOfRangeAppts.isNotEmpty()) {
                        items(outOfRangeAppts, key = { "oor_${it.id}" }) { appt ->
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

                    items(dailyHours, key = { "hour_$it" }) { hour ->
                        val slotLabel = String.format(Locale("pt", "BR"), "%02d:00", hour)
                        val hourAppointments = dayAppointments.filter { appt ->
                            val cal = Calendar.getInstance().apply { timeInMillis = appt.scheduledAtMillis }
                            cal.get(Calendar.HOUR_OF_DAY) == hour && appt.appointmentStatus != AppointmentStatus.CANCELADA
                        }
                        val coveringBlock = dayBlocks.firstOrNull { block ->
                            val startMin = block.hour * 60 + block.minute
                            val endMin = startMin + block.durationMinutes
                            val slotStartMin = hour * 60L
                            val slotEndMin = (hour + 1) * 60L
                            slotStartMin < endMin && slotEndMin > startMin
                        }

                        if (hourAppointments.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = slotLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                hourAppointments.forEach { appt ->
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
                        } else if (coveringBlock != null) {
                            BlockedSlotCard(
                                slotLabel = slotLabel,
                                block = coveringBlock,
                                onMarkAvailable = { onUnblockSlot(coveringBlock.id) }
                            )
                        } else {
                            AvailableSlotRow(
                                slotLabel = slotLabel,
                                hour = hour,
                                onScheduleHere = {
                                    if (!hasPatients) onScheduleClick()
                                    else onScheduleAtSlot(selectedDateMillis, hour, 0)
                                },
                                onMarkOccupied = {
                                    blockDialogTarget = hour to 0
                                }
                            )
                        }
                    }
                } else {
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

                    if (filteredListAppointments.isEmpty()) {
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
                        items(filteredListAppointments, key = { it.id }) { appt ->
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

    blockDialogTarget?.let { (hour, minute) ->
        val dateLabel = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(Date(selectedDateMillis))
        BlockSlotDialog(
            dateLabel = dateLabel,
            hour = hour,
            minute = minute,
            isBusy = isBusy,
            onDismiss = { blockDialogTarget = null },
            onConfirm = { dur, reason ->
                onBlockSlot(selectedDateKey, hour, minute, dur, reason)
                blockDialogTarget = null
            }
        )
    }
}

@Composable
private fun MonthlyCalendarCard(
    monthStartMillis: Long,
    selectedDateKey: String,
    todayKey: String,
    monthTitle: String,
    appointmentDays: Set<String>,
    blockedDays: Set<String>,
    dateKeyFmt: SimpleDateFormat,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDate: (Long) -> Unit
) {
    val cells = remember(monthStartMillis) {
        val cal = Calendar.getInstance().apply { timeInMillis = monthStartMillis }
        val firstDow = cal.get(Calendar.DAY_OF_WEEK) // 1=Sun..7=Sat
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val leadingEmpty = firstDow - Calendar.SUNDAY
        val list = mutableListOf<Long?>()
        repeat(leadingEmpty) { list.add(null) }
        for (day in 1..daysInMonth) {
            val dayCal = Calendar.getInstance().apply {
                timeInMillis = monthStartMillis
                set(Calendar.DAY_OF_MONTH, day)
            }
            list.add(dayCal.timeInMillis)
        }
        while (list.size % 7 != 0) list.add(null)
        list.chunked(7)
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevMonth) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Mês anterior"
                    )
                }
                Text(
                    text = monthTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onNextMonth) {
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Próximo mês"
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("D", "S", "T", "Q", "Q", "S", "S").forEach { dow ->
                    Text(
                        text = dow,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            cells.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    week.forEach { dayMillis ->
                        if (dayMillis == null) {
                            Spacer(modifier = Modifier.weight(1f).height(48.dp))
                        } else {
                            val key = dateKeyFmt.format(Date(dayMillis))
                            val dayNum = Calendar.getInstance().apply { timeInMillis = dayMillis }
                                .get(Calendar.DAY_OF_MONTH)
                            val isSelected = key == selectedDateKey
                            val isToday = key == todayKey
                            val hasAppt = appointmentDays.contains(key)
                            val hasBlock = blockedDays.contains(key)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                            else -> MaterialTheme.colorScheme.surface
                                        }
                                    )
                                    .then(
                                        if (isToday && !isSelected) {
                                            Modifier.border(
                                                1.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(12.dp)
                                            )
                                        } else Modifier
                                    )
                                    .clickable { onSelectDate(dayMillis) }
                                    .testTag("calendar_day_$key"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = dayNum.toString(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        modifier = Modifier.height(6.dp)
                                    ) {
                                        if (hasAppt) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                        else MaterialTheme.colorScheme.primary
                                                    )
                                            )
                                        }
                                        if (hasBlock) {
                                            Box(
                                                modifier = Modifier
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                                        else MaterialTheme.colorScheme.secondary
                                                    )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Consulta", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text("Horário ocupado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun AvailableSlotRow(
    slotLabel: String,
    hour: Int,
    onScheduleHere: () -> Unit,
    onMarkOccupied: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = slotLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(10.dp))
                Surface(
                    color = StatusSuccessBg,
                    shape = RoundedCornerShape(50)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.EventAvailable,
                            contentDescription = null,
                            tint = StatusSuccess,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Disponível",
                            style = MaterialTheme.typography.labelSmall,
                            color = StatusSuccess,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = onMarkOccupied,
                    modifier = Modifier.testTag("block_slot_$hour")
                ) {
                    Text("Ocupar")
                }
                FilledTonalButton(
                    onClick = onScheduleHere,
                    modifier = Modifier.testTag("schedule_slot_$hour")
                ) {
                    Text("Agendar")
                }
            }
        }
    }
}

@Composable
private fun BlockedSlotCard(
    slotLabel: String,
    block: SlotBlock,
    onMarkAvailable: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = StatusWarningBg.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Block,
                    contentDescription = null,
                    tint = StatusWarning,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "$slotLabel • Ocupado (${block.durationMinutes} min)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = StatusWarning
                    )
                    Text(
                        text = block.reason.ifBlank { "Horário indisponível" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            OutlinedButton(
                onClick = onMarkAvailable,
                modifier = Modifier.testTag("unblock_slot_${block.hour}")
            ) {
                Text("Liberar")
            }
        }
    }
}

private fun formatLeadTime(minutes: Long): String = when {
    minutes >= 1440L -> "${minutes / 1440L}d"
    minutes >= 60L -> "${minutes / 60L}h"
    else -> "${minutes}m"
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

            if ((appointment.reminderEnabled || appointment.patientReminderEnabled) &&
                !appointment.appointmentStatus.isTerminal
            ) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (appointment.reminderEnabled) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Fono ${formatLeadTime(appointment.reminderLeadMinutes)} antes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    if (appointment.patientReminderEnabled) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "WhatsApp Resp. ${formatLeadTime(appointment.patientReminderLeadMinutes)} antes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }
                }
            }

            if (appointment.reminderSentMillis > 0L) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Aviso WhatsApp enviado ao responsável em ${sentFmt.format(Date(appointment.reminderSentMillis))}",
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
                        modifier = Modifier
                            .weight(1f)
                            .testTag("agenda_whatsapp_reminder_${appointment.id}")
                    ) {
                        Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("WhatsApp", maxLines = 1)
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
