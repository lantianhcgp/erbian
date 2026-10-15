package net

import json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureTest {

    @Test
    fun knownHmacVector() {
        // RFC 2202 风格公开测试向量 (HMAC-SHA256)
        val hex = Signature.hmacSha256Hex("key", "The quick brown fox jumps over the lazy dog")
        assertEquals("f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8", hex)
    }

    @Test
    fun headerNameStable() {
        // Hermes webhook 平台通用 V2 验签头（契约，勿改回自造头）
        assertEquals("X-Webhook-Signature-V2", Signature.HEADER)
    }

    @Test
    fun v2SigningContent() {
        // V2 签名内容 = "<秒级时间戳>.<body>" —— 与 gateway/platforms/webhook.py 对齐
        val ts = "1792000000"
        val body = "{\"id\":\"x\"}"
        val sig = Signature.hmacSha256Hex("s3cr3t", "$ts.$body")
        assertEquals(64, sig.length)
        // 双重签名不同内容必不同
        assertTrue(sig != Signature.hmacSha256Hex("s3cr3t", body))
    }

    @Test
    fun uplinkBodyShape() {
        val body = UplinkContract.bodyJson("m1", "明天月考", UplinkContract.ORIGIN_FLOAT, 1760000000000L)
        val v = Json.parse(body)
        assertEquals("m1", v.optString("id"))
        assertEquals("明天月考", v.optString("text"))
        assertEquals("float", v.optString("origin"))
        assertEquals(1760000000000L, v.optLong("ts"))
        assertTrue(body.startsWith("{") && body.endsWith("}"))
    }
}
