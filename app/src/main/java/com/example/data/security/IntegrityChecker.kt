package com.example.data.security

import android.os.Build
import java.io.File

data class SecurityStatus(
    val isEmulator: Boolean,
    val rootIndicatorsFound: List<String>,
    val isDebuggableBuild: Boolean,
    val sslPinningConfigured: Boolean
) {
    val isCleanDevice: Boolean
        get() = rootIndicatorsFound.isEmpty()

    val summaryLabel: String
        get() = when {
            rootIndicatorsFound.isNotEmpty() -> "Tamper / Root Indicator (${rootIndicatorsFound.size})"
            isEmulator -> "Verified Emulator Environment"
            else -> "Hardware Integrity Verified"
        }
}

object IntegrityChecker {
    private val suspiciousSuPaths = listOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/su/bin/su"
    )

    fun inspect(isDebugBuild: Boolean, sslPinSha256: String): SecurityStatus {
        val emulator = (Build.FINGERPRINT.startsWith("generic")
            || Build.FINGERPRINT.startsWith("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || Build.HARDWARE.contains("goldfish")
            || Build.HARDWARE.contains("ranchu")
            || Build.PRODUCT.contains("sdk_gphone"))

        val indicators = buildList {
            suspiciousSuPaths.forEach { path ->
                if (runCatching { File(path).exists() }.getOrDefault(false)) {
                    add(path)
                }
            }
            if (!emulator && Build.TAGS?.contains("test-keys") == true) {
                add("test-keys")
            }
        }

        return SecurityStatus(
            isEmulator = emulator,
            rootIndicatorsFound = indicators,
            isDebuggableBuild = isDebugBuild,
            sslPinningConfigured = sslPinSha256.trim().startsWith("sha256/")
        )
    }
}
