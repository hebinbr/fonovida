package com.example.ui.navigation

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.auth.signOut
import com.example.data.model.CustomExerciseList
import com.example.data.model.ExerciseCategory
import com.example.data.model.Patient
import com.example.notifications.ReminderScheduler
import com.example.pdf.ClinicalPdfExporter
import com.example.ui.components.EditProfileDialog
import com.example.ui.components.ExerciseListBuilderDialog
import com.example.ui.components.LogSessionDialog
import com.example.ui.components.PatientAccessCodesDialog
import com.example.ui.components.PatientFormDialog
import com.example.ui.components.PickListForPatientDialog
import com.example.ui.components.PickPatientForListDialog
import com.example.ui.components.PrescribeExerciseDialog
import com.example.ui.components.ScheduleAppointmentDialog
import com.example.ui.components.WeeklyFrequencyGoalDialog
import com.example.ui.screens.AgendaScreen
import com.example.ui.screens.ClinicalSheetScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.ExerciseListsScreen
import com.example.ui.screens.PatientDetailScreen
import com.example.ui.screens.PatientsScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.viewmodel.ClinicData
import com.example.ui.viewmodel.FonoViewModel
import com.example.ui.viewmodel.UiState
import kotlinx.serialization.Serializable

@Serializable
data object RouteDashboard

@Serializable
data object RoutePatients

@Serializable
data class RoutePatientDetail(val patientId: String)

@Serializable
data class RoutePatientClinicalSheet(val patientId: String)

@Serializable
data object RouteReports

@Serializable
data object RouteAgenda

@Serializable
data object RouteProfile

@Serializable
data object RouteExerciseLists

private data class TopLevelNavItem<T : Any>(
    val route: T,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun FonoAuthenticatedShell(
    viewModel: FonoViewModel,
    fallbackName: String,
    fallbackEmail: String,
    pendingNotificationApptId: String = "",
    onConsumeNotificationApptId: () -> Unit = {},
    onSwitchToPatientMode: () -> Unit = {},
    onSignedOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    LaunchedEffect(pendingNotificationApptId) {
        if (pendingNotificationApptId.isNotBlank()) {
            viewModel.markAppointmentReminderSent(pendingNotificationApptId)
            onConsumeNotificationApptId()
        }
    }

    val clinicState by viewModel.clinicState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled silently during scheduling */ }

    fun ensureNotificationPermissionPrompted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !ReminderScheduler.hasNotificationPermission(context)
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var showPatientDialog by remember { mutableStateOf(false) }
    var editingPatient by remember { mutableStateOf<Patient?>(null) }
    var editingWeeklyGoalPatient by remember { mutableStateOf<Patient?>(null) }
    var prescribeForPatientId by remember { mutableStateOf<String?>(null) }
    var logSessionTarget by remember {
        mutableStateOf<Triple<Patient, String, ExerciseCategory>?>(null)
    }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var schedulePrefill by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showExerciseListBuilder by remember { mutableStateOf(false) }
    var editingExerciseList by remember { mutableStateOf<CustomExerciseList?>(null) }
    var listPreselectedPatient by remember { mutableStateOf<Patient?>(null) }
    var pickingPatientForList by remember { mutableStateOf<CustomExerciseList?>(null) }
    var pickingListForPatient by remember { mutableStateOf<Patient?>(null) }
    var showAccessCodesDialog by remember { mutableStateOf(false) }
    var confirmWhatsAppTarget by remember { mutableStateOf<com.example.data.model.Appointment?>(null) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val topItems = remember {
        listOf(
            TopLevelNavItem(RouteDashboard, "Início", Icons.Rounded.Home, Icons.Outlined.Home, "nav_dashboard"),
            TopLevelNavItem(RoutePatients, "Pacientes", Icons.Rounded.People, Icons.Outlined.People, "nav_patients"),
            TopLevelNavItem(RouteReports, "Relatórios", Icons.Rounded.Assessment, Icons.Outlined.Assessment, "nav_reports"),
            TopLevelNavItem(RouteAgenda, "Agenda", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_agenda"),
            TopLevelNavItem(RouteProfile, "Perfil", Icons.Rounded.Person, Icons.Outlined.Person, "nav_profile")
        )
    }

    val isDetailRoute = currentDestination?.hasRoute<RoutePatientDetail>() == true ||
        currentDestination?.hasRoute<RoutePatientClinicalSheet>() == true ||
        currentDestination?.hasRoute<RouteExerciseLists>() == true

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail && !isDetailRoute) {
                    NavigationBar {
                        topItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail && !isDetailRoute) {
                    NavigationRail(
                        modifier = Modifier
                            .fillMaxHeight()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        Spacer(Modifier.height(12.dp))
                        topItems.forEach { item ->
                            val selected = currentDestination?.hasRoute(item.route::class) == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(item.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = item.label
                                    )
                                },
                                label = { Text(item.label) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    when (val state = clinicState) {
                        UiState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Carregando prontuários e agenda...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is UiState.Error -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Não foi possível carregar os dados",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is UiState.Success<ClinicData> -> {
                            val data = state.data
                            val therapistDisplay = data.profile?.displayName?.ifBlank { fallbackName } ?: fallbackName
                            val clinicDisplay = data.profile?.clinicName.orEmpty()
                            val customReminderTemplate = data.profile?.customReminderTemplate.orEmpty()

                            LaunchedEffect(
                                data.patients,
                                data.appointments,
                                therapistDisplay,
                                clinicDisplay,
                                customReminderTemplate
                            ) {
                                ReminderScheduler.syncTherapistReminders(
                                    context = context,
                                    patients = data.patients,
                                    appointments = data.appointments,
                                    therapistName = therapistDisplay,
                                    customReminderTemplate = customReminderTemplate,
                                    clinicName = clinicDisplay
                                )
                            }

                            NavHost(
                                navController = navController,
                                startDestination = RouteDashboard
                            ) {
                                composable<RouteDashboard> {
                                    DashboardScreen(
                                        data = data,
                                        fallbackName = fallbackName,
                                        onOpenPatient = { pid ->
                                            navController.navigate(RoutePatientDetail(pid))
                                        },
                                        onOpenPatientsTab = { navigateTopLevel(RoutePatients) },
                                        onOpenReportsTab = { navigateTopLevel(RouteReports) },
                                        onOpenAgendaTab = { navigateTopLevel(RouteAgenda) },
                                        onAddPatientClick = {
                                            editingPatient = null
                                            showPatientDialog = true
                                        },
                                        onScheduleAppointmentClick = {
                                            if (data.patients.isEmpty()) {
                                                viewModel.notifyMessage("Cadastre um paciente antes de agendar uma consulta.")
                                                editingPatient = null
                                                showPatientDialog = true
                                            } else {
                                                schedulePrefill = null
                                                showScheduleDialog = true
                                            }
                                        },
                                        onOpenAccessCodes = {
                                            showAccessCodesDialog = true
                                        },
                                        onSendAppointmentReminder = { appt ->
                                            viewModel.dispatchGuardianAppointmentReminder(
                                                context = context,
                                                appointment = appt,
                                                therapistName = therapistDisplay,
                                                customTemplate = customReminderTemplate,
                                                clinicName = clinicDisplay
                                            )
                                        },
                                        onConfirmAppointment = { appt ->
                                            viewModel.updateAppointmentStatus(
                                                context = context,
                                                appointment = appt,
                                                newStatus = com.example.data.model.AppointmentStatus.CONFIRMADA
                                            )
                                            confirmWhatsAppTarget = appt
                                        }
                                    )
                                }

                                composable<RoutePatients> {
                                    PatientsScreen(
                                        patients = data.patients,
                                        sessions = data.sessions,
                                        accessCodes = data.accessCodes,
                                        exerciseListsCount = data.exerciseLists.size,
                                        onOpenExerciseLists = {
                                            navController.navigate(RouteExerciseLists)
                                        },
                                        onPatientClick = { pid ->
                                            navController.navigate(RoutePatientDetail(pid))
                                        },
                                        onOpenClinicalSheet = { pid ->
                                            navController.navigate(RoutePatientClinicalSheet(pid))
                                        },
                                        onAddPatientClick = {
                                            editingPatient = null
                                            showPatientDialog = true
                                        },
                                        onGenerateAccessCode = { pat ->
                                            viewModel.generatePatientAccessCode(
                                                patient = pat,
                                                data = data,
                                                fallbackName = fallbackName
                                            )
                                        },
                                        onCopyAccessCode = { code ->
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            clipboard?.setPrimaryClip(ClipData.newPlainText("Código FonoVida", code))
                                            viewModel.notifyMessage("Código $code copiado!")
                                        },
                                        onShareAccessCode = { pat, code ->
                                            viewModel.dispatchAccessCodeShare(
                                                context = context,
                                                patient = pat,
                                                code = code,
                                                therapistName = therapistDisplay,
                                                clinicName = data.profile?.clinicName ?: ""
                                            )
                                        }
                                    )
                                }

                                composable<RouteExerciseLists> {
                                    ExerciseListsScreen(
                                        exerciseLists = data.exerciseLists,
                                        patients = data.patients,
                                        onBack = { navController.popBackStack() },
                                        onCreateList = {
                                            editingExerciseList = null
                                            listPreselectedPatient = null
                                            showExerciseListBuilder = true
                                        },
                                        onEditList = { list ->
                                            editingExerciseList = list
                                            listPreselectedPatient = null
                                            showExerciseListBuilder = true
                                        },
                                        onDeleteList = { listId ->
                                            viewModel.deleteExerciseList(listId)
                                        },
                                        onApplyList = { list ->
                                            val boundPatient = data.patients.firstOrNull { it.id == list.patientId }
                                            if (boundPatient != null) {
                                                viewModel.applyExerciseListToPatient(boundPatient, list)
                                            } else {
                                                pickingPatientForList = list
                                            }
                                        },
                                        onShareList = { list ->
                                            val boundPatient = data.patients.firstOrNull { it.id == list.patientId }
                                            viewModel.dispatchExerciseListShare(
                                                context = context,
                                                patient = boundPatient,
                                                list = list,
                                                therapistName = therapistDisplay
                                            )
                                        },
                                        onExportPdf = { list ->
                                            val boundPatient = data.patients.firstOrNull { it.id == list.patientId }
                                            ClinicalPdfExporter.generateAndShareExerciseListPdf(
                                                context = context,
                                                list = list,
                                                patient = boundPatient,
                                                profile = data.profile
                                            ).onSuccess {
                                                viewModel.notifyMessage("PDF da lista gerado! Escolha o app para compartilhar.")
                                            }.onFailure { err ->
                                                viewModel.notifyMessage(err.localizedMessage ?: "Erro ao gerar PDF da lista.")
                                            }
                                        }
                                    )
                                }

                                composable<RouteReports> {
                                    ReportsScreen(
                                        patients = data.patients,
                                        sessions = data.sessions,
                                        exercises = data.exercises,
                                        profile = data.profile,
                                        onOpenPatient = { pid ->
                                            navController.navigate(RoutePatientDetail(pid))
                                        },
                                        onAddPatientClick = {
                                            editingPatient = null
                                            showPatientDialog = true
                                        },
                                        onPreviewPdf = { patient, timeframe, filteredSessions, patExercises ->
                                            ClinicalPdfExporter.openPdfPreview(
                                                context = context,
                                                patient = patient,
                                                sessions = filteredSessions,
                                                exercises = patExercises,
                                                profile = data.profile,
                                                periodLabel = timeframe.label
                                            ).onFailure { err ->
                                                viewModel.notifyMessage(
                                                    err.localizedMessage ?: "Nenhum visualizador de PDF encontrado."
                                                )
                                            }
                                        },
                                        onExportPdf = { patient, timeframe, filteredSessions, patExercises ->
                                            ClinicalPdfExporter.generateAndShareReport(
                                                context = context,
                                                patient = patient,
                                                sessions = filteredSessions,
                                                exercises = patExercises,
                                                profile = data.profile,
                                                periodLabel = timeframe.label
                                            ).onSuccess {
                                                viewModel.notifyMessage("Relatório PDF gerado! Escolha o app para compartilhar.")
                                            }.onFailure { err ->
                                                viewModel.notifyMessage(
                                                    err.localizedMessage ?: "Erro ao gerar relatório PDF."
                                                )
                                            }
                                        }
                                    )
                                }

                                composable<RoutePatientDetail> { entry ->
                                    val route = entry.toRoute<RoutePatientDetail>()
                                    val patient = data.patients.firstOrNull { it.id == route.patientId }
                                    if (patient == null) {
                                        LaunchedEffect(Unit) {
                                            navController.popBackStack()
                                        }
                                    } else {
                                        val patExercises = data.exercises.filter { it.patientId == patient.id }
                                        val patSessions = data.sessions.filter { it.patientId == patient.id }
                                        val patLists = data.exerciseLists.filter { it.patientId == patient.id }
                                        val activeAccessCode = data.accessCodes.firstOrNull {
                                            it.patientId == patient.id && it.active
                                        }

                                        PatientDetailScreen(
                                            patient = patient,
                                            exercises = patExercises,
                                            sessions = patSessions,
                                            patientExerciseLists = patLists,
                                            totalExerciseListsCount = data.exerciseLists.size,
                                            profile = data.profile,
                                            activeAccessCode = activeAccessCode,
                                            isAccessCodeBusy = isBusy,
                                            onBack = { navController.popBackStack() },
                                            onOpenClinicalSheet = {
                                                navController.navigate(RoutePatientClinicalSheet(patient.id))
                                            },
                                            onDefineWeeklyGoal = {
                                                editingWeeklyGoalPatient = patient
                                            },
                                            onEditPatient = {
                                                editingPatient = patient
                                                showPatientDialog = true
                                            },
                                            onDeletePatient = {
                                                viewModel.deletePatient(patient.id) {
                                                    navController.popBackStack()
                                                }
                                            },
                                            onGenerateAccessCode = {
                                                viewModel.generatePatientAccessCode(
                                                    patient = patient,
                                                    data = data,
                                                    fallbackName = fallbackName
                                                )
                                            },
                                            onRevokeAccessCode = { code ->
                                                viewModel.revokePatientAccessCode(code)
                                            },
                                            onShareAccessCode = { code ->
                                                viewModel.dispatchAccessCodeShare(
                                                    context = context,
                                                    patient = patient,
                                                    code = code,
                                                    therapistName = therapistDisplay,
                                                    clinicName = data.profile?.clinicName ?: ""
                                                )
                                            },
                                            onCopyAccessCode = { code ->
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                clipboard?.setPrimaryClip(ClipData.newPlainText("Código FonoVida", code))
                                                viewModel.notifyMessage("Código $code copiado!")
                                            },
                                            onPrescribeExercise = {
                                                prescribeForPatientId = patient.id
                                            },
                                            onToggleExerciseActive = { ex ->
                                                viewModel.toggleExerciseActive(ex)
                                            },
                                            onDeleteExercise = { exId ->
                                                viewModel.deleteExercise(exId, patient.id)
                                            },
                                            onGenerateExerciseList = {
                                                editingExerciseList = null
                                                listPreselectedPatient = patient
                                                showExerciseListBuilder = true
                                            },
                                            onApplySavedExerciseList = {
                                                pickingListForPatient = patient
                                            },
                                            onApplySpecificList = { customList ->
                                                viewModel.applyExerciseListToPatient(patient, customList)
                                            },
                                            onEditExerciseList = { customList ->
                                                editingExerciseList = customList
                                                listPreselectedPatient = patient
                                                showExerciseListBuilder = true
                                            },
                                            onDeleteExerciseList = { listId ->
                                                viewModel.deleteExerciseList(listId)
                                            },
                                            onShareExerciseList = { customList ->
                                                viewModel.dispatchExerciseListShare(
                                                    context = context,
                                                    patient = patient,
                                                    list = customList,
                                                    therapistName = therapistDisplay
                                                )
                                            },
                                            onExportExerciseListPdf = { customList ->
                                                ClinicalPdfExporter.generateAndShareExerciseListPdf(
                                                    context = context,
                                                    list = customList,
                                                    patient = patient,
                                                    profile = data.profile
                                                ).onSuccess {
                                                    viewModel.notifyMessage("PDF da lista gerado! Escolha o app para compartilhar.")
                                                }.onFailure { err ->
                                                    viewModel.notifyMessage(err.localizedMessage ?: "Erro ao gerar PDF da lista.")
                                                }
                                            },
                                            onLogSession = { prefillTitle, prefillCat ->
                                                logSessionTarget = Triple(patient, prefillTitle, prefillCat)
                                            },
                                            onDeleteSession = { sessId ->
                                                viewModel.deleteSession(sessId)
                                            },
                                            onPreviewPdf = { timeframe, filteredSessions ->
                                                ClinicalPdfExporter.openPdfPreview(
                                                    context = context,
                                                    patient = patient,
                                                    sessions = filteredSessions,
                                                    exercises = patExercises,
                                                    profile = data.profile,
                                                    periodLabel = timeframe.label
                                                ).onFailure { err ->
                                                    viewModel.notifyMessage(
                                                        err.localizedMessage ?: "Nenhum visualizador de PDF encontrado."
                                                    )
                                                }
                                            },
                                            onExportPdf = { timeframe, filteredSessions ->
                                                ClinicalPdfExporter.generateAndShareReport(
                                                    context = context,
                                                    patient = patient,
                                                    sessions = filteredSessions,
                                                    exercises = patExercises,
                                                    profile = data.profile,
                                                    periodLabel = timeframe.label
                                                ).onSuccess {
                                                    viewModel.notifyMessage("Relatório PDF gerado! Escolha o app para compartilhar.")
                                                }.onFailure { err ->
                                                    viewModel.notifyMessage(
                                                        err.localizedMessage ?: "Erro ao gerar relatório PDF."
                                                    )
                                                }
                                            },
                                            onSendExerciseReminder = {
                                                viewModel.dispatchGuardianExerciseReminder(
                                                    context = context,
                                                    patient = patient,
                                                    activeExercises = patExercises.filter { it.activeFlag },
                                                    therapistName = therapistDisplay
                                                )
                                            }
                                        )
                                    }
                                }

                                composable<RoutePatientClinicalSheet> { entry ->
                                    val route = entry.toRoute<RoutePatientClinicalSheet>()
                                    val patient = data.patients.firstOrNull { it.id == route.patientId }
                                    if (patient == null) {
                                        LaunchedEffect(Unit) {
                                            navController.popBackStack()
                                        }
                                    } else {
                                        ClinicalSheetScreen(
                                            patient = patient,
                                            isBusy = isBusy,
                                            onBack = { navController.popBackStack() },
                                            onSave = { medHistory, complaints, goals ->
                                                viewModel.saveClinicalSheet(
                                                    patientId = patient.id,
                                                    medicalHistory = medHistory,
                                                    mainComplaints = complaints,
                                                    therapeuticGoals = goals
                                                )
                                            }
                                        )
                                    }
                                }

                                composable<RouteAgenda> {
                                    AgendaScreen(
                                        appointments = data.appointments,
                                        slotBlocks = data.slotBlocks,
                                        hasPatients = data.patients.isNotEmpty(),
                                        isBusy = isBusy,
                                        onScheduleClick = {
                                            if (data.patients.isEmpty()) {
                                                viewModel.notifyMessage("Cadastre um paciente antes de agendar.")
                                                editingPatient = null
                                                showPatientDialog = true
                                            } else {
                                                schedulePrefill = null
                                                showScheduleDialog = true
                                            }
                                        },
                                        onScheduleAtSlot = { dateMillis, hour, _ ->
                                            if (data.patients.isEmpty()) {
                                                viewModel.notifyMessage("Cadastre um paciente antes de agendar.")
                                                editingPatient = null
                                                showPatientDialog = true
                                            } else {
                                                schedulePrefill = dateMillis to hour
                                                showScheduleDialog = true
                                            }
                                        },
                                        onBlockSlot = { dateKey, hour, minute, dur, reason ->
                                            viewModel.blockTimeSlot(
                                                dateKey = dateKey,
                                                hour = hour,
                                                minute = minute,
                                                durationMinutes = dur,
                                                reason = reason
                                            )
                                        },
                                        onUnblockSlot = { blockId ->
                                            viewModel.unblockTimeSlot(blockId)
                                        },
                                        onOpenPatient = { pid ->
                                            navController.navigate(RoutePatientDetail(pid))
                                        },
                                        onUpdateStatus = { appt, st ->
                                            viewModel.updateAppointmentStatus(context, appt, st)
                                            if (st == com.example.data.model.AppointmentStatus.CONFIRMADA) {
                                                confirmWhatsAppTarget = appt
                                            }
                                        },
                                        onSendGuardianReminder = { appt ->
                                            viewModel.dispatchGuardianAppointmentReminder(
                                                context = context,
                                                appointment = appt,
                                                therapistName = therapistDisplay,
                                                customTemplate = customReminderTemplate,
                                                clinicName = clinicDisplay
                                            )
                                        },
                                        onDeleteAppointment = { apptId ->
                                            viewModel.deleteAppointment(context, apptId)
                                        }
                                    )
                                }

                                composable<RouteProfile> {
                                    ProfileScreen(
                                        profile = data.profile,
                                        fallbackName = fallbackName,
                                        fallbackEmail = fallbackEmail,
                                        patientCount = data.patients.size,
                                        sessionCount = data.sessions.size,
                                        onEditProfile = { showEditProfileDialog = true },
                                        onNotifyFeedback = { viewModel.notifyMessage(it) },
                                        onSwitchToPatientMode = onSwitchToPatientMode,
                                        onSignOut = {
                                            onSignedOut()
                                            signOut(credentialManager, scope)
                                        }
                                    )
                                }
                            }

                            // Modal Dialogs
                            if (showPatientDialog) {
                                PatientFormDialog(
                                    initialPatient = editingPatient,
                                    isBusy = isBusy,
                                    onDismiss = {
                                        showPatientDialog = false
                                        editingPatient = null
                                    },
                                    onConfirm = { name, age, focus, gName, gPhone, gEmail, status, goal, freqGoal, notes, dailyRem, rHour, rMin ->
                                        if (dailyRem) ensureNotificationPermissionPrompted()
                                        viewModel.savePatient(
                                            context = context,
                                            editingPatientId = editingPatient?.id,
                                            fullName = name,
                                            ageYears = age,
                                            clinicalFocus = focus,
                                            guardianName = gName,
                                            guardianPhone = gPhone,
                                            guardianEmail = gEmail,
                                            status = status,
                                            weeklyGoalMinutes = goal,
                                            weeklyExerciseFrequencyGoal = freqGoal,
                                            initialNotes = notes,
                                            dailyExerciseReminderEnabled = dailyRem,
                                            reminderHour = rHour,
                                            reminderMinute = rMin,
                                            onSuccess = {
                                                showPatientDialog = false
                                                editingPatient = null
                                            }
                                        )
                                    }
                                )
                            }

                            editingWeeklyGoalPatient?.let { targetPat ->
                                WeeklyFrequencyGoalDialog(
                                    patient = targetPat,
                                    isBusy = isBusy,
                                    onDismiss = { editingWeeklyGoalPatient = null },
                                    onConfirm = { newFreqGoal ->
                                        viewModel.updatePatientWeeklyFrequencyGoal(
                                            patientId = targetPat.id,
                                            frequencyGoal = newFreqGoal,
                                            onSuccess = { editingWeeklyGoalPatient = null }
                                        )
                                    }
                                )
                            }

                            prescribeForPatientId?.let { pid ->
                                PrescribeExerciseDialog(
                                    isBusy = isBusy,
                                    onDismiss = { prescribeForPatientId = null },
                                    onConfirm = { title, cat, instructions, dur, freq ->
                                        viewModel.prescribeExercise(
                                            patientId = pid,
                                            title = title,
                                            category = cat,
                                            instructions = instructions,
                                            targetDurationMinutes = dur,
                                            frequencyPerWeek = freq,
                                            onSuccess = { prescribeForPatientId = null }
                                        )
                                    }
                                )
                            }

                            logSessionTarget?.let { (pat, preTitle, preCat) ->
                                LogSessionDialog(
                                    patientName = pat.fullName,
                                    prefilledTitle = preTitle,
                                    prefilledCategory = preCat,
                                    isBusy = isBusy,
                                    onDismiss = { logSessionTarget = null },
                                    onConfirm = { title, cat, dur, clarity, stability, comfort, tmf, notes, guidance ->
                                        viewModel.logSession(
                                            patient = pat,
                                            exerciseTitle = title,
                                            category = cat,
                                            durationMinutes = dur,
                                            vocalClarityScore = clarity,
                                            stabilityScore = stability,
                                            comfortScore = comfort,
                                            tmfSeconds = tmf,
                                            clinicalNotes = notes,
                                            homeGuidance = guidance,
                                            onSuccess = { logSessionTarget = null }
                                        )
                                    }
                                )
                            }

                            if (showScheduleDialog) {
                                ScheduleAppointmentDialog(
                                    patients = data.patients,
                                    defaultLeadMinutes = data.profile?.defaultReminderMinutes ?: 60L,
                                    initialDateMillis = schedulePrefill?.first,
                                    initialHour = schedulePrefill?.second,
                                    existingAppointments = data.appointments,
                                    slotBlocks = data.slotBlocks,
                                    isBusy = isBusy,
                                    onDismiss = {
                                        showScheduleDialog = false
                                        schedulePrefill = null
                                    },
                                    onConfirm = { pat, schedMillis, dur, type, notes, remEnabled, remLead, patRemEnabled, patRemLead ->
                                        if (remEnabled || patRemEnabled) ensureNotificationPermissionPrompted()
                                        viewModel.scheduleAppointment(
                                            context = context,
                                            patient = pat,
                                            scheduledAtMillis = schedMillis,
                                            durationMinutes = dur,
                                            sessionType = type,
                                            notes = notes,
                                            reminderEnabled = remEnabled,
                                            reminderLeadMinutes = remLead,
                                            patientReminderEnabled = patRemEnabled,
                                            patientReminderLeadMinutes = patRemLead,
                                            onSuccess = {
                                                showScheduleDialog = false
                                                schedulePrefill = null
                                            }
                                        )
                                    }
                                )
                            }

                            if (showEditProfileDialog) {
                                EditProfileDialog(
                                    profile = data.profile,
                                    fallbackName = fallbackName,
                                    isBusy = isBusy,
                                    onDismiss = { showEditProfileDialog = false },
                                    onConfirm = { name, crfa, clinic, lead, autoRem, customTemplate ->
                                        viewModel.updateTherapistProfile(
                                            displayName = name,
                                            crfa = crfa,
                                            clinicName = clinic,
                                            defaultReminderMinutes = lead,
                                            autoRemindersEnabled = autoRem,
                                            customReminderTemplate = customTemplate,
                                            onSuccess = { showEditProfileDialog = false }
                                        )
                                    }
                                )
                            }

                            if (showExerciseListBuilder) {
                                ExerciseListBuilderDialog(
                                    patients = data.patients,
                                    initialList = editingExerciseList,
                                    preselectedPatient = listPreselectedPatient,
                                    isBusy = isBusy,
                                    onDismiss = {
                                        showExerciseListBuilder = false
                                        editingExerciseList = null
                                        listPreselectedPatient = null
                                    },
                                    onConfirm = { title, objective, pat, items, applyNow ->
                                        viewModel.saveExerciseList(
                                            editingListId = editingExerciseList?.id,
                                            title = title,
                                            clinicalObjective = objective,
                                            patient = pat,
                                            items = items,
                                            applyToPatientNow = applyNow,
                                            onSuccess = {
                                                showExerciseListBuilder = false
                                                editingExerciseList = null
                                                listPreselectedPatient = null
                                            }
                                        )
                                    }
                                )
                            }

                            pickingPatientForList?.let { list ->
                                PickPatientForListDialog(
                                    listTitle = list.title,
                                    patients = data.patients,
                                    isBusy = isBusy,
                                    onDismiss = { pickingPatientForList = null },
                                    onSelectPatient = { pat ->
                                        viewModel.applyExerciseListToPatient(
                                            patient = pat,
                                            list = list,
                                            onSuccess = { pickingPatientForList = null }
                                        )
                                    }
                                )
                            }

                            pickingListForPatient?.let { pat ->
                                PickListForPatientDialog(
                                    patientName = pat.fullName,
                                    lists = data.exerciseLists,
                                    isBusy = isBusy,
                                    onDismiss = { pickingListForPatient = null },
                                    onSelectList = { list ->
                                        viewModel.applyExerciseListToPatient(
                                            patient = pat,
                                            list = list,
                                            onSuccess = { pickingListForPatient = null }
                                        )
                                    }
                                )
                            }

                            if (showAccessCodesDialog) {
                                PatientAccessCodesDialog(
                                    patients = data.patients,
                                    accessCodes = data.accessCodes,
                                    isBusy = isBusy,
                                    onDismiss = { showAccessCodesDialog = false },
                                    onAddPatient = {
                                        editingPatient = null
                                        showPatientDialog = true
                                    },
                                    onGenerateCode = { pat ->
                                        viewModel.generatePatientAccessCode(
                                            patient = pat,
                                            data = data,
                                            fallbackName = fallbackName
                                        )
                                    },
                                    onCopyCode = { code ->
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        clipboard?.setPrimaryClip(ClipData.newPlainText("Código FonoVida", code))
                                        viewModel.notifyMessage("Código $code copiado!")
                                    },
                                    onShareCode = { pat, code ->
                                        viewModel.dispatchAccessCodeShare(
                                            context = context,
                                            patient = pat,
                                            code = code,
                                            therapistName = therapistDisplay,
                                            clinicName = data.profile?.clinicName ?: ""
                                        )
                                    },
                                    onRevokeCode = { code ->
                                        viewModel.revokePatientAccessCode(code)
                                    }
                                )
                            }

                            confirmWhatsAppTarget?.let { appt ->
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = { confirmWhatsAppTarget = null },
                                    title = { Text("Enviar Confirmação no WhatsApp?") },
                                    text = {
                                        val guardian = appt.guardianName.ifBlank { "o responsável" }
                                        Text(
                                            text = "Consulta de ${appt.patientName} confirmada! Deseja abrir o WhatsApp (wa.me) para enviar o aviso de confirmação para $guardian?"
                                        )
                                    },
                                    confirmButton = {
                                        androidx.compose.material3.Button(
                                            onClick = {
                                                viewModel.dispatchGuardianConfirmationNotice(
                                                    context = context,
                                                    appointment = appt,
                                                    therapistName = therapistDisplay
                                                )
                                                confirmWhatsAppTarget = null
                                            },
                                            modifier = Modifier.testTag("confirm_dialog_whatsapp_btn")
                                        ) {
                                            Text("Enviar no WhatsApp")
                                        }
                                    },
                                    dismissButton = {
                                        androidx.compose.material3.TextButton(
                                            onClick = { confirmWhatsAppTarget = null },
                                            modifier = Modifier.testTag("confirm_dialog_skip_btn")
                                        ) {
                                            Text("Agora não")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
