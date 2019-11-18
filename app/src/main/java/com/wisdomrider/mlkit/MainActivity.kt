package com.wisdomrider.mlkit

import android.content.pm.PackageManager
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.cloud.FirebaseVisionCloudDetectorOptions
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity(), LifecycleOwner {
    private val REQUEST_CODE_PERMISSIONS = 101
    private val REQUIRED_PERMISSIONS =
        arrayOf("android.permission.CAMERA", "android.permission.WRITE_EXTERNAL_STORAGE")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        if (allPermissionsGranted()) {
            startCamera() //start camera if permission has been granted by user
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun startCamera() {

        CameraX.unbindAll()
        val screen = Size(textureView.width, textureView.height) //size of the screen


        val pConfig = PreviewConfig.Builder()
            .setTargetResolution(screen)
            //.setLensFacing(CameraX.LensFacing.FRONT)
            .build()
        val preview = Preview(pConfig)

        preview.setOnPreviewOutputUpdateListener { output ->
            val parent = textureView.parent as ViewGroup
            parent.removeView(textureView)
            parent.addView(textureView, 0)

            textureView.surfaceTexture = output.surfaceTexture
            updateTransform()
        }
        val options = FirebaseVisionCloudDetectorOptions.Builder()
            .setModelType(FirebaseVisionCloudDetectorOptions.LATEST_MODEL)
            .setMaxResults(5)
            .build()

        val detector = FirebaseVision.getInstance().getVisionCloudLandmarkDetector(options)


        val imageCaptureConfig =
            ImageCaptureConfig.Builder().setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY)
                .setTargetRotation(windowManager.defaultDisplay.rotation).build()
        val imgCap = ImageCapture(imageCaptureConfig)

        capture_button.setOnClickListener {
            val file = File("${Environment.getExternalStorageDirectory()}/landmark.jpg")
            imgCap.takePicture(
                file,
                Executors.newSingleThreadExecutor(),
                object : ImageCapture.OnImageSavedListener {
                    override fun onError(
                        imageCaptureError: ImageCapture.ImageCaptureError,
                        message: String,
                        cause: Throwable?
                    ) {

                        val msg = "Pic capture failed : $message"
                        Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                        if (cause != null) {
                            cause!!.printStackTrace()
                        }

                    }

                    override fun onImageSaved(file: File) {
                        val image =
                            FirebaseVisionImage.fromFilePath(this@MainActivity, file.toUri())
                        detector.detectInImage(image)
                            .addOnSuccessListener {
                                runOnUiThread {
                                    Toast.makeText(baseContext,it.toHashSet().toString(), Toast.LENGTH_LONG).show()

                                }
                            }
                            .addOnFailureListener{
                                runOnUiThread {
                                    Toast.makeText(baseContext,it.message, Toast.LENGTH_LONG).show()

                                }

                            }
                   }


                })
        }
        CameraX.bindToLifecycle(this as LifecycleOwner, preview, imgCap)
    }

    private fun updateTransform() {
        val mx = Matrix()
        val w = textureView.measuredWidth.toFloat()
        val h = textureView.measuredHeight.toFloat()

        val cX = w / 2f
        val cY = h / 2f

        val rotationDgr: Int
        val rotation = textureView.rotation.toInt()

        when (rotation) {
            Surface.ROTATION_0 -> rotationDgr = 0
            Surface.ROTATION_90 -> rotationDgr = 90
            Surface.ROTATION_180 -> rotationDgr = 180
            Surface.ROTATION_270 -> rotationDgr = 270
            else -> return
        }

        mx.postRotate(rotationDgr.toFloat(), cX, cY)
        textureView.setTransform(mx)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    private fun allPermissionsGranted(): Boolean {

        for (permission in REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    permission
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

}
