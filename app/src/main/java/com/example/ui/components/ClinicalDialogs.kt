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
import com.example.data.model.AppointmentType
import com.example.data.model.CuratedVoiceExerciseTemplates
import com.example.data.model.ExerciseCategory
import com.example.data.model.Patient
import com.example.data.model.PatientStatus
import com.example.data.model.TherapistProfile
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
                    when {
                        fullName.trim().length < 2 -> errorMsg = "Informe o nome do paciente."
                        age !in 1..120 -> errorMsg = "Idade válida entre 1 e 120."
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
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (
        patient: Patient,
        scheduledAtMillis: Long,
        durationMinutes: Int,
        sessionType: AppointmentType,
        notes: String,
        reminderEnabled: Boolean,
        reminderLeadMinutes: Long
    ) -> Unit
) {
    var selectedPatient by remember { mutableStateOf(patients.firstOrNull()) }
    var patientDropdownOpen by remember { mutableStateOf(false) }

    val defaultCal = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 10)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
    }
    var selectedDateMillis by remember { mutableLongStateOf(defaultCal.timeInMillis) }
    var selectedHour by remember { mutableIntStateOf(10) }
    var selectedMinute by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    var durationMinutes by remember { mutableIntStateOf(45) }
    var sessionType by remember { mutableStateOf(AppointmentType.PRESENCIAL) }
    var notes by remember { mutableStateOf("") }
    var reminderEnabled by remember { mutableStateOf(true) }
    var reminderLeadMinutes by remember { mutableLongStateOf(defaultLeadMinutes) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val dateFormatter = remember { SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")) }

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

                Text("Modalidade", style = MaterialTheme.typography.labelMedium)
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
                    label = { Text("Objetivo / Observações da consulta") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Lembrete Automático", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Notificar antes da sessão",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
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
                        reminderLeadMinutes
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
                            // Adjust UTC midnight from DatePicker to local calendar date
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
        autoRemindersEnabled: Boolean
    ) -> Unit
) {
    var displayName by remember { mutableStateOf(profile?.displayName?.ifBlank { fallbackName } ?: fallbackName) }
    var crfa by remember { mutableStateOf(profile?.crfa ?: "") }
    var clinicName by remember { mutableStateOf(profile?.clinicName ?: "Consultório de Fonoaudiologia") }
    var defaultLead by remember { mutableLongStateOf(profile?.defaultReminderMinutes ?: 60L) }
    var autoReminders by remember { mutableStateOf(profile?.autoRemindersEnabled ?: true) }

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
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = crfa,
                    onValueChange = { crfa = it },
                    label = { Text("Registro CRFa (ex.: CRFa 2-19402)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = clinicName,
                    onValueChange = { clinicName = it },
                    label = { Text("Clínica / Consultório") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lembretes automáticos", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = autoReminders, onCheckedChange = { autoReminders = it })
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
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (displayName.trim().isNotEmpty()) {
                        onConfirm(displayName, crfa, clinicName, defaultLead, autoReminders)
                    }
                },
                enabled = !isBusy
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancelar") }
        }
    )
}
