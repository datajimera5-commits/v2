package com.example.data.network

import com.example.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class RemotePlan(
    val planId: String,
    val name: String,
    val price: Double,
    val priceAnnual: Double?,
    val durationDays: Int,
    val badge: String,
    val features: List<String>,
    val coinsPerCycle: Int,
    val genLimit: Int = 0,
    val maxDevices: Int,
    val active: Boolean,
    val showInApp: Boolean
)

data class RemoteFeature(
    val featureKey: String,
    val displayName: String,
    val status: String,
    val minPlan: String,
    val messageEn: String,
    val messageHi: String
)

data class RemoteTicket(
    val ticketId: String,
    val userId: String,
    val subject: String,
    val status: String,
    val reply: String,
    val createdAt: String
)

data class RemoteUserProfile(
    val userId: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val planId: String = "",
    val status: String = "",
    val expiryDate: String = "",
    val coins: Int? = null,
    val genCount: Int? = null,
    val maxDevices: Int = 1,
    val licenceKey: String = "",
    val adminMessage: String = "",
    val whatsappOwner: String = "",
    val referralCode: String = ""
)

data class RemotePublicSettings(
    val whatsappOwner: String = "",
    val fireDriveUrl: String = "",
    val fireSaJsonRaw: String = "",
    val legitTextSkill: String = "",
    val legitImageSkill: String = "",
    val primeTextSkill: String = "",
    val primeImageSkill: String = "",
    val legacySkillLink: String = ""
)

sealed interface ServerLicenseResult {
    data class Active(val profile: RemoteUserProfile, val message: String) : ServerLicenseResult
    data class Trial(val profile: RemoteUserProfile, val message: String) : ServerLicenseResult
    data class Blocked(val adminMessage: String, val whatsappOwner: String = "") : ServerLicenseResult
    data class Unlicensed(val message: String) : ServerLicenseResult
    data class NetworkError(val message: String) : ServerLicenseResult
}

data class ServerFireOrderResult(
    val ok: Boolean,
    val uid: String,
    val status: String,
    val adminMessage: String,
    val videoUrl: String,
    val errorMessage: String = ""
)

data class ServerStudioResponse(
    val ok: Boolean,
    val text: String = "",
    val mediaUrl: String = "",
    val errorMessage: String = ""
)

object VidoProSecretConfig {
    private const val PLACEHOLDER_SECRET = "MY_VIDOPRO_SUB_SECRET"

    val subSecret: String
        get() = BuildConfig.VIDOPRO_SUB_SECRET.trim()

    val isSecretConfigured: Boolean
        get() = subSecret.isNotBlank() && subSecret != PLACEHOLDER_SECRET

    val defaultApiUrl: String
        get() = BuildConfig.VIDOPRO_API_URL.trim()
}

/**
 * Matches admin_php_51605a0e.html `stableStringify` + `hmacHex(cfg.secret, canon + ts + nonce)` byte-for-byte.
 */
object VidoProSigner {

    fun signPayload(
        secret: String,
        endpoint: String,
        payload: Map<String, Any?>,
        ts: String,
        nonce: String
    ): Pair<String, String> {
        val merged = LinkedHashMap<String, Any?>()
        payload.forEach { (k, v) ->
            if (v != null) merged[k] = v
        }
        merged["endpoint"] = endpoint

        val canon = stableStringify(merged)
        val sig = if (secret.isNotBlank()) hmacSha256Hex(secret, canon + ts + nonce) else ""

        val bodyMap = LinkedHashMap<String, Any?>()
        merged.forEach { (k, v) -> bodyMap[k] = v }
        bodyMap["ts"] = ts
        bodyMap["nonce"] = nonce
        bodyMap["sig"] = sig

        return stableStringify(bodyMap) to sig
    }

    fun stableStringify(value: Any?): String {
        return when (value) {
            null -> "null"
            is Boolean -> if (value) "true" else "false"
            is Int, is Long, is Short, is Byte -> value.toString()
            is Float, is Double -> {
                val d = (value as Number).toDouble()
                if (d.isNaN() || d.isInfinite()) "null"
                else if (d % 1.0 == 0.0 && d in Long.MIN_VALUE.toDouble()..Long.MAX_VALUE.toDouble()) {
                    d.toLong().toString()
                } else {
                    d.toString()
                }
            }
            is String -> jsonQuote(value)
            is JSONObject -> {
                val keys = mutableListOf<String>()
                val it = value.keys()
                while (it.hasNext()) keys.add(it.next())
                keys.sort()
                keys.joinToString(prefix = "{", postfix = "}", separator = ",") { k ->
                    val v = if (value.isNull(k)) null else value.get(k)
                    "${jsonQuote(k)}:${stableStringify(v)}"
                }
            }
            is JSONArray -> {
                val parts = ArrayList<String>(value.length())
                for (i in 0 until value.length()) {
                    val item = if (value.isNull(i)) null else value.get(i)
                    parts.add(stableStringify(item))
                }
                parts.joinToString(prefix = "[", postfix = "]", separator = ",")
            }
            is Map<*, *> -> {
                val sortedKeys = value.keys.mapNotNull { it?.toString() }.sorted()
                sortedKeys.joinToString(prefix = "{", postfix = "}", separator = ",") { k ->
                    "${jsonQuote(k)}:${stableStringify(value[k])}"
                }
            }
            is Iterable<*> -> {
                value.joinToString(prefix = "[", postfix = "]", separator = ",") { stableStringify(it) }
            }
            else -> jsonQuote(value.toString())
        }
    }

    private fun jsonQuote(s: String): String {
        val sb = StringBuilder(s.length + 2)
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> {
                    if (ch.code < 0x20) {
                        sb.append("\\u%04x".format(ch.code))
                    } else {
                        sb.append(ch)
                    }
                }
            }
        }
        sb.append('"')
        return sb.toString()
    }

    private fun hmacSha256Hex(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
