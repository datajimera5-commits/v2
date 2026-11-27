package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.util.UUID

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "vidopro_prefs")

enum class LicenseStatus {
    UNLICENSED,
    TRIAL,
    ACTIVE,
    BLOCKED
}

data class UserSessionPrefs(
    val deviceUuid: String = "",
    val subscriptionId: String = "",
    val serverUserId: String = "",
    val userName: String = "",
    val userPhone: String = "",
    val planId: String = "",
    val expiryDate: String = "",
    val genCount: Int = 0,
    val whatsappOwner: String = "",
    val licenseStatus: LicenseStatus = LicenseStatus.UNLICENSED,
    val licenseKey: String = "",
    val blockedMessage: String = "",
    val credits: Int = 0,
    val referralCode: String = "",
    val referralCount: Int = 0,
    val claimedReferralCodes: Set<String> = emptySet(),
    val backendServerUrl: String = BuildConfig.VIDOPRO_API_URL,
    val sslPinSha256: String = "",
    val hapticsEnabled: Boolean = true,
    val deviceResetRequested: Boolean = false
) {
    val effectiveSubscriptionId: String
        get() = serverUserId.ifBlank { subscriptionId }.ifBlank { deviceUuid }
}

class DevicePreferences(private val context: Context) {

    private object Keys {
        val DEVICE_UUID = stringPreferencesKey("device_uuid")
        val SUBSCRIPTION_ID = stringPreferencesKey("subscription_id")
        val SERVER_USER_ID = stringPreferencesKey("server_user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_PHONE = stringPreferencesKey("user_phone")
        val PLAN_ID = stringPreferencesKey("plan_id")
        val EXPIRY_DATE = stringPreferencesKey("expiry_date")
        val GEN_COUNT = intPreferencesKey("gen_count")
        val WHATSAPP_OWNER = stringPreferencesKey("whatsapp_owner")
        val LICENSE_STATUS = stringPreferencesKey("license_status")
        val LICENSE_KEY = stringPreferencesKey("license_key")
        val BLOCKED_MESSAGE = stringPreferencesKey("blocked_message")
        val CREDITS = intPreferencesKey("credits")
        val REFERRAL_CODE = stringPreferencesKey("referral_code")
        val REFERRAL_COUNT = intPreferencesKey("referral_count")
        val CLAIMED_REFERRALS = stringSetPreferencesKey("claimed_referrals")
        val BACKEND_SERVER_URL = stringPreferencesKey("backend_server_url")
        val SSL_PIN_SHA256 = stringPreferencesKey("ssl_pin_sha256")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val DEVICE_RESET_REQUESTED = booleanPreferencesKey("device_reset_requested")
    }

    val sessionFlow: Flow<UserSessionPrefs> = context.dataStore.data.map { prefs ->
        val statusRaw = prefs[Keys.LICENSE_STATUS] ?: LicenseStatus.UNLICENSED.name
        val status = LicenseStatus.entries.firstOrNull { it.name == statusRaw } ?: LicenseStatus.UNLICENSED
        UserSessionPrefs(
            deviceUuid = prefs[Keys.DEVICE_UUID].orEmpty(),
            subscriptionId = prefs[Keys.SUBSCRIPTION_ID].orEmpty(),
            serverUserId = prefs[Keys.SERVER_USER_ID].orEmpty(),
            userName = prefs[Keys.USER_NAME].orEmpty(),
            userPhone = prefs[Keys.USER_PHONE].orEmpty(),
            planId = prefs[Keys.PLAN_ID].orEmpty(),
            expiryDate = prefs[Keys.EXPIRY_DATE].orEmpty(),
            genCount = prefs[Keys.GEN_COUNT] ?: 0,
            whatsappOwner = prefs[Keys.WHATSAPP_OWNER].orEmpty(),
            licenseStatus = status,
            licenseKey = prefs[Keys.LICENSE_KEY].orEmpty(),
            blockedMessage = prefs[Keys.BLOCKED_MESSAGE].orEmpty(),
            credits = prefs[Keys.CREDITS] ?: 0,
            referralCode = prefs[Keys.REFERRAL_CODE].orEmpty(),
            referralCount = prefs[Keys.REFERRAL_COUNT] ?: 0,
            claimedReferralCodes = prefs[Keys.CLAIMED_REFERRALS] ?: emptySet(),
            backendServerUrl = prefs[Keys.BACKEND_SERVER_URL]?.takeIf { it.isNotBlank() }
                ?: BuildConfig.VIDOPRO_API_URL,
            sslPinSha256 = prefs[Keys.SSL_PIN_SHA256].orEmpty(),
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            deviceResetRequested = prefs[Keys.DEVICE_RESET_REQUESTED] ?: false
        )
    }

    suspend fun ensureInitialized(): String {
        var resolvedUuid = ""
        context.dataStore.edit { prefs ->
            val existing = prefs[Keys.DEVICE_UUID]
            if (existing.isNullOrBlank()) {
                val raw = UUID.randomUUID().toString()
                val hash = MessageDigest.getInstance("SHA-256")
                    .digest(raw.toByteArray())
                    .joinToString("") { "%02X".format(it) }
                    .take(12)
                resolvedUuid = "VP-$hash"
                prefs[Keys.DEVICE_UUID] = resolvedUuid
            } else {
                resolvedUuid = existing
            }
            if (prefs[Keys.SUBSCRIPTION_ID].isNullOrBlank()) {
                prefs[Keys.SUBSCRIPTION_ID] = "APP-${resolvedUuid.removePrefix("VP-").take(6)}"
            }
            if (prefs[Keys.REFERRAL_CODE].isNullOrBlank()) {
                prefs[Keys.REFERRAL_CODE] = "VP-${resolvedUuid.takeLast(6)}"
            }
            if (prefs[Keys.CREDITS] == null) {
                prefs[Keys.CREDITS] = 0
            }
        }
        return resolvedUuid
    }

    suspend fun setUserContactInfo(name: String, phone: String) {
        context.dataStore.edit { prefs ->
            if (name.isNotBlank()) prefs[Keys.USER_NAME] = name.trim()
            if (phone.isNotBlank()) prefs[Keys.USER_PHONE] = phone.trim()
        }
    }

    suspend fun applyServerProfile(
        status: LicenseStatus,
        licenseKey: String,
        userId: String,
        userName: String,
        planId: String,
        expiryDate: String,
        coins: Int?,
        genCount: Int?,
        whatsappOwner: String,
        referralCode: String,
        blockedMessage: String = ""
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LICENSE_STATUS] = status.name
            if (licenseKey.isNotBlank()) {
                prefs[Keys.LICENSE_KEY] = licenseKey.trim().uppercase()
            }
            if (userId.isNotBlank()) prefs[Keys.SERVER_USER_ID] = userId.trim()
            if (userName.isNotBlank()) prefs[Keys.USER_NAME] = userName.trim()
            if (planId.isNotBlank()) prefs[Keys.PLAN_ID] = planId.trim()
            if (expiryDate.isNotBlank()) prefs[Keys.EXPIRY_DATE] = expiryDate.trim()
            if (coins != null) prefs[Keys.CREDITS] = coins.coerceAtLeast(0)
            if (genCount != null) prefs[Keys.GEN_COUNT] = genCount.coerceAtLeast(0)
            if (whatsappOwner.isNotBlank()) prefs[Keys.WHATSAPP_OWNER] = whatsappOwner.trim()
            if (referralCode.isNotBlank()) prefs[Keys.REFERRAL_CODE] = referralCode.trim()
            prefs[Keys.BLOCKED_MESSAGE] = blockedMessage
        }
    }

    suspend fun setWhatsappOwner(whatsapp: String) {
        if (whatsapp.isBlank()) return
        context.dataStore.edit { prefs ->
            prefs[Keys.WHATSAPP_OWNER] = whatsapp.trim()
        }
    }

    suspend fun setCreditsExact(amount: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.CREDITS] = amount.coerceAtLeast(0)
        }
    }

    suspend fun activateProLicense(key: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LICENSE_STATUS] = LicenseStatus.ACTIVE.name
            prefs[Keys.LICENSE_KEY] = key.trim().uppercase()
            prefs[Keys.BLOCKED_MESSAGE] = ""
            if ((prefs[Keys.CREDITS] ?: 0) < 500) {
                prefs[Keys.CREDITS] = 500
            }
        }
    }

    suspend fun startTrial() {
        context.dataStore.edit { prefs ->
            prefs[Keys.LICENSE_STATUS] = LicenseStatus.TRIAL.name
            prefs[Keys.LICENSE_KEY] = "TRIAL-7DAY-SEEDANCE"
            prefs[Keys.PLAN_ID] = "PLAN_TRIAL_SUB"
            prefs[Keys.BLOCKED_MESSAGE] = ""
            if ((prefs[Keys.CREDITS] ?: 0) < 250) {
                prefs[Keys.CREDITS] = 250
            }
        }
    }

    suspend fun setBlocked(adminMessage: String, whatsappOwner: String = "") {
        context.dataStore.edit { prefs ->
            prefs[Keys.LICENSE_STATUS] = LicenseStatus.BLOCKED.name
            prefs[Keys.BLOCKED_MESSAGE] = adminMessage
            if (whatsappOwner.isNotBlank()) {
                prefs[Keys.WHATSAPP_OWNER] = whatsappOwner.trim()
            }
        }
    }

    suspend fun revokeToUnlicensed() {
        context.dataStore.edit { prefs ->
            prefs[Keys.LICENSE_STATUS] = LicenseStatus.UNLICENSED.name
            prefs[Keys.LICENSE_KEY] = ""
            prefs[Keys.SERVER_USER_ID] = ""
            prefs[Keys.PLAN_ID] = ""
            prefs[Keys.EXPIRY_DATE] = ""
            prefs[Keys.BLOCKED_MESSAGE] = ""
        }
    }

    suspend fun clearLicense() {
        context.dataStore.edit { prefs ->
            prefs[Keys.LICENSE_STATUS] = LicenseStatus.UNLICENSED.name
            prefs[Keys.LICENSE_KEY] = ""
            prefs[Keys.SERVER_USER_ID] = ""
            prefs[Keys.USER_NAME] = ""
            prefs[Keys.PLAN_ID] = ""
            prefs[Keys.EXPIRY_DATE] = ""
            prefs[Keys.BLOCKED_MESSAGE] = ""
        }
    }

    suspend fun deductCredits(amount: Int): Boolean {
        var success = false
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CREDITS] ?: 0
            if (current >= amount) {
                prefs[Keys.CREDITS] = current - amount
                success = true
            }
        }
        return success
    }

    suspend fun addCredits(amount: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CREDITS] ?: 0
            prefs[Keys.CREDITS] = current + amount
        }
    }

    suspend fun claimReferralCode(code: String): Boolean {
        val normalized = code.trim().uppercase()
        var claimed = false
        context.dataStore.edit { prefs ->
            val own = prefs[Keys.REFERRAL_CODE].orEmpty()
            val alreadyClaimed = prefs[Keys.CLAIMED_REFERRALS] ?: emptySet()
            if (normalized.length >= 5 && normalized != own && !alreadyClaimed.contains(normalized)) {
                prefs[Keys.CLAIMED_REFERRALS] = alreadyClaimed + normalized
                prefs[Keys.REFERRAL_COUNT] = (prefs[Keys.REFERRAL_COUNT] ?: 0) + 1
                prefs[Keys.CREDITS] = (prefs[Keys.CREDITS] ?: 0) + 100
                claimed = true
            }
        }
        return claimed
    }

    suspend fun requestDeviceReset() {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEVICE_RESET_REQUESTED] = true
        }
    }

    suspend fun updateBackendConfig(serverUrl: String, sslPinSha256: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.BACKEND_SERVER_URL] = serverUrl.trim()
            prefs[Keys.SSL_PIN_SHA256] = sslPinSha256.trim()
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HAPTICS_ENABLED] = enabled
        }
    }
}
