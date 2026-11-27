package com.example.data.repository

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.example.data.local.FireOrderStatus
import com.example.data.local.GenerationEntity
import com.example.data.network.VidoProBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.Locale

data class EnhancedPromptResult(
    val enhancedPrompt: String,
    val notice: String
)

data class ImageGenerationResult(
    val title: String,
    val summary: String,
    val mediaUrl: String = ""
)

data class MotionPlanResult(
    val title: String,
    val choreographySummary: String,
    val suggestedFirePrompt: String
)

data class FireOrderDispatchResult(
    val orderUid: String,
    val status: FireOrderStatus,
    val adminMessage: String,
    val remoteVideoUrl: String,
    val outputTitle: String,
    val outputSummary: String
)

interface AiRepository {
    suspend fun enhancePrompt(
        userId: String,
        deviceUuid: String,
        rawPrompt: String,
        referenceCount: Int,
        aspectRatio: String,
        durationSeconds: Int
    ): Result<EnhancedPromptResult>

    suspend fun generateImagePreview(
        userId: String,
        deviceUuid: String,
        prompt: String,
        stylePreset: String,
        aspectRatio: String
    ): Result<ImageGenerationResult>

    suspend fun planMotionChoreography(
        userId: String,
        deviceUuid: String,
        subjectPrompt: String,
        cameraMove: String,
        intensity: Int,
        durationSeconds: Int
    ): Result<MotionPlanResult>

    suspend fun submitFireModeOrder(
        orderUid: String,
        userId: String,
        deviceUuid: String,
        prompt: String,
        durationSeconds: Int,
        aspectRatio: String,
        referenceUris: List<Uri>
    ): Result<FireOrderDispatchResult>

    suspend fun pollFireModeOrder(
        orderUid: String,
        userId: String,
        deviceUuid: String
    ): Result<FireOrderDispatchResult>

    suspend fun exportGenerationAsset(entity: GenerationEntity): Result<String>

    suspend fun cleanupFireOrder(orderUid: String)
}

class HybridAiRepository(private val context: Context) : AiRepository {

    private val client get() = VidoProBackend.client

    override suspend fun enhancePrompt(
        userId: String,
        deviceUuid: String,
        rawPrompt: String,
        referenceCount: Int,
        aspectRatio: String,
        durationSeconds: Int
    ): Result<EnhancedPromptResult> = withContext(Dispatchers.IO) {
        try {
            val clean = rawPrompt.trim()
            require(clean.isNotEmpty()) { "Enter a prompt before enhancing." }

            val serverResp = client.requestStudioAction(
                endpoint = "/sub/enhance",
                action = "enhance_prompt",
                userId = userId,
                deviceUuid = deviceUuid,
                params = mapOf(
                    "prompt" to clean,
                    "ref_count" to referenceCount,
                    "ratio" to aspectRatio,
                    "dur" to durationSeconds
                )
            )
            if (serverResp.ok && serverResp.text.isNotBlank()) {
                return@withContext Result.success(
                    EnhancedPromptResult(
                        enhancedPrompt = serverResp.text,
                        notice = "Prompt enhanced for Seedance 2.5."
                    )
                )
            }

            val refClause = if (referenceCount > 0) {
                "preserving subject continuity across $referenceCount reference frame(s), "
            } else {
                ""
            }
            val enhanced = "$clean — shot on anamorphic 35mm cinema lens, ${refClause}volumetric rim lighting, " +
                "smooth Seedance 2.5 temporal motion over ${durationSeconds}s in $aspectRatio framing, " +
                "high dynamic range color grade, ultra-crisp micro-texture."

            Result.success(
                EnhancedPromptResult(
                    enhancedPrompt = enhanced,
                    notice = "Cinematic Seedance 2.5 prompt enhancement applied."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun generateImagePreview(
        userId: String,
        deviceUuid: String,
        prompt: String,
        stylePreset: String,
        aspectRatio: String
    ): Result<ImageGenerationResult> = withContext(Dispatchers.IO) {
        try {
            val clean = prompt.trim()
            require(clean.isNotEmpty()) { "Enter an image prompt first." }

            val serverResp = client.requestStudioAction(
                endpoint = "/sub/image",
                action = "image_submit",
                userId = userId,
                deviceUuid = deviceUuid,
                params = mapOf(
                    "prompt" to clean,
                    "style" to stylePreset,
                    "ratio" to aspectRatio
                )
            )
            if (serverResp.ok) {
                return@withContext Result.success(
                    ImageGenerationResult(
                        title = "Image Render ($stylePreset)",
                        summary = serverResp.text.ifBlank {
                            "Style: $stylePreset · Aspect: $aspectRatio\nPrompt: $clean"
                        },
                        mediaUrl = serverResp.mediaUrl
                    )
                )
            }

            Result.success(
                ImageGenerationResult(
                    title = "Concept Frame ($stylePreset)",
                    summary = "Style: $stylePreset · Aspect: $aspectRatio\nPrompt: $clean"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun planMotionChoreography(
        userId: String,
        deviceUuid: String,
        subjectPrompt: String,
        cameraMove: String,
        intensity: Int,
        durationSeconds: Int
    ): Result<MotionPlanResult> = withContext(Dispatchers.IO) {
        try {
            val clean = subjectPrompt.trim().ifEmpty { "Cyberpunk luxury sports coupe in rain" }

            val serverResp = client.requestStudioAction(
                endpoint = "/sub/motion",
                action = "motion_plan",
                userId = userId,
                deviceUuid = deviceUuid,
                params = mapOf(
                    "prompt" to clean,
                    "camera_move" to cameraMove,
                    "intensity" to intensity,
                    "dur" to durationSeconds
                )
            )

            val intensityLabel = when {
                intensity >= 75 -> "High-velocity kinetic"
                intensity >= 40 -> "Fluid cinematic"
                else -> "Subtle stabilized"
            }

            val suggestedPrompt = if (serverResp.ok && serverResp.text.isNotBlank()) {
                serverResp.text
            } else {
                "$clean, $intensityLabel $cameraMove camera trajectory ($intensity% intensity), " +
                    "continuous ${durationSeconds}s one-take sequence, zero jitter, Seedance 2.5 motion lock."
            }

            val summary = "Vector: $cameraMove ($intensity% intensity)\n" +
                "Keyframes: 0.0s Ease-In -> ${(durationSeconds * 0.5f).toInt()}s Peak Velocity -> ${durationSeconds}s Ease-Out\n" +
                "Shutter: 180° cinematic motion blur"

            Result.success(
                MotionPlanResult(
                    title = "$cameraMove · $intensity% Motion",
                    choreographySummary = summary,
                    suggestedFirePrompt = suggestedPrompt
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun submitFireModeOrder(
        orderUid: String,
        userId: String,
        deviceUuid: String,
        prompt: String,
        durationSeconds: Int,
        aspectRatio: String,
        referenceUris: List<Uri>
    ): Result<FireOrderDispatchResult> = withContext(Dispatchers.IO) {
        try {
            val imagePayloads = referenceUris.take(10).mapNotNull { uri ->
                compressReferenceImage(uri)
            }

            val serverResult = client.submitFireOrder(
                uid = orderUid,
                userId = userId,
                deviceUuid = deviceUuid,
                prompt = prompt,
                durationSeconds = durationSeconds,
                aspectRatio = aspectRatio,
                imagePayloads = imagePayloads
            )

            if (serverResult.ok) {
                val mappedStatus = if (serverResult.videoUrl.isNotBlank()) {
                    FireOrderStatus.READY
                } else {
                    mapBackendStatus(serverResult.status)
                }
                return@withContext Result.success(
                    FireOrderDispatchResult(
                        orderUid = serverResult.uid,
                        status = mappedStatus,
                        adminMessage = serverResult.adminMessage,
                        remoteVideoUrl = serverResult.videoUrl,
                        outputTitle = "Seedance 2.5 · ${serverResult.uid}",
                        outputSummary = "Submitted to Seedance 2.5 cloud queue (${imagePayloads.size} reference asset(s))."
                    )
                )
            }

            Result.failure(
                IllegalStateException(
                    serverResult.errorMessage.ifBlank { "Unable to start generation. Please try again." }
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Fire Mode dispatch failed", e)
            Result.failure(e)
        }
    }

    override suspend fun pollFireModeOrder(
        orderUid: String,
        userId: String,
        deviceUuid: String
    ): Result<FireOrderDispatchResult> = withContext(Dispatchers.IO) {
        try {
            val serverResult = client.pollFireOrder(orderUid, userId, deviceUuid)
            if (serverResult.ok) {
                val mappedStatus = if (serverResult.videoUrl.isNotBlank()) {
                    FireOrderStatus.READY
                } else {
                    mapBackendStatus(serverResult.status)
                }
                Result.success(
                    FireOrderDispatchResult(
                        orderUid = serverResult.uid,
                        status = mappedStatus,
                        adminMessage = serverResult.adminMessage,
                        remoteVideoUrl = serverResult.videoUrl,
                        outputTitle = "Seedance 2.5 · ${serverResult.uid}",
                        outputSummary = "Render status: ${serverResult.status}"
                    )
                )
            } else {
                Result.failure(IllegalStateException(serverResult.errorMessage.ifBlank { "Status check failed." }))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun cleanupFireOrder(orderUid: String) {
        if (orderUid.isNotBlank()) {
            client.purgeDriveOrder(orderUid)
        }
    }

    override suspend fun exportGenerationAsset(entity: GenerationEntity): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (entity.remoteMediaUrl.startsWith("https://") || entity.remoteMediaUrl.startsWith("http://")) {
                    val isImage = entity.type == com.example.data.local.GenerationType.IMAGE
                    val ext = if (isImage) "jpg" else "mp4"
                    val mime = if (isImage) "image/jpeg" else "video/mp4"
                    val fileName = "${entity.orderUid}.$ext"

                    if (entity.remoteMediaUrl.contains("googleapis.com/drive/v3/files/")) {
                        val bytes = client.downloadDriveMediaBytes(entity.remoteMediaUrl)
                            ?: return@withContext Result.failure(
                                IllegalStateException("Unable to download media from Drive.")
                            )
                        val savedPath = saveMediaBytesToDownloads(fileName, mime, bytes)
                        client.purgeDriveOrder(entity.orderUid)
                        return@withContext Result.success(savedPath)
                    }

                    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                    if (dm != null) {
                        val req = DownloadManager.Request(Uri.parse(entity.remoteMediaUrl))
                            .setTitle("VidoPro AI · ${entity.orderUid}")
                            .setDescription("Downloading Seedance 2.5 output")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_DOWNLOADS,
                                "VidoPro Videos/$fileName"
                            )
                        dm.enqueue(req)
                        return@withContext Result.success("Downloads/VidoPro Videos/$fileName")
                    }
                }

                val fileName = "${entity.orderUid}_details.txt"
                val content = buildString {
                    appendLine("=== VidoPro AI Studio ===")
                    appendLine("Order UID: ${entity.orderUid}")
                    appendLine("Engine: ${entity.modelName}")
                    appendLine("Aspect Ratio: ${entity.aspectRatio}")
                    appendLine("Duration: ${entity.durationSeconds}s")
                    appendLine("Reference Assets: ${entity.referenceCount}")
                    appendLine("Upscaled: ${entity.isUpscaled}")
                    appendLine()
                    appendLine("Prompt:")
                    appendLine(entity.prompt)
                    if (entity.enhancedPrompt.isNotBlank()) {
                        appendLine()
                        appendLine("Enhanced Prompt:")
                        appendLine(entity.enhancedPrompt)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                        put(
                            MediaStore.Downloads.RELATIVE_PATH,
                            "${Environment.DIRECTORY_DOWNLOADS}/VidoPro Videos"
                        )
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            out.write(content.toByteArray())
                        }
                        return@withContext Result.success("Downloads/VidoPro Videos/$fileName")
                    }
                }

                val exportDir = File(context.filesDir, "vidopro_exports").apply { mkdirs() }
                val outFile = File(exportDir, fileName)
                outFile.writeText(content)
                Result.success(outFile.absolutePath)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun compressReferenceImage(uri: Uri): ByteArray? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            val maxDim = 1600
            var sampleSize = 1
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                var w = bounds.outWidth
                var h = bounds.outHeight
                while (w / 2 >= maxDim || h / 2 >= maxDim) {
                    w /= 2
                    h /= 2
                    sampleSize *= 2
                }
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = resolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            }
            if (decoded != null) {
                val scaled = if (decoded.width > maxDim || decoded.height > maxDim) {
                    val ratio = minOf(
                        maxDim.toFloat() / decoded.width.toFloat(),
                        maxDim.toFloat() / decoded.height.toFloat()
                    )
                    val targetW = (decoded.width * ratio).toInt().coerceAtLeast(1)
                    val targetH = (decoded.height * ratio).toInt().coerceAtLeast(1)
                    Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                        if (it !== decoded) decoded.recycle()
                    }
                } else {
                    decoded
                }
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
                scaled.recycle()
                out.toByteArray()
            } else {
                resolver.openInputStream(uri)?.use { it.readBytes() }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to read reference image $uri", e)
            null
        }
    }

    private fun saveMediaBytesToDownloads(fileName: String, mimeType: String, bytes: ByteArray): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(
                    MediaStore.Downloads.RELATIVE_PATH,
                    "${Environment.DIRECTORY_DOWNLOADS}/VidoPro Videos"
                )
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out ->
                    out.write(bytes)
                }
                return "Downloads/VidoPro Videos/$fileName"
            }
        }
        val exportDir = File(context.filesDir, "vidopro_exports").apply { mkdirs() }
        val outFile = File(exportDir, fileName)
        outFile.writeBytes(bytes)
        return outFile.absolutePath
    }

    private fun mapBackendStatus(raw: String): FireOrderStatus {
        return when (raw.trim().lowercase(Locale.US)) {
            "pending", "queued", "new", "sent" -> FireOrderStatus.UPLOADING
            "processing", "in progress", "generating" -> FireOrderStatus.RENDERING
            "hold", "on hold", "on_hold" -> FireOrderStatus.HOLD
            "retry" -> FireOrderStatus.RETRY
            "cancelled", "canceled" -> FireOrderStatus.CANCELLED
            "failed", "error", "gone", "removed" -> FireOrderStatus.FAILED
            "ready", "done", "completed", "ok" -> FireOrderStatus.READY
            else -> FireOrderStatus.RENDERING
        }
    }

    companion object {
        private const val TAG = "HybridAiRepository"

        fun generateOrderUid(deviceUuid: String, sequence: Int): String {
            val digest = MessageDigest.getInstance("MD5")
                .digest("$deviceUuid|T0".toByteArray())
                .joinToString("") { "%02X".format(it) }
                .take(6)
            val stamp = (System.currentTimeMillis() % 1_000_000L).toString().padStart(6, '0')
            return "FO-$digest-T0-$stamp-${sequence.toString().padStart(2, '0')}"
        }
    }
}
