package com.example.phonekb

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.OutputStreamWriter
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val TAG = "PhoneKB"
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var hidDevice: BluetoothHidDevice? = null
    private var connectedDevice: BluetoothDevice? = null

    private lateinit var edtInput: EditText
    private lateinit var btnSendText: Button
    private lateinit var btnSendA: Button
    private lateinit var btnConnectNetwork: Button
    private lateinit var edtHost: EditText
    private lateinit var edtPort: EditText
    private lateinit var chkAdbReverse: CheckBox
    private lateinit var radioMode: RadioGroup
    private lateinit var txtStatus: TextView

    private var socket: Socket? = null
    private var writer: OutputStreamWriter? = null
    private val executor = Executors.newSingleThreadExecutor()

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged registered=$registered device=$device")
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.d(TAG, "onConnectionStateChanged state=$state device=$device")
            if (state == BluetoothProfile.STATE_CONNECTED) connectedDevice = device
            else if (state == BluetoothProfile.STATE_DISCONNECTED) connectedDevice = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter

        requestNecessaryPermissions()

        edtInput = findViewById(R.id.edtInput)
        btnSendText = findViewById(R.id.btnSendText)
        btnSendA = findViewById(R.id.btnSendA)
        btnConnectNetwork = findViewById(R.id.btnConnectNetwork)
        edtHost = findViewById(R.id.edtHost)
        edtPort = findViewById(R.id.edtPort)
        chkAdbReverse = findViewById(R.id.chkAdbReverse)
        radioMode = findViewById(R.id.radioMode)
        txtStatus = findViewById(R.id.txtStatus)

        btnSendA.setOnClickListener { sendTextInternal("a") }
        btnSendText.setOnClickListener { sendTextInternal(edtInput.text.toString()) }
        btnConnectNetwork.setOnClickListener {
            val host = if (chkAdbReverse.isChecked) "127.0.0.1" else edtHost.text.toString().ifEmpty { "127.0.0.1" }
            val port = edtPort.text.toString().toIntOrNull() ?: 7777
            connectSocket(host, port)
        }

        try {
            bluetoothAdapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        hidDevice = proxy as BluetoothHidDevice
                        registerHidApp()
                    }
                }

                override fun onServiceDisconnected(profile: Int) {}
            }, BluetoothProfile.HID_DEVICE)
        } catch (e: Exception) {
            Log.w(TAG, "Bluetooth HID proxy not available", e)
            txtStatus.text = "Bluetooth HID not available on this device"
        }
    }

    private fun requestNecessaryPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            perms.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        perms.add(Manifest.permission.INTERNET)
        val toRequest = perms.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest, 101)
        }
    }

    private fun registerHidApp() {
        try {
            val sdp = BluetoothHidDeviceAppSdpSettings(
                "PhoneKB",
                "Phone keyboard",
                "PhoneKB",
                BluetoothHidDevice.SUBCLASS1_COMBO,
                REPORT_DESC
            )
            val qos = BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
                800, 9, 0, 0, 0
            )
            hidDevice?.registerApp(sdp, qos, qos, executor, hidCallback)
            runOnUiThread { txtStatus.text = "Bluetooth HID app registered" }
        } catch (e: Exception) {
            Log.e(TAG, "registerHidApp failed", e)
            runOnUiThread { txtStatus.text = "registerHidApp failed: ${e.message}" }
        }
    }

    private fun sendTextInternal(text: String) {
        when (radioMode.checkedRadioButtonId) {
            R.id.radioBluetooth -> sendTextHid(text)
            R.id.radioNetwork -> sendTextNetwork(text)
            else -> sendTextNetwork(text)
        }
    }

    private fun sendTextHid(text: String) {
        val device = connectedDevice
        if (device == null || hidDevice == null) {
            runOnUiThread { Toast.makeText(this, "Not connected via Bluetooth HID", Toast.LENGTH_SHORT).show() }
            return
        }
        executor.execute {
            try {
                for (ch in text) {
                    val (mod, code) = charToHid(ch)
                    if (code.toInt() == 0) continue
                    val press = ByteArray(8)
                    press[0] = mod
                    press[2] = code
                    hidDevice?.sendReport(device, 0, press)
                    Thread.sleep(40)
                    val release = ByteArray(8)
                    hidDevice?.sendReport(device, 0, release)
                    Thread.sleep(20)
                }
            } catch (e: Exception) {
                Log.e(TAG, "sendTextHid error", e)
            }
        }
    }

    private fun sendTextNetwork(text: String) {
        if (socket == null || writer == null) {
            runOnUiThread { Toast.makeText(this, "Network socket not connected", Toast.LENGTH_SHORT).show() }
            return
        }
        executor.execute {
            try {
                writer?.write(text)
                writer?.write("\n")
                writer?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "sendTextNetwork failed", e)
                runOnUiThread { Toast.makeText(this, "Network send failed: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun connectSocket(host: String, port: Int) {
        executor.execute {
            try {
                socket?.close()
                socket = Socket(host, port)
                writer = OutputStreamWriter(socket!!.getOutputStream(), StandardCharsets.UTF_8)
                runOnUiThread { txtStatus.text = "Network connected to $host:$port"; Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                Log.e(TAG, "connectSocket failed", e)
                runOnUiThread { txtStatus.text = "Connect failed: ${e.message}"; Toast.makeText(this, "Connect failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { socket?.close() } catch (ignored: Exception) {}
        try { hidDevice = null } catch (ignored: Exception) {}
    }

    private fun charToHid(c: Char): Pair<Byte, Byte> {
        if (c in 'a'..'z') {
            val keycode = (c - 'a' + 0x04).toByte()
            return Pair(0x00, keycode)
        } else if (c in 'A'..'Z') {
            val keycode = (c - 'A' + 0x04).toByte()
            return Pair(0x02, keycode)
        } else if (c == ' ') return Pair(0x00, 0x2c)
        else if (c == '\n') return Pair(0x00, 0x28)
        else if (c in '1'..'9') {
            val keycode = (0x1e + (c - '1')).toByte()
            return Pair(0x00, keycode)
        } else if (c == '0') return Pair(0x00, 0x27)
        return Pair(0x00, 0x00)
    }

    companion object {
        val REPORT_DESC = byteArrayOf(
            0x05.toByte(),0x01.toByte(),0x09.toByte(),0x06.toByte(),0xa1.toByte(),0x01.toByte(),0x05.toByte(),0x07.toByte(),
            0x19.toByte(),0xe0.toByte(),0x29.toByte(),0xe7.toByte(),0x15.toByte(),0x00.toByte(),0x25.toByte(),0x01.toByte(),
            0x75.toByte(),0x01.toByte(),0x95.toByte(),0x08.toByte(),0x81.toByte(),0x02.toByte(),0x95.toByte(),0x01.toByte(),
            0x75.toByte(),0x08.toByte(),0x81.toByte(),0x01.toByte(),0x95.toByte(),0x05.toByte(),0x75.toByte(),0x01.toByte(),
            0x05.toByte(),0x08.toByte(),0x19.toByte(),0x01.toByte(),0x29.toByte(),0x05.toByte(),0x91.toByte(),0x02.toByte(),
            0x95.toByte(),0x01.toByte(),0x75.toByte(),0x03.toByte(),0x91.toByte(),0x01.toByte(),0x95.toByte(),0x06.toByte(),
            0x75.toByte(),0x08.toByte(),0x15.toByte(),0x00.toByte(),0x25.toByte(),0x65.toByte(),0x05.toByte(),0x07.toByte(),
            0x19.toByte(),0x00.toByte(),0x29.toByte(),0x65.toByte(),0x81.toByte(),0x00.toByte(),0xc0.toByte()
        )
    }
}
