package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.MedicalInformation
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.Patient
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessBg
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.StatusWarningBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_CLINICAL_FIELD_CHARS = 2000

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicalSheetScreen(
    patient: Patient,
    isBusy: Boolean,
    onBack: () -> Unit,
    onSave: (
        medicalHistory: String,
        mainComplaints: String,
        therapeuticGoals: String
    ) -> Unit
) {
    var medicalHistory by rememberSaveable(patient.id) { mutableStateOf(patient.medicalHistory) }
    var mainComplaints by rememberSaveable(patient.id) { mutableStateOf(patient.mainComplaints) }
    var therapeuticGoals by rememberSaveable(patient.id) { mutableStateOf(patient.therapeuticGoals) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val hasUnsavedChanges = remember(
        medicalHistory,
        mainComplaints,
        therapeuticGoals,
        patient.medicalHistory,
        patient.mainComplaints,
        patient.therapeuticGoals
    ) {
        medicalHistory.trim() != patient.medicalHistory.trim() ||
            mainComplaints.trim() != patient.mainComplaints.trim() ||
            therapeuticGoals.trim() != patient.therapeuticGoals.trim()
    }

    val filledCount = listOf(medicalHistory, mainComplaints, therapeuticGoals)
        .count { it.trim().isNotEmpty() }

    val handleBackNavigation = {
        if (hasUnsavedChanges && !isBusy) {
            showDiscardDialog = true
        } else if (!isBusy) {
            onBack()
        }
    }

    BackHandler(enabled = hasUnsavedChanges && !isBusy) {
        showDiscardDialog = true
    }

    val dateFmt = remember { SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")) }
    val lastUpdatedLabel = patient.updatedAt?.toDate()?.let { dateFmt.format(it) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Ficha Clínica",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1
                        )
                        Text(
                            text = "${patient.fullName} • ${patient.ageYears} anos",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = handleBackNavigation,
                        modifier = Modifier.testTag("clinical_sheet_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = { onSave(medicalHistory, mainComplaints, therapeuticGoals) },
                        enabled = !isBusy,
                        modifier = Modifier.testTag("clinical_sheet_top_save_btn")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Save,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Salvar")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = handleBackNavigation,
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("clinical_sheet_cancel_btn")
                    ) {
                        Text("Voltar")
                    }
                    Button(
                        onClick = { onSave(medicalHistory, mainComplaints, therapeuticGoals) },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1.6f)
                            .height(50.dp)
                            .testTag("save_clinical_sheet_btn")
                    ) {
                        if (isBusy) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Salvando...")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.CloudDone,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Salvar Ficha Clínica")
                        }
                    }
                }
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
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Patient & Completion Status Banner
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = patient.clinicalFocus,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Responsável: ${patient.guardianName}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                val (statusBg, statusFg, statusLabel) = when {
                                    hasUnsavedChanges -> Triple(
                                        StatusWarningBg,
                                        StatusWarning,
                                        "Alterações não salvas"
                                    )
                                    filledCount > 0 -> Triple(
                                        StatusSuccessBg,
                                        StatusSuccess,
                                        "Salvo no Firestore"
                                    )
                                    else -> Triple(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                        "Pendente"
                                    )
                                }
                                Surface(color = statusBg, shape = RoundedCornerShape(50)) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (hasUnsavedChanges) {
                                                Icons.Rounded.Save
                                            } else {
                                                Icons.Rounded.CheckCircleOutline
                                            },
                                            contentDescription = null,
                                            tint = statusFg,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = statusLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = statusFg
                                        )
                                    }
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Preenchimento: $filledCount de 3 blocos",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (lastUpdatedLabel != null) {
                                        Text(
                                            text = "Atualizado em $lastUpdatedLabel",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                LinearProgressIndicator(
                                    progress = { (filledCount / 3f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(50))
                                )
                            }
                        }
                    }
                }

                // Section 1: Histórico Médico
                item {
                    ClinicalSectionEditorCard(
                        title = "Histórico Médico",
                        subtitle = "Antecedentes clínicos, cirurgias, exames ORL, alergias e medicações.",
                        icon = Icons.Rounded.MedicalInformation,
                        accentColor = MaterialTheme.colorScheme.primary,
                        value = medicalHistory,
                        onValueChange = { next ->
                            if (next.length <= MAX_CLINICAL_FIELD_CHARS) {
                                medicalHistory = next
                            }
                        },
                        placeholder = "Ex.: Videolaringoscopia em 10/2026 sem lesões estruturais; sem cirurgias prévias; histórico de rinite alérgica controlada...",
                        quickChips = listOf(
                            "Sem cirurgias laríngeas",
                            "Videolaringoscopia realizada",
                            "Refluxo gastroesofágico",
                            "Rinite / Alergia respiratória"
                        ),
                        fieldTestTag = "clinical_sheet_medical_history_input"
                    )
                }

                // Section 2: Queixas Principais
                item {
                    ClinicalSectionEditorCard(
                        title = "Queixas Principais",
                        subtitle = "Sintomas vocais relatados, início do quadro e impacto comunicativo.",
                        icon = Icons.Rounded.RecordVoiceOver,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        value = mainComplaints,
                        onValueChange = { next ->
                            if (next.length <= MAX_CLINICAL_FIELD_CHARS) {
                                mainComplaints = next
                            }
                        },
                        placeholder = "Ex.: Cansaço vocal e rouquidão ao final do período escolar/trabalho, esforço para projetar a voz...",
                        quickChips = listOf(
                            "Rouquidão persistente",
                            "Fadiga vocal ao falar",
                            "Esforço fonatório",
                            "Quebras na voz"
                        ),
                        fieldTestTag = "clinical_sheet_main_complaints_input"
                    )
                }

                // Section 3: Metas Terapêuticas
                item {
                    ClinicalSectionEditorCard(
                        title = "Metas Terapêuticas",
                        subtitle = "Objetivos fonoaudiológicos de curto e médio prazo e critérios de alta.",
                        icon = Icons.Rounded.Flag,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        value = therapeuticGoals,
                        onValueChange = { next ->
                            if (next.length <= MAX_CLINICAL_FIELD_CHARS) {
                                therapeuticGoals = next
                            }
                        },
                        placeholder = "Ex.: Reduzir tensão cervical/laríngea, melhorar coordenação pneumofonoarticulatória e atingir TMF >= 14s com conforto...",
                        quickChips = listOf(
                            "Coordenação pneumofonoarticulatória",
                            "Reduzir tensão laríngea",
                            "Aumentar TMF",
                            "Higiene e hidratação vocal"
                        ),
                        fieldTestTag = "clinical_sheet_therapeutic_goals_input"
                    )
                }
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Descartar alterações?") },
            text = {
                Text("Você editou campos da ficha clínica que ainda não foram salvos no Firebase Firestore.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        onSave(medicalHistory, mainComplaints, therapeuticGoals)
                    },
                    modifier = Modifier.testTag("discard_dialog_save_btn")
                ) {
                    Text("Salvar agora")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBack()
                    },
                    modifier = Modifier.testTag("discard_dialog_confirm_btn")
                ) {
                    Text("Descartar e sair")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClinicalSectionEditorCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    quickChips: List<String>,
    fieldTestTag: String
) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = accentColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                quickChips.forEach { suggestion ->
                    AssistChip(
                        onClick = {
                            val trimmed = value.trimEnd()
                            val appended = when {
                                trimmed.isEmpty() -> suggestion
                                trimmed.endsWith(".") || trimmed.endsWith(";") -> "$trimmed $suggestion"
                                else -> "$trimmed; $suggestion"
                            }.take(MAX_CLINICAL_FIELD_CHARS)
                            onValueChange(appended)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = {
                            Text(suggestion, style = MaterialTheme.typography.labelSmall)
                        }
                    )
                }
            }

            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                minLines = 3,
                maxLines = 8,
                supportingText = {
                    Text("${value.length}/$MAX_CLINICAL_FIELD_CHARS caracteres")
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(fieldTestTag)
            )
        }
    }
}
