package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.DevicePreferences
import com.example.data.local.FireOrderStatus
import com.example.data.local.GenerationDao
import com.example.data.local.GenerationEntity
import com.example.data.local.GenerationType
import com.example.data.local.LicenseStatus
import com.example.data.local.VidoProDatabase
import com.example.data.network.VidoProBackend
import com.example.data.repository.AiRepository
import com.example.data.repository.HybridAiRepository
import com.example.data.repository.ImageGenerationResult
import com.example.data.repository.MotionPlanResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FireStudioUiState(
    val prompt: String = "",
    val referenceUris: List<Uri> = emptyList(),
    val aspectRatio: String = "16:9",
    val durationSeconds: Int = 10,
    val isEnhancing: Boolean = false,
    val enhanceNotice: String? = null,
    val isGenerating: Boolean = false,
    val activeOrder: GenerationEntity? = null,
    val elapsedSeconds: Int = 0,
    val errorMessage: String? = null,
    val showResultModal: Boolean = false
)

data class ImageStudioUiState(
    val prompt: String = "",
    val aspectRatio: String = "1:1",
    val stylePreset: String = "Cinematic",
    val isGenerating: Boolean = false,
    val result: ImageGenerationResult? = null,
    val latestEntity: GenerationEntity? = null,
    val errorMessage: String? = null
)

data class MotionStudioUiState(
    val subjectPrompt: String = "",
    val cameraMove: String = "Orbit 360",
    val intensity: Int = 65,
    val durationSeconds: Int = 10,
    val isPlanning: Boolean = false,
    val result: MotionPlanResult? = null,
    val errorMessage: String? = null
)

class StudioViewModel(
    private val preferences: DevicePreferences,
    private val generationDao: GenerationDao,
    private val aiRepository: AiRepository
) : ViewModel() {

    private val _fireState = MutableStateFlow(FireStudioUiState())
    val fireState: StateFlow<FireStudioUiState> = _fireState.asStateFlow()

    private val _imageState = MutableStateFlow(ImageStudioUiState())
    val imageState: StateFlow<ImageStudioUiState> = _imageState.asStateFlow()

    private val _motionState = MutableStateFlow(MotionStudioUiState())
    val motionState: StateFlow<MotionStudioUiState> = _motionState.asStateFlow()

    private var orderSequence = 1
    private var timerJob: Job? = null
    private var autoPollJob: Job? = null

    fun updateFirePrompt(value: String) {
        _fireState.update { it.copy(prompt = value, errorMessage = null) }
    }

    fun addReferenceUris(uris: List<Uri>) {
        _fireState.update { state ->
            val combined = (state.referenceUris + uris).distinct().take(10)
            state.copy(referenceUris = combined, errorMessage = null)
        }
    }

    fun removeReferenceUri(uri: Uri) {
        _fireState.update { state ->
            state.copy(referenceUris = state.referenceUris.filterNot { it == uri })
        }
    }

    fun setFireAspectRatio(ratio: String) {
        _fireState.update { it.copy(aspectRatio = ratio) }
    }

    fun setFireDuration(seconds: Int) {
        _fireState.update { it.copy(durationSeconds = seconds) }
    }

    fun magicEnhancePrompt() {
        val current = _fireState.value
        if (current.prompt.isBlank()) {
            _fireState.update { it.copy(errorMessage = "Enter a base prompt before using Magic Enhance.") }
            return
        }
        viewModelScope.launch {
            val prefs = preferences.sessionFlow.first()
            val userId = prefs.effectiveSubscriptionId
            _fireState.update { it.copy(isEnhancing = true, errorMessage = null, enhanceNotice = null) }
            aiRepository.enhancePrompt(
                userId = userId,
                deviceUuid = prefs.deviceUuid,
                rawPrompt = current.prompt,
                referenceCount = current.referenceUris.size,
                aspectRatio = current.aspectRatio,
                durationSeconds = current.durationSeconds
            ).onSuccess { res ->
                _fireState.update {
                    it.copy(
                        prompt = res.enhancedPrompt,
                        isEnhancing = false,
                        enhanceNotice = res.notice
                    )
                }
            }.onFailure { err ->
                _fireState.update {
                    it.copy(
                        isEnhancing = false,
                        errorMessage = err.localizedMessage ?: "Magic Enhance failed."
                    )
                }
            }
        }
    }

    fun igniteFireMode() {
        val current = _fireState.value
        if (current.prompt.isBlank()) {
            _fireState.update { it.copy(errorMessage = "Please enter a prompt to ignite Fire Mode.") }
            return
        }
        if (current.isGenerating) return

        viewModelScope.launch {
            val prefs = preferences.sessionFlow.first()
            val effectiveDur = if (prefs.licenseStatus == LicenseStatus.TRIAL && current.durationSeconds > 15) {
                _fireState.update { it.copy(durationSeconds = 15) }
                15
            } else {
                current.durationSeconds
            }
            val creditCost = when (effectiveDur) {
                30 -> 60
                15 -> 40
                10 -> 30
                else -> 15
            }
            if (!preferences.deductCredits(creditCost)) {
                _fireState.update {
                    it.copy(errorMessage = "Not enough credits ($creditCost required). Sync account or upgrade plan.")
                }
                return@launch
            }

            val userId = prefs.effectiveSubscriptionId
            val uid = HybridAiRepository.generateOrderUid(prefs.deviceUuid, orderSequence++)
            val initialEntity = GenerationEntity(
                orderUid = uid,
                type = GenerationType.FIRE_VIDEO,
                prompt = current.prompt,
                modelName = "Seedance 2.5 (Fire Mode)",
                aspectRatio = current.aspectRatio,
                durationSeconds = effectiveDur,
                referenceUris = current.referenceUris.joinToString("|") { it.toString() },
                referenceCount = current.referenceUris.size,
                status = FireOrderStatus.UPLOADING,
                outputTitle = "Seedance 2.5 · $uid",
                isSamplePlaceholder = false
            )
            val insertedId = generationDao.insert(initialEntity)
            val savedEntity = initialEntity.copy(id = insertedId)

            _fireState.update {
                it.copy(
                    isGenerating = true,
                    activeOrder = savedEntity,
                    elapsedSeconds = 0,
                    errorMessage = null,
                    showResultModal = false
                )
            }
            startElapsedTicker()

            aiRepository.submitFireModeOrder(
                orderUid = uid,
                userId = userId,
                deviceUuid = prefs.deviceUuid,
                prompt = current.prompt,
                durationSeconds = effectiveDur,
                aspectRatio = current.aspectRatio,
                referenceUris = current.referenceUris
            ).onSuccess { result ->
                val remoteBalance = VidoProBackend.client.syncCoinUsage(
                    userId = userId,
                    deviceUuid = prefs.deviceUuid,
                    amount = creditCost,
                    reason = "Fire Mode ($uid)"
                )
                if (remoteBalance != null) {
                    preferences.setCreditsExact(remoteBalance)
                }

                val updated = savedEntity.copy(
                    status = result.status,
                    adminMessage = result.adminMessage,
                    remoteMediaUrl = result.remoteVideoUrl,
                    outputTitle = result.outputTitle,
                    outputSummary = result.outputSummary,
                    isSamplePlaceholder = false
                )
                generationDao.update(updated)
                val isStillWorking = result.status == FireOrderStatus.UPLOADING ||
                    result.status == FireOrderStatus.RENDERING
                if (!isStillWorking) {
                    stopElapsedTicker()
                } else {
                    startAutoPoll(uid, userId, prefs.deviceUuid)
                }
                _fireState.update {
                    it.copy(
                        isGenerating = isStillWorking,
                        activeOrder = updated,
                        showResultModal = result.status == FireOrderStatus.READY
                    )
                }
            }.onFailure { err ->
                stopElapsedTicker()
                stopAutoPoll()
                preferences.addCredits(creditCost)
                val failed = savedEntity.copy(
                    status = FireOrderStatus.FAILED,
                    adminMessage = err.localizedMessage ?: "Dispatch failed. Please retry."
                )
                generationDao.update(failed)
                _fireState.update {
                    it.copy(
                        isGenerating = false,
                        activeOrder = failed,
                        errorMessage = failed.adminMessage
                    )
                }
            }
        }
    }

    fun pollActiveOrder() {
        val active = _fireState.value.activeOrder ?: return
        viewModelScope.launch {
            val prefs = preferences.sessionFlow.first()
            val userId = prefs.effectiveSubscriptionId
            aiRepository.pollFireModeOrder(active.orderUid, userId, prefs.deviceUuid)
                .onSuccess { res ->
                    val updated = active.copy(
                        status = res.status,
                        adminMessage = res.adminMessage,
                        remoteMediaUrl = res.remoteVideoUrl,
                        outputSummary = res.outputSummary,
                        isSamplePlaceholder = false
                    )
                    generationDao.update(updated)
                    val isDone = res.status == FireOrderStatus.READY ||
                        res.status == FireOrderStatus.FAILED ||
                        res.status == FireOrderStatus.CANCELLED
                    if (isDone) {
                        stopElapsedTicker()
                        stopAutoPoll()
                    }
                    _fireState.update {
                        it.copy(
                            isGenerating = !isDone,
                            activeOrder = updated,
                            showResultModal = res.status == FireOrderStatus.READY
                        )
                    }
                }
                .onFailure { err ->
                    _fireState.update { it.copy(errorMessage = err.localizedMessage) }
                }
        }
    }

    private fun startAutoPoll(orderUid: String, userId: String, deviceUuid: String) {
        autoPollJob?.cancel()
        autoPollJob = viewModelScope.launch {
            repeat(36) {
                delay(5000L)
                val cur = _fireState.value.activeOrder ?: return@launch
                if (cur.orderUid != orderUid) return@launch
                if (cur.status != FireOrderStatus.UPLOADING && cur.status != FireOrderStatus.RENDERING) {
                    return@launch
                }
                val res = aiRepository.pollFireModeOrder(orderUid, userId, deviceUuid).getOrNull() ?: return@repeat
                val updated = cur.copy(
                    status = res.status,
                    adminMessage = res.adminMessage,
                    remoteMediaUrl = res.remoteVideoUrl,
                    outputSummary = res.outputSummary,
                    isSamplePlaceholder = false
                )
                generationDao.update(updated)
                val finished = res.status == FireOrderStatus.READY ||
                    res.status == FireOrderStatus.FAILED ||
                    res.status == FireOrderStatus.CANCELLED
                if (finished) {
                    stopElapsedTicker()
                }
                _fireState.update {
                    it.copy(
                        isGenerating = !finished,
                        activeOrder = updated,
                        showResultModal = res.status == FireOrderStatus.READY
                    )
                }
                if (finished) return@launch
            }
        }
    }

    fun openResultModal() {
        _fireState.update { it.copy(showResultModal = true) }
    }

    fun closeResultModal() {
        _fireState.update { it.copy(showResultModal = false) }
    }

    fun dismissActiveOrder() {
        val uid = _fireState.value.activeOrder?.orderUid
        stopElapsedTicker()
        stopAutoPoll()
        _fireState.update { it.copy(activeOrder = null, isGenerating = false, elapsedSeconds = 0) }
        if (!uid.isNullOrBlank()) {
            viewModelScope.launch {
                aiRepository.cleanupFireOrder(uid)
            }
        }
    }

    fun refreshActiveOrderFromDb(orderUid: String) {
        viewModelScope.launch {
            val fresh = generationDao.getByOrderUid(orderUid)
            if (fresh != null) {
                _fireState.update { it.copy(activeOrder = fresh) }
            }
        }
    }

    // --- Image Studio ---
    fun updateImagePrompt(value: String) {
        _imageState.update { it.copy(prompt = value, errorMessage = null) }
    }

    fun setImageAspectRatio(ratio: String) {
        _imageState.update { it.copy(aspectRatio = ratio) }
    }

    fun setImageStyle(style: String) {
        _imageState.update { it.copy(stylePreset = style) }
    }

    fun generateImage() {
        val current = _imageState.value
        if (current.prompt.isBlank()) {
            _imageState.update { it.copy(errorMessage = "Enter an image prompt first.") }
            return
        }
        viewModelScope.launch {
            if (!preferences.deductCredits(10)) {
                _imageState.update { it.copy(errorMessage = "Insufficient credits (10 required).") }
                return@launch
            }
            _imageState.update { it.copy(isGenerating = true, errorMessage = null) }
            val prefs = preferences.sessionFlow.first()
            val userId = prefs.effectiveSubscriptionId
            aiRepository.generateImagePreview(
                userId = userId,
                deviceUuid = prefs.deviceUuid,
                prompt = current.prompt,
                stylePreset = current.stylePreset,
                aspectRatio = current.aspectRatio
            ).onSuccess { result ->
                val remoteBalance = VidoProBackend.client.syncCoinUsage(
                    userId = userId,
                    deviceUuid = prefs.deviceUuid,
                    amount = 10,
                    reason = "Image Studio"
                )
                if (remoteBalance != null) {
                    preferences.setCreditsExact(remoteBalance)
                }
                val uid = HybridAiRepository.generateOrderUid(prefs.deviceUuid, orderSequence++)
                val entity = GenerationEntity(
                    orderUid = uid,
                    type = GenerationType.IMAGE,
                    prompt = current.prompt,
                    modelName = "Image Studio · ${current.stylePreset}",
                    aspectRatio = current.aspectRatio,
                    durationSeconds = 0,
                    status = FireOrderStatus.READY,
                    remoteMediaUrl = result.mediaUrl,
                    outputTitle = result.title,
                    outputSummary = result.summary,
                    isSamplePlaceholder = false
                )
                val id = generationDao.insert(entity)
                _imageState.update {
                    it.copy(
                        isGenerating = false,
                        result = result,
                        latestEntity = entity.copy(id = id)
                    )
                }
            }.onFailure { err ->
                preferences.addCredits(10)
                _imageState.update {
                    it.copy(isGenerating = false, errorMessage = err.localizedMessage ?: "Image generation failed.")
                }
            }
        }
    }

    // --- Motion Control ---
    fun updateMotionSubject(value: String) {
        _motionState.update { it.copy(subjectPrompt = value, errorMessage = null) }
    }

    fun setCameraMove(move: String) {
        _motionState.update { it.copy(cameraMove = move) }
    }

    fun setMotionIntensity(intensity: Int) {
        _motionState.update { it.copy(intensity = intensity) }
    }

    fun setMotionDuration(seconds: Int) {
        _motionState.update { it.copy(durationSeconds = seconds) }
    }

    fun generateMotionPlan() {
        val current = _motionState.value
        viewModelScope.launch {
            _motionState.update { it.copy(isPlanning = true, errorMessage = null) }
            val prefs = preferences.sessionFlow.first()
            val userId = prefs.effectiveSubscriptionId
            aiRepository.planMotionChoreography(
                userId = userId,
                deviceUuid = prefs.deviceUuid,
                subjectPrompt = current.subjectPrompt,
                cameraMove = current.cameraMove,
                intensity = current.intensity,
                durationSeconds = current.durationSeconds
            ).onSuccess { plan ->
                val uid = HybridAiRepository.generateOrderUid(prefs.deviceUuid, orderSequence++)
                generationDao.insert(
                    GenerationEntity(
                        orderUid = uid,
                        type = GenerationType.MOTION,
                        prompt = plan.suggestedFirePrompt,
                        modelName = "Motion Control",
                        aspectRatio = "16:9",
                        durationSeconds = current.durationSeconds,
                        motionPreset = current.cameraMove,
                        motionIntensity = current.intensity,
                        status = FireOrderStatus.READY,
                        outputTitle = plan.title,
                        outputSummary = plan.choreographySummary,
                        isSamplePlaceholder = false
                    )
                )
                _motionState.update { it.copy(isPlanning = false, result = plan) }
            }.onFailure { err ->
                _motionState.update {
                    it.copy(isPlanning = false, errorMessage = err.localizedMessage ?: "Motion planning failed.")
                }
            }
        }
    }

    fun applyMotionToFireStudio(suggestedPrompt: String, durationSeconds: Int) {
        _fireState.update {
            it.copy(
                prompt = suggestedPrompt,
                durationSeconds = durationSeconds,
                errorMessage = null
            )
        }
    }

    private fun startElapsedTicker() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000L)
                _fireState.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
            }
        }
    }

    private fun stopElapsedTicker() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun stopAutoPoll() {
        autoPollJob?.cancel()
        autoPollJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopElapsedTicker()
        stopAutoPoll()
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val prefs = DevicePreferences(appContext)
                val db = VidoProDatabase.getInstance(appContext)
                val repo = HybridAiRepository(appContext)
                StudioViewModel(prefs, db.generationDao(), repo)
            }
        }
    }
}
