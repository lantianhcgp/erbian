package com.lantianhcgp.erbian.voice

import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class AssistInteractionService : VoiceInteractionService()

class AssistSessionService : VoiceInteractionSessionService() {
    // SDK35 真实签名: onNewSession(Bundle args)
    override fun onNewSession(args: android.os.Bundle?): VoiceInteractionSession = AssistSession(this)
}
