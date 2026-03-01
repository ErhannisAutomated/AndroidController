package com.example.androidcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.MotionEvent
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.androidcontroller.databinding.ActivityMainBinding

@SuppressLint("MissingPermission", "ClickableViewAccessibility")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private val report = GamepadReport()

    // ── Service binding ───────────────────────────────────────────────────

    private var hidService: BluetoothHidService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val svc = (binder as BluetoothHidService.LocalBinder).service
            hidService = svc
            serviceBound = true
            svc.activityListener = hidListener
            // Sync UI with whatever state the service is already in
            if (svc.isConnected) binding.controllerLayout.alpha = 1f
        }

        override fun onServiceDisconnected(name: ComponentName) {
            hidService = null
            serviceBound = false
        }
    }

    // ── Permission / BT-enable launchers ─────────────────────────────────

    private val requiredPermissions: Array<String>
        get() = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) initBluetooth()
            else setStatus("Bluetooth permissions denied.")
        }

    private val enableBtLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (bluetoothAdapter.isEnabled) startAndBind()
            else setStatus("Bluetooth not enabled.")
        }

    // ── Lifecycle ─────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bluetoothAdapter = getSystemService(BluetoothManager::class.java).adapter ?: run {
            setStatus("No Bluetooth adapter found.")
            return
        }

        wireButtons()

        if (hasPermissions()) initBluetooth()
        else permissionLauncher.launch(requiredPermissions)
    }

    override fun onStart() {
        super.onStart()
        // Re-attach to the service when returning from another app.
        // startForegroundService is safe to call if already running.
        if (!serviceBound && hasPermissions() && bluetoothAdapter.isEnabled) {
            startAndBind()
        }
    }

    override fun onStop() {
        super.onStop()
        if (serviceBound) {
            hidService?.activityListener = null
            hidService = null
            unbindService(serviceConnection)
            serviceBound = false
        }
    }

    // ── Bluetooth init ────────────────────────────────────────────────────

    private fun hasPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun initBluetooth() {
        if (!bluetoothAdapter.isEnabled)
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        else
            startAndBind()
    }

    /** Start the foreground service (idempotent) and bind to it. */
    private fun startAndBind() {
        val intent = Intent(this, BluetoothHidService::class.java)
        startForegroundService(intent)
        if (!serviceBound) bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    // ── HID listener (UI updates only — service owns the BT state) ────────

    private val hidListener = object : BluetoothHidManager.Listener {
        override fun onRegistered() = runOnUiThread {
            setStatus("Waiting for host…\nPair from the host device.")
        }
        override fun onUnregistered() = runOnUiThread {
            setStatus("HID service unregistered.")
        }
        override fun onHostConnected(device: BluetoothDevice) = runOnUiThread {
            setStatus("Connected: ${device.name ?: device.address}")
            binding.controllerLayout.alpha = 1f
        }
        override fun onHostDisconnected(device: BluetoothDevice) = runOnUiThread {
            setStatus("Disconnected. Waiting for host…")
            binding.controllerLayout.alpha = 0.4f
        }
        override fun onError(message: String) = runOnUiThread {
            setStatus("Error: $message")
        }
    }

    // ── Button wiring ─────────────────────────────────────────────────────

    private fun wireButtons() {
        val cfg = ControllerConfig
        mapOf(
            binding.btnA      to cfg.btnA,
            binding.btnB      to cfg.btnB,
            binding.btnX      to cfg.btnX,
            binding.btnY      to cfg.btnY,
            binding.btnLb     to cfg.btnLB,
            binding.btnRb     to cfg.btnRB,
            binding.btnLt     to cfg.btnLT,
            binding.btnRt     to cfg.btnRT,
            binding.btnBack   to cfg.btnBack,
            binding.btnStart  to cfg.btnStart,
            binding.btnDUp    to cfg.btnDUp,
            binding.btnDDown  to cfg.btnDDown,
            binding.btnDLeft  to cfg.btnDLeft,
            binding.btnDRight to cfg.btnDRight,
        ).forEach { (view, buttonCfg) ->
            (view as? Button)?.text = buttonCfg.label
            view.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        report.pressButton(buttonCfg)
                        hidService?.sendReport(report)
                        v.isPressed = true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        report.releaseButton(buttonCfg)
                        hidService?.sendReport(report)
                        v.isPressed = false
                    }
                }
                true
            }
        }
        binding.controllerLayout.alpha = 0.4f
    }

    private fun setStatus(text: String) {
        binding.tvStatus.text = text
    }
}
