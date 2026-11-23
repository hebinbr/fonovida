package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.authStateFlow
import com.example.data.firebase.FonoRepository
import com.example.notifications.ReminderScheduler
import com.example.ui.navigation.FonoAuthenticatedShell
import com.example.ui.screens.AuthScreen
import com.example.ui.theme.FonoVidaTheme
import com.example.ui.viewmodel.FonoViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ReminderScheduler.ensureNotificationChannel(this)

        setContent {
            FonoVidaTheme {
                AppContent()
            }
        }
    }
}

@Composable
fun AppContent() {
    val auth = Firebase.auth
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = { /* AuthStateListener updates currentUser reactively */ })
    } else {
        key(user.uid) {
            val viewModel: FonoViewModel = viewModel(
                key = user.uid,
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
                fallbackEmail = user.email ?: ""
            )
        }
    }
}
