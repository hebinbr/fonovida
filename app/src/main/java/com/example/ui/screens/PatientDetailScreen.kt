package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MedicalInformation
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material3.FilterChip
import androidx.compose.ui.unit.sp
import com.example.data.model.CustomExerciseList
import com.example.data.model.EvolutionSession
import com.example.data.model.ExerciseCategory
import com.example.data.model.Patient
import com.example.data.model.PatientAccessCode
import com.example.data.model.ReportTimeframe
import com.example.data.model.TherapistProfile
import com.example.data.model.VoiceExercise
import com.example.ui.components.CategoryPerformanceCard
import com.example.ui.components.ProgressDeltaCard
import com.example.ui.components.TmfProgressionChartCard
import com.example.ui.components.VoiceEvolutionChartCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDetailScreen(
    patient: Patient,
    exercises: List<VoiceExercise>,
    sessions: List<EvolutionSession>,
    patientExerciseLists: List<CustomExerciseList>,
    totalExerciseListsCount: Int,
    profile: TherapistProfile?,
    activeAccessCode: PatientAccessCode? = null,
    isAccessCodeBusy: Boolean = false,
    onBack: () -> Unit,
    onOpenClinicalSheet: () -> Unit = {},
    onEditPatient: () -> Unit,
    onDeletePatient: () -> Unit,
    onGenerateAccessCode: () -> Unit = {},
    onRevokeAccessCode: (String) -> Unit = {},
    onShareAccessCode: (String) -> Unit = {},
    onCopyAccessCode: (String) -> Unit = {},
    onPrescribeExercise: () -> Unit,
    onToggleExerciseActive: (VoiceExercise) -> Unit,
    onDeleteExercise: (String) -> Unit,
    onGenerateExerciseList: () -> Unit,
    onApplySavedExerciseList: () -> Unit,
    onApplySpecificList: (CustomExerciseList) -> Unit,
    onEditExerciseList: (CustomExerciseList) -> Unit,
    onDeleteExerciseList: (String) -> Unit,
    onShareExerciseList: (CustomExerciseList) -> Unit,
    onExportExerciseListPdf: (CustomExerciseList) -> Unit,
    onLogSession: (prefillTitle: String, prefillCategory: ExerciseCategory) -> Unit,
    onDeleteSession: (String) -> Unit,
    onPreviewPdf: (ReportTimeframe, List<EvolutionSession>) -> Unit,
    onExportPdf: (ReportTimeframe, List<EvolutionSession>) -> Unit,
    onSendExerciseReminder: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var confirmDeletePatient by remember { mutableStateOf(false) }
    var reportTimeframe by remember { mutableStateOf(ReportTimeframe.TUDO) }

    val orderedSessions = remember(sessions) {
        sessions.sortedByDescending { it.sessionDateMillis }
    }
    val filteredReportSessions = remember(sessions, reportTimeframe) {
        val days = reportTimeframe.days
        if (days == null) {
            sessions
        } else {
            val cutoff = System.currentTimeMillis() - (days.toLong() * 86_400_000L)
            sessions.filter { it.sessionDateMillis >= cutoff }
        }
    }
    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = patient.fullName,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1
                        )
                        Text(
                            text = "${patient.ageYears} anos • ${patient.clinicalFocus}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenClinicalSheet,
                        modifier = Modifier.testTag("topbar_clinical_sheet_btn")
                    ) {
                        Icon(
                            Icons.Rounded.MedicalInformation,
                            contentDescription = "Ficha clínica",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onEditPatient, modifier = Modifier.testTag("edit_patient_btn")) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Editar paciente")
                    }
                    IconButton(onClick = { confirmDeletePatient = true }) {
                        Icon(
                            Icons.Rounded.DeleteOutline,
                            contentDescription = "Excluir paciente",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            when (selectedTab) {
                0 -> FloatingActionButton(
                    onClick = {
                        val firstEx = exercises.firstOrNull { it.activeFlag }
                        onLogSession(
                            firstEx?.title ?: "",
                            firstEx?.exerciseCategory ?: ExerciseCategory.AQUECIMENTO
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("fab_log_session")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Registrar sessão de voz")
                }

                1 -> FloatingActionButton(
                    onClick = onPrescribeExercise,
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.testTag("fab_add_exercise")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Prescrever exercício")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.widthIn(max = 840.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Evolução (${sessions.size})") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Exercícios (${exercises.size})") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Relatórios") }
                )
            }

            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            PatientKpiSummaryRow(sessions = sessions)
                        }
                        item {
                            PatientClinicalSheetCard(
                                patient = patient,
                                onOpenClinicalSheet = onOpenClinicalSheet
                            )
                        }
                        item {
                            PatientAccessCodeCard(
                                activeAccessCode = activeAccessCode,
                                isBusy = isAccessCodeBusy,
                                onGenerate = onGenerateAccessCode,
                                onRevoke = onRevokeAccessCode,
                                onShare = onShareAccessCode,
                                onCopy = onCopyAccessCode
                            )
                        }
                        item {
                            VoiceEvolutionChartCard(sessions = sessions)
                        }
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Histórico Completo de Evolução",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                TextButton(
                                    onClick = {
                                        val firstEx = exercises.firstOrNull { it.activeFlag }
                                        onLogSession(
                                            firstEx?.title ?: "",
                                            firstEx?.exerciseCategory ?: ExerciseCategory.AQUECIMENTO
                                        )
                                    }
                                ) {
                                    Text("+ Nova sessão")
                                }
                            }
                        }

                        if (orderedSessions.isEmpty()) {
                            item {
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Text(
                                            text = "Sem registros de evolução",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = "Toque em + para registrar a primeira sessão de exercícios de voz com escores de clareza, estabilidade, conforto e TMF.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            items(orderedSessions, key = { it.id }) { session ->
                                SessionHistoryCard(
                                    session = session,
                                    dateLabel = dateFmt.format(Date(session.sessionDateMillis)),
                                    onDelete = { onDeleteSession(session.id) }
                                )
                            }
                        }
                    }
                }

                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            PatientAccessCodeCard(
                                activeAccessCode = activeAccessCode,
                                isBusy = isAccessCodeBusy,
                                onGenerate = onGenerateAccessCode,
                                onRevoke = onRevokeAccessCode,
                                onShare = onShareAccessCode,
                                onCopy = onCopyAccessCode
                            )
                        }
                        item {
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Plano Vocal Personalizado",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "Prescreva exercícios e envie o roteiro para ${patient.guardianName}.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FilledTonalButton(onClick = onSendExerciseReminder) {
                                        Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Enviar")
                                    }
                                }
                            }
                        }

                        item {
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = "Listas Personalizadas de Exercícios",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Gere um roteiro exclusivo ou aplique um modelo salvo da clínica.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = onGenerateExerciseList,
                                            modifier = Modifier.weight(1f).testTag("patient_generate_list_btn")
                                        ) {
                                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Gerar Lista")
                                        }
                                        FilledTonalButton(
                                            onClick = onApplySavedExerciseList,
                                            enabled = totalExerciseListsCount > 0,
                                            modifier = Modifier.weight(1f).testTag("patient_apply_list_btn")
                                        ) {
                                            Icon(Icons.Rounded.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Aplicar Modelo")
                                        }
                                    }
                                }
                            }
                        }

                        if (patientExerciseLists.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Listas Vinculadas (${patientExerciseLists.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            items(patientExerciseLists, key = { "plist_${it.id}" }) { customList ->
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = customList.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${customList.items.size} exercício(s) • ${customList.totalWeeklyMinutes} min/sem",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (customList.clinicalObjective.isNotBlank()) {
                                            Text(
                                                text = customList.clinicalObjective,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        customList.items.forEachIndexed { idx, item ->
                                            Text(
                                                text = "${idx + 1}. ${item.title} (${item.durationMinutes} min • ${item.frequencyPerWeek}x/sem)",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            FilledTonalButton(onClick = { onApplySpecificList(customList) }) {
                                                Icon(Icons.Rounded.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Aplicar")
                                            }
                                            OutlinedButton(onClick = { onExportExerciseListPdf(customList) }) {
                                                Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("PDF")
                                            }
                                            OutlinedButton(onClick = { onShareExerciseList(customList) }) {
                                                Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Enviar")
                                            }
                                            IconButton(onClick = { onEditExerciseList(customList) }) {
                                                Icon(Icons.Rounded.Edit, contentDescription = "Editar lista", tint = MaterialTheme.colorScheme.primary)
                                            }
                                            IconButton(onClick = { onDeleteExerciseList(customList.id) }) {
                                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Excluir lista", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (exercises.isEmpty()) {
                            item {
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Text("Nenhum exercício prescrito", style = MaterialTheme.typography.titleSmall)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "Adicione exercícios terapêuticos a partir dos modelos clínicos ou crie orientações específicas.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Button(onClick = onPrescribeExercise) {
                                            Text("Prescrever Exercício")
                                        }
                                    }
                                }
                            }
                        } else {
                            items(exercises, key = { it.id }) { ex ->
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(ex.title, style = MaterialTheme.typography.titleMedium)
                                                Text(
                                                    "${ex.exerciseCategory.label} • ${ex.targetDurationMinutes} min • ${ex.frequencyPerWeek}x/sem",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Switch(
                                                checked = ex.activeFlag,
                                                onCheckedChange = { onToggleExerciseActive(ex) }
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = ex.instructions,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            FilledTonalButton(
                                                onClick = { onLogSession(ex.title, ex.exerciseCategory) }
                                            ) {
                                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Registrar Sessão")
                                            }
                                            IconButton(onClick = { onDeleteExercise(ex.id) }) {
                                                Icon(
                                                    Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Remover exercício",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .widthIn(max = 840.dp)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Período do Relatório",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ReportTimeframe.entries.forEach { tf ->
                                        FilterChip(
                                            selected = reportTimeframe == tf,
                                            onClick = { reportTimeframe = tf },
                                            label = { Text(tf.label) },
                                            modifier = Modifier.testTag("timeframe_chip_${tf.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }

                        item {
                            ProgressDeltaCard(sessions = filteredReportSessions)
                        }

                        item {
                            VoiceEvolutionChartCard(sessions = filteredReportSessions)
                        }

                        item {
                            TmfProgressionChartCard(sessions = filteredReportSessions)
                        }

                        item {
                            CategoryPerformanceCard(sessions = filteredReportSessions)
                        }

                        item {
                            Card(
                                shape = MaterialTheme.shapes.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.PictureAsPdf,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "Exportar Relatório Clínico (PDF)",
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                            Text(
                                                text = "Período: ${reportTimeframe.label} • ${filteredReportSessions.size} sessões",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(14.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Layout profissional incluído no PDF:",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text("• Cabeçalho clínico com identificação (${profile?.crfa?.ifBlank { "CRFa no Perfil" } ?: "CRFa"})", style = MaterialTheme.typography.bodySmall)
                                            Text("• KPIs de evolução (Clareza, Estabilidade, TMF e delta inicial)", style = MaterialTheme.typography.bodySmall)
                                            Text("• Dois gráficos vetoriais: Curva Vocal e Barras de TMF", style = MaterialTheme.typography.bodySmall)
                                            Text("• Exercícios ativos, histórico completo e campo de assinatura", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        FilledTonalButton(
                                            onClick = { onPreviewPdf(reportTimeframe, filteredReportSessions) },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(50.dp)
                                                .testTag("preview_pdf_button")
                                        ) {
                                            Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Visualizar")
                                        }

                                        Button(
                                            onClick = { onExportPdf(reportTimeframe, filteredReportSessions) },
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier
                                                .weight(1.3f)
                                                .height(50.dp)
                                                .testTag("export_pdf_button")
                                        ) {
                                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Exportar PDF")
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = onSendExerciseReminder,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Enviar Lembrete de Exercícios ao Responsável")
                                    }
                                }
                            }
                        }

                        if (patient.initialNotes.isNotBlank()) {
                            item {
                                Card(
                                    shape = MaterialTheme.shapes.medium,
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text("Anamnese / Observações Iniciais", style = MaterialTheme.typography.titleSmall)
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = patient.initialNotes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmDeletePatient) {
        AlertDialog(
            onDismissRequest = { confirmDeletePatient = false },
            title = { Text("Excluir paciente?") },
            text = {
                Text("Esta ação removerá ${patient.fullName} e todo o histórico de sessões, exercícios e agendamentos vinculados.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDeletePatient = false
                        onDeletePatient()
                    }
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePatient = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun PatientKpiSummaryRow(sessions: List<EvolutionSession>) {
    val avgClarity = if (sessions.isEmpty()) 0 else sessions.map { it.vocalClarityScore }.average().toInt()
    val latestTmf = sessions.maxByOrNull { it.sessionDateMillis }?.tmfSeconds ?: 0.0
    val totalMin = sessions.sumOf { it.durationMinutes }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallMetricBox("Clareza Média", if (sessions.isEmpty()) "—" else "$avgClarity%", Modifier.weight(1f))
        SmallMetricBox(
            "Último TMF",
            if (sessions.isEmpty()) "—" else String.format(Locale("pt", "BR"), "%.1fs", latestTmf),
            Modifier.weight(1f)
        )
        SmallMetricBox("Tempo Total", "${totalMin}m", Modifier.weight(1f))
    }
}

@Composable
private fun SmallMetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SessionHistoryCard(
    session: EvolutionSession,
    dateLabel: String,
    onDelete: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(session.exerciseTitle, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "$dateLabel • ${session.durationMinutes} min • ${session.exerciseCategory.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Remover sessão",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip("Clareza ${session.vocalClarityScore}%")
                MetricChip("Estab. ${session.stabilityScore}%")
                MetricChip("Conforto ${session.comfortScore}%")
                MetricChip(String.format(Locale("pt", "BR"), "TMF %.1fs", session.tmfSeconds))
            }

            if (session.clinicalNotes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Evolução: ${session.clinicalNotes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (session.homeGuidance.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Para casa: ${session.homeGuidance}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun MetricChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PatientAccessCodeCard(
    activeAccessCode: PatientAccessCode?,
    isBusy: Boolean,
    onGenerate: () -> Unit,
    onRevoke: (String) -> Unit,
    onShare: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VpnKey,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(8.dp).size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Código de Acesso do Paciente",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "O paciente só acessa o portal no app após inserir este código.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (activeAccessCode != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = activeAccessCode.code,
                            style = MaterialTheme.typography.headlineSmall.copy(
                                letterSpacing = 2.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("active_patient_access_code")
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                text = if (activeAccessCode.redeemedByUid.isNotBlank()) "Ativado" else "Aguardando uso",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onShare(activeAccessCode.code) },
                        modifier = Modifier.testTag("share_patient_code_btn")
                    ) {
                        Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enviar")
                    }
                    FilledTonalButton(
                        onClick = { onCopy(activeAccessCode.code) },
                        modifier = Modifier.testTag("copy_patient_code_btn")
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copiar")
                    }
                    OutlinedButton(
                        onClick = onGenerate,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("generate_patient_code_btn")
                    ) {
                        Text("Novo Código")
                    }
                    OutlinedButton(
                        onClick = { onRevoke(activeAccessCode.code) },
                        enabled = !isBusy,
                        modifier = Modifier.testTag("revoke_patient_code_btn")
                    ) {
                        Text("Desativar", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Button(
                    onClick = onGenerate,
                    enabled = !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("generate_patient_code_btn")
                ) {
                    Icon(Icons.Rounded.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Gerar Código Exclusivo de Acesso")
                }
            }
        }
    }
}

@Composable
private fun PatientClinicalSheetCard(
    patient: Patient,
    onOpenClinicalSheet: () -> Unit
) {
    val filledCount = listOf(
        patient.medicalHistory,
        patient.mainComplaints,
        patient.therapeuticGoals
    ).count { it.isNotBlank() }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
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
                    Icon(
                        imageVector = Icons.Rounded.MedicalInformation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Ficha Clínica do Paciente",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (filledCount == 0) {
                                "Histórico médico, queixas e metas ainda não preenchidos"
                            } else {
                                "$filledCount de 3 seções preenchidas"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (patient.hasClinicalSheetContent) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (patient.medicalHistory.isNotBlank()) {
                            Text(
                                text = "Histórico médico: ${patient.medicalHistory}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2
                            )
                        }
                        if (patient.mainComplaints.isNotBlank()) {
                            Text(
                                text = "Queixas principais: ${patient.mainComplaints}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2
                            )
                        }
                        if (patient.therapeuticGoals.isNotBlank()) {
                            Text(
                                text = "Metas terapêuticas: ${patient.therapeuticGoals}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

            FilledTonalButton(
                onClick = onOpenClinicalSheet,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_clinical_sheet_btn")
            ) {
                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (patient.hasClinicalSheetContent) "Editar Ficha Clínica"
                    else "Preencher Ficha Clínica"
                )
            }
        }
    }
}

