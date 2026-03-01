package com.example.androidcontroller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps the Bluetooth HID registration alive
 * regardless of whether the activity is in the foreground.
 *
 * The activity binds to get [LocalBinder], registers [activityListener] for
 * UI callbacks, and calls [sendReport].  When the activity goes away it
 * unbinds — but the service (and HID registration) keep running.
 */
class BluetoothHidService : Service() {

    inner class LocalBinder : Binder() {
        val service: BluetoothHidService get() = this@BluetoothHidService
    }

    private val binder = LocalBinder()
    private lateinit var hidManager: BluetoothHidManager

    /** Swapped out by the activity on bind/unbind. Must only be read on the UI thread. */
    var activityListener: BluetoothHidManager.Listener? = null

    val isConnected: Boolean get() = hidManager.isConnected

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Waiting for host…"))

        val adapter = getSystemService(BluetoothManager::class.java).adapter
        hidManager = BluetoothHidManager(this, adapter)
        hidManager.listener = managerListener
        hidManager.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY   // restart automatically if killed

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        hidManager.stop()
        super.onDestroy()
    }

    // ── Report forwarding ─────────────────────────────────────────────────

    fun sendReport(report: GamepadReport) = hidManager.sendReport(report)

    // ── Notification ──────────────────────────────────────────────────────

    private fun updateNotification(status: String) =
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, buildNotification(status))

    private fun buildNotification(status: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BT Gamepad")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(tapIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        NotificationChannel(CHANNEL_ID, "BT Gamepad", NotificationManager.IMPORTANCE_LOW)
            .also { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }
    }

    // ── Bridge: HID manager → notification + activity ─────────────────────
    //
    // Callbacks may arrive on a background thread; activityListener.runOnUiThread
    // is the activity's responsibility.

    private val managerListener = object : BluetoothHidManager.Listener {
        override fun onRegistered() {
            updateNotification("Waiting for host…")
            activityListener?.onRegistered()
        }
        override fun onUnregistered() {
            updateNotification("HID service stopped")
            activityListener?.onUnregistered()
        }
        override fun onHostConnected(device: BluetoothDevice) {
            updateNotification("Connected: ${device.name ?: device.address}")
            activityListener?.onHostConnected(device)
        }
        override fun onHostDisconnected(device: BluetoothDevice) {
            updateNotification("Waiting for host…")
            activityListener?.onHostDisconnected(device)
        }
        override fun onError(message: String) {
            updateNotification("Error: $message")
            activityListener?.onError(message)
        }
    }

    companion object {
        private const val CHANNEL_ID = "bt_gamepad"
        private const val NOTIF_ID   = 1
    }
}
