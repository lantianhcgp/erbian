package com.lantianhcgp.erbian.voice

import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class AssistInteractionService : VoiceInteractionService()

class AssistSessionService : VoiceInteractionSessionService() {
    override fun onCreateSession(): VoiceInteractionSession = AssistSession(this)
}
