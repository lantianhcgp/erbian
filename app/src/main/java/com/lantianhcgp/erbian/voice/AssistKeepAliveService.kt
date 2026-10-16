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
        // 2026-10-16 用户：不要抢前台的自动启动，改后台服务减少侵入性。
        // 原实现 startForeground + 常驻通知「耳边浮层运行中」；现改为纯后台吊命：
        // 不建通知渠道、不占前台、不发通知（浮层关闭仍 stopSelf）。
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
