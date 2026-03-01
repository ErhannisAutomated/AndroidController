package com.example.androidcontroller

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDevice.Callback
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import java.util.concurrent.Executors

/**
 * Manages the Bluetooth HID Device profile.
 *
 * Usage:
 *  1. Call [start] to obtain the profile proxy and register the HID app.
 *  2. Observe [listener] callbacks for connection state changes.
 *  3. Call [sendReport] to transmit a gamepad report to the connected host.
 *  4. Call [stop] in onDestroy.
 *
 * The host (PC / console) initiates the connection after pairing.  This
 * class simply keeps the HID service registered and ready.
 */
@SuppressLint("MissingPermission")  // caller must hold BLUETOOTH_CONNECT
class BluetoothHidManager(
    private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter,
) {

    interface Listener {
        fun onRegistered()
        fun onUnregistered()
        fun onHostConnected(device: BluetoothDevice)
        fun onHostDisconnected(device: BluetoothDevice)
        fun onError(message: String)
    }

    var listener: Listener? = null

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private val executor = Executors.newSingleThreadExecutor()

    // ── Public API ───────────────────────────────────────────────────────────

    fun start() {
        val ok = bluetoothAdapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE)
        if (!ok) listener?.onError("getProfileProxy returned false")
    }

    fun stop() {
        hidDevice?.let { hid ->
            hid.unregisterApp()
            bluetoothAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid)
        }
        hidDevice = null
        connectedHost = null
    }

    /** Send a gamepad report to the currently connected host, if any. */
    fun sendReport(report: GamepadReport) {
        val hid  = hidDevice    ?: return
        val host = connectedHost ?: return
        val sent = hid.sendReport(host, ControllerConfig.reportId, report.toBytes())
        if (!sent) Log.w(TAG, "sendReport returned false")
    }

    val isConnected: Boolean get() = connectedHost != null

    // ── BluetoothProfile.ServiceListener ────────────────────────────────────

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            val hid = proxy as BluetoothHidDevice
            hidDevice = hid

            val sdp = BluetoothHidDeviceAppSdpSettings(
                ControllerConfig.deviceName,
                ControllerConfig.deviceDescription,
                ControllerConfig.deviceProvider,
                ControllerConfig.subclass1,
                ControllerConfig.hidDescriptor,
            )

            // QosSettings: service type BEST_EFFORT, sensible defaults
            val qos = BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                800,    // token rate (bytes/s)
                9,      // token bucket size
                0,      // peak bandwidth (0 = don't care)
                11250,  // latency (µs)
                BluetoothHidDeviceAppQosSettings.MAX,
            )

            val registered = hid.registerApp(sdp, qos, qos, executor, hidCallback)
            if (!registered) listener?.onError("registerApp failed")
        }

        override fun onServiceDisconnected(profile: Int) {
            hidDevice = null
            connectedHost = null
            listener?.onUnregistered()
        }
    }

    // ── BluetoothHidDevice.Callback ──────────────────────────────────────────

    private val hidCallback = object : Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged registered=$registered device=$pluggedDevice")
            if (registered) listener?.onRegistered() else listener?.onUnregistered()
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            Log.d(TAG, "onConnectionStateChanged state=$state device=${device.address}")
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    listener?.onHostConnected(device)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (connectedHost?.address == device.address) connectedHost = null
                    listener?.onHostDisconnected(device)
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice, type: Byte, id: Byte, bufferSize: Int) {
            // Host is requesting the current report; reply with an empty/neutral one
            hidDevice?.replyReport(device, type, id, GamepadReport().toBytes())
        }

        override fun onSetReport(device: BluetoothDevice, type: Byte, id: Byte, data: ByteArray) {
            hidDevice?.reportError(device, BluetoothHidDevice.ERROR_RSP_UNSUPPORTED_REQ)
        }

        override fun onSetProtocol(device: BluetoothDevice, protocol: Byte) {
            Log.d(TAG, "onSetProtocol protocol=$protocol")
        }

        override fun onInterruptData(device: BluetoothDevice, reportId: Byte, data: ByteArray) {}

        override fun onVirtualCableUnplug(device: BluetoothDevice) {
            if (connectedHost?.address == device.address) connectedHost = null
            listener?.onHostDisconnected(device)
        }
    }

    companion object {
        private const val TAG = "BluetoothHidManager"
    }
}
