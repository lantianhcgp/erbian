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
        get() = _mcpEnabled.value
        set(v) { _mcpEnabled.value = v; sp.edit().putBoolean("mcp_enabled", v).apply() }

    var dynamicColor: Boolean
        get() = _dynamicColor.value
        set(v) { _dynamicColor.value = v; sp.edit().putBoolean("dynamic_color", v).apply() }

    var pureBlack: Boolean
        get() = _pureBlack.value
        set(v) { _pureBlack.value = v; sp.edit().putBoolean("pure_black", v).apply() }

    // Compose 可观察：纯 SharedPreferences 字段写入不触发重组 → Switch 看着"按不动"、
    // 主题也不刷新（2026-10-16 用户反馈：设置页两个开关点了没反应）
    private val _mcpEnabled = androidx.compose.runtime.mutableStateOf(sp.getBoolean("mcp_enabled", true))
    private val _dynamicColor = androidx.compose.runtime.mutableStateOf(sp.getBoolean("dynamic_color", true))
    private val _pureBlack = androidx.compose.runtime.mutableStateOf(sp.getBoolean("pure_black", false))

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
