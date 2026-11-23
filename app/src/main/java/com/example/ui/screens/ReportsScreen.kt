package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import com.example.data.model.EvolutionSession
import com.example.data.model.Patient
import com.example.data.model.ReportTimeframe
import com.example.data.model.TherapistProfile
import com.example.data.model.VoiceExercise
import com.example.ui.components.CategoryPerformanceCard
import com.example.ui.components.ProgressDeltaCard
import com.example.ui.components.TmfProgressionChartCard
import com.example.ui.components.VoiceEvolutionChartCard
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    patients: List<Patient>,
    sessions: List<EvolutionSession>,
    exercises: List<VoiceExercise>,
    profile: TherapistProfile?,
    onOpenPatient: (String) -> Unit,
    onAddPatientClick: () -> Unit,
    onPreviewPdf: (Patient, ReportTimeframe, List<EvolutionSession>, List<VoiceExercise>) -> Unit,
    onExportPdf: (Patient, ReportTimeframe, List<EvolutionSession>, List<VoiceExercise>) -> Unit
) {
    var selectedPatientId by remember(patients) {
        mutableStateOf(patients.firstOrNull()?.id)
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var reportTimeframe by remember { mutableStateOf(ReportTimeframe.TUDO) }

    val selectedPatient = remember(patients, selectedPatientId) {
        patients.firstOrNull { it.id == selectedPatientId } ?: patients.firstOrNull()
    }

    val patientSessions = remember(sessions, selectedPatient) {
        if (selectedPatient == null) emptyList()
        else sessions.filter { it.patientId == selectedPatient.id }
    }
    val patientExercises = remember(exercises, selectedPatient) {
        if (selectedPatient == null) emptyList()
        else exercises.filter { it.patientId == selectedPatient.id }
    }

    val filteredSessions = remember(patientSessions, reportTimeframe) {
        val days = reportTimeframe.days
        if (days == null) patientSessions
        else {
            val cutoff = System.currentTimeMillis() - (days.toLong() * 86_400_000L)
            patientSessions.filter { it.sessionDateMillis >= cutoff }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Relatórios Clínicos",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Analise o desempenho vocal ao longo do tempo e exporte relatórios profissionais em PDF.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (selectedPatient == null) {
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
                                imageVector = Icons.Rounded.Assessment,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Nenhum paciente cadastrado",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Cadastre um paciente e registre sessões de voz para visualizar gráficos detalhados e gerar relatórios em PDF.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = onAddPatientClick) {
                                Text("Cadastrar Paciente")
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ExposedDropdownMenuBox(
                                expanded = dropdownExpanded,
                                onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                            ) {
                                OutlinedTextField(
                                    value = "${selectedPatient.fullName} • ${selectedPatient.clinicalFocus}",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Paciente analisado") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                                    },
                                    modifier = Modifier
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                        .testTag("reports_patient_selector")
                                )
                                ExposedDropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false }
                                ) {
                                    patients.forEach { pat ->
                                        DropdownMenuItem(
                                            text = { Text("${pat.fullName} (${pat.clinicalFocus})") },
                                            onClick = {
                                                selectedPatientId = pat.id
                                                dropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ReportTimeframe.entries.forEach { tf ->
                                    FilterChip(
                                        selected = reportTimeframe == tf,
                                        onClick = { reportTimeframe = tf },
                                        label = { Text(tf.label) },
                                        modifier = Modifier.testTag("reports_timeframe_${tf.name.lowercase()}")
                                    )
                                }
                            }

                            val avgClarity = if (filteredSessions.isEmpty()) 0
                            else filteredSessions.map { it.vocalClarityScore }.average().toInt()
                            val avgComfort = if (filteredSessions.isEmpty()) 0
                            else filteredSessions.map { it.comfortScore }.average().toInt()
                            val totalMinutes = filteredSessions.sumOf { it.durationMinutes }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ReportStatChip(
                                    label = "Sessões",
                                    value = "${filteredSessions.size}",
                                    modifier = Modifier.weight(1f)
                                )
                                ReportStatChip(
                                    label = "Clareza",
                                    value = if (filteredSessions.isEmpty()) "—" else "$avgClarity%",
                                    modifier = Modifier.weight(1f)
                                )
                                ReportStatChip(
                                    label = "Conforto",
                                    value = if (filteredSessions.isEmpty()) "—" else "$avgComfort%",
                                    modifier = Modifier.weight(1f)
                                )
                                ReportStatChip(
                                    label = "Prática",
                                    value = "${totalMinutes}m",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                item {
                    ProgressDeltaCard(sessions = filteredSessions)
                }

                item {
                    VoiceEvolutionChartCard(sessions = filteredSessions)
                }

                item {
                    TmfProgressionChartCard(sessions = filteredSessions)
                }

                item {
                    CategoryPerformanceCard(sessions = filteredSessions)
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
                                        text = "Relatório PDF Profissional",
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                    Text(
                                        text = String.format(
                                            Locale("pt", "BR"),
                                            "%s • %s • %d sessões",
                                            selectedPatient.fullName,
                                            reportTimeframe.label,
                                            filteredSessions.size
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        onPreviewPdf(selectedPatient, reportTimeframe, filteredSessions, patientExercises)
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("reports_preview_pdf_btn")
                                ) {
                                    Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Visualizar")
                                }

                                Button(
                                    onClick = {
                                        onExportPdf(selectedPatient, reportTimeframe, filteredSessions, patientExercises)
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1.3f)
                                        .height(50.dp)
                                        .testTag("reports_export_pdf_btn")
                                ) {
                                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Exportar PDF")
                                }
                            }

                            OutlinedButton(
                                onClick = { onOpenPatient(selectedPatient.id) },
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Abrir Prontuário de ${selectedPatient.fullName}")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
