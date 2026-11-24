package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.MedicalInformation
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.EvolutionSession
import com.example.data.model.Patient
import com.example.data.model.PatientAccessCode
import com.example.data.model.PatientStatus
import com.example.data.model.computeWeeklyExerciseFrequencyProgress
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg

@Composable
fun PatientsScreen(
    patients: List<Patient>,
    sessions: List<EvolutionSession>,
    accessCodes: List<PatientAccessCode> = emptyList(),
    exerciseListsCount: Int = 0,
    onOpenExerciseLists: () -> Unit = {},
    onPatientClick: (String) -> Unit,
    onOpenClinicalSheet: (String) -> Unit = {},
    onAddPatientClick: () -> Unit,
    onGenerateAccessCode: (Patient) -> Unit = {},
    onCopyAccessCode: (String) -> Unit = {},
    onShareAccessCode: (Patient, String) -> Unit = { _, _ -> }
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<PatientStatus?>(null) }

    val filteredPatients = remember(patients, searchQuery, selectedFilter) {
        patients.filter { pat ->
            val matchesStatus = selectedFilter == null || pat.patientStatus == selectedFilter
            val matchesQuery = searchQuery.isBlank() ||
                pat.fullName.contains(searchQuery, ignoreCase = true) ||
                pat.clinicalFocus.contains(searchQuery, ignoreCase = true) ||
                pat.guardianName.contains(searchQuery, ignoreCase = true)
            matchesStatus && matchesQuery
        }
    }

    val weekAgoMillis = remember { System.currentTimeMillis() - 7L * 24L * 3600_000L }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddPatientClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_add_patient")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Cadastrar paciente")
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
                    Text(
                        text = "Pacientes",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Acompanhe exercícios de voz, metas semanais e evolução clínica.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Buscar por nome, foco clínico ou responsável") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().testTag("patient_search_input")
                    )
                }

                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilter == null,
                            onClick = { selectedFilter = null },
                            label = { Text("Todos (${patients.size})") }
                        )
                        PatientStatus.entries.forEach { st ->
                            val count = patients.count { it.patientStatus == st }
                            FilterChip(
                                selected = selectedFilter == st,
                                onClick = { selectedFilter = if (selectedFilter == st) null else st },
                                label = { Text("${st.label} ($count)") }
                            )
                        }
                    }
                }

                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenExerciseLists() }
                            .testTag("open_exercise_lists_btn")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondary,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.FormatListBulleted,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondary,
                                        modifier = Modifier.padding(8.dp).size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Listas de Exercícios Personalizadas",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "$exerciseListsCount lista(s) salva(s) • Protocolos, PDF e envio",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = "Abrir listas personalizadas",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                if (filteredPatients.isEmpty()) {
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
                                Image(
                                    painter = painterResource(id = R.drawable.img_empty_patients),
                                    contentDescription = stringResource(R.string.cd_empty_patients),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(180.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = if (patients.isEmpty()) "Nenhum paciente cadastrado ainda"
                                    else "Nenhum paciente encontrado",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (patients.isEmpty())
                                        "Cadastre seu primeiro paciente para prescrever exercícios vocais, acompanhar gráficos de evolução e exportar relatórios em PDF."
                                    else "Ajuste o filtro ou o termo de busca.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (patients.isEmpty()) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = onAddPatientClick) {
                                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Cadastrar Primeiro Paciente")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(filteredPatients, key = { it.id }) { patient ->
                        val patSessions = sessions.filter { it.patientId == patient.id }
                        val latestSession = patSessions.maxByOrNull { it.sessionDateMillis }
                        val weeklyMinutes = patSessions
                            .filter { it.sessionDateMillis >= weekAgoMillis }
                            .sumOf { it.durationMinutes }
                        val freqProgress = computeWeeklyExerciseFrequencyProgress(
                            sessions = patSessions,
                            targetFrequencyGoal = patient.effectiveWeeklyFrequencyGoal
                        )
                        val goalProgress = (weeklyMinutes.toFloat() / patient.weeklyGoalMinutes.coerceAtLeast(1).toFloat())
                            .coerceIn(0f, 1f)

                        val activeCode = accessCodes.firstOrNull { it.patientId == patient.id && it.active }?.code

                        PatientSummaryCard(
                            patient = patient,
                            sessionCount = patSessions.size,
                            latestClarity = latestSession?.vocalClarityScore?.toInt(),
                            weeklyMinutes = weeklyMinutes.toInt(),
                            completedExercisesWeek = freqProgress.completedSessions,
                            goalProgress = goalProgress,
                            activeCode = activeCode,
                            onClick = { onPatientClick(patient.id) },
                            onOpenClinicalSheet = { onOpenClinicalSheet(patient.id) },
                            onGenerateCode = { onGenerateAccessCode(patient) },
                            onCopyCode = { code -> onCopyAccessCode(code) },
                            onShareCode = { code -> onShareAccessCode(patient, code) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PatientSummaryCard(
    patient: Patient,
    sessionCount: Int,
    latestClarity: Int?,
    weeklyMinutes: Int,
    completedExercisesWeek: Int = 0,
    goalProgress: Float,
    activeCode: String? = null,
    onClick: () -> Unit,
    onOpenClinicalSheet: () -> Unit,
    onGenerateCode: () -> Unit,
    onCopyCode: (String) -> Unit,
    onShareCode: (String) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("patient_card_${patient.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = patient.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${patient.ageYears} anos • ${patient.clinicalFocus}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Responsável: ${patient.guardianName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                val (badgeBg, badgeFg) = when (patient.patientStatus) {
                    PatientStatus.ATIVO -> StatusSuccessBg to StatusSuccess
                    PatientStatus.PAUSA -> StatusWarningBg to StatusWarning
                    PatientStatus.ALTA -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
                }
                Surface(color = badgeBg, shape = RoundedCornerShape(50)) {
                    Text(
                        text = patient.patientStatus.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (!activeCode.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VpnKey,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Código: $activeCode",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onCopyCode(activeCode) },
                            modifier = Modifier.testTag("copy_code_card_${patient.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copiar código $activeCode",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onShareCode(activeCode) },
                            modifier = Modifier.testTag("share_code_card_${patient.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Send,
                                contentDescription = "Enviar código $activeCode",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onGenerateCode,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("generate_code_card_${patient.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VpnKey,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Gerar Código de Acesso")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Meta: $completedExercisesWeek/${patient.effectiveWeeklyFrequencyGoal} exerc. • $weeklyMinutes/${patient.weeklyGoalMinutes} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (latestClarity != null) "Última clareza: $latestClarity% ($sessionCount sessões)"
                    else "Sem sessões ainda",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { goalProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (patient.dailyExerciseReminderEnabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = String.format(
                                java.util.Locale("pt", "BR"),
                                "Lembrete diário às %02d:%02d",
                                patient.reminderHour,
                                patient.reminderMinute
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onOpenClinicalSheet,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag("patient_card_clinical_sheet_${patient.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MedicalInformation,
                            contentDescription = "Abrir ficha clínica de ${patient.fullName}",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Ficha Clínica", style = MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Prontuário",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
