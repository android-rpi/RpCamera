package com.arpi.rpcamera

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.Camera
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Button
import android.widget.Toast
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.concurrent.thread

class MainActivity: Activity(), SurfaceHolder.Callback {
    private var mCamera: Camera? = null
    private lateinit var mSurfaceHolder: SurfaceHolder
    private lateinit var mCaptureButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.main)
        mSurfaceHolder = findViewById<SurfaceView>(R.id.surface).holder
        mSurfaceHolder.addCallback(this)

        mCaptureButton = findViewById<Button>(R.id.button)

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), requestCodeCameraPermission)
        }
    }

    private val requestCodeCameraPermission: Int = 1

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>,
                                            grantResults: IntArray) {
        when(requestCode) {
            requestCodeCameraPermission -> {
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    openCamera()
                    startPreview()
                }
            }
        }
    }


    private fun openCamera() {
        mCamera = Camera.open(0)
        mCamera?.apply {
            parameters.also { params->
                params.setPictureSize(640, 480)
                parameters = params
            }
        }
    }

    private fun startPreview() {
        mCamera?.apply {
            setPreviewDisplay(mSurfaceHolder)
            startPreview()
        }
    }


    override fun surfaceCreated(h: SurfaceHolder) {
        startPreview()
    }

    override fun surfaceChanged(h: SurfaceHolder, p1: Int, p2: Int, p3: Int) {}

    override fun surfaceDestroyed(h: SurfaceHolder) {
        mCamera?.stopPreview()
    }


    fun onClick(v: View) {
        mCaptureButton.isClickable = false
        mCamera?.takePicture(null, null, mPictureCallback)
    }

    private val mPictureCallback = object: Camera.PictureCallback {
        override fun onPictureTaken(data: ByteArray?, cam: Camera?) {
            val fileName = LocalDateTime.now().format(DateTimeFormatter.ofPattern(
                    "yyMMdd_HHmmss")) + ".jpg"
            val filePath = applicationContext.getExternalFilesDir(null)!!.absolutePath +
                    "/" + fileName

            Files.write(Paths.get(filePath), data,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

            Toast.makeText(applicationContext,
                    "Captured: /sdcard/Android/data/$packageName/files/" + fileName,
                    Toast.LENGTH_LONG).show()

            thread {
                Thread.sleep(4000)
                mCamera?.startPreview()
                mCaptureButton.isClickable = true
            }
        }
    }
}
