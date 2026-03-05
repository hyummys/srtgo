package com.srtgo.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.srtgo.app.R

class MacroNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "macro_progress"
        const val NOTIFICATION_ID = 1001
        private const val CHANNEL_NAME = "매크로 진행"
        private const val CHANNEL_DESC = "매크로 예매 진행 상태를 표시합니다"
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESC
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildProgressNotification(attempts: Int, elapsed: Long): Notification {
        val min = elapsed / 60
        val sec = elapsed % 60
        val elapsedText = String.format("%02d:%02d", min, sec)

        return baseBuilder()
            .setContentTitle("매크로 실행중")
            .setContentText("시도 ${attempts}회 | 소요 $elapsedText")
            .setOngoing(true)
            .setProgress(0, 0, true)
            .addAction(buildCancelAction())
            .build()
    }

    fun buildSuccessNotification(trainInfo: String): Notification {
        return baseBuilder()
            .setContentTitle("예매 성공!")
            .setContentText(trainInfo)
            .setOngoing(false)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    fun buildFailureNotification(error: String): Notification {
        return baseBuilder()
            .setContentTitle("매크로 실패")
            .setContentText(error)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
    }

    private fun baseBuilder(): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
    }

    private fun buildCancelAction(): NotificationCompat.Action {
        val cancelIntent = Intent(context, MacroForegroundService::class.java).apply {
            action = MacroForegroundService.ACTION_CANCEL
        }
        val pendingIntent = PendingIntent.getService(
            context, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Action.Builder(
            0, "취소", pendingIntent
        ).build()
    }
}
