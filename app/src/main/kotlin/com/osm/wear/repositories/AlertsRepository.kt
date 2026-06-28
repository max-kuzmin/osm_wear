package com.osm.wear.repositories

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.osm.wear.models.enums.NavigationAlertMode
import com.osm.wear.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: SharedPreferences
) : IAlertsRepository, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AlertsRepository"
    }

    private val _alertMode = MutableStateFlow(NavigationAlertMode.VOICE)
    override val alertMode: StateFlow<NavigationAlertMode> = _alertMode.asStateFlow()

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? = if (android.os.Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        _alertMode.value = getAlertMode()
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.ENGLISH
            isTtsInitialized = true
        } else {
            Log.e(TAG, "TTS Initialization failed")
        }
    }

    override fun getAlertMode(): NavigationAlertMode {
        val name = prefs.getString("navigation_alert_mode", NavigationAlertMode.VOICE.name)
        return try {
            NavigationAlertMode.valueOf(name!!)
        } catch (e: Exception) {
            NavigationAlertMode.VOICE
        }
    }

    override fun setAlertMode(mode: NavigationAlertMode) {
        _alertMode.value = mode
        prefs.edit().putString("navigation_alert_mode", mode.name).apply()
    }

    override fun announce(message: String) {
        val mode = _alertMode.value
        if (mode == NavigationAlertMode.SILENT) return

        if (mode == NavigationAlertMode.VOICE) {
            if (isTtsInitialized && tts != null) {
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                playNotificationSound()
                showDeviceNotification(message)
            }
        } else if (mode == NavigationAlertMode.SOUND) {
            playNotificationSound()
        }
    }

    override fun vibrate(pattern: LongArray) {
        val mode = _alertMode.value
        if (mode == NavigationAlertMode.SILENT) return

        if (mode == NavigationAlertMode.VOICE || mode == NavigationAlertMode.SOUND || mode == NavigationAlertMode.VIBRATION) {
            try {
                val amplitudes = IntArray(pattern.size) { idx -> if (idx % 2 == 0) 0 else 255 }
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        pattern,
                        amplitudes,
                        -1
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Vibration failed", e)
            }
        }
    }

    override fun release() {
        tts?.stop()
        tts?.shutdown()
    }

    private fun playNotificationSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Notification sound failed", e)
        }
    }

    private fun showDeviceNotification(message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "navigation_channel",
                    "Navigation",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Shows ongoing navigation status"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntentFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingIntentFlags)

            val notification = NotificationCompat.Builder(context, "navigation_channel")
                .setContentTitle("Navigation Alert")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(102, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show device notification", e)
        }
    }
}
