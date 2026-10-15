package com.lantianhcgp.erbian.voice

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 识别服务桩：耳边只做文本输入（转写交给输入法，设计文档 §一），从不做人声识别。
 * 但 assist_interaction.xml 的 recognitionService 是**必填项**——缺失时系统报
 * "Bad voice interaction service: No recognitionService specified"，
 * VoiceInteractionServiceInfo 解析失败 → mComponentInfo=null → showSession NPE，
 * 长按唤醒永远不会到达 App（2026-10-15 真机 logcat 实锤）。
 * 校验器只检查该组件能作为服务解析；识别永不被调起，onBind 返回 null 即可。
 */
class AssistRecognitionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
