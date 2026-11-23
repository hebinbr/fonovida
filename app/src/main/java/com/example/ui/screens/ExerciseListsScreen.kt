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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlaylistAddCheck
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TopAppBar
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
import com.example.data.model.CustomExerciseList
import com.example.data.model.Patient

private enum class ExerciseListFilter(val label: String) {
    TODAS("Todas"),
    MODELOS("Modelos da Clínica"),
    PACIENTES("De Pacientes")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseListsScreen(
    exerciseLists: List<CustomExerciseList>,
    patients: List<Patient>,
    onBack: () -> Unit,
    onCreateList: () -> Unit,
    onEditList: (CustomExerciseList) -> Unit,
    onDeleteList: (String) -> Unit,
    onApplyList: (CustomExerciseList) -> Unit,
    onShareList: (CustomExerciseList) -> Unit,
    onExportPdf: (CustomExerciseList) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(ExerciseListFilter.TODAS) }
    var pendingDeleteList by remember { mutableStateOf<CustomExerciseList?>(null) }

    val patientLookup = remember(patients) {
        patients.associateBy { it.id }
    }

    val filteredLists = remember(exerciseLists, selectedFilter) {
        when (selectedFilter) {
            ExerciseListFilter.TODAS -> exerciseLists
            ExerciseListFilter.MODELOS -> exerciseLists.filter { it.patientId.isBlank() }
            ExerciseListFilter.PACIENTES -> exerciseLists.filter { it.patientId.isNotBlank() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Listas Personalizadas",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1
                        )
                        Text(
                            text = "${exerciseLists.size} lista(s) salva(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("exercise_lists_back_btn")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateList,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("fab_create_exercise_list")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Gerar lista personalizada")
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Gerador de Roteiros Vocais",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Crie listas personalizadas a partir de protocolos clínicos ou exercícios próprios, aplique direto ao plano do paciente, envie ao responsável ou exporte em PDF.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Button(
                                onClick = onCreateList,
                                modifier = Modifier.testTag("generate_new_exercise_list_btn")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Gerar Nova Lista")
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ExerciseListFilter.entries.forEach { filter ->
                            val count = when (filter) {
                                ExerciseListFilter.TODAS -> exerciseLists.size
                                ExerciseListFilter.MODELOS -> exerciseLists.count { it.patientId.isBlank() }
                                ExerciseListFilter.PACIENTES -> exerciseLists.count { it.patientId.isNotBlank() }
                            }
                            FilterChip(
                                selected = selectedFilter == filter,
                                onClick = { selectedFilter = filter },
                                label = { Text("${filter.label} ($count)") },
                                modifier = Modifier.testTag("list_filter_${filter.name.lowercase()}")
                            )
                        }
                    }
                }

                if (filteredLists.isEmpty()) {
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
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FormatListBulleted,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = if (exerciseLists.isEmpty()) "Nenhuma lista personalizada criada"
                                    else "Nenhuma lista nesta categoria",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = if (exerciseLists.isEmpty())
                                        "Gere sua primeira lista personalizada usando protocolos prontos de disfonia, ressonância, TMF ou articulação."
                                    else "Troque o filtro acima ou crie uma nova lista.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (exerciseLists.isEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Button(onClick = onCreateList) {
                                        Text("Gerar Primeira Lista")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    items(filteredLists, key = { it.id }) { list ->
                        val linkedPatient = patientLookup[list.patientId]
                        ExerciseListCard(
                            list = list,
                            linkedPatientName = linkedPatient?.fullName ?: list.patientName,
                            onApply = { onApplyList(list) },
                            onExportPdf = { onExportPdf(list) },
                            onShare = { onShareList(list) },
                            onEdit = { onEditList(list) },
                            onDelete = { pendingDeleteList = list }
                        )
                    }
                }
            }
        }
    }

    pendingDeleteList?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteList = null },
            title = { Text("Excluir lista personalizada?") },
            text = {
                Text("A lista \"${target.title}\" será removida. Os exercícios já aplicados a prontuários permanecerão ativos.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteList(target.id)
                        pendingDeleteList = null
                    }
                ) { Text("Excluir") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteList = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ExerciseListCard(
    list: CustomExerciseList,
    linkedPatientName: String,
    onApply: () -> Unit,
    onExportPdf: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("exercise_list_card_${list.id}")
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
                        text = list.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${list.items.size} exercício(s) • ${list.totalWeeklyMinutes} min/semana",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val isPatientBound = list.patientId.isNotBlank()
                Surface(
                    color = if (isPatientBound) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (isPatientBound && linkedPatientName.isNotBlank()) linkedPatientName
                        else "Modelo da clínica",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPatientBound) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            if (list.clinicalObjective.isNotBlank()) {
                Text(
                    text = list.clinicalObjective,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    list.items.forEachIndexed { idx, item ->
                        Column {
                            Text(
                                text = "${idx + 1}. ${item.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${item.exerciseCategory.label} • ${item.durationMinutes} min • ${item.frequencyPerWeek}x/sem",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalButton(
                    onClick = onApply,
                    modifier = Modifier.testTag("apply_list_btn_${list.id}")
                ) {
                    Icon(Icons.Rounded.PlaylistAddCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Aplicar")
                }

                OutlinedButton(
                    onClick = onExportPdf,
                    modifier = Modifier.testTag("pdf_list_btn_${list.id}")
                ) {
                    Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("PDF")
                }

                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.testTag("share_list_btn_${list.id}")
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Enviar")
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Rounded.Edit,
                        contentDescription = "Editar lista",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = "Excluir lista",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
