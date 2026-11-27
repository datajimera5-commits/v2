package com.example.data.network

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url
import java.net.URI
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

interface VidoProRawApi {
    @POST
    suspend fun postJson(
        @Url url: String,
        @Body body: RequestBody
    ): Response<ResponseBody>
}

class VidoProServerClient(
    baseApiUrl: String = VidoProSecretConfig.defaultApiUrl,
    sslPinSha256: String = ""
) {
    private val resolvedEndpoint: String = normalizeEndpoint(baseApiUrl)
        ?: normalizeEndpoint(VidoProSecretConfig.defaultApiUrl)
        ?: "https://qr.apnakundli.com/index.php"

    private val okHttpClient: OkHttpClient = buildHttpClient(resolvedEndpoint, sslPinSha256)
    private val api: VidoProRawApi = Retrofit.Builder()
        .baseUrl(extractBaseRoot(resolvedEndpoint))
        .client(okHttpClient)
        .build()
        .create(VidoProRawApi::class.java)

    private val driveClient by lazy { GoogleDriveFireClient(okHttpClient) }

    @Volatile
    private var cachedDriveConfig: DriveFireConfig? = null

    @Volatile
    var cachedWhatsappOwner: String = ""
        private set

    private data class SignedCallResponse(
        val httpCode: Int,
        val isSuccessful: Boolean,
        val json: JSONObject?,
        val rawText: String,
        val isUnknownEndpoint: Boolean
    )

    private suspend fun callSignedApi(endpoint: String, payload: Map<String, Any?>): SignedCallResponse {
        val secret = if (VidoProSecretConfig.isSecretConfigured) VidoProSecretConfig.subSecret else ""
        val ts = (System.currentTimeMillis() / 1000L).toString()
        val nonce = UUID.randomUUID().toString()
        val (signedBody, _) = VidoProSigner.signPayload(secret, endpoint, payload, ts, nonce)

        val requestBody = signedBody.toRequestBody("application/json".toMediaType())
        val response = api.postJson(resolvedEndpoint, requestBody)
        val raw = (if (response.isSuccessful) response.body()?.string() else response.errorBody()?.string()).orEmpty()
        val json = runCatching { JSONObject(raw) }.getOrNull()
        val errText = json?.optString("error").orEmpty().lowercase(Locale.US)
        val unknown = response.code() == 404 ||
            errText.contains("unknown endpoint") ||
            errText.contains("invalid endpoint") ||
            errText.contains("not found") ||
            errText.contains("route")

        return SignedCallResponse(
            httpCode = response.code(),
            isSuccessful = response.isSuccessful,
            json = json,
            rawText = raw,
            isUnknownEndpoint = unknown
        )
    }

    private suspend fun callWithEndpointFallbacks(
        endpoints: List<String>,
        payload: Map<String, Any?>
    ): SignedCallResponse {
        var last: SignedCallResponse? = null
        for (ep in endpoints) {
            val res = callSignedApi(ep, payload)
            last = res
            if (!res.isUnknownEndpoint) return res
        }
        return last ?: SignedCallResponse(500, false, null, "", false)
    }

    suspend fun fetchPlans(): Result<List<RemotePlan>> = withContext(Dispatchers.IO) {
        try {
            val res = callSignedApi("/sub/plans", emptyMap())
            val json = res.json ?: return@withContext Result.failure(
                IllegalStateException("Invalid plans response (HTTP ${res.httpCode})")
            )
            if (!res.isSuccessful || json.optBoolean("ok", false).not()) {
                return@withContext Result.failure(
                    IllegalStateException(json.optString("error").ifBlank { "Unable to fetch plans" })
                )
            }
            json.optString("whatsapp_owner").trim().takeIf { it.isNotBlank() }?.let {
                cachedWhatsappOwner = it
            }
            val arr = json.optJSONArray("plans") ?: JSONArray()
            val list = ArrayList<RemotePlan>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val featuresRaw = obj.opt("features_json") ?: obj.opt("included_features")
                val features = mutableListOf<String>()
                when (featuresRaw) {
                    is JSONArray -> {
                        for (j in 0 until featuresRaw.length()) {
                            featuresRaw.optString(j).takeIf { it.isNotBlank() }?.let { features.add(it) }
                        }
                    }
                    is String -> {
                        runCatching { JSONArray(featuresRaw) }.getOrNull()?.let { parsed ->
                            for (j in 0 until parsed.length()) {
                                parsed.optString(j).takeIf { it.isNotBlank() }?.let { features.add(it) }
                            }
                        }
                    }
                }
                val isActive = obj.optBoolean("active", obj.optInt("active", 1) == 1)
                val showInApp = obj.optBoolean("show_in_app", obj.optInt("show_in_app", 1) == 1)
                list.add(
                    RemotePlan(
                        planId = obj.optString("plan_id"),
                        name = obj.optString("name").ifBlank { obj.optString("plan_id") },
                        price = obj.optDouble("price", 0.0),
                        priceAnnual = if (obj.has("price_annual") && !obj.isNull("price_annual")) obj.optDouble("price_annual") else null,
                        durationDays = obj.optInt("duration_days", 30),
                        badge = obj.optString("badge"),
                        features = features,
                        coinsPerCycle = obj.optInt("coins_per_cycle", 0),
                        genLimit = obj.optInt("gen_limit", 0),
                        maxDevices = obj.optInt("max_devices", 1),
                        active = isActive,
                        showInApp = showInApp
                    )
                )
            }
            Result.success(list.filter { it.active && it.showInApp })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "fetchPlans failed", e)
            Result.failure(e)
        }
    }

    suspend fun registerOrStartTrial(
        subscriptionId: String,
        deviceUuid: String,
        name: String,
        phone: String
    ): ServerLicenseResult = withContext(Dispatchers.IO) {
        try {
            val rawDigits = phone.filter { it.isDigit() }
            val cleanPhone = if (rawDigits.length == 12 && rawDigits.startsWith("91")) {
                rawDigits.drop(2)
            } else {
                rawDigits
            }
            val payload = linkedMapOf<String, Any?>(
                "device_id" to deviceUuid,
                "device_uuid" to deviceUuid,
                "name" to name.trim().ifBlank { cleanPhone.ifBlank { "Mobile User" } },
                "phone" to cleanPhone,
                "app_version" to BuildConfig.VERSION_NAME,
                "plan_id" to "PLAN_TRIAL_SUB",
                "status" to "trial",
                "platform" to "android",
                "app" to "vidopro_mobile"
            )
            if (subscriptionId.isNotBlank() && !subscriptionId.startsWith("APP-")) {
                payload["user_id"] = subscriptionId
                payload["subscription_id"] = subscriptionId
            }
            val res = callWithEndpointFallbacks(
                listOf("/sub/register", "/sub/trial", "/sub/signup", "/sub/create_user", "/sub/activate"),
                payload
            )
            parseLicenseCallResult(res, defaultSubId = subscriptionId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "registerOrStartTrial failed", e)
            ServerLicenseResult.NetworkError(e.localizedMessage ?: "Network unavailable")
        }
    }

    suspend fun checkOrVerifyLicense(
        deviceUuid: String,
        licenseKey: String,
        userId: String = "",
        isActivationAttempt: Boolean
    ): ServerLicenseResult = withContext(Dispatchers.IO) {
        try {
            val payload = linkedMapOf<String, Any?>(
                "device_id" to deviceUuid,
                "device_uuid" to deviceUuid,
                "app_version" to BuildConfig.VERSION_NAME,
                "platform" to "android",
                "app" to "vidopro_mobile"
            )
            if (licenseKey.isNotBlank()) {
                payload["key"] = licenseKey
                payload["licence_key"] = licenseKey
                payload["license_key"] = licenseKey
            }
            if (userId.isNotBlank() && !userId.startsWith("APP-")) {
                payload["user_id"] = userId
                payload["subscription_id"] = userId
            }

            val routes = if (isActivationAttempt) {
                listOf("/sub/verify", "/sub/activate", "/sub/login", "/sub/check")
            } else {
                listOf("/sub/verify", "/sub/check", "/sub/status", "/sub/login")
            }

            val res = callWithEndpointFallbacks(routes, payload)
            parseLicenseCallResult(res, defaultSubId = userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "License verification failed", e)
            ServerLicenseResult.NetworkError(e.localizedMessage ?: "Network unavailable")
        }
    }

    private fun parseLicenseCallResult(res: SignedCallResponse, defaultSubId: String): ServerLicenseResult =
        parseLicenseJson(res.json, res.httpCode, res.isSuccessful, defaultSubId)

    internal fun parseLicenseJson(
        json: JSONObject?,
        httpCode: Int,
        isSuccessful: Boolean,
        defaultSubId: String
    ): ServerLicenseResult {
        if (json == null) return ServerLicenseResult.NetworkError("Server HTTP $httpCode")
        val userObj = json.optJSONObject("user") ?: json.optJSONObject("data") ?: json
        val statusRaw = userObj.optString("status")
            .ifBlank { json.optString("status") }
            .ifBlank { userObj.optString("state") }
            .ifBlank { json.optString("state") }
            .trim().lowercase(Locale.US)
        val adminMsg = userObj.optString("notes").ifBlank {
            userObj.optString("admin_msg").ifBlank {
                json.optString("message").ifBlank { json.optString("error") }
            }
        }
        val whatsapp = json.optString("whatsapp_owner").ifBlank { userObj.optString("whatsapp_owner") }
        if (whatsapp.isNotBlank()) {
            cachedWhatsappOwner = whatsapp.trim()
        }

        // Cache Drive config if backend embeds settings alongside /sub/verify or /sub/register
        val embeddedSettings = json.optJSONObject("settings") ?: userObj.optJSONObject("settings") ?: json
        val embeddedDriveUrl = embeddedSettings.optString("fire_drive_url")
        val embeddedSaRaw = embeddedSettings.optString("fire_sa_json")
        if (embeddedDriveUrl.isNotBlank() && embeddedSaRaw.isNotBlank()) {
            val fid = GoogleDriveFireClient.extractFolderId(embeddedDriveUrl)
            val saJson = runCatching { JSONObject(embeddedSaRaw) }.getOrNull()
            if (fid.isNotBlank() && saJson != null && saJson.optString("client_email").isNotBlank()) {
                cachedDriveConfig = DriveFireConfig(fid, saJson)
            }
        }

        val isBlocked = userObj.optBoolean("blocked", false) ||
            userObj.optBoolean("is_blocked", false) ||
            statusRaw in setOf("blocked", "suspended", "banned", "disabled")

        if (isBlocked) {
            return ServerLicenseResult.Blocked(
                adminMessage = adminMsg.ifBlank { "Device access suspended by administrator." },
                whatsappOwner = whatsapp
            )
        }

        val explicitOkFalse = json.has("ok") && !json.optBoolean("ok", true)
        val isRejectedState = statusRaw in setOf(
            "expired", "bad_key", "bad_phone", "bad_device", "invalid", "unlicensed", "revoked"
        )
        if (!isSuccessful || explicitOkFalse || isRejectedState) {
            return ServerLicenseResult.Unlicensed(
                adminMsg.ifBlank { "License not active for this device." }
            )
        }

        val profile = parseUserProfile(json, userObj, defaultSubId)
        val isOk = json.optBoolean("ok", false) ||
            userObj.optBoolean("valid", false) ||
            userObj.optBoolean("active", false) ||
            statusRaw in setOf("active", "pro", "valid", "ok")

        return when {
            statusRaw == "trial" || (profile.planId.equals("PLAN_TRIAL_SUB", ignoreCase = true) && statusRaw != "active") -> {
                ServerLicenseResult.Trial(profile, adminMsg)
            }
            isOk -> {
                ServerLicenseResult.Active(profile, adminMsg)
            }
            else -> {
                ServerLicenseResult.Unlicensed(adminMsg.ifBlank { "License not active for this device." })
            }
        }
    }

    suspend fun fetchFeatures(userId: String, deviceUuid: String): Result<List<RemoteFeature>> =
        withContext(Dispatchers.IO) {
            try {
                val res = callSignedApi(
                    "/sub/features",
                    mapOf("user_id" to userId, "device_id" to deviceUuid)
                )
                val json = res.json ?: return@withContext Result.failure(IllegalStateException("HTTP ${res.httpCode}"))
                if (!json.optBoolean("ok", false)) {
                    return@withContext Result.failure(IllegalStateException(json.optString("error").ifBlank { "Features error" }))
                }
                val arr = json.optJSONArray("features") ?: JSONArray()
                val out = ArrayList<RemoteFeature>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    out.add(
                        RemoteFeature(
                            featureKey = obj.optString("feature_key"),
                            displayName = obj.optString("display_name"),
                            status = obj.optString("status", "on"),
                            minPlan = obj.optString("min_plan"),
                            messageEn = obj.optString("message_en"),
                            messageHi = obj.optString("message_hi")
                        )
                    )
                }
                Result.success(out)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun fetchPublicSettings(userId: String, deviceUuid: String): RemotePublicSettings? =
        withContext(Dispatchers.IO) {
            try {
                val res = callWithEndpointFallbacks(
                    listOf("/sub/settings", "/sub/fire_config", "/sub/config"),
                    mapOf("op" to "list", "user_id" to userId, "device_id" to deviceUuid)
                )
                val json = res.json ?: return@withContext null
                if (!json.optBoolean("ok", false)) return@withContext null

                val map = mutableMapOf<String, String>()
                val arr = json.optJSONArray("settings") ?: json.optJSONArray("rows")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val row = arr.optJSONObject(i) ?: continue
                        val k = row.optString("setting_key").ifBlank { row.optString("key") }
                        if (k.isNotBlank()) map[k] = row.optString("value")
                    }
                } else {
                    val obj = json.optJSONObject("settings") ?: json.optJSONObject("data") ?: json
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        map[k] = obj.optString(k)
                    }
                }

                val driveUrl = map["fire_drive_url"].orEmpty()
                val saRaw = map["fire_sa_json"].orEmpty()
                if (driveUrl.isNotBlank() && saRaw.isNotBlank()) {
                    val fid = GoogleDriveFireClient.extractFolderId(driveUrl)
                    val saJson = runCatching { JSONObject(saRaw) }.getOrNull()
                    if (fid.isNotBlank() && saJson != null && saJson.optString("client_email").isNotBlank()) {
                        cachedDriveConfig = DriveFireConfig(fid, saJson)
                    }
                }

                RemotePublicSettings(
                    whatsappOwner = map["whatsapp_owner"].orEmpty(),
                    fireDriveUrl = driveUrl,
                    fireSaJsonRaw = saRaw,
                    legitTextSkill = map["legit_text_skill"].orEmpty(),
                    legitImageSkill = map["legit_image_skill"].orEmpty(),
                    primeTextSkill = map["prime_text_skill"].orEmpty(),
                    primeImageSkill = map["prime_image_skill"].orEmpty(),
                    legacySkillLink = map["skill_link"].orEmpty()
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        }

    suspend fun fetchTickets(userId: String, deviceUuid: String): Result<List<RemoteTicket>> =
        withContext(Dispatchers.IO) {
            try {
                val res = callSignedApi(
                    "/sub/tickets",
                    mapOf("op" to "list", "user_id" to userId, "device_id" to deviceUuid)
                )
                val json = res.json ?: return@withContext Result.failure(IllegalStateException("HTTP ${res.httpCode}"))
                if (!json.optBoolean("ok", false)) {
                    return@withContext Result.failure(IllegalStateException(json.optString("error").ifBlank { "Tickets error" }))
                }
                val arr = json.optJSONArray("tickets") ?: JSONArray()
                val out = ArrayList<RemoteTicket>(arr.length())
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    out.add(
                        RemoteTicket(
                            ticketId = obj.optString("ticket_id"),
                            userId = obj.optString("user_id"),
                            subject = obj.optString("subject"),
                            status = obj.optString("status", "open"),
                            reply = obj.optString("reply").ifBlank { obj.optString("admin_reply") },
                            createdAt = obj.optString("created_at")
                        )
                    )
                }
                Result.success(out)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun createTicket(
        userId: String,
        deviceUuid: String,
        subject: String,
        message: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val res = callSignedApi(
                "/sub/tickets",
                mapOf(
                    "op" to "create",
                    "user_id" to userId,
                    "device_id" to deviceUuid,
                    "subject" to subject,
                    "message" to message
                )
            )
            val json = res.json
            if (res.isSuccessful && json?.optBoolean("ok", false) == true) {
                Result.success(json.optString("ticket_id").ifBlank { "Submitted" })
            } else {
                Result.failure(IllegalStateException(json?.optString("error").orEmpty().ifBlank { "Ticket submission failed" }))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncCoinUsage(
        userId: String,
        deviceUuid: String,
        amount: Int,
        reason: String
    ): Int? = withContext(Dispatchers.IO) {
        try {
            val res = callWithEndpointFallbacks(
                listOf("/sub/coins", "/sub/use"),
                mapOf(
                    "op" to "deduct",
                    "user_id" to userId,
                    "device_id" to deviceUuid,
                    "amount" to amount,
                    "reason" to reason
                )
            )
            val json = res.json ?: return@withContext null
            if (json.optBoolean("ok", false) && json.has("balance")) {
                json.optInt("balance")
            } else if (json.optBoolean("ok", false) && json.has("coins")) {
                json.optInt("coins")
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun requestDeviceReset(userId: String, deviceUuid: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val res = callWithEndpointFallbacks(
                listOf("/sub/device_reset", "/sub/reset_device"),
                mapOf("user_id" to userId, "device_id" to deviceUuid, "device_uuid" to deviceUuid)
            )
            if (res.isSuccessful && res.json?.optBoolean("ok", false) == true) {
                return@withContext true
            }
            if (res.isUnknownEndpoint) {
                return@withContext createTicket(
                    userId = userId,
                    deviceUuid = deviceUuid,
                    subject = "Device Reset Request",
                    message = "User requested hardware device unbind for $deviceUuid."
                ).isSuccess
            }
            false
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun claimReferral(userId: String, deviceUuid: String, referralCode: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val res = callSignedApi(
                    "/sub/referral",
                    mapOf(
                        "user_id" to userId,
                        "device_id" to deviceUuid,
                        "referral_code" to referralCode
                    )
                )
                res.isSuccessful && res.json?.optBoolean("ok", false) == true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }

    suspend fun submitFireOrder(
        uid: String,
        userId: String,
        deviceUuid: String,
        prompt: String,
        durationSeconds: Int,
        aspectRatio: String,
        imagePayloads: List<ByteArray>
    ): ServerFireOrderResult = withContext(Dispatchers.IO) {
        try {
            val encodedImages = imagePayloads.take(10).map { bytes ->
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
            val signedRes = callWithEndpointFallbacks(
                listOf("/sub/fire", "/sub/fire_order", "/sub/order", "/sub/generate"),
                mapOf(
                    "op" to "submit",
                    "uid" to uid,
                    "user_id" to userId,
                    "subscription_id" to userId,
                    "device_id" to deviceUuid,
                    "prompt" to prompt,
                    "dur" to durationSeconds,
                    "ratio" to aspectRatio,
                    "images_b64" to encodedImages
                )
            )

            if (!signedRes.isUnknownEndpoint) {
                return@withContext parseFireResult(uid, signedRes)
            }

            // If /sub/fire is not on PHP router, check runtime Drive pipeline from /sub/settings
            val driveCfg = cachedDriveConfig ?: run {
                fetchPublicSettings(userId, deviceUuid)
                cachedDriveConfig
            }
            if (driveCfg != null) {
                return@withContext driveClient.uploadFireOrder(
                    config = driveCfg,
                    uid = uid,
                    userId = userId,
                    deviceUuid = deviceUuid,
                    prompt = prompt,
                    durationSeconds = durationSeconds,
                    aspectRatio = aspectRatio,
                    imagePayloads = imagePayloads
                )
            }

            ServerFireOrderResult(
                ok = false,
                uid = uid,
                status = "",
                adminMessage = "",
                videoUrl = "",
                errorMessage = "Server order route (/sub/fire) or Drive settings (/sub/settings) not enabled on index.php."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fire order dispatch error", e)
            ServerFireOrderResult(
                ok = false,
                uid = uid,
                status = "",
                adminMessage = "",
                videoUrl = "",
                errorMessage = e.localizedMessage ?: "Unable to reach generation server."
            )
        }
    }

    suspend fun pollFireOrder(uid: String, userId: String, deviceUuid: String): ServerFireOrderResult =
        withContext(Dispatchers.IO) {
            try {
                val signedRes = callWithEndpointFallbacks(
                    listOf("/sub/fire", "/sub/fire_order", "/sub/order", "/sub/generate"),
                    mapOf("op" to "poll", "uid" to uid, "user_id" to userId, "subscription_id" to userId, "device_id" to deviceUuid)
                )
                if (!signedRes.isUnknownEndpoint) {
                    return@withContext parseFireResult(uid, signedRes)
                }
                val driveCfg = cachedDriveConfig
                if (driveCfg != null) {
                    return@withContext driveClient.pollDriveOrder(driveCfg, uid)
                }
                ServerFireOrderResult(
                    ok = false,
                    uid = uid,
                    status = "",
                    adminMessage = "",
                    videoUrl = "",
                    errorMessage = "Order status endpoint unavailable."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ServerFireOrderResult(
                    ok = false,
                    uid = uid,
                    status = "",
                    adminMessage = "",
                    videoUrl = "",
                    errorMessage = e.localizedMessage ?: "Status check error"
                )
            }
        }

    suspend fun downloadDriveMediaBytes(url: String): ByteArray? {
        val cfg = cachedDriveConfig ?: return null
        return driveClient.downloadDriveMediaBytes(cfg, url)
    }

    suspend fun purgeDriveOrder(uid: String) {
        val cfg = cachedDriveConfig ?: return
        driveClient.purgeOrderFiles(cfg, uid)
    }

    suspend fun requestStudioAction(
        endpoint: String,
        action: String,
        userId: String,
        deviceUuid: String,
        params: Map<String, Any?>
    ): ServerStudioResponse = withContext(Dispatchers.IO) {
        try {
            val merged = LinkedHashMap<String, Any?>().apply {
                put("op", action)
                put("user_id", userId)
                put("subscription_id", userId)
                put("device_id", deviceUuid)
                putAll(params)
            }
            val res = callSignedApi(endpoint, merged)
            val dataObj = res.json?.optJSONObject("data") ?: res.json
            if (res.isSuccessful && dataObj != null && (dataObj.optBoolean("ok", false) ||
                    dataObj.has("result") ||
                    dataObj.has("enhanced_prompt") ||
                    dataObj.has("image_url"))
            ) {
                ServerStudioResponse(
                    ok = true,
                    text = dataObj.optString("enhanced_prompt").ifBlank {
                        dataObj.optString("result").ifBlank { dataObj.optString("summary") }
                    },
                    mediaUrl = dataObj.optString("image_url").ifBlank { dataObj.optString("video_url") }
                )
            } else {
                ServerStudioResponse(
                    ok = false,
                    errorMessage = dataObj?.optString("error").orEmpty().ifBlank { "HTTP ${res.httpCode}" }
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ServerStudioResponse(ok = false, errorMessage = e.localizedMessage ?: "Network error")
        }
    }

    private fun parseUserProfile(root: JSONObject, userObj: JSONObject, defaultSubId: String): RemoteUserProfile {
        val coinsVal = when {
            userObj.has("coins") -> userObj.optInt("coins")
            userObj.has("credits") -> userObj.optInt("credits")
            userObj.has("balance") -> userObj.optInt("balance")
            root.has("coins") -> root.optInt("coins")
            else -> null
        }
        val genVal = when {
            userObj.has("gen_count") -> userObj.optInt("gen_count")
            userObj.has("renders_count") -> userObj.optInt("renders_count")
            else -> null
        }
        val keyVal = userObj.optString("licence_key")
            .ifBlank { userObj.optString("license_key") }
            .ifBlank { userObj.optString("key") }
            .ifBlank { root.optString("licence_key") }
            .ifBlank { root.optString("license_key") }
            .ifBlank { root.optString("key") }
        return RemoteUserProfile(
            userId = userObj.optString("user_id").ifBlank {
                root.optString("user_id").ifBlank { defaultSubId }
            },
            name = userObj.optString("name"),
            phone = userObj.optString("phone"),
            email = userObj.optString("email"),
            planId = userObj.optString("plan_id"),
            status = userObj.optString("status").ifBlank { userObj.optString("state") },
            expiryDate = userObj.optString("expiry_date"),
            coins = coinsVal,
            genCount = genVal,
            maxDevices = userObj.optInt("max_devices", 1),
            licenceKey = keyVal,
            adminMessage = userObj.optString("notes").ifBlank { userObj.optString("admin_msg") },
            whatsappOwner = root.optString("whatsapp_owner").ifBlank { userObj.optString("whatsapp_owner") },
            referralCode = userObj.optString("referral_code")
        )
    }

    private fun parseFireResult(uid: String, res: SignedCallResponse): ServerFireOrderResult {
        val json = res.json
        val dataObj = json?.optJSONObject("data") ?: json
        val okFlag = json?.optBoolean("ok", false) == true ||
            dataObj?.optBoolean("ok", false) == true ||
            dataObj?.has("video_url") == true
        val errMsg = dataObj?.optString("error").orEmpty()
            .ifBlank { json?.optString("error").orEmpty() }
            .ifBlank { dataObj?.optString("message").orEmpty() }

        return if (res.isSuccessful && okFlag && dataObj?.optBoolean("ok", true) != false) {
            ServerFireOrderResult(
                ok = true,
                uid = dataObj?.optString("uid")?.takeIf { it.isNotBlank() } ?: uid,
                status = dataObj?.optString("status")?.takeIf { it.isNotBlank() } ?: "processing",
                adminMessage = dataObj?.optString("admin_msg").orEmpty().ifBlank { dataObj?.optString("message").orEmpty() },
                videoUrl = dataObj?.optString("video_url").orEmpty()
            )
        } else {
            ServerFireOrderResult(
                ok = false,
                uid = uid,
                status = "",
                adminMessage = "",
                videoUrl = "",
                errorMessage = errMsg.ifBlank { "Server returned HTTP ${res.httpCode}." }
            )
        }
    }

    companion object {
        private const val TAG = "VidoProServerClient"

        fun normalizeEndpoint(raw: String): String? {
            val trimmed = raw.trim()
            if (!trimmed.startsWith("https://")) return null
            return trimmed
        }

        private fun extractBaseRoot(endpointUrl: String): String {
            val uri = URI(endpointUrl)
            return "${uri.scheme}://${uri.authority}/"
        }

        private fun buildHttpClient(endpointUrl: String, sslPinSha256: String): OkHttpClient {
            val host = URI(endpointUrl).host.orEmpty()
            val builder = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })

            val cleanPin = sslPinSha256.trim()
            if (cleanPin.startsWith("sha256/") && host.isNotBlank()) {
                builder.certificatePinner(
                    CertificatePinner.Builder().add(host, cleanPin).build()
                )
            }
            return builder.build()
        }
    }
}

object VidoProBackend {
    val client: VidoProServerClient by lazy {
        VidoProServerClient(VidoProSecretConfig.defaultApiUrl)
    }
}
