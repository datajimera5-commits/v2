package com.example

import com.example.data.network.GoogleDriveFireClient
import com.example.data.network.VidoProServerClient
import com.example.data.network.VidoProSigner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class ExampleUnitTest {
    @Test
    fun stableStringify_sortsKeysAndPreservesSlashes() {
        val raw = VidoProSigner.stableStringify(
            mapOf(
                "z" to 1,
                "endpoint" to "/sub/plans",
                "a" to listOf("x", 2, true, null)
            )
        )
        assertEquals(
            """{"a":["x",2,true,null],"endpoint":"/sub/plans","z":1}""",
            raw
        )
    }

    @Test
    fun signPayload_matchesControlCenterHmacFormat() {
        val secret = "sample_unit_test_secret"
        val ts = "1732737556"
        val nonce = "00000000-0000-4000-8000-000000000001"
        val (body, sig) = VidoProSigner.signPayload(
            secret = secret,
            endpoint = "/sub/plans",
            payload = mapOf("device_id" to "VP-123"),
            ts = ts,
            nonce = nonce
        )

        val expectedCanon = """{"device_id":"VP-123","endpoint":"/sub/plans"}"""
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        }
        val expectedSig = mac.doFinal((expectedCanon + ts + nonce).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        assertEquals(expectedSig, sig)
        assertTrue(body.contains(""""endpoint":"/sub/plans""""))
        assertTrue(body.contains(""""ts":"$ts""""))
        assertTrue(body.contains(""""nonce":"$nonce""""))
        assertTrue(body.contains(""""sig":"$expectedSig""""))
    }

    @Test
    fun matchesUidMediaFile_acceptsResultVideoAndRejectsOrderOrOtherUid() {
        val uid = "FO-ABCDEF-T0-123456-01"
        assertTrue(GoogleDriveFireClient.matchesUidMediaFile("${uid}.mp4", uid))
        assertTrue(GoogleDriveFireClient.matchesUidMediaFile("${uid}_final.mp4", uid))
        assertFalse(GoogleDriveFireClient.matchesUidMediaFile("ORDER_${uid}.json", uid))
        assertFalse(GoogleDriveFireClient.matchesUidMediaFile("IMG_${uid}_0.jpg", uid))
        assertFalse(GoogleDriveFireClient.matchesUidMediaFile("${uid}2.mp4", uid))
    }

    @Test
    fun extractFolderId_parsesDriveFolderUrls() {
        assertEquals(
            "1AbCdEfGhIjKlMnOpQrStUv",
            GoogleDriveFireClient.extractFolderId("https://drive.google.com/drive/folders/1AbCdEfGhIjKlMnOpQrStUv?usp=sharing")
        )
        assertEquals(
            "1AbCdEfGhIjKlMnOpQrStUv",
            GoogleDriveFireClient.extractFolderId("https://drive.google.com/open?id=1AbCdEfGhIjKlMnOpQrStUv")
        )
    }

    @Test
    fun normalizeEndpoint_requiresHttps() {
        assertEquals(
            "https://qr.apnakundli.com/index.php",
            VidoProServerClient.normalizeEndpoint("  https://qr.apnakundli.com/index.php  ")
        )
        assertNull(VidoProServerClient.normalizeEndpoint("http://qr.apnakundli.com/index.php"))
    }
}

