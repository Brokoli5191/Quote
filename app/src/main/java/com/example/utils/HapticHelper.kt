package com.example.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticHelper {
    fun triggerLight(context: Context) {
        triggerVibration(context, duration = 15, amplitude = 45)
    }

    fun triggerMedium(context: Context) {
        triggerVibration(context, duration = 30, amplitude = 125)
    }

    fun triggerHeavy(context: Context) {
        triggerVibration(context, duration = 50, amplitude = 220)
    }

    private fun triggerVibration(context: Context, duration: Long, amplitude: Int) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createOneShot(duration, amplitude.coerceIn(1, 255))
                    vibrator.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(duration)
                }
            }
        } catch (e: Exception) {
            // Safe fallback to prevent crashes on unsupported devices
        }
    }
}
