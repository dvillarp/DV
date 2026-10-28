package com.dv.wimc

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.LocationServices

/**
 * Detección automática de coche con la API de transiciones de Activity Recognition
 * (IN_VEHICLE entra/sale). Al salir del vehículo avisa con una notificación
 * "Parece que has parado. ¿Aparcar aquí?" o, si "Auto-aparcar" está activo y hay permiso, guarda solo.
 */
object DrivingDetection {
    const val CHANNEL_ID = "wimc_driving"
    const val NOTIF_ID = 4201
    const val ACTION_TRANSITION = "com.dv.wimc.TRANSITION"
    const val ACTION_IGNORE = "com.dv.wimc.IGNORE"
    const val ACTION_PARK_NOW = "com.dv.wimc.PARK_NOW"
    const val ACTION_AUTO_ALWAYS = "com.dv.wimc.AUTO_ALWAYS"

    fun hasPerm(ctx: Context, perm: String): Boolean =
        ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Detección de aparcamiento", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun transitionPending(ctx: Context): PendingIntent {
        val i = Intent(ctx, DrivingReceiver::class.java).setAction(ACTION_TRANSITION)
        return PendingIntent.getBroadcast(
            ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    fun register(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 29 && !hasPerm(ctx, Manifest.permission.ACTIVITY_RECOGNITION)) return
        val transitions = listOf(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build(),
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.IN_VEHICLE)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build(),
        )
        try {
            ActivityRecognition.getClient(ctx)
                .requestActivityTransitionUpdates(ActivityTransitionRequest(transitions), transitionPending(ctx))
        } catch (e: SecurityException) {
        }
    }

    @SuppressLint("MissingPermission")
    fun unregister(ctx: Context) {
        try {
            ActivityRecognition.getClient(ctx).removeActivityTransitionUpdates(transitionPending(ctx))
        } catch (e: SecurityException) {
        }
    }

    private fun activityPending(ctx: Context, action: String, rc: Int): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, rc, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    private fun post(ctx: Context, n: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 33 && !hasPerm(ctx, Manifest.permission.POST_NOTIFICATIONS)) return
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID, n)
    }

    fun notifyStopped(ctx: Context) {
        ensureChannel(ctx)
        val ignore = PendingIntent.getBroadcast(
            ctx, 12, Intent(ctx, DrivingReceiver::class.java).setAction(ACTION_IGNORE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Parece que has parado")
            .setContentText("¿Aparcar aquí?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(activityPending(ctx, ACTION_PARK_NOW, 10))
            .addAction(0, "Aparcar ahora", activityPending(ctx, ACTION_PARK_NOW, 10))
            .addAction(0, "Auto-aparcar siempre", activityPending(ctx, ACTION_AUTO_ALWAYS, 11))
            .addAction(0, "Ignorar", ignore)
            .build()
        post(ctx, n)
    }

    fun notifySaved(ctx: Context) {
        ensureChannel(ctx)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("✅ Coche guardado automáticamente")
            .setContentText("Abre WIMC para ver dónde has aparcado")
            .setAutoCancel(true)
            .setContentIntent(activityPending(ctx, Intent.ACTION_MAIN, 13))
            .build()
        post(ctx, n)
    }
}

class DrivingReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val store = Store(ctx)
        when (intent.action) {
            DrivingDetection.ACTION_IGNORE ->
                NotificationManagerCompat.from(ctx).cancel(DrivingDetection.NOTIF_ID)

            DrivingDetection.ACTION_TRANSITION -> {
                if (!ActivityTransitionResult.hasResult(intent)) return
                val result = ActivityTransitionResult.extractResult(intent) ?: return
                for (e in result.transitionEvents) {
                    if (e.activityType != DetectedActivity.IN_VEHICLE) continue
                    if (e.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER) {
                        store.driving = true
                    } else if (store.autoDetect) {
                        onStopped(ctx, store)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun onStopped(ctx: Context, store: Store) {
        store.driving = false
        val canAuto = store.autoPark &&
            DrivingDetection.hasPerm(ctx, Manifest.permission.ACCESS_FINE_LOCATION) &&
            (Build.VERSION.SDK_INT < 29 || DrivingDetection.hasPerm(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION))
        if (!canAuto) {
            DrivingDetection.notifyStopped(ctx)
            return
        }
        val pending = goAsync()
        try {
            LocationServices.getFusedLocationProviderClient(ctx).lastLocation.addOnCompleteListener { t ->
                val loc = if (t.isSuccessful) t.result else null
                val fresh = loc != null && System.currentTimeMillis() - loc.time < 10 * 60_000L
                if (loc != null && fresh && loc.hasAccuracy() && loc.accuracy < 100f) {
                    store.savePark(ParkPoint(loc.latitude, loc.longitude, System.currentTimeMillis(), loc.accuracy, "auto"))
                    DrivingDetection.notifySaved(ctx)
                } else {
                    DrivingDetection.notifyStopped(ctx)
                }
                pending.finish()
            }
        } catch (e: SecurityException) {
            DrivingDetection.notifyStopped(ctx)
            pending.finish()
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Store(ctx).autoDetect) {
            DrivingDetection.register(ctx)
        }
    }
}
