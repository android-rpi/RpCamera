package com.arpi.rpcamera

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.main.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

typealias RecogListener = (result: String) -> Unit

class MainActivity: AppCompatActivity() {
    companion object {
        private const val TAG = "RpCamera"
        private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
        private const val REQUEST_CODE_1: Int = 1
    }

    private var preview: Preview? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var imageAnalyzer: ImageAnalyzer

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

    override fun onStart() {
        super.onStart()
        imageAnalyzer = ImageAnalyzer(this) { result ->
            runOnUiThread {
                recogText.text = result
            }
        }
        imageAnalyzer.tfInit(toggleButton.isChecked)
    }

    override fun onStop() {
        super.onStop()
        imageAnalyzer.tfClose()
    }

    private fun startPreview() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener( {
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            preview = Preview.Builder().setTargetResolution(Size(640,480)).build()

            imageCapture = ImageCapture.Builder()
                    .setTargetResolution(Size(640,480)).build()

            imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    .also {
                        it.setAnalyzer(cameraExecutor, imageAnalyzer)
                    }

            val cameraSelector = CameraSelector.Builder().build()
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalysis)
            preview!!.setSurfaceProvider(viewFinder.surfaceProvider)
        }, ContextCompat.getMainExecutor(this))
    }

    fun onClick(v: View) {
        imageAnalyzer.tfClose()
        imageAnalyzer.tfInit(toggleButton.isChecked)
    }
}
