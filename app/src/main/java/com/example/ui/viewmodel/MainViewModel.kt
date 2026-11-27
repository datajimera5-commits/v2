package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.BuildConfig
import com.example.data.local.DevicePreferences
import com.example.data.local.FireOrderStatus
import com.example.data.local.GenerationDao
import com.example.data.local.GenerationEntity
import com.example.data.local.LicenseStatus
import com.example.data.local.UserSessionPrefs
import com.example.data.local.VidoProDatabase
import com.example.data.network.RemoteFeature
import com.example.data.network.RemotePlan
import com.example.data.network.RemoteTicket
import com.example.data.network.ServerLicenseResult
import com.example.data.network.VidoProBackend
import com.example.data.repository.AiRepository
import com.example.data.repository.HybridAiRepository
import com.example.data.security.IntegrityChecker
import com.example.data.security.SecurityStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(
    private val preferences: DevicePreferences,
    private val generationDao: GenerationDao,
    private val aiRepository: AiRepository,
    val securityStatus: SecurityStatus
) : ViewModel() {

    val sessionPrefs: StateFlow<UserSessionPrefs> = preferences.sessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UserSessionPrefs()
        )

    val generations: StateFlow<List<GenerationEntity>> = generationDao.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    private val _splashFinished = MutableStateFlow(false)
    val splashFinished: StateFlow<Boolean> = _splashFinished.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _licenseActionLoading = MutableStateFlow(false)
    val licenseActionLoading: StateFlow<Boolean> = _licenseActionLoading.asStateFlow()

    private val _plans = MutableStateFlow<List<RemotePlan>>(emptyList())
    val plans: StateFlow<List<RemotePlan>> = _plans.asStateFlow()

    private val _features = MutableStateFlow<List<RemoteFeature>>(emptyList())
    val features: StateFlow<List<RemoteFeature>> = _features.asStateFlow()

    private val _tickets = MutableStateFlow<List<RemoteTicket>>(emptyList())
    val tickets: StateFlow<List<RemoteTicket>> = _tickets.asStateFlow()

    private val _syncingAccount = MutableStateFlow(false)
    val syncingAccount: StateFlow<Boolean> = _syncingAccount.asStateFlow()

    private val client get() = VidoProBackend.client

    init {
        viewModelScope.launch {
            val uuid = preferences.ensureInitialized()
            val prefs = preferences.sessionFlow.first()
            syncFromServerInternal(
                deviceUuid = uuid,
                subscriptionId = prefs.effectiveSubscriptionId,
                licenseKey = prefs.licenseKey,
                serverUserId = prefs.serverUserId,
                userName = prefs.userName,
                userPhone = prefs.userPhone,
                localStatus = prefs.licenseStatus
            )
            delay(850L)
            _splashFinished.value = true
        }
    }

    private suspend fun syncFromServerInternal(
        deviceUuid: String,
        subscriptionId: String,
        licenseKey: String,
        serverUserId: String,
        userName: String,
        userPhone: String,
        localStatus: LicenseStatus
    ) {
        client.fetchPlans().onSuccess { _plans.value = it }
        if (client.cachedWhatsappOwner.isNotBlank()) {
            preferences.setWhatsappOwner(client.cachedWhatsappOwner)
        }
        val pubSettings = client.fetchPublicSettings(subscriptionId, deviceUuid)
        if (pubSettings != null && pubSettings.whatsappOwner.isNotBlank()) {
            preferences.setWhatsappOwner(pubSettings.whatsappOwner)
        }

        val hasRealKey = licenseKey.isNotBlank() && licenseKey != "TRIAL-7DAY-SEEDANCE"
        val cleanPhone = normalizePhoneDigits(userPhone)

        val result: ServerLicenseResult = when {
            hasRealKey -> client.checkOrVerifyLicense(
                deviceUuid = deviceUuid,
                licenseKey = licenseKey,
                userId = serverUserId,
                isActivationAttempt = false
            )
            localStatus == LicenseStatus.TRIAL && cleanPhone.length == 10 -> client.registerOrStartTrial(
                subscriptionId = serverUserId,
                deviceUuid = deviceUuid,
                name = userName,
                phone = cleanPhone
            )
            localStatus == LicenseStatus.TRIAL -> {
                preferences.revokeToUnlicensed()
                return
            }
            else -> return
        }

        when (result) {
            is ServerLicenseResult.Blocked -> {
                preferences.setBlocked(result.adminMessage, result.whatsappOwner)
            }
            is ServerLicenseResult.Active -> {
                val p = result.profile
                preferences.applyServerProfile(
                    status = LicenseStatus.ACTIVE,
                    licenseKey = p.licenceKey.ifBlank { licenseKey },
                    userId = p.userId,
                    userName = p.name,
                    planId = p.planId,
                    expiryDate = p.expiryDate,
                    coins = p.coins,
                    genCount = p.genCount,
                    whatsappOwner = p.whatsappOwner.ifBlank { client.cachedWhatsappOwner },
                    referralCode = p.referralCode
                )
            }
            is ServerLicenseResult.Trial -> {
                val p = result.profile
                preferences.applyServerProfile(
                    status = LicenseStatus.TRIAL,
                    licenseKey = p.licenceKey.ifBlank { licenseKey },
                    userId = p.userId,
                    userName = p.name,
                    planId = p.planId.ifBlank { "PLAN_TRIAL_SUB" },
                    expiryDate = p.expiryDate,
                    coins = p.coins,
                    genCount = p.genCount,
                    whatsappOwner = p.whatsappOwner.ifBlank { client.cachedWhatsappOwner },
                    referralCode = p.referralCode
                )
            }
            is ServerLicenseResult.Unlicensed -> {
                if (localStatus == LicenseStatus.ACTIVE || localStatus == LicenseStatus.TRIAL) {
                    preferences.revokeToUnlicensed()
                    _statusBannerMessage.value = result.message
                }
            }
            is ServerLicenseResult.NetworkError -> {
                // Preserve local trial/active state if offline
            }
        }

        val updated = preferences.sessionFlow.first()
        val resolvedSubId = updated.effectiveSubscriptionId
        client.fetchFeatures(resolvedSubId, deviceUuid).onSuccess { _features.value = it }
        client.fetchTickets(resolvedSubId, deviceUuid).onSuccess { _tickets.value = it }
    }

    fun syncAccountFromServer() {
        viewModelScope.launch {
            _syncingAccount.value = true
            val prefs = sessionPrefs.value
            syncFromServerInternal(
                deviceUuid = prefs.deviceUuid,
                subscriptionId = prefs.effectiveSubscriptionId,
                licenseKey = prefs.licenseKey,
                serverUserId = prefs.serverUserId,
                userName = prefs.userName,
                userPhone = prefs.userPhone,
                localStatus = prefs.licenseStatus
            )
            _syncingAccount.value = false
            _statusBannerMessage.value = "Account, coins & plans synced."
        }
    }

    fun activateLicense(keyInput: String, userIdInput: String = "") {
        val cleanKey = keyInput.trim().uppercase()
        val cleanUser = userIdInput.trim()
        if (cleanKey.length < 4) {
            _statusBannerMessage.value = "Enter a valid License Key."
            return
        }
        viewModelScope.launch {
            _licenseActionLoading.value = true
            val prefs = sessionPrefs.value
            val targetSubId = cleanUser.ifBlank { prefs.serverUserId }
            when (val result = client.checkOrVerifyLicense(
                deviceUuid = prefs.deviceUuid,
                licenseKey = cleanKey,
                userId = targetSubId,
                isActivationAttempt = true
            )) {
                is ServerLicenseResult.Blocked -> {
                    preferences.setBlocked(result.adminMessage, result.whatsappOwner)
                }
                is ServerLicenseResult.Active -> {
                    val p = result.profile
                    val resolvedKey = p.licenceKey.ifBlank { cleanKey }
                    val resolvedSubId = p.userId.ifBlank { targetSubId.ifBlank { prefs.effectiveSubscriptionId } }
                    preferences.applyServerProfile(
                        status = LicenseStatus.ACTIVE,
                        licenseKey = resolvedKey,
                        userId = resolvedSubId,
                        userName = p.name,
                        planId = p.planId,
                        expiryDate = p.expiryDate,
                        coins = p.coins,
                        genCount = p.genCount,
                        whatsappOwner = p.whatsappOwner.ifBlank { client.cachedWhatsappOwner },
                        referralCode = p.referralCode
                    )
                    client.fetchTickets(resolvedSubId, prefs.deviceUuid).onSuccess { _tickets.value = it }
                    client.fetchFeatures(resolvedSubId, prefs.deviceUuid).onSuccess { _features.value = it }
                    _statusBannerMessage.value = result.message.ifBlank {
                        "Pro Plan activated (Sub ID: $resolvedSubId)."
                    }
                }
                is ServerLicenseResult.Trial -> {
                    val p = result.profile
                    val resolvedKey = p.licenceKey.ifBlank { cleanKey }
                    val resolvedSubId = p.userId.ifBlank { targetSubId.ifBlank { prefs.effectiveSubscriptionId } }
                    preferences.applyServerProfile(
                        status = LicenseStatus.TRIAL,
                        licenseKey = resolvedKey,
                        userId = resolvedSubId,
                        userName = p.name,
                        planId = p.planId.ifBlank { "PLAN_TRIAL_SUB" },
                        expiryDate = p.expiryDate,
                        coins = p.coins,
                        genCount = p.genCount,
                        whatsappOwner = p.whatsappOwner.ifBlank { client.cachedWhatsappOwner },
                        referralCode = p.referralCode
                    )
                    _statusBannerMessage.value = result.message.ifBlank {
                        "Trial plan activated (Sub ID: $resolvedSubId)."
                    }
                }
                is ServerLicenseResult.Unlicensed -> {
                    _statusBannerMessage.value = result.message
                }
                is ServerLicenseResult.NetworkError -> {
                    _statusBannerMessage.value = "Unable to verify license right now (${result.message})."
                }
            }
            _licenseActionLoading.value = false
        }
    }

    fun startSevenDayTrial(nameInput: String = "", phoneInput: String = "") {
        val cleanName = nameInput.trim()
        val cleanPhone = normalizePhoneDigits(phoneInput)
        if (cleanPhone.length != 10) {
            _statusBannerMessage.value = "Enter a valid 10-digit WhatsApp / mobile number to register."
            return
        }
        viewModelScope.launch {
            _licenseActionLoading.value = true
            _statusBannerMessage.value = null
            preferences.setUserContactInfo(cleanName, cleanPhone)
            val prefs = preferences.sessionFlow.first()
            val regResult = client.registerOrStartTrial(
                subscriptionId = "",
                deviceUuid = prefs.deviceUuid,
                name = cleanName.ifBlank { prefs.userName },
                phone = cleanPhone
            )
            when (regResult) {
                is ServerLicenseResult.Blocked -> {
                    preferences.setBlocked(regResult.adminMessage, regResult.whatsappOwner)
                }
                is ServerLicenseResult.Active -> {
                    val p = regResult.profile
                    val resolvedId = p.userId.ifBlank { prefs.effectiveSubscriptionId }
                    preferences.applyServerProfile(
                        status = LicenseStatus.ACTIVE,
                        licenseKey = p.licenceKey.ifBlank { prefs.licenseKey },
                        userId = resolvedId,
                        userName = p.name.ifBlank { cleanName },
                        planId = p.planId,
                        expiryDate = p.expiryDate,
                        coins = p.coins,
                        genCount = p.genCount,
                        whatsappOwner = p.whatsappOwner.ifBlank { client.cachedWhatsappOwner },
                        referralCode = p.referralCode
                    )
                    _statusBannerMessage.value = "Subscription active · Sub ID $resolvedId"
                }
                is ServerLicenseResult.Trial -> {
                    val p = regResult.profile
                    val resolvedId = p.userId.ifBlank { prefs.effectiveSubscriptionId }
                    preferences.applyServerProfile(
                        status = LicenseStatus.TRIAL,
                        licenseKey = p.licenceKey,
                        userId = resolvedId,
                        userName = p.name.ifBlank { cleanName },
                        planId = p.planId.ifBlank { "PLAN_TRIAL_SUB" },
                        expiryDate = p.expiryDate,
                        coins = p.coins ?: 50,
                        genCount = p.genCount,
                        whatsappOwner = p.whatsappOwner.ifBlank { client.cachedWhatsappOwner },
                        referralCode = p.referralCode
                    )
                    client.fetchFeatures(resolvedId, prefs.deviceUuid).onSuccess { _features.value = it }
                    client.fetchTickets(resolvedId, prefs.deviceUuid).onSuccess { _tickets.value = it }
                    _statusBannerMessage.value = "Free Trial registered · Sub ID $resolvedId"
                }
                is ServerLicenseResult.Unlicensed -> {
                    _statusBannerMessage.value = regResult.message
                }
                is ServerLicenseResult.NetworkError -> {
                    _statusBannerMessage.value = "Unable to connect to server (${regResult.message})."
                }
            }
            _licenseActionLoading.value = false
        }
    }

    private fun normalizePhoneDigits(raw: String): String {
        val digits = raw.filter { it.isDigit() }
        return if (digits.length == 12 && digits.startsWith("91")) digits.drop(2) else digits
    }

    fun resetLicenseGate() {
        viewModelScope.launch {
            preferences.clearLicense()
        }
    }

    fun requestDeviceReset() {
        viewModelScope.launch {
            val prefs = sessionPrefs.value
            val userId = prefs.effectiveSubscriptionId
            val serverAck = client.requestDeviceReset(userId, prefs.deviceUuid)
            preferences.requestDeviceReset()
            _statusBannerMessage.value = if (serverAck) {
                "Device reset request submitted to Control Center."
            } else {
                "Device reset request queued for approval."
            }
        }
    }

    fun claimReferralCode(code: String) {
        viewModelScope.launch {
            val cleanCode = code.trim().uppercase()
            val ok = preferences.claimReferralCode(cleanCode)
            if (ok) {
                val prefs = sessionPrefs.value
                client.claimReferral(prefs.effectiveSubscriptionId, prefs.deviceUuid, cleanCode)
            }
            _statusBannerMessage.value = if (ok) {
                "+100 bonus credits added from referral!"
            } else {
                "Invalid, duplicate, or own referral code."
            }
        }
    }

    fun submitSupportTicket(subject: String, message: String) {
        val cleanSubject = subject.trim()
        val cleanMessage = message.trim()
        if (cleanSubject.isBlank() || cleanMessage.isBlank()) {
            _statusBannerMessage.value = "Enter both ticket subject and message."
            return
        }
        viewModelScope.launch {
            val prefs = sessionPrefs.value
            val userId = prefs.effectiveSubscriptionId
            client.createTicket(userId, prefs.deviceUuid, cleanSubject, cleanMessage)
                .onSuccess { ticketId ->
                    client.fetchTickets(userId, prefs.deviceUuid).onSuccess { _tickets.value = it }
                    _statusBannerMessage.value = "Support ticket submitted ($ticketId)."
                }
                .onFailure { err ->
                    _statusBannerMessage.value = err.localizedMessage ?: "Unable to submit ticket right now."
                }
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setHapticsEnabled(enabled)
        }
    }

    fun exportGeneration(entity: GenerationEntity) {
        viewModelScope.launch {
            aiRepository.exportGenerationAsset(entity)
                .onSuccess { path ->
                    generationDao.markDownloaded(entity.id, FireOrderStatus.DOWNLOADED, path)
                    _statusBannerMessage.value = "Saved to $path"
                }
                .onFailure { err ->
                    _statusBannerMessage.value = err.localizedMessage ?: "Export failed."
                }
        }
    }

    fun upscaleGeneration(entity: GenerationEntity) {
        viewModelScope.launch {
            if (entity.isUpscaled) {
                _statusBannerMessage.value = "Asset is already upscaled to 4K."
                return@launch
            }
            val deducted = preferences.deductCredits(15)
            if (!deducted) {
                _statusBannerMessage.value = "Insufficient credits for 4K Upscale (15 credits required)."
                return@launch
            }
            val prefs = sessionPrefs.value
            val remoteBalance = client.syncCoinUsage(
                userId = prefs.effectiveSubscriptionId,
                deviceUuid = prefs.deviceUuid,
                amount = 15,
                reason = "4K Upscale (${entity.orderUid})"
            )
            if (remoteBalance != null) {
                preferences.setCreditsExact(remoteBalance)
            }
            generationDao.markUpscaled(entity.id)
            _statusBannerMessage.value = "4K Neural Upscale applied to ${entity.orderUid}."
        }
    }

    fun deleteGeneration(id: Long) {
        viewModelScope.launch {
            generationDao.deleteById(id)
        }
    }

    fun clearBannerMessage() {
        _statusBannerMessage.value = null
    }

    companion object {
        fun provideFactory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val appContext = context.applicationContext
                val prefs = DevicePreferences(appContext)
                val db = VidoProDatabase.getInstance(appContext)
                val repo = HybridAiRepository(appContext)
                val sec = IntegrityChecker.inspect(
                    isDebugBuild = BuildConfig.DEBUG,
                    sslPinSha256 = ""
                )
                MainViewModel(prefs, db.generationDao(), repo, sec)
            }
        }
    }
}
