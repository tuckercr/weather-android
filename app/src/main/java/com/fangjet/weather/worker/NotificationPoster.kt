package com.fangjet.weather.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fangjet.weather.MainActivity
import com.fangjet.weather.R
import com.fangjet.weather.ui.pets.petSceneIcon

/** Whether the app may post notifications (always below Android 13, which has no permission for it). */
internal fun Context.canPostNotifications(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Creates (or, if it exists, just renames) the channel for [kind]. Safe to call again: Android keeps
 * the user's own choices for a channel that already exists.
 */
internal fun Context.createNotificationChannel(kind: NotificationKind) {
    val (id, name, description) = when (kind) {
        NotificationKind.MORNING_BRIEFING -> Triple(
            NotificationChannels.CHANNEL_MORNING_BRIEFING,
            R.string.notif_channel_briefing_name,
            R.string.notif_channel_briefing_description,
        )
        NotificationKind.WALK_TIME -> Triple(
            NotificationChannels.CHANNEL_WALK_TIME,
            R.string.notif_channel_walk_name,
            R.string.notif_channel_walk_description,
        )
        NotificationKind.RAIN -> Triple(
            NotificationChannels.CHANNEL_RAIN,
            R.string.notif_channel_rain_name,
            R.string.notif_channel_rain_description,
        )
    }
    val channel = NotificationChannel(id, getString(name), NotificationManager.IMPORTANCE_DEFAULT).apply {
        this.description = getString(description)
    }
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}

/**
 * Posts a [WeatherNotification] in the brand look: the accent blue, the pets in the notification's
 * mood, and the full text when expanded. Each kind has its own channel and id, so a walk nudge
 * never replaces the morning briefing.
 */
internal fun Context.postWeatherNotification(notification: WeatherNotification) {
    if (!canPostNotifications()) return
    // Made when needed: a notification never goes to a channel that doesn't exist yet.
    createNotificationChannel(notification.kind)
    val tapIntent = Intent(this, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    val tapPendingIntent = PendingIntent.getActivity(
        this,
        0,
        tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val (channel, id) = when (notification.kind) {
        NotificationKind.MORNING_BRIEFING -> NotificationChannels.CHANNEL_MORNING_BRIEFING to 1000
        NotificationKind.WALK_TIME -> NotificationChannels.CHANNEL_WALK_TIME to 1001
        NotificationKind.RAIN -> NotificationChannels.CHANNEL_RAIN to 1002
    }
    val iconSize = resources.getDimensionPixelSize(android.R.dimen.notification_large_icon_width)
    val built = NotificationCompat
        .Builder(this, channel)
        .setSmallIcon(R.drawable.ic_notification_weather)
        .setColor(ContextCompat.getColor(this, R.color.notification_accent))
        .setLargeIcon(petSceneIcon(notification.mood, iconSize, notification.southernHemisphere))
        .setContentTitle(notification.title)
        .setContentText(notification.body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
        .setContentIntent(tapPendingIntent)
        .setAutoCancel(true)
        .build()
    @Suppress("MissingPermission") // checked by canPostNotifications above
    NotificationManagerCompat.from(this).notify(id, built)
}
