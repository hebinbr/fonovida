package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AccessSessionStore
import com.example.data.firebase.FonoRepository
import com.example.data.model.AccessCodeGenerator
import com.example.data.model.PatientAccessCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PatientPortalState {
    data class Locked(val revokedNotice: String? = null) : PatientPortalState
    data object Loading : PatientPortalState
    data class Unlocked(val accessCode: PatientAccessCode) : PatientPortalState
}

class PatientPortalViewModel(
    private val repository: FonoRepository,
    private val accessStore: AccessSessionStore,
    private val currentUserId: String
) : ViewModel() {

    private val savedCodeFlow = MutableStateFlow(accessStore.getRedeemedCode(currentUserId))
    private val _revokedNotice = MutableStateFlow<String?>(null)

    private val _isRedeeming = MutableStateFlow(false)
    val isRedeeming: StateFlow<Boolean> = _isRedeeming.asStateFlow()

    private val _redeemError = MutableStateFlow<String?>(null)
    val redeemError: StateFlow<String?> = _redeemError.asStateFlow()

    private val _completedToday = MutableStateFlow(
        accessStore.getRedeemedCode(currentUserId)
            ?.let { accessStore.getCompletedExercisesToday(it) }
            ?: emptySet()
    )
    val completedToday: StateFlow<Set<String>> = _completedToday.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val portalState: StateFlow<PatientPortalState> = savedCodeFlow
        .flatMapLatest { code ->
            if (code.isNullOrBlank()) {
                _revokedNotice.map { notice -> PatientPortalState.Locked(notice) }
            } else {
                flow {
                    emit(PatientPortalState.Loading)
                    repository.observeRedeemedPatientAccessCode(code)
                        .collect { accessObj ->
                            if (accessObj != null) {
                                _revokedNotice.value = null
                                val activeCode = accessObj.code.ifBlank { code }
                                _completedToday.value = accessStore.getCompletedExercisesToday(activeCode)
                                emit(PatientPortalState.Unlocked(accessObj))
                            } else {
                                accessStore.setRedeemedCode(currentUserId, null)
                                val msg = "Este código foi desativado ou substituído pela fonoaudióloga. Insira o novo código para acessar."
                                _revokedNotice.value = msg
                                savedCodeFlow.value = null
                                emit(PatientPortalState.Locked(msg))
                            }
                        }
                }.catch { err ->
                    emit(
                        PatientPortalState.Locked(
                            err.localizedMessage ?: "Não foi possível validar o código de acesso."
                        )
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = if (savedCodeFlow.value.isNullOrBlank()) {
                PatientPortalState.Locked()
            } else {
                PatientPortalState.Loading
            }
        )

    fun submitAccessCode(rawCode: String) {
        val normalized = AccessCodeGenerator.normalize(rawCode)
        if (normalized.length < 6) {
            _redeemError.value = "Informe o código completo enviado pela fonoaudióloga (ex.: FV8K4M2P)."
            return
        }
        viewModelScope.launch {
            _redeemError.value = null
            _revokedNotice.value = null
            _isRedeeming.value = true
            val result = repository.redeemPatientAccessCode(normalized)
            _isRedeeming.value = false
            result.onSuccess { accessObj ->
                val resolvedCode = accessObj.code.ifBlank { normalized }
                accessStore.setRedeemedCode(currentUserId, resolvedCode)
                _completedToday.value = accessStore.getCompletedExercisesToday(resolvedCode)
                savedCodeFlow.value = resolvedCode
            }.onFailure { err ->
                _redeemError.value = err.localizedMessage
                    ?: "Não foi possível validar o código. Confira com sua fonoaudióloga."
            }
        }
    }

    fun clearError() {
        _redeemError.value = null
    }

    fun toggleExerciseDone(code: String, exerciseKey: String) {
        if (code.isBlank() || exerciseKey.isBlank()) return
        _completedToday.value = accessStore.toggleExerciseCompletedToday(code, exerciseKey)
    }

    fun clearCodeAndLock() {
        accessStore.setRedeemedCode(currentUserId, null)
        _redeemError.value = null
        _revokedNotice.value = null
        savedCodeFlow.value = null
    }
}
