package com.lantianhcgp.erbian.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 浮层吊命服务（:assist 进程）。
 * 2026-10-15 实锤：会话回调后系统会自动结束会话→解绑→:assist 进程秒回收→悬浮窗随进程消失
 * （日志：overlay added 后 3 秒 onCreate 又跑了一遍 = 进程已重启）。
 * 浮层展示期间以前台服务钉住进程；浮层关闭即 stopSelf。
 */
class AssistKeepAliveService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        runCatching {
            nm.createNotificationChannel(
                NotificationChannel("erbian_float", "耳边浮层", NotificationManager.IMPORTANCE_LOW)
            )
            val n = Notification.Builder(this, "erbian_float")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("耳边")
                .setContentText("唤醒浮层运行中")
                .setOngoing(true)
                .build()
            startForeground(41, n)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
