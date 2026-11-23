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
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
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
import com.example.data.model.EvolutionSession
import com.example.data.model.ExerciseCategory
import com.example.data.model.Patient
import com.example.data.model.TherapistProfile
import com.example.data.model.VoiceExercise
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
    profile: TherapistProfile?,
    onBack: () -> Unit,
    onEditPatient: () -> Unit,
    onDeletePatient: () -> Unit,
    onPrescribeExercise: () -> Unit,
    onToggleExerciseActive: (VoiceExercise) -> Unit,
    onDeleteExercise: (String) -> Unit,
    onLogSession: (prefillTitle: String, prefillCategory: ExerciseCategory) -> Unit,
    onDeleteSession: (String) -> Unit,
    onExportPdf: () -> Unit,
    onSendExerciseReminder: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var confirmDeletePatient by remember { mutableStateOf(false) }

    val orderedSessions = remember(sessions) {
        sessions.sortedByDescending { it.sessionDateMillis }
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
                    text = { Text("Relatório PDF") }
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
                                                text = "Relatório Clínico em PDF",
                                                style = MaterialTheme.typography.titleLarge
                                            )
                                            Text(
                                                text = "Pronto para compartilhar com ${patient.guardianName}",
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
                                                text = "Conteúdo do documento:",
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text("• Identificação da profissional (${profile?.crfa?.ifBlank { "CRFa a preencher no Perfil" } ?: "CRFa"})", style = MaterialTheme.typography.bodySmall)
                                            Text("• Dados do paciente e responsável (${patient.guardianName})", style = MaterialTheme.typography.bodySmall)
                                            Text("• Indicadores de Clareza, Estabilidade, Conforto e TMF", style = MaterialTheme.typography.bodySmall)
                                            Text("• Gráfico vetorial da curva de evolução (${sessions.size} sessões)", style = MaterialTheme.typography.bodySmall)
                                            Text("• Plano de exercícios ativos e orientações para casa", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }

                                    Button(
                                        onClick = onExportPdf,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                            .testTag("export_pdf_button")
                                    ) {
                                        Icon(Icons.Rounded.PictureAsPdf, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Exportar e Compartilhar PDF")
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
