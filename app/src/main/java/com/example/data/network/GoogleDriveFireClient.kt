package com.example.data.network

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.concurrent.TimeUnit

data class DriveFireConfig(
    val folderId: String,
    val saJson: JSONObject
)

class GoogleDriveFireClient(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()
) {
    private var cachedSaToken: String = ""
    private var cachedSaExp: Long = 0L
    private var cachedOAuthToken: String = ""
    private var cachedOAuthExp: Long = 0L

    suspend fun uploadFireOrder(
        config: DriveFireConfig,
        uid: String,
        userId: String,
        deviceUuid: String,
        prompt: String,
        durationSeconds: Int,
        aspectRatio: String,
        imagePayloads: List<ByteArray>
    ): ServerFireOrderResult = withContext(Dispatchers.IO) {
        try {
            val uploadToken = getUploadToken(config.saJson)
            if (uploadToken.isBlank()) {
                return@withContext ServerFireOrderResult(
                    ok = false,
                    uid = uid,
                    status = "",
                    adminMessage = "",
                    videoUrl = "",
                    errorMessage = "Unable to authenticate Drive pipeline."
                )
            }

            val uploadedImgIds = mutableListOf<String>()
            imagePayloads.take(10).forEachIndexed { idx, bytes ->
                val uploadRes = uploadMultipartToDrive(
                    token = uploadToken,
                    folderId = config.folderId,
                    name = "IMG_${uid}_$idx.jpg",
                    mimeType = "image/jpeg",
                    data = bytes
                )
                if (uploadRes.fileId.isNotBlank()) {
                    uploadedImgIds.add(uploadRes.fileId)
                } else if (uploadRes.errorMessage.isNotBlank()) {
                    return@withContext ServerFireOrderResult(
                        ok = false,
                        uid = uid,
                        status = "",
                        adminMessage = "",
                        videoUrl = "",
                        errorMessage = uploadRes.errorMessage
                    )
                }
            }

            val orderJson = JSONObject().apply {
                put("uid", uid)
                put("tabuid", uid.substringBeforeLast("-").substringBeforeLast("-"))
                put("user", userId.ifBlank { "mobile" })
                put("device", deviceUuid)
                put("tab", 0)
                put("prompt", prompt)
                put("dur", durationSeconds)
                put("ratio", aspectRatio)
                put("imgs", org.json.JSONArray(uploadedImgIds))
                put("status", "pending")
                put("ts", System.currentTimeMillis() / 1000L)
                put("file", "")
            }

            val orderUpload = uploadMultipartToDrive(
                token = uploadToken,
                folderId = config.folderId,
                name = "ORDER_$uid.json",
                mimeType = "application/json",
                data = orderJson.toString().toByteArray(Charsets.UTF_8)
            )

            if (orderUpload.fileId.isBlank()) {
                return@withContext ServerFireOrderResult(
                    ok = false,
                    uid = uid,
                    status = "",
                    adminMessage = "",
                    videoUrl = "",
                    errorMessage = orderUpload.errorMessage.ifBlank { "Order queue upload failed." }
                )
            }

            ServerFireOrderResult(
                ok = true,
                uid = uid,
                status = "processing",
                adminMessage = "",
                videoUrl = ""
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Drive Fire Order failed", e)
            ServerFireOrderResult(
                ok = false,
                uid = uid,
                status = "",
                adminMessage = "",
                videoUrl = "",
                errorMessage = e.localizedMessage ?: "Order submission failed."
            )
        }
    }

    suspend fun pollDriveOrder(config: DriveFireConfig, uid: String): ServerFireOrderResult =
        withContext(Dispatchers.IO) {
            try {
                val token = getServiceAccountToken(config.saJson)
                if (token.isBlank()) {
                    return@withContext ServerFireOrderResult(
                        ok = false,
                        uid = uid,
                        status = "",
                        adminMessage = "",
                        videoUrl = "",
                        errorMessage = "Unable to check render status."
                    )
                }

                val q = URLEncoder.encode("'${config.folderId}' in parents and trashed=false", "UTF-8")
                val url = "https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id,name,mimeType,webContentLink)&pageSize=200&supportsAllDrives=true&includeItemsFromAllDrives=true"
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()

                val resp = httpClient.newCall(req).execute()
                val raw = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    return@withContext ServerFireOrderResult(
                        ok = false,
                        uid = uid,
                        status = "",
                        adminMessage = "",
                        videoUrl = "",
                        errorMessage = "Status check HTTP ${resp.code}"
                    )
                }

                val files = JSONObject(raw).optJSONArray("files")
                var orderJsonId: String? = null
                var videoFileId: String? = null

                if (files != null) {
                    for (i in 0 until files.length()) {
                        val f = files.optJSONObject(i) ?: continue
                        val name = f.optString("name")
                        val mime = f.optString("mimeType")
                        val id = f.optString("id")
                        if (name == "ORDER_$uid.json") {
                            orderJsonId = id
                        } else if (matchesUidMediaFile(name, uid) &&
                            (mime.startsWith("video/") || name.endsWith(".mp4", ignoreCase = true))
                        ) {
                            videoFileId = id
                        }
                    }
                }

                var metaStatus = ""
                var metaNote = ""
                var isRemoved = false
                if (!orderJsonId.isNullOrBlank()) {
                    val metaText = fetchFileText(token, orderJsonId)
                    val metaJson = runCatching { JSONObject(metaText) }.getOrNull()
                    if (metaJson != null) {
                        metaStatus = metaJson.optString("status").trim().lowercase()
                        metaNote = metaJson.optString("admin_msg").ifBlank { metaJson.optString("note") }
                        isRemoved = metaJson.optBoolean("removed", false)
                    }
                }

                if (isRemoved) {
                    return@withContext ServerFireOrderResult(
                        ok = true,
                        uid = uid,
                        status = "failed",
                        adminMessage = metaNote.ifBlank { "Something went wrong while generating. Please retry." },
                        videoUrl = ""
                    )
                }

                if (!videoFileId.isNullOrBlank() && metaStatus in setOf("", "pending", "processing", "ready", "done", "completed")) {
                    val downloadUrl = "https://www.googleapis.com/drive/v3/files/$videoFileId?alt=media&supportsAllDrives=true"
                    return@withContext ServerFireOrderResult(
                        ok = true,
                        uid = uid,
                        status = "ready",
                        adminMessage = metaNote,
                        videoUrl = downloadUrl
                    )
                }

                if (!orderJsonId.isNullOrBlank()) {
                    return@withContext ServerFireOrderResult(
                        ok = true,
                        uid = uid,
                        status = metaStatus.ifBlank { "processing" },
                        adminMessage = metaNote,
                        videoUrl = ""
                    )
                }

                ServerFireOrderResult(
                    ok = true,
                    uid = uid,
                    status = "processing",
                    adminMessage = "",
                    videoUrl = ""
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
                    errorMessage = e.localizedMessage ?: "Status poll failed"
                )
            }
        }

    suspend fun downloadDriveMediaBytes(config: DriveFireConfig, fileUrl: String): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val token = getServiceAccountToken(config.saJson).ifBlank { getOAuthRefreshToken(config.saJson) }
                if (token.isBlank()) return@withContext null
                val req = Request.Builder()
                    .url(fileUrl)
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val bytes = resp.body?.bytes() ?: return@withContext null
                    if (bytes.size <= 1024) return@withContext null
                    bytes
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Drive media download failed", e)
                null
            }
        }

    suspend fun purgeOrderFiles(config: DriveFireConfig, uid: String) = withContext(Dispatchers.IO) {
        try {
            val tokens = listOfNotNull(
                getOAuthRefreshToken(config.saJson).takeIf { it.isNotBlank() },
                getServiceAccountToken(config.saJson).takeIf { it.isNotBlank() }
            )
            if (tokens.isEmpty()) return@withContext
            val listToken = tokens.first()
            val q = URLEncoder.encode("'${config.folderId}' in parents and trashed=false", "UTF-8")
            val url = "https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id,name)&pageSize=200&supportsAllDrives=true&includeItemsFromAllDrives=true"
            val listReq = Request.Builder().url(url).header("Authorization", "Bearer $listToken").get().build()
            val raw = httpClient.newCall(listReq).execute().use { it.body?.string().orEmpty() }
            val files = runCatching { JSONObject(raw).optJSONArray("files") }.getOrNull() ?: return@withContext
            val idsToDelete = mutableListOf<String>()
            for (i in 0 until files.length()) {
                val f = files.optJSONObject(i) ?: continue
                val name = f.optString("name")
                val id = f.optString("id")
                if (id.isNotBlank() && (
                        name == "ORDER_$uid.json" ||
                            name.startsWith("IMG_${uid}_") ||
                            matchesUidMediaFile(name, uid)
                        )
                ) {
                    idsToDelete.add(id)
                }
            }
            for (fid in idsToDelete) {
                for (tok in tokens) {
                    val delUrl = "https://www.googleapis.com/drive/v3/files/$fid?supportsAllDrives=true"
                    val delReq = Request.Builder().url(delUrl).header("Authorization", "Bearer $tok").delete().build()
                    val deleted = runCatching {
                        httpClient.newCall(delReq).execute().use { it.isSuccessful || it.code == 404 }
                    }.getOrDefault(false)
                    if (deleted) break
                    val trashReq = Request.Builder()
                        .url(delUrl)
                        .header("Authorization", "Bearer $tok")
                        .patch("""{"trashed":true}""".toRequestBody("application/json".toMediaType()))
                        .build()
                    val trashed = runCatching {
                        httpClient.newCall(trashReq).execute().use { it.isSuccessful || it.code == 404 }
                    }.getOrDefault(false)
                    if (trashed) break
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private fun fetchFileText(token: String, fileId: String): String {
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media&supportsAllDrives=true")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return runCatching { httpClient.newCall(req).execute().use { it.body?.string().orEmpty() } }.getOrDefault("")
    }

    private data class DriveUploadOutcome(val fileId: String, val errorMessage: String = "")

    private fun uploadMultipartToDrive(
        token: String,
        folderId: String,
        name: String,
        mimeType: String,
        data: ByteArray
    ): DriveUploadOutcome {
        val boundary = "----vp${System.currentTimeMillis()}"
        val meta = JSONObject().apply {
            put("name", name)
            put("parents", org.json.JSONArray(listOf(folderId)))
        }.toString()

        val head = ("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
            "--$boundary\r\nContent-Type: $mimeType\r\n\r\n").toByteArray(Charsets.UTF_8)
        val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val combined = ByteArray(head.size + data.size + tail.size)
        System.arraycopy(head, 0, combined, 0, head.size)
        System.arraycopy(data, 0, combined, head.size, data.size)
        System.arraycopy(tail, 0, combined, head.size + data.size, tail.size)

        val req = Request.Builder()
            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true")
            .header("Authorization", "Bearer $token")
            .post(combined.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
            .build()

        return httpClient.newCall(req).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                Log.w(TAG, "Drive upload HTTP ${resp.code}: $bodyStr")
                val detail = runCatching {
                    JSONObject(bodyStr).optJSONObject("error")?.optString("message").orEmpty()
                }.getOrDefault("")
                val msg = if (detail.isNotBlank()) "Drive HTTP ${resp.code}: $detail" else "Drive upload HTTP ${resp.code}"
                DriveUploadOutcome("", msg)
            } else {
                val id = runCatching { JSONObject(bodyStr).optString("id") }.getOrDefault("")
                DriveUploadOutcome(id, if (id.isBlank()) "Drive upload returned empty file ID." else "")
            }
        }
    }

    private fun getUploadToken(saJson: JSONObject): String {
        val oauth = getOAuthRefreshToken(saJson)
        if (oauth.isNotBlank()) return oauth
        return getServiceAccountToken(saJson)
    }

    private fun getOAuthRefreshToken(saJson: JSONObject): String {
        val cid = saJson.optString("oauth_client_id").trim()
        val cs = saJson.optString("oauth_client_secret").trim()
        val rt = saJson.optString("oauth_refresh_token").trim()
        if (cid.isBlank() || cs.isBlank() || rt.isBlank()) return ""

        val now = System.currentTimeMillis() / 1000L
        if (cachedOAuthToken.isNotBlank() && now < cachedOAuthExp - 60) {
            return cachedOAuthToken
        }

        val form = "client_id=${URLEncoder.encode(cid, "UTF-8")}" +
            "&client_secret=${URLEncoder.encode(cs, "UTF-8")}" +
            "&refresh_token=${URLEncoder.encode(rt, "UTF-8")}" +
            "&grant_type=refresh_token"
        val req = Request.Builder()
            .url("https://oauth2.googleapis.com/token")
            .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        return runCatching {
            val res = httpClient.newCall(req).execute()
            val json = JSONObject(res.body?.string().orEmpty())
            val tok = json.optString("access_token")
            if (tok.isNotBlank()) {
                cachedOAuthToken = tok
                cachedOAuthExp = now + json.optLong("expires_in", 3600L)
            }
            tok
        }.getOrDefault("")
    }

    private fun getServiceAccountToken(saJson: JSONObject): String {
        val now = System.currentTimeMillis() / 1000L
        if (cachedSaToken.isNotBlank() && now < cachedSaExp - 60) {
            return cachedSaToken
        }

        val clientEmail = saJson.optString("client_email").trim()
        val rawPem = saJson.optString("private_key")
            .replace("\\r\\n", "\n")
            .replace("\\n", "\n")
            .replace("\\r", "\n")
            .trim()
        if (clientEmail.isBlank() || rawPem.isBlank()) return ""

        return runCatching {
            val pemBody = rawPem
                .replace(Regex("-----[A-Z ]+-----"), "")
                .replace(Regex("\\s+"), "")
            val pkcs8Bytes = Base64.decode(pemBody, Base64.DEFAULT)
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))

            val header = b64Url(JSONObject(mapOf("alg" to "RS256", "typ" to "JWT")).toString().toByteArray())
            val claims = b64Url(
                JSONObject(
                    mapOf(
                        "iss" to clientEmail,
                        "scope" to "https://www.googleapis.com/auth/drive",
                        "aud" to "https://oauth2.googleapis.com/token",
                        "iat" to now,
                        "exp" to (now + 3600L)
                    )
                ).toString().toByteArray()
            )
            val signingInput = "$header.$claims"
            val signature = Signature.getInstance("SHA256withRSA").apply {
                initSign(privateKey)
                update(signingInput.toByteArray(Charsets.UTF_8))
            }.sign()
            val jwt = "$signingInput.${b64Url(signature)}"

            val form = "grant_type=${URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", "UTF-8")}" +
                "&assertion=${URLEncoder.encode(jwt, "UTF-8")}"
            val req = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            val res = httpClient.newCall(req).execute()
            val json = JSONObject(res.body?.string().orEmpty())
            val tok = json.optString("access_token")
            if (tok.isNotBlank()) {
                cachedSaToken = tok
                cachedSaExp = now + json.optLong("expires_in", 3600L)
            }
            tok
        }.getOrDefault("")
    }

    companion object {
        private const val TAG = "GoogleDriveFireClient"

        fun matchesUidMediaFile(name: String, uid: String): Boolean {
            val n = name.lowercase()
            val u = uid.lowercase()
            if (u.isBlank() || !n.startsWith(u) || n.startsWith("order_") || n.startsWith("img_")) {
                return false
            }
            val next = n.getOrNull(u.length)
            return next == null || !next.isDigit()
        }

        fun extractFolderId(link: String): String {
            val folderMatch = Regex("/folders/([A-Za-z0-9_-]{10,})").find(link)
            if (folderMatch != null) return folderMatch.groupValues[1]
            val idMatch = Regex("[?&]id=([A-Za-z0-9_-]{10,})").find(link)
            if (idMatch != null) return idMatch.groupValues[1]
            return ""
        }

        private fun b64Url(bytes: ByteArray): String =
            Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
