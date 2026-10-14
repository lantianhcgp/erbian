package com.lantianhcgp.erbian.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件日志（设计文档 §五：MCP log_tail 暴露完整日志）。
 * 落 filesDir/logs/app-yyyyMMdd.log，附内存环形缓冲（tail 用）。
 */
class Logger(context: Context) {

    private val dir = File(context.filesDir, "logs").apply { mkdirs() }
    private val ring = ArrayDeque<String>(500)
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
    private val lock = Any()

    fun i(tag: String, msg: String) = log("I", tag, msg)
    fun w(tag: String, msg: String) = log("W", tag, msg)
    fun e(tag: String, msg: String, tr: Throwable? = null) =
        log("E", tag, msg + (tr?.let { " :: ${it.javaClass.simpleName}: ${it.message}" } ?: ""))

    private fun log(level: String, tag: String, msg: String) {
        val line = "${fmt.format(Date())} $level/$tag: $msg"
        synchronized(lock) {
            ring.addLast(line)
            if (ring.size > 500) ring.removeFirst()
            runCatching {
                File(dir, "app-${dayFmt.format(Date())}.log").appendText(line + "\n")
            }
        }
    }

    fun tail(lines: Int = 200): List<String> = synchronized(lock) { ring.toList().takeLast(lines) }

    fun logDir(): File = dir
}
