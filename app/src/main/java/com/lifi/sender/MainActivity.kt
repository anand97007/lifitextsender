package com.lifi.sender

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var send: Button
    private lateinit var cameraManager: CameraManager
    private var cameraId: String? = null
    private var sending = false

    companion object {
        private const val ZERO_MS = 150L
        private const val ONE_MS = 350L
        private const val GAP_MS = 150L
        private const val BYTE_GAP_MS = 250L
        // 12-bit sync: 101010101010. It is used only while receiver is idle.
        private const val SYNC = "101010101010"
    }

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(this, "Camera permission is required for flash", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        input = findViewById(R.id.messageInput)
        status = findViewById(R.id.statusText)
        progress = findViewById(R.id.progressBar)
        send = findViewById(R.id.sendButton)
        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        cameraId = findFlashCameraId()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permission.launch(Manifest.permission.CAMERA)
        }
        send.setOnClickListener {
            val text = input.text.toString()
            if (text.isEmpty()) {
                Toast.makeText(this, "Type a message first", Toast.LENGTH_SHORT).show()
            } else if (!sending && cameraId != null) {
                sendText(text)
            }
        }
    }

    private fun findFlashCameraId(): String? = try {
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (_: CameraAccessException) { null }

    private fun torch(on: Boolean) {
        try { cameraId?.let { cameraManager.setTorchMode(it, on) } } catch (_: CameraAccessException) {}
    }

    private fun pulse(one: Boolean) {
        torch(true)
        Thread.sleep(if (one) ONE_MS else ZERO_MS)
        torch(false)
        Thread.sleep(GAP_MS)
    }

    private fun sendBits(bits: String) {
        for (b in bits) pulse(b == '1')
    }

    private fun bitsOf(value: Int, count: Int): String =
        value.toString(2).padStart(count, '0').takeLast(count)

    private fun sendText(text: String) {
        sending = true
        send.isEnabled = false
        val payload = text.toByteArray(Charsets.UTF_8)
        val checksum = payload.fold(0) { acc, b -> acc xor (b.toInt() and 0xFF) }
        progress.max = payload.size
        progress.progress = 0
        thread {
            try {
                // Quiet period before sync.
                torch(false)
                Thread.sleep(500)
                update("Sending...")
                sendBits(SYNC)
                // 16-bit payload length (0..65535 bytes)
                sendBits(bitsOf(payload.size and 0xFFFF, 16))
                for ((i, byte) in payload.withIndex()) {
                    sendBits(bitsOf(byte.toInt() and 0xFF, 8))
                    Thread.sleep(BYTE_GAP_MS)
                    runOnUiThread { progress.progress = i + 1 }
                }
                sendBits(bitsOf(checksum, 8))
                torch(false)
                update("Done - ${payload.size} bytes sent")
            } finally {
                torch(false)
                runOnUiThread { sending = false; send.isEnabled = true }
            }
        }
    }

    private fun update(s: String) { runOnUiThread { status.text = s } }

    override fun onDestroy() { torch(false); super.onDestroy() }
}
