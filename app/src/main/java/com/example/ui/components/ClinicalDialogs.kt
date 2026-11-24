package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.sp
import com.example.data.model.Appointment
import com.example.data.model.AppointmentType
import com.example.data.model.CuratedProtocolPresets
import com.example.data.model.CuratedVoiceExerciseTemplates
import com.example.data.model.CustomExerciseList
import com.example.data.model.ExerciseCategory
import com.example.data.model.ExerciseListItem
import com.example.data.model.Patient
import com.example.data.model.PatientAccessCode
import com.example.data.model.PatientStatus
import com.example.data.model.SlotBlock
import com.example.data.model.TherapistProfile
import com.example.notifications.AppointmentReminderTemplateDefaults
import com.example.notifications.ReminderScheduler
import com.example.ui.theme.StatusDanger
import com.example.ui.theme.StatusDangerBg
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PatientFormDialog(
    initialPatient: Patient? = null,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        fullName: String,
        ageYears: Int,
        clinicalFocus: String,
        guardianName: String,
        guardianPhone: String,
        guardianEmail: String,
        status: PatientStatus,
        weeklyGoalMinutes: Int,
        weeklyExerciseFrequencyGoal: Int,
        initialNotes: String,
        dailyReminder: Boolean,
        reminderHour: Int,
        reminderMinute: Int
    ) -> Unit
) {
    var fullName by remember { mutableStateOf(initialPatient?.fullName ?: "") }
    var ageText by remember { mutableStateOf((initialPatient?.ageYears ?: 8L).toString()) }
    var clinicalFocus by remember { mutableStateOf(initialPatient?.clinicalFocus ?: "") }
    var guardianName by remember { mutableStateOf(initialPatient?.guardianName ?: "") }
    var guardianPhone by remember { mutableStateOf(initialPatient?.guardianPhone ?: "") }
    var guardianEmail by remember { mutableStateOf(initialPatient?.guardianEmail ?: "") }
    var status by remember { mutableStateOf(initialPatient?.patientStatus ?: PatientStatus.ATIVO) }
    var weeklyGoal by remember { mutableStateOf((initialPatient?.weeklyGoalMinutes ?: 60L).toString()) }
    var weeklyFrequencyText by remember { mutableStateOf((initialPatient?.effectiveWeeklyFrequencyGoal ?: 5).toString()) }
    var notes by remember { mutableStateOf(initialPatient?.initialNotes ?: "") }
    var dailyReminder by remember { mutableStateOf(initialPatient?.dailyExerciseReminderEnabled ?: true) }
    var reminderHour by remember { mutableIntStateOf((initialPatient?.reminderHour ?: 18L).toInt()) }
    var reminderMinute by remember { mutableIntStateOf((initialPatient?.reminderMinute ?: 0L).toInt()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Text(
                text = if (initialPatient == null) "Cadastrar Paciente" else "Editar Paciente",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it; errorMsg = null },
                    label = { Text("Nome completo do paciente *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("patient_name_input")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = ageText,
                        onValueChange = { ageText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Idade *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = weeklyGoal,
                        onValueChange = { weeklyGoal = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Meta min/sem") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1.3f)
                    )
                }
                OutlinedTextField(
                    value = weeklyFrequencyText,
                    onValueChange = { weeklyFrequencyText = it.filter { ch -> ch.isDigit() }.take(2) },
                    label = { Text("Meta de frequência semanal (exercícios/sem)") },
                    placeholder = { Text("Ex.: 5") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("patient_frequency_goal_input")
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(3, 5, 7, 10, 14).forEach { preset ->
                        FilterChip(
                            selected = weeklyFrequencyText.toIntOrNull() == preset,
                            onClick = { weeklyFrequencyText = preset.toString() },
                            label = { Text("${preset}x/sem") }
                        )
                    }
                }
                OutlinedTextField(
                    value = clinicalFocus,
                    onValueChange = { clinicalFocus = it; errorMsg = null },
                    label = { Text("Foco clínico / Diagnóstico *") },
                    placeholder = { Text("Ex.: Disfonia infantil, Nódulos vocais") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("patient_focus_input")
                )
                OutlinedTextField(
                    value = guardianName,
                    onValueChange = { guardianName = it; errorMsg = null },
                    label = { Text("Nome do responsável *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("patient_guardian_input")
                )
                OutlinedTextField(
                    value = guardianPhone,
                    onValueChange = { guardianPhone = it },
                    label = { Text("WhatsApp / Telefone do responsável") },
                    placeholder = { Text("11999998888") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = guardianEmail,
                    onValueChange = { guardianEmail = it },
                    label = { Text("E-mail do responsável") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (initialPatient != null) {
                    Text(
                        text = "Status do Acompanhamento",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PatientStatus.entries.forEach { st ->
                            FilterChip(
                                selected = status == st,
                                onClick = { status = st },
                                label = { Text(st.label) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações iniciais / Anamnese") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lembrete diário de treino vocal",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = String.format(Locale("pt", "BR"), "Notificação às %02d:%02d", reminderHour, reminderMinute),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = dailyReminder, onCheckedChange = { dailyReminder = it })
                }

                if (dailyReminder) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(9 to 0, 14 to 0, 18 to 0, 20 to 0).forEach { (h, m) ->
                            FilterChip(
                                selected = reminderHour == h && reminderMinute == m,
                                onClick = { reminderHour = h; reminderMinute = m },
                                label = { Text(String.format(Locale("pt", "BR"), "%02d:%02d", h, m)) }
                            )
                        }
                    }
                }

                errorMsg?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val age = ageText.toIntOrNull() ?: 0
                    val goal = weeklyGoal.toIntOrNull() ?: 60
                    val freqGoal = weeklyFrequencyText.toIntOrNull() ?: 0
                    when {
                        fullName.trim().length < 2 -> errorMsg = "Informe o nome do paciente."
                        age !in 1..120 -> errorMsg = "Idade válida entre 1 e 120."
                        freqGoal !in 1..21 -> errorMsg = "Meta de frequência semanal entre 1 e 21 exercícios."
                        clinicalFocus.trim().length < 2 -> errorMsg = "Informe o foco clínico."
                        guardianName.trim().length < 2 -> errorMsg = "Informe o nome do responsável."
                        else -> onConfirm(
                            fullName,
                            age,
                            clinicalFocus,
                            guardianName,
                            guardianPhone,
                            guardianEmail,
                            status,
                            goal.coerceIn(10, 1000),
                            freqGoal.coerceIn(1, 21),
                            notes,
                            dailyReminder,
                            reminderHour,
                            reminderMinute
                        )
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("save_patient_button")
            ) {
                Text(if (isBusy) "Salvando..." else "Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun PrescribeExerciseDialog(
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        category: ExerciseCategory,
        instructions: String,
        durationMinutes: Int,
        frequencyPerWeek: Int
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(ExerciseCategory.AQUECIMENTO) }
    var instructions by remember { mutableStateOf("") }
    var durationText by remember { mutableStateOf("10") }
    var frequencyText by remember { mutableStateOf("5") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Prescrever Exercício de Voz", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Modelos Clínicos Rápidos",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CuratedVoiceExerciseTemplates.templates.forEach { tpl ->
                        FilterChip(
                            selected = title == tpl.title,
                            onClick = {
                                title = tpl.title
                                category = tpl.category
                                instructions = tpl.instructions
                                durationText = tpl.targetDurationMinutes.toString()
                                frequencyText = tpl.frequencyPerWeek.toString()
                                errorMsg = null
                            },
                            label = { Text(tpl.title.substringBefore(" (")) }
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; errorMsg = null },
                    label = { Text("Título do exercício *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("exercise_title_input")
                )

                Text(
                    text = "Categoria",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExerciseCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it; errorMsg = null },
                    label = { Text("Instruções técnicas *") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Duração (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = frequencyText,
                        onValueChange = { frequencyText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Vezes / sem") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dur = (durationText.toIntOrNull() ?: 10).coerceIn(1, 120)
                    val freq = (frequencyText.toIntOrNull() ?: 5).coerceIn(1, 21)
                    if (title.trim().length < 2 || instructions.trim().length < 2) {
                        errorMsg = "Preencha o título e as instruções."
                    } else {
                        onConfirm(title, category, instructions, dur, freq)
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("save_exercise_button")
            ) {
                Text(if (isBusy) "Salvando..." else "Prescrever")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancelar") }
        }
    )
}

@Composable
fun LogSessionDialog(
    patientName: String,
    prefilledTitle: String = "",
    prefilledCategory: ExerciseCategory = ExerciseCategory.AQUECIMENTO,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        exerciseTitle: String,
        category: ExerciseCategory,
        durationMinutes: Int,
        clarityScore: Int,
        stabilityScore: Int,
        comfortScore: Int,
        tmfSeconds: Double,
        clinicalNotes: String,
        homeGuidance: String
    ) -> Unit
) {
    var title by remember { mutableStateOf(prefilledTitle) }
    var category by remember { mutableStateOf(prefilledCategory) }
    var durationText by remember { mutableStateOf("15") }
    var clarity by remember { mutableFloatStateOf(78f) }
    var stability by remember { mutableFloatStateOf(74f) }
    var comfort by remember { mutableFloatStateOf(82f) }
    var tmfText by remember { mutableStateOf("12.5") }
    var notes by remember { mutableStateOf("") }
    var guidance by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Column {
                Text("Registrar Sessão de Voz", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = patientName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; errorMsg = null },
                    label = { Text("Exercício realizado *") },
                    placeholder = { Text("Ex.: Vibração de lábios + /m/ sustentado") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("session_exercise_input")
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExerciseCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = durationText,
                        onValueChange = { durationText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Duração (min)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = tmfText,
                        onValueChange = { tmfText = it.replace(',', '.') },
                        label = { Text("TMF (seg)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                MetricSlider(
                    label = "Clareza Vocal",
                    value = clarity,
                    onValueChange = { clarity = it }
                )
                MetricSlider(
                    label = "Estabilidade Respiratória",
                    value = stability,
                    onValueChange = { stability = it }
                )
                MetricSlider(
                    label = "Conforto Laríngeo",
                    value = comfort,
                    onValueChange = { comfort = it }
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Observações clínicas da evolução") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = guidance,
                    onValueChange = { guidance = it },
                    label = { Text("Orientações para os responsáveis (em casa)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val dur = (durationText.toIntOrNull() ?: 15).coerceIn(1, 240)
                    val tmf = (tmfText.toDoubleOrNull() ?: 10.0).coerceIn(0.0, 120.0)
                    if (title.trim().length < 2) {
                        errorMsg = "Informe o nome do exercício realizado."
                    } else {
                        onConfirm(
                            title,
                            category,
                            dur,
                            clarity.roundToInt(),
                            stability.roundToInt(),
                            comfort.roundToInt(),
                            tmf,
                            notes,
                            guidance
                        )
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("save_session_button")
            ) {
                Text(if (isBusy) "Salvando..." else "Registrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancelar") }
        }
    )
}

@Composable
private fun MetricSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                "${value.roundToInt()}%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..100f
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleAppointmentDialog(
    patients: List<Patient>,
    defaultLeadMinutes: Long,
    initialDateMillis: Long? = null,
    initialHour: Int? = null,
    initialMinute: Int? = null,
    existingAppointments: List<Appointment> = emptyList(),
    slotBlocks: List<SlotBlock> = emptyList(),
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        patient: Patient,
        scheduledAtMillis: Long,
        durationMinutes: Int,
        sessionType: AppointmentType,
        notes: String,
        reminderEnabled: Boolean,
        reminderLeadMinutes: Long,
        patientReminderEnabled: Boolean,
        patientReminderLeadMinutes: Long
    ) -> Unit
) {
    var selectedPatient by remember { mutableStateOf(patients.firstOrNull()) }
    var patientDropdownOpen by remember { mutableStateOf(false) }

    val defaultCal = remember(initialDateMillis, initialHour, initialMinute) {
        Calendar.getInstance().apply {
            if (initialDateMillis != null) {
                timeInMillis = initialDateMillis
            } else {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            set(Calendar.HOUR_OF_DAY, initialHour ?: 10)
            set(Calendar.MINUTE, initialMinute ?: 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    var selectedDateMillis by remember(defaultCal) { mutableLongStateOf(defaultCal.timeInMillis) }
    var selectedHour by remember(defaultCal) { mutableIntStateOf(defaultCal.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember(defaultCal) { mutableIntStateOf(defaultCal.get(Calendar.MINUTE)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var durationMinutes by remember { mutableIntStateOf(45) }
    var sessionType by remember { mutableStateOf(AppointmentType.PRESENCIAL) }
    var notes by remember { mutableStateOf("") }
    var reminderEnabled by remember { mutableStateOf(true) }
    var reminderLeadMinutes by remember { mutableLongStateOf(defaultLeadMinutes) }
    var patientReminderEnabled by remember { mutableStateOf(true) }
    var patientReminderLeadMinutes by remember { mutableLongStateOf(1440L) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }
    val dateKeyFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale("pt", "BR")) }

    val conflictMessage = remember(
        selectedDateMillis,
        selectedHour,
        selectedMinute,
        durationMinutes,
        existingAppointments,
        slotBlocks
    ) {
        val proposedCal = Calendar.getInstance().apply {
            timeInMillis = selectedDateMillis
            set(Calendar.HOUR_OF_DAY, selectedHour)
            set(Calendar.MINUTE, selectedMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val proposedStart = proposedCal.timeInMillis
        val proposedEnd = proposedStart + durationMinutes * 60_000L
        val proposedDateKey = dateKeyFormatter.format(Date(proposedStart))

        val conflictingAppt = existingAppointments.firstOrNull { appt ->
            if (appt.appointmentStatus.isTerminal) return@firstOrNull false
            val apptStart = appt.scheduledAtMillis
            val apptEnd = apptStart + appt.durationMinutes * 60_000L
            proposedStart < apptEnd && proposedEnd > apptStart
        }
        if (conflictingAppt != null) {
            return@remember "Conflito com consulta de ${conflictingAppt.patientName} às ${timeFormatter.format(Date(conflictingAppt.scheduledAtMillis))}."
        }

        val conflictingBlock = slotBlocks.firstOrNull { block ->
            if (block.dateKey != proposedDateKey) return@firstOrNull false
            val blockCal = Calendar.getInstance().apply {
                timeInMillis = proposedStart
                set(Calendar.HOUR_OF_DAY, block.hour.toInt())
                set(Calendar.MINUTE, block.minute.toInt())
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val blockStart = blockCal.timeInMillis
            val blockEnd = blockStart + block.durationMinutes * 60_000L
            proposedStart < blockEnd && proposedEnd > blockStart
        }
        conflictingBlock?.let {
            "Horário bloqueado como ocupado (${it.reason.ifBlank { "Indisponível" }})."
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Agendar Consulta", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = patientDropdownOpen,
                    onExpandedChange = { patientDropdownOpen = !patientDropdownOpen }
                ) {
                    OutlinedTextField(
                        value = selectedPatient?.fullName ?: "Selecione um paciente",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Paciente *") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = patientDropdownOpen)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = patientDropdownOpen,
                        onDismissRequest = { patientDropdownOpen = false }
                    ) {
                        patients.forEach { pat ->
                            DropdownMenuItem(
                                text = { Text("${pat.fullName} (${pat.clinicalFocus})") },
                                onClick = {
                                    selectedPatient = pat
                                    patientDropdownOpen = false
                                    errorMsg = null
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(dateFormatter.format(Date(selectedDateMillis)))
                    }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(String.format(Locale("pt", "BR"), "%02d:%02d", selectedHour, selectedMinute))
                    }
                }

                if (conflictMessage != null) {
                    Surface(
                        color = StatusDangerBg,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = conflictMessage,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = StatusDanger,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Text("Tipo de Sessão", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppointmentType.entries.forEach { type ->
                        FilterChip(
                            selected = sessionType == type,
                            onClick = { sessionType = type },
                            label = { Text(type.label) }
                        )
                    }
                }

                Text("Duração", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 45, 60).forEach { dur ->
                        FilterChip(
                            selected = durationMinutes == dur,
                            onClick = { durationMinutes = dur },
                            label = { Text("$dur min") }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas / Objetivo da consulta") },
                    placeholder = { Text("Ex.: Reavaliação de TMF e ajuste do plano vocal") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Lembrete para a Fonoaudióloga", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Notificação local antes da consulta",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = reminderEnabled,
                                onCheckedChange = { reminderEnabled = it },
                                modifier = Modifier.testTag("switch_therapist_reminder")
                            )
                        }

                        if (reminderEnabled) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(30L to "30 min", 60L to "1 hora", 1440L to "24 horas").forEach { (mins, lbl) ->
                                    FilterChip(
                                        selected = reminderLeadMinutes == mins,
                                        onClick = { reminderLeadMinutes = mins },
                                        label = { Text(lbl) }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Lembrete para Paciente / Resp.", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Notificação automática + envio no WhatsApp (wa.me)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = patientReminderEnabled,
                                onCheckedChange = { patientReminderEnabled = it },
                                modifier = Modifier.testTag("switch_patient_reminder")
                            )
                        }

                        if (patientReminderEnabled) {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(60L to "1 hora", 720L to "12 horas", 1440L to "24 horas").forEach { (mins, lbl) ->
                                    FilterChip(
                                        selected = patientReminderLeadMinutes == mins,
                                        onClick = { patientReminderLeadMinutes = mins },
                                        label = { Text(lbl) }
                                    )
                                }
                            }
                        }
                    }
                }

                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val patient = selectedPatient
                    if (patient == null) {
                        errorMsg = "Cadastre ou selecione um paciente."
                        return@Button
                    }
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = selectedDateMillis
                        set(Calendar.HOUR_OF_DAY, selectedHour)
                        set(Calendar.MINUTE, selectedMinute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onConfirm(
                        patient,
                        cal.timeInMillis,
                        durationMinutes,
                        sessionType,
                        notes,
                        reminderEnabled,
                        reminderLeadMinutes,
                        patientReminderEnabled,
                        patientReminderLeadMinutes
                    )
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("save_appointment_button")
            ) {
                Text(if (isBusy) "Agendando..." else "Agendar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancelar") }
        }
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = utcMillis
                            }
                            val localCal = Calendar.getInstance().apply {
                                set(
                                    utcCal.get(Calendar.YEAR),
                                    utcCal.get(Calendar.MONTH),
                                    utcCal.get(Calendar.DAY_OF_MONTH)
                                )
                            }
                            selectedDateMillis = localCal.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Horário da Consulta") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedHour = timeState.hour
                        selectedMinute = timeState.minute
                        showTimePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun BlockSlotDialog(
    dateLabel: String,
    hour: Int,
    minute: Int,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (durationMinutes: Int, reason: String) -> Unit
) {
    var reason by remember { mutableStateOf("Horário ocupado") }
    var durationMinutes by remember { mutableIntStateOf(60) }
    val presets = remember { listOf("Horário ocupado", "Almoço", "Reunião clínica", "Indisponível") }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Column {
                Text("Marcar Horário como Ocupado", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = String.format(Locale("pt", "BR"), "%s • %02d:%02d", dateLabel, hour, minute),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Motivo rápido", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { preset ->
                        FilterChip(
                            selected = reason == preset,
                            onClick = { reason = preset },
                            label = { Text(preset) }
                        )
                    }
                }
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo do bloqueio") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("block_slot_reason_input")
                )
                Text("Duração do bloqueio", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(30, 60, 90, 120).forEach { dur ->
                        FilterChip(
                            selected = durationMinutes == dur,
                            onClick = { durationMinutes = dur },
                            label = { Text("$dur min") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(durationMinutes, reason.ifBlank { "Horário ocupado" }) },
                enabled = !isBusy,
                modifier = Modifier.testTag("confirm_block_slot_button")
            ) {
                Text("Bloquear Horário")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileDialog(
    profile: TherapistProfile?,
    fallbackName: String,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        displayName: String,
        crfa: String,
        clinicName: String,
        defaultReminderMinutes: Long,
        autoRemindersEnabled: Boolean,
        customReminderTemplate: String
    ) -> Unit
) {
    var displayName by remember { mutableStateOf(profile?.displayName?.ifBlank { fallbackName } ?: fallbackName) }
    var crfa by remember { mutableStateOf(profile?.crfa ?: "") }
    var clinicName by remember { mutableStateOf(profile?.clinicName ?: "Consultório de Fonoaudiologia") }
    var defaultLead by remember { mutableLongStateOf(profile?.defaultReminderMinutes ?: 60L) }
    var autoReminders by remember { mutableStateOf(profile?.autoRemindersEnabled ?: true) }
    val initialTemplate = profile?.customReminderTemplate ?: ""
    var customTemplateValue by remember {
        mutableStateOf(
            TextFieldValue(
                text = initialTemplate,
                selection = TextRange(initialTemplate.length)
            )
        )
    }

    fun insertVariableToken(token: String) {
        val current = customTemplateValue
        val baseText = current.text.ifBlank { "" }
        val start = current.selection.start.coerceIn(0, baseText.length)
        val end = current.selection.end.coerceIn(0, baseText.length)
        val needsLeadingSpace = start > 0 && !baseText[start - 1].isWhitespace()
        val insertion = if (needsLeadingSpace) " $token" else token
        val updated = buildString {
            append(baseText.substring(0, start))
            append(insertion)
            append(baseText.substring(end))
        }.take(1000)
        val newCursor = (start + insertion.length).coerceAtMost(updated.length)
        customTemplateValue = TextFieldValue(
            text = updated,
            selection = TextRange(newCursor)
        )
    }

    val previewText = remember(customTemplateValue.text, displayName, clinicName) {
        ReminderScheduler.renderAppointmentTemplatePreview(
            template = customTemplateValue.text,
            therapistName = displayName.ifBlank { fallbackName },
            clinicName = clinicName
        )
    }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Dados Profissionais e Lembretes", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Nome da Fonoaudióloga *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_display_name_input")
                )
                OutlinedTextField(
                    value = crfa,
                    onValueChange = { crfa = it },
                    label = { Text("Registro CRFa (ex.: CRFa 2-19402)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_crfa_input")
                )
                OutlinedTextField(
                    value = clinicName,
                    onValueChange = { clinicName = it },
                    label = { Text("Clínica / Consultório") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("profile_clinic_name_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lembretes automáticos", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = autoReminders,
                        onCheckedChange = { autoReminders = it },
                        modifier = Modifier.testTag("profile_auto_reminders_switch")
                    )
                }
                Text("Antecedência padrão", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30L to "30 min", 60L to "1 hora", 1440L to "24 horas").forEach { (mins, lbl) ->
                        FilterChip(
                            selected = defaultLead == mins,
                            onClick = { defaultLead = mins },
                            label = { Text(lbl) }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mensagem padrão de lembrete de consulta",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val nextText = if (customTemplateValue.text.isBlank()) {
                                AppointmentReminderTemplateDefaults.DEFAULT_TEMPLATE
                            } else {
                                ""
                            }
                            customTemplateValue = TextFieldValue(
                                text = nextText,
                                selection = TextRange(nextText.length)
                            )
                        },
                        modifier = Modifier.testTag("reset_reminder_template_btn")
                    ) {
                        Icon(
                            Icons.Rounded.RestartAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (customTemplateValue.text.isBlank()) "Editar modelo" else "Restaurar padrão"
                        )
                    }
                }

                Text(
                    text = "Toque nas variáveis para inserir automaticamente o nome do paciente e data/hora da sessão:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AppointmentReminderTemplateDefaults.availableVariables.forEach { (token, desc) ->
                        val safeTag = token.trim('{', '}')
                        AssistChip(
                            onClick = { insertVariableToken(token) },
                            label = { Text("$token • $desc", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.testTag("insert_var_$safeTag")
                        )
                    }
                }

                OutlinedTextField(
                    value = customTemplateValue,
                    onValueChange = { next ->
                        if (next.text.length <= 1000) {
                            customTemplateValue = next
                        }
                    },
                    label = { Text("Texto personalizado do lembrete") },
                    placeholder = {
                        Text(
                            "Deixe em branco para usar o modelo padrão do FonoVida...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    minLines = 3,
                    maxLines = 6,
                    supportingText = {
                        Text("${customTemplateValue.text.length}/1000 caracteres")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("profile_custom_reminder_template_input")
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Pré-visualização (exemplo de WhatsApp e notificação)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.trim().isNotEmpty()) {
                        onConfirm(
                            displayName,
                            crfa,
                            clinicName,
                            defaultLead,
                            autoReminders,
                            customTemplateValue.text.trim().take(1000)
                        )
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("save_profile_button")
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancelar") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseListBuilderDialog(
    patients: List<Patient>,
    initialList: CustomExerciseList? = null,
    preselectedPatient: Patient? = null,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        clinicalObjective: String,
        patient: Patient?,
        items: List<ExerciseListItem>,
        applyToPatientNow: Boolean
    ) -> Unit
) {
    val resolvedInitialPatient = remember(initialList, preselectedPatient, patients) {
        when {
            preselectedPatient != null -> preselectedPatient
            !initialList?.patientId.isNullOrBlank() -> patients.firstOrNull { it.id == initialList?.patientId }
            else -> null
        }
    }

    var title by remember { mutableStateOf(initialList?.title ?: "") }
    var objective by remember { mutableStateOf(initialList?.clinicalObjective ?: "") }
    var selectedPatient by remember { mutableStateOf(resolvedInitialPatient) }
    var patientMenuOpen by remember { mutableStateOf(false) }
    var items by remember {
        mutableStateOf(
            initialList?.items?.takeIf { it.isNotEmpty() }
                ?: CuratedProtocolPresets.presets.first().items
        )
    }
    var applyNow by remember {
        mutableStateOf(preselectedPatient != null && initialList == null)
    }

    // Inline custom exercise composer state
    var showCustomItemForm by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf(ExerciseCategory.AQUECIMENTO) }
    var newInstructions by remember { mutableStateOf("") }
    var newDurationText by remember { mutableStateOf("10") }
    var newFrequencyText by remember { mutableStateOf("5") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Text(
                text = if (initialList == null) "Gerar Lista Personalizada" else "Editar Lista Personalizada",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Gerar por Protocolo Clínico",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CuratedProtocolPresets.presets.forEach { preset ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                if (title.isBlank()) title = preset.defaultTitle
                                if (objective.isBlank()) objective = preset.objective
                                items = preset.items
                                errorMsg = null
                            },
                            label = { Text(preset.label) },
                            modifier = Modifier.testTag("preset_protocol_${preset.id}")
                        )
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; errorMsg = null },
                    label = { Text("Título da lista *") },
                    placeholder = { Text("Ex.: Treino Vocal Matinal - Semana 1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("exercise_list_title_input")
                )

                OutlinedTextField(
                    value = objective,
                    onValueChange = { objective = it },
                    label = { Text("Objetivo clínico / Foco") },
                    placeholder = { Text("Ex.: Reduzir tensão laríngea e elevar TMF") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = patientMenuOpen,
                    onExpandedChange = { patientMenuOpen = !patientMenuOpen }
                ) {
                    OutlinedTextField(
                        value = selectedPatient?.fullName ?: "Nenhum (Modelo reutilizável da clínica)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Vincular a paciente (opcional)") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = patientMenuOpen)
                        },
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = patientMenuOpen,
                        onDismissRequest = { patientMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Nenhum (Modelo reutilizável)") },
                            onClick = {
                                selectedPatient = null
                                applyNow = false
                                patientMenuOpen = false
                            }
                        )
                        patients.forEach { pat ->
                            DropdownMenuItem(
                                text = { Text("${pat.fullName} (${pat.clinicalFocus})") },
                                onClick = {
                                    selectedPatient = pat
                                    patientMenuOpen = false
                                }
                            )
                        }
                    }
                }

                if (selectedPatient != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Aplicar ao plano de ${selectedPatient?.fullName}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Prescreve todos os exercícios da lista no prontuário",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = applyNow, onCheckedChange = { applyNow = it })
                    }
                }

                Text(
                    text = "Adicionar exercício rápido",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CuratedVoiceExerciseTemplates.templates.forEach { tpl ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                if (items.size < 20) {
                                    items = items + ExerciseListItem(
                                        title = tpl.title,
                                        category = tpl.category.name,
                                        instructions = tpl.instructions,
                                        durationMinutes = tpl.targetDurationMinutes.toLong(),
                                        frequencyPerWeek = tpl.frequencyPerWeek.toLong()
                                    )
                                    errorMsg = null
                                }
                            },
                            label = { Text("+ ${tpl.title.substringBefore(" (")}") }
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showCustomItemForm = !showCustomItemForm },
                    modifier = Modifier.fillMaxWidth().testTag("toggle_custom_exercise_item")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (showCustomItemForm) "Ocultar formulário personalizado" else "Criar exercício personalizado na lista")
                }

                if (showCustomItemForm) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = newTitle,
                                onValueChange = { newTitle = it },
                                label = { Text("Nome do exercício *") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("custom_list_item_title")
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ExerciseCategory.entries.forEach { cat ->
                                    FilterChip(
                                        selected = newCategory == cat,
                                        onClick = { newCategory = cat },
                                        label = { Text(cat.label) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = newDurationText,
                                    onValueChange = { newDurationText = it.filter { c -> c.isDigit() } },
                                    label = { Text("Minutos") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newFrequencyText,
                                    onValueChange = { newFrequencyText = it.filter { c -> c.isDigit() } },
                                    label = { Text("Vezes/sem") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            OutlinedTextField(
                                value = newInstructions,
                                onValueChange = { newInstructions = it },
                                label = { Text("Instruções *") },
                                minLines = 2,
                                maxLines = 3,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FilledTonalButton(
                                onClick = {
                                    if (newTitle.trim().length < 2) {
                                        errorMsg = "Informe o nome do exercício personalizado."
                                        return@FilledTonalButton
                                    }
                                    if (items.size < 20) {
                                        items = items + ExerciseListItem(
                                            title = newTitle.trim(),
                                            category = newCategory.name,
                                            instructions = newInstructions.trim().ifBlank { "Executar conforme orientação clínica." },
                                            durationMinutes = (newDurationText.toLongOrNull() ?: 10L).coerceIn(1L, 120L),
                                            frequencyPerWeek = (newFrequencyText.toLongOrNull() ?: 5L).coerceIn(1L, 21L)
                                        )
                                        newTitle = ""
                                        newInstructions = ""
                                        showCustomItemForm = false
                                        errorMsg = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().testTag("add_custom_item_btn")
                            ) {
                                Text("Adicionar à Lista")
                            }
                        }
                    }
                }

                Text(
                    text = "Exercícios na Lista (${items.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                items.forEachIndexed { idx, item ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${idx + 1}. ${item.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${item.exerciseCategory.label} • ${item.durationMinutes} min • ${item.frequencyPerWeek}x/sem",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (idx > 0) {
                                IconButton(
                                    onClick = {
                                        val mutable = items.toMutableList()
                                        val moved = mutable.removeAt(idx)
                                        mutable.add(idx - 1, moved)
                                        items = mutable
                                    }
                                ) {
                                    Icon(Icons.Rounded.ArrowUpward, contentDescription = "Subir exercício", modifier = Modifier.size(16.dp))
                                }
                            }
                            if (idx < items.lastIndex) {
                                IconButton(
                                    onClick = {
                                        val mutable = items.toMutableList()
                                        val moved = mutable.removeAt(idx)
                                        mutable.add(idx + 1, moved)
                                        items = mutable
                                    }
                                ) {
                                    Icon(Icons.Rounded.ArrowDownward, contentDescription = "Descer exercício", modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(
                                onClick = {
                                    items = items.filterIndexed { i, _ -> i != idx }
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "Remover exercício da lista",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                errorMsg?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        title.trim().length < 2 -> errorMsg = "Informe o título da lista."
                        items.isEmpty() -> errorMsg = "Adicione ao menos 1 exercício à lista."
                        else -> onConfirm(title, objective, selectedPatient, items, applyNow)
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("save_exercise_list_button")
            ) {
                Text(if (isBusy) "Salvando..." else "Salvar Lista")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancelar") }
        }
    )
}

@Composable
fun PickPatientForListDialog(
    listTitle: String,
    patients: List<Patient>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSelectPatient: (Patient) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Column {
                Text("Aplicar Lista a Paciente", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = listTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (patients.isEmpty()) {
                    Text(
                        text = "Cadastre um paciente primeiro para aplicar esta lista.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    patients.forEach { pat ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isBusy) { onSelectPatient(pat) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(pat.fullName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${pat.ageYears} anos • ${pat.clinicalFocus}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Fechar") }
        }
    )
}

@Composable
fun PickListForPatientDialog(
    patientName: String,
    lists: List<CustomExerciseList>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSelectList: (CustomExerciseList) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Column {
                Text("Aplicar Lista Personalizada", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "Paciente: $patientName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (lists.isEmpty()) {
                    Text(
                        text = "Você ainda não criou nenhuma lista personalizada.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    lists.forEach { list ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isBusy) { onSelectList(list) }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(list.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    text = "${list.items.size} exercício(s) • ${list.totalWeeklyMinutes} min/sem",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (list.clinicalObjective.isNotBlank()) {
                                    Text(
                                        text = list.clinicalObjective,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Fechar") }
        }
    )
}

@Composable
fun PatientAccessCodesDialog(
    patients: List<Patient>,
    accessCodes: List<PatientAccessCode>,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onAddPatient: () -> Unit,
    onGenerateCode: (Patient) -> Unit,
    onCopyCode: (String) -> Unit,
    onShareCode: (Patient, String) -> Unit,
    onRevokeCode: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.VpnKey,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Códigos de Acesso", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Gere um código exclusivo para cada paciente entrar no app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (patients.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Nenhum paciente cadastrado ainda",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Cadastre um paciente primeiro para gerar o código exclusivo de acesso ao portal.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    onDismiss()
                                    onAddPatient()
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cadastrar Paciente")
                            }
                        }
                    }
                } else {
                    patients.forEach { patient ->
                        val activeCode = accessCodes.firstOrNull {
                            it.patientId == patient.id && it.active
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = patient.fullName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = patient.clinicalFocus,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (activeCode != null) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = activeCode.code,
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    letterSpacing = 1.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }

                                if (activeCode != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = { onCopyCode(activeCode.code) },
                                            enabled = !isBusy
                                        ) {
                                            Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Copiar")
                                        }
                                        Button(
                                            onClick = { onShareCode(patient, activeCode.code) },
                                            enabled = !isBusy
                                        ) {
                                            Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Enviar")
                                        }
                                        OutlinedButton(
                                            onClick = { onGenerateCode(patient) },
                                            enabled = !isBusy,
                                            modifier = Modifier.testTag("dialog_generate_code_${patient.id}")
                                        ) {
                                            Text("Novo")
                                        }
                                        OutlinedButton(
                                            onClick = { onRevokeCode(activeCode.code) },
                                            enabled = !isBusy
                                        ) {
                                            Text("Desativar", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { onGenerateCode(patient) },
                                        enabled = !isBusy,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("dialog_generate_code_${patient.id}")
                                    ) {
                                        Icon(Icons.Rounded.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (isBusy) "Gerando..." else "Gerar Código de Acesso")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Fechar") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeeklyFrequencyGoalDialog(
    patient: Patient,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var goalValue by remember(patient.id) { mutableIntStateOf(patient.effectiveWeeklyFrequencyGoal) }
    var goalText by remember(patient.id) { mutableStateOf(patient.effectiveWeeklyFrequencyGoal.toString()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = {
            Text(
                text = "Meta de Frequência Semanal",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Defina quantas execuções ou sessões de exercícios vocais ${patient.fullName} deve completar por semana.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            val next = (goalValue - 1).coerceIn(1, 21)
                            goalValue = next
                            goalText = next.toString()
                            errorMsg = null
                        },
                        enabled = !isBusy && goalValue > 1,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("frequency_goal_minus_btn"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("-", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = goalText,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(2)
                            goalText = digits
                            digits.toIntOrNull()?.let { parsed ->
                                if (parsed in 1..21) {
                                    goalValue = parsed
                                }
                            }
                            errorMsg = null
                        },
                        label = { Text("Exercícios / semana") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("weekly_frequency_goal_input")
                    )

                    FilledTonalButton(
                        onClick = {
                            val next = (goalValue + 1).coerceIn(1, 21)
                            goalValue = next
                            goalText = next.toString()
                            errorMsg = null
                        },
                        enabled = !isBusy && goalValue < 21,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("frequency_goal_plus_btn"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
                    ) {
                        Text("+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }

                Text(
                    text = "Sugestões rápidas",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(3, 5, 7, 10, 14).forEach { preset ->
                        FilterChip(
                            selected = goalText.toIntOrNull() == preset,
                            onClick = {
                                goalValue = preset
                                goalText = preset.toString()
                                errorMsg = null
                            },
                            label = { Text("${preset}x/sem") }
                        )
                    }
                }

                errorMsg?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = goalText.toIntOrNull()
                    if (parsed == null || parsed !in 1..21) {
                        errorMsg = "Informe um valor entre 1 e 21 exercícios por semana."
                    } else {
                        onConfirm(parsed)
                    }
                },
                enabled = !isBusy,
                modifier = Modifier.testTag("save_weekly_frequency_goal_btn")
            ) {
                Text(if (isBusy) "Salvando..." else "Salvar Meta")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) {
                Text("Cancelar")
            }
        }
    )
}


