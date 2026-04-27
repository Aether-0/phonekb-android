package com.example.phonekb

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val TAG = "PhoneKB"
    private lateinit var bluetoothAdapter: android.bluetooth.BluetoothAdapter
    private var hidDevice: BluetoothHidDevice? = null

    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(device: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "onAppStatusChanged registered=$registered device=$device")
        }
        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            Log.d(TAG, "onConnectionStateChanged state=$state device=$device")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = manager.adapter

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN
            ), 0)
        }

        bluetoothAdapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HID_DEVICE) {
                    hidDevice = proxy as BluetoothHidDevice
                    registerHidApp()
                }
            }
            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.HID_DEVICE)

        val btnSendA = findViewById<Button>(R.id.btnSendA)
        val btnSendText = findViewById<Button>(R.id.btnSendText)
        val edtInput = findViewById<EditText>(R.id.edtInput)

        btnSendA.setOnClickListener { sendKeyA() }
        btnSendText.setOnClickListener {
            val text = edtInput.text.toString()
            sendText(text)
        }
    }

    private fun registerHidApp() {
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "PhoneKB",
            "Phone keyboard",
            "ExampleProvider",
            BluetoothHidDevice.SUBCLASS1_COMBO,
            REPORT_DESC
        )
        val qos = BluetoothHidDeviceAppQosSettings(
            BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT,
            800, 9, 0, 0, 0
        )
        val ok = hidDevice?.registerApp(sdp, qos, qos, Executors.newSingleThreadExecutor(), hidCallback)
        Log.d(TAG, "registerApp returned $ok")
    }

    private fun sendKeyA() {
        val report = ByteArray(8)
        report[2] = 0x04 // 'a'
        val device = findBondedDevice() ?: run { Log.d(TAG, "No bonded device"); return }
        hidDevice?.sendReport(device, 0, report)
        val release = ByteArray(8)
        hidDevice?.sendReport(device, 0, release)
    }

    private fun sendText(text: String) {
        val device = findBondedDevice() ?: run { Log.d(TAG, "No bonded device"); return }
        Thread {
            for (c in text) {
                val (mod, code) = charToHid(c)
                if (code.toInt() == 0) continue
                val press = ByteArray(8)
                press[0] = mod
                press[2] = code
                hidDevice?.sendReport(device, 0, press)
                Thread.sleep(50)
                val release = ByteArray(8)
                hidDevice?.sendReport(device, 0, release)
                Thread.sleep(30)
            }
        }.start()
    }

    private fun charToHid(c: Char): Pair<Byte, Byte> {
        if (c in 'a'..'z') {
            val keycode = (c - 'a' + 0x04).toByte()
            return Pair(0x00, keycode)
        } else if (c in 'A'..'Z') {
            val keycode = (c - 'A' + 0x04).toByte()
            return Pair(0x02, keycode) // left shift
        } else if (c == ' ') return Pair(0x00, 0x2c)
        else if (c == '\n') return Pair(0x00, 0x28)
        else if (c in '1'..'9') {
            val keycode = (0x1e + (c - '1')).toByte()
            return Pair(0x00, keycode)
        } else if (c == '0') return Pair(0x00, 0x27)
        return Pair(0x00, 0x00)
    }

    private fun findBondedDevice(): BluetoothDevice? {
        val paired = bluetoothAdapter.bondedDevices
        return paired.firstOrNull()
    }

    companion object {
        val REPORT_DESC = byteArrayOf(
            0x05,0x01,0x09,0x06,0xa1.toByte(),0x01,0x05,0x07,
            0x19,0xe0.toByte(),0x29,0xe7.toByte(),0x15,0x00,0x25,0x01,
            0x75,0x01,0x95,0x08,0x81.toByte(),0x02,0x95,0x01,
            0x75,0x08,0x81.toByte(),0x01,0x95,0x05,0x75,0x01,
            0x05,0x08,0x19,0x01,0x29,0x05,0x91.toByte(),0x02,
            0x95,0x01,0x75,0x03,0x91.toByte(),0x01,0x95,0x06,
            0x75,0x08,0x15,0x00,0x25,0x65,0x05,0x07,
            0x19,0x00,0x29,0x65,0x81.toByte(),0x00,0xc0.toByte()
        )
    }
}
