package com.example.androidcontroller

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.androidcontroller.databinding.ActivityMainBinding

@SuppressLint("MissingPermission", "ClickableViewAccessibility")
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var hidManager: BluetoothHidManager

    private val report = GamepadReport()

    // ── Permission handling ──────────────────────────────────────────────────

    private val requiredPermissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
            )
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) {
                initBluetooth()
            } else {
                setStatus("Bluetooth permissions denied.")
            }
        }

    private val enableBtLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (bluetoothAdapter.isEnabled) startHid()
            else setStatus("Bluetooth not enabled.")
        }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val btManager = getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = btManager.adapter ?: run {
            setStatus("No Bluetooth adapter found.")
            return
        }

        hidManager = BluetoothHidManager(this, bluetoothAdapter)
        hidManager.listener = hidListener

        wireButtons()

        if (hasPermissions()) initBluetooth()
        else permissionLauncher.launch(requiredPermissions)
    }

    override fun onDestroy() {
        super.onDestroy()
        hidManager.stop()
    }

    // ── Bluetooth init ───────────────────────────────────────────────────────

    private fun hasPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun initBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } else {
            startHid()
        }
    }

    private fun startHid() {
        setStatus("Registering HID service…")
        hidManager.start()
    }

    // ── HID callbacks (may arrive on a background thread) ───────────────────

    private val hidListener = object : BluetoothHidManager.Listener {
        override fun onRegistered() = runOnUiThread {
            setStatus("Waiting for host to connect…\nMake your phone discoverable and pair from the host.")
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

    // ── Button wiring ────────────────────────────────────────────────────────

    /**
     * Map each view in the layout to a [ButtonConfig] and attach touch
     * listeners that press/release the corresponding HID input.
     */
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
                        hidManager.sendReport(report)
                        v.isPressed = true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        report.releaseButton(buttonCfg)
                        hidManager.sendReport(report)
                        v.isPressed = false
                    }
                }
                true
            }
        }

        // Dim controller until connected
        binding.controllerLayout.alpha = 0.4f
    }

    private fun setStatus(text: String) {
        binding.tvStatus.text = text
    }
}
