package com.lantianhcgp.erbian.util

import android.content.Context
import java.security.SecureRandom

/**
 * 运行配置（SharedPreferences）。
 * uplink: webhook 上行地址 + HMAC secret
 * server: 内嵌服务端口/token/MCP 开关
 * wake:   本应用 assistant 组件名
 */
class AppConfig(context: Context) {

    private val sp = context.getSharedPreferences("erbian", Context.MODE_PRIVATE)

    var uplinkUrl: String
        get() = sp.getString("uplink_url", DEFAULT_UPLINK_URL)!!
        set(v) = sp.edit().putString("uplink_url", v).apply()

    var uplinkSecret: String
        get() = sp.getString("uplink_secret", "")!!
        set(v) = sp.edit().putString("uplink_secret", v).apply()

    var serverPort: Int
        get() = sp.getInt("server_port", 8790)
        set(v) = sp.edit().putInt("server_port", v).apply()

    var serverToken: String
        get() = sp.getString("server_token", "")!!.let {
            if (it.isEmpty()) {
                val gen = randomToken()
                sp.edit().putString("server_token", gen).apply()
                gen
            } else it
        }
        set(v) = sp.edit().putString("server_token", v).apply()

    var mcpEnabled: Boolean
        get() = sp.getBoolean("mcp_enabled", true)
        set(v) = sp.edit().putBoolean("mcp_enabled", v).apply()

    var dynamicColor: Boolean
        get() = sp.getBoolean("dynamic_color", true)
        set(v) = sp.edit().putBoolean("dynamic_color", v).apply()

    var pureBlack: Boolean
        get() = sp.getBoolean("pure_black", false)
        set(v) = sp.edit().putBoolean("pure_black", v).apply()

    companion object {
        const val DEFAULT_UPLINK_URL = "http://127.0.0.1:8644/webhooks/erbian-record"

        fun randomToken(): String {
            val r = SecureRandom()
            val b = ByteArray(24)
            r.nextBytes(b)
            return b.joinToString("") { "%02x".format(it) }
        }
    }
}
