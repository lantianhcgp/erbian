package net

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Signature {
    /** HMAC-SHA256, 输出小写 hex。 */
    fun hmacSha256Hex(secret: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(raw.size * 2)
        for (b in raw) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    const val HEADER = "X-Webhook-Signature-V2"
}

/**
 * 上行 webhook 契约（计划 Task 7）。
 * POST {baseUrl}  body = 下方 JSON  签名头 = Signature.HEADER: hmac(body)
 */
object UplinkContract {
    const val ORIGIN_FLOAT = "float"
    const val ORIGIN_CHANNEL = "channel"

    fun bodyJson(id: String, text: String, origin: String, ts: Long): String =
        json.Json.write(
            json.Json.obj(
                "id" to json.JsonValue.Str(id),
                "text" to json.JsonValue.Str(text),
                "origin" to json.JsonValue.Str(origin),
                "ts" to json.JsonValue.Num(ts.toDouble()),
            )
        )
}
