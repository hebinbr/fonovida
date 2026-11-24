package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.AccessSessionStore
import com.example.data.AppUserRole
import com.example.data.firebase.FonoRepository
import com.example.notifications.ReminderScheduler
import com.example.ui.navigation.FonoAuthenticatedShell
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.PatientCodeGateScreen
import com.example.ui.screens.PatientPortalScreen
import com.example.ui.screens.RoleChoiceScreen
import com.example.ui.theme.FonoVidaTheme
import com.example.ui.viewmodel.FonoViewModel
import com.example.ui.viewmodel.PatientPortalState
import com.example.ui.viewmodel.PatientPortalViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PendingWhatsAppDispatch(
    val uri: String,
    val appointmentId: String
)

class MainActivity : ComponentActivity() {
    private val pendingWhatsAppFlow = MutableStateFlow<PendingWhatsAppDispatch?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderScheduler.ensureNotificationChannel(this)
        handleReminderWhatsAppIntent(intent)

        setContent {
            FonoVidaTheme {
                AppContent(
                    pendingWhatsAppState = pendingWhatsAppFlow.asStateFlow(),
                    onConsumePendingWhatsApp = { pendingWhatsAppFlow.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderWhatsAppIntent(intent)
    }

    private fun handleReminderWhatsAppIntent(intent: Intent?) {
        val waUri = intent?.getStringExtra(ReminderScheduler.EXTRA_WHATSAPP_URI) ?: return
        val rawApptId = intent.getStringExtra(ReminderScheduler.EXTRA_WHATSAPP_APPT_ID).orEmpty()
        val token = intent.getStringExtra(ReminderScheduler.EXTRA_WHATSAPP_TOKEN)
        intent.removeExtra(ReminderScheduler.EXTRA_WHATSAPP_URI)
        intent.removeExtra(ReminderScheduler.EXTRA_WHATSAPP_APPT_ID)
        intent.removeExtra(ReminderScheduler.EXTRA_WHATSAPP_TOKEN)

        if (!ReminderScheduler.isAllowedWhatsAppUri(waUri)) return
        val safeApptId = if (rawApptId.isBlank()) {
            ""
        } else if (ReminderScheduler.isValidSafeDocumentId(rawApptId)) {
            rawApptId
        } else {
            return
        }
        if (!ReminderScheduler.verifyIntentDispatchToken(this, waUri, safeApptId, token)) {
            return
        }
        pendingWhatsAppFlow.value = PendingWhatsAppDispatch(waUri, safeApptId)
    }
}

@Composable
fun AppContent(
    pendingWhatsAppState: StateFlow<PendingWhatsAppDispatch?> = MutableStateFlow(null),
    onConsumePendingWhatsApp: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }
    val accessStore = remember(context) { AccessSessionStore(context) }

    val pendingWhatsApp by pendingWhatsAppState.collectAsStateWithLifecycle()
    var pendingNotificationApptId by remember { mutableStateOf("") }

    LaunchedEffect(pendingWhatsApp) {
        val pending = pendingWhatsApp ?: return@LaunchedEffect
        ReminderScheduler.launchWhatsAppUrl(context, pending.uri)
        if (pending.appointmentId.isNotBlank()) {
            pendingNotificationApptId = pending.appointmentId
        }
        onConsumePendingWhatsApp()
    }

    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    var pendingAuthRole by remember { mutableStateOf<AppUserRole?>(null) }
    var currentRole by remember(user?.uid) {
        mutableStateOf(user?.let { accessStore.getRole(it.uid) } ?: AppUserRole.NONE)
    }
    var cameFromRoleChoice by remember(user?.uid) { mutableStateOf(false) }

    LaunchedEffect(user?.uid, pendingAuthRole) {
        val signedUser = user
        if (signedUser == null) {
            currentRole = AppUserRole.NONE
            cameFromRoleChoice = false
        } else {
            val pending = pendingAuthRole
            if (pending != null && pending != AppUserRole.NONE) {
                accessStore.setRole(signedUser.uid, pending)
                currentRole = pending
                pendingAuthRole = null
            } else {
                currentRole = accessStore.getRole(signedUser.uid)
            }
        }
    }

    if (user == null) {
        AuthScreen(
            onAuthSuccess = { chosenRole ->
                pendingAuthRole = chosenRole
                val signedUser = auth.currentUser
                if (signedUser != null && chosenRole != AppUserRole.NONE) {
                    accessStore.setRole(signedUser.uid, chosenRole)
                    currentRole = chosenRole
                    pendingAuthRole = null
                }
            }
        )
    } else {
        key(user.uid) {
            when (currentRole) {
                AppUserRole.NONE -> {
                    RoleChoiceScreen(
                        userName = user.displayName ?: "",
                        userEmail = user.email ?: "",
                        onSelectTherapist = {
                            cameFromRoleChoice = true
                            accessStore.setRole(user.uid, AppUserRole.THERAPIST)
                            currentRole = AppUserRole.THERAPIST
                        },
                        onSelectPatient = {
                            cameFromRoleChoice = true
                            accessStore.setRole(user.uid, AppUserRole.PATIENT)
                            currentRole = AppUserRole.PATIENT
                        },
                        onSignOut = {
                            accessStore.clearUserSession(user.uid)
                            currentRole = AppUserRole.NONE
                            signOut(credentialManager, scope)
                        }
                    )
                }

                AppUserRole.THERAPIST -> {
                    val viewModel: FonoViewModel = viewModel(
                        key = "therapist_${user.uid}",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY]) {
                                    "APPLICATION_KEY missing from CreationExtras"
                                }
                                val databaseId = app.getString(R.string.firestore_database_id)
                                val db = FirebaseFirestore.getInstance(databaseId)
                                FonoViewModel(
                                    repository = FonoRepository(db),
                                    currentUserId = user.uid,
                                    userDisplayName = user.displayName ?: "Dra. Fonoaudióloga",
                                    userEmail = user.email ?: ""
                                )
                            }
                        }
                    )
                    FonoAuthenticatedShell(
                        viewModel = viewModel,
                        fallbackName = user.displayName ?: "Dra. Fonoaudióloga",
                        fallbackEmail = user.email ?: "",
                        pendingNotificationApptId = pendingNotificationApptId,
                        onConsumeNotificationApptId = { pendingNotificationApptId = "" },
                        onSwitchToPatientMode = {
                            cameFromRoleChoice = true
                            accessStore.setRole(user.uid, AppUserRole.PATIENT)
                            currentRole = AppUserRole.PATIENT
                        },
                        onSignedOut = {
                            accessStore.clearUserSession(user.uid)
                            currentRole = AppUserRole.NONE
                        }
                    )
                }

                AppUserRole.PATIENT -> {
                    val portalViewModel: PatientPortalViewModel = viewModel(
                        key = "patient_${user.uid}",
                        factory = viewModelFactory {
                            initializer {
                                val app = checkNotNull(this[APPLICATION_KEY]) {
                                    "APPLICATION_KEY missing from CreationExtras"
                                }
                                val databaseId = app.getString(R.string.firestore_database_id)
                                val db = FirebaseFirestore.getInstance(databaseId)
                                PatientPortalViewModel(
                                    repository = FonoRepository(db),
                                    accessStore = AccessSessionStore(app),
                                    currentUserId = user.uid
                                )
                            }
                        }
                    )

                    val portalState by portalViewModel.portalState.collectAsStateWithLifecycle()
                    val isRedeeming by portalViewModel.isRedeeming.collectAsStateWithLifecycle()
                    val redeemError by portalViewModel.redeemError.collectAsStateWithLifecycle()
                    val completedToday by portalViewModel.completedToday.collectAsStateWithLifecycle()
                    val reminderSettings by portalViewModel.reminderSettings.collectAsStateWithLifecycle()

                    LaunchedEffect(portalState, reminderSettings) {
                        val unlocked = portalState as? PatientPortalState.Unlocked
                        if (unlocked != null) {
                            portalViewModel.syncReminders(context, unlocked.accessCode)
                        }
                    }

                    if (cameFromRoleChoice) {
                        BackHandler {
                            cameFromRoleChoice = false
                            accessStore.setRole(user.uid, AppUserRole.NONE)
                            currentRole = AppUserRole.NONE
                        }
                    }

                    when (val pState = portalState) {
                        is PatientPortalState.Locked -> {
                            PatientCodeGateScreen(
                                userEmail = user.email ?: "",
                                isRedeeming = isRedeeming,
                                errorMessage = redeemError,
                                revokedNotice = pState.revokedNotice,
                                onClearError = { portalViewModel.clearError() },
                                onSubmitCode = { code -> portalViewModel.submitAccessCode(code) },
                                onSwitchRole = {
                                    accessStore.setRole(user.uid, AppUserRole.NONE)
                                    currentRole = AppUserRole.NONE
                                },
                                onSignOut = {
                                    portalViewModel.clearCodeAndLock(context)
                                    accessStore.clearUserSession(user.uid)
                                    currentRole = AppUserRole.NONE
                                    signOut(credentialManager, scope)
                                }
                            )
                        }

                        PatientPortalState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "Validando código de acesso...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        is PatientPortalState.Unlocked -> {
                            PatientPortalScreen(
                                accessCode = pState.accessCode,
                                completedToday = completedToday,
                                reminderSettings = reminderSettings,
                                onToggleExerciseDone = { exKey ->
                                    portalViewModel.toggleExerciseDone(
                                        code = pState.accessCode.code,
                                        exerciseKey = exKey
                                    )
                                },
                                onUpdateReminderSettings = { enabled, hour, minute ->
                                    portalViewModel.updateReminderSettings(
                                        context = context,
                                        accessCode = pState.accessCode,
                                        enabled = enabled,
                                        hour = hour,
                                        minute = minute
                                    )
                                },
                                onSendTestReminder = {
                                    portalViewModel.sendWorkManagerTestNotification(
                                        context = context,
                                        patientName = pState.accessCode.patientName
                                    )
                                },
                                onChangeCode = { portalViewModel.clearCodeAndLock(context) },
                                onSwitchRole = {
                                    accessStore.setRole(user.uid, AppUserRole.NONE)
                                    currentRole = AppUserRole.NONE
                                },
                                onSignOut = {
                                    portalViewModel.clearCodeAndLock(context)
                                    accessStore.clearUserSession(user.uid)
                                    currentRole = AppUserRole.NONE
                                    signOut(credentialManager, scope)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
