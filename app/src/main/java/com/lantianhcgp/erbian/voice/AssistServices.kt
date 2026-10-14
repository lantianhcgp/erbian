package com.lantianhcgp.erbian.voice

import android.os.UserHandle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class AssistInteractionService : VoiceInteractionService()

class AssistSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(user: UserHandle?): VoiceInteractionSession = AssistSession(this)
}
