package com.arpi.rpcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.main.*
import java.io.File
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.concurrent.thread

typealias LumaListener = (luma: Double) -> Unit

class MainActivity: AppCompatActivity() {
    companion object {
        private const val TAG = "RpCamera"
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_1: Int = 1
    }

    private var preview: Preview? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        if (permissionsGranted()) {
            startPreview()
        } else {
            requestPermissions(PERMISSIONS, REQUEST_CODE_1)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun permissionsGranted() = PERMISSIONS.all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
                                            grantResults: IntArray) {
        when(requestCode) {
            REQUEST_CODE_1 -> {
                if (permissionsGranted()) {
                    startPreview()
                } else {
                    Toast.makeText(applicationContext,
                            "Permissions not granted",
                            Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun startPreview() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener( {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            preview = Preview.Builder().setTargetResolution(Size(640,480)).build()

            imageCapture = ImageCapture.Builder()
                    .setTargetResolution(Size(640,480)).build()

            imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    .also {
                        it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                            Log.d(TAG, "Luminosity: $luma")
                            Thread.sleep(1000)
                        })
                    }

            val cameraSelector = CameraSelector.Builder().build()
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)
            preview!!.setSurfaceProvider(viewFinder.surfaceProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    fun onClick(v: View) {
        val imageCapture = imageCapture ?: return
        captureButton.isClickable = false

        val photoFile = File(filesDir,LocalDateTime.now().format(DateTimeFormatter.ofPattern(
                        "yyMMdd_HHmmss")) + ".jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val savedPath =
                        Toast.makeText(applicationContext,
                                "Captured: " + photoFile.absolutePath,
                                Toast.LENGTH_LONG).show()
                        enableCaptureButton(3000)
                    }
                    override fun onError(exception: ImageCaptureException) {
                        Toast.makeText(applicationContext,
                                "Capture failed: ${exception.message}",
                                Toast.LENGTH_LONG).show()
                        enableCaptureButton(3000)
                    }
                })
    }

    private fun enableCaptureButton(delay:Long) {
        thread {
            Thread.sleep(delay)
            this@MainActivity.runOnUiThread {
                captureButton.isClickable = true
            }
        }
    }

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }
        override fun analyze(image: ImageProxy) {
            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()
            image.close()

            listener(luma)
        }

    }
}
