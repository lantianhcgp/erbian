package com.lantianhcgp.erbian.voice

import android.service.voice.VoiceRecognitionService

/**
 * 识别服务桩：耳边只做文本输入（转写交给输入法，设计文档 §一），从不做人声识别。
 * 但 assist_interaction.xml 的 recognitionService 是**必填项**——缺失时系统报
 * "Bad voice interaction service: No recognitionService specified"，
 * VoiceInteractionServiceInfo 解析失败 → mComponentInfo=null → showSession NPE，
 * 长按唤醒永远不会到达 App（2026-10-15 真机 logcat 实锤）。
 * 本桩只满足校验；识别被调起时基类自行处理，我们永不调用。
 */
class AssistRecognitionService : VoiceRecognitionService() {
}
