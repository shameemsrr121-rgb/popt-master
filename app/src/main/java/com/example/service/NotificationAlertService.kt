package com.example.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.AppSettings
import com.example.data.SoundSourceType
import com.example.data.entity.PurchaseOrderEntity
import com.example.data.entity.PurchasePlanNoteEntity
import java.util.Calendar
import java.util.concurrent.TimeUnit

object NotificationAlertService {
    private const val TAG = "NotificationAlertService"
    const val CHANNEL_ID_ALERTS = "garden_po_master_alerts_channel"
    const val CHANNEL_NAME_ALERTS = "PO & Purchase Plan Alerts"

    private var activeMediaPlayer: MediaPlayer? = null

    fun initializeNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                CHANNEL_NAME_ALERTS,
                importance
            ).apply {
                description = "Alerts for approaching PO delivery dates and purchase planning schedules"
                enableVibration(true)
                setShowBadge(true)
            }

            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Plays alert sound based on current AppSettings or preview settings.
     */
    fun playAlertSound(context: Context, settings: AppSettings) {
        if (!settings.soundAlertsEnabled) {
            if (settings.vibrateAlertsEnabled) {
                triggerVibration(context)
            }
            return
        }

        stopCurrentSound()

        try {
            when (settings.soundSourceType) {
                SoundSourceType.PRESET -> {
                    playPresetTone(settings.selectedPresetSound)
                }
                SoundSourceType.SYSTEM_NOTIFICATION -> {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    playMediaUri(context, uri)
                }
                SoundSourceType.SYSTEM_ALARM -> {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    playMediaUri(context, uri)
                }
                SoundSourceType.SYSTEM_RINGTONE -> {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    playMediaUri(context, uri)
                }
                SoundSourceType.CUSTOM_AUDIO -> {
                    if (settings.customAudioUri.isNotBlank()) {
                        val uri = Uri.parse(settings.customAudioUri)
                        playMediaUri(context, uri)
                    } else {
                        playPresetTone(settings.selectedPresetSound)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play sound: ${e.message}", e)
            playPresetTone("Crystal Chime")
        }

        if (settings.vibrateAlertsEnabled) {
            triggerVibration(context)
        }
    }

    private fun playPresetTone(presetName: String) {
        try {
            val toneType = when (presetName) {
                "Service Bell" -> ToneGenerator.TONE_CDMA_HIGH_L
                "Modern Ping" -> ToneGenerator.TONE_PROP_BEEP
                "Soft Melody" -> ToneGenerator.TONE_SUP_CONFIRM
                "Elegant Harp" -> ToneGenerator.TONE_PROP_PROMPT
                "Urgent Alert" -> ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
                else -> ToneGenerator.TONE_PROP_ACK // Crystal Chime
            }
            val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
            toneGenerator.startTone(toneType, 350)
        } catch (e: Exception) {
            Log.e(TAG, "ToneGenerator error", e)
        }
    }

    private fun playMediaUri(context: Context, uri: Uri) {
        try {
            activeMediaPlayer?.release()
            activeMediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .build()
                )
                setDataSource(context, uri)
                prepare()
                start()
                setOnCompletionListener {
                    try {
                        it.release()
                    } catch (_: Exception) {}
                    if (activeMediaPlayer == it) activeMediaPlayer = null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer playback error: ${e.message}", e)
            playPresetTone("Crystal Chime")
        }
    }

    fun stopCurrentSound() {
        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
            activeMediaPlayer = null
        } catch (_: Exception) {}
    }

    private fun triggerVibration(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(200)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }
    }

    /**
     * Posts a notification alert for approaching PO delivery.
     */
    fun postPODeliveryNotification(
        context: Context,
        po: PurchaseOrderEntity,
        daysLeft: Long,
        isOverdue: Boolean,
        settings: AppSettings
    ) {
        if (!settings.notificationsEnabled || !settings.poDeliveryAlertsEnabled) return
        initializeNotificationChannels(context)

        val title = if (isOverdue) {
            "⚠️ Overdue PO: ${po.poNumber}"
        } else if (daysLeft == 0L) {
            "📦 PO Delivery Expected Today: ${po.poNumber}"
        } else {
            "🚚 Approaching PO Delivery (${daysLeft}d left): ${po.poNumber}"
        }

        val dateStr = java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(po.expectedDeliveryDateMillis))
        val message = "${po.supplierName} • Total: ₹${String.format("%.2f", po.totalAmount)} • Due: $dateStr"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            po.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$message\nStatus: ${po.status} • Location: ${po.location}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(po.id.toInt() + 1000, builder.build())

        playAlertSound(context, settings)
    }

    /**
     * Posts a notification alert for approaching Purchase Plan.
     */
    fun postPurchasePlanNotification(
        context: Context,
        plan: PurchasePlanNoteEntity,
        daysLeft: Long,
        settings: AppSettings
    ) {
        if (!settings.notificationsEnabled || !settings.purchasePlanAlertsEnabled) return
        initializeNotificationChannels(context)

        val supplierOrTitle = if (plan.categoryOrSupplier.isNotBlank()) plan.categoryOrSupplier else plan.title
        val title = if (daysLeft == 0L) {
            "📅 Purchase Plan Scheduled Today: $supplierOrTitle"
        } else {
            "📝 Approaching Purchase Plan (${daysLeft}d left): $supplierOrTitle"
        }

        val dateStr = java.text.SimpleDateFormat("dd-MMM-yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(plan.plannedDateMillis))
        val message = "Priority: ${plan.priority} • Est: ₹${String.format("%.2f", plan.estimatedBudget)} • Date: $dateStr"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            plan.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$message\nNote: ${plan.notes}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(plan.id.toInt() + 2000, builder.build())

        playAlertSound(context, settings)
    }
}
