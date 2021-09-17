package com.shaon2016.facerecongnition.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.shaon2016.facerecongnition.face_mask_detection.FaceContourDetectionProcessor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class CameraXManager(
    private val context: Context,
    private val viewFinder: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val graphicOverlay: GraphicOverlay
    ) {

    private val TAG = "CameraXManager"

    // CameraX
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var cameraProvider: ProcessCameraProvider
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    fun setupCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("front camera are unavailable")
            }

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(context))

    }

    private fun bindCameraUseCases() {
        val preview = configurePreviewUseCase()

        imageAnalyzer = configureImageAnalyzer()

        // Select front camera as a default
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner, cameraSelector, preview, imageAnalyzer
            )

        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun configureImageAnalyzer(): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setTargetRotation(viewFinder.display.rotation)
            .setTargetResolution(Size(800, 600))
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, selectAnalyzer())
            }
    }

    private fun selectAnalyzer(): ImageAnalysis.Analyzer {
        return FaceContourDetectionProcessor(graphicOverlay, context)
    }

    private fun configurePreviewUseCase() = Preview.Builder()
        .setTargetResolution(Size(800, 600))
        .build()
        .also {
            it.setSurfaceProvider(viewFinder.surfaceProvider)
        }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera() = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
    private fun hasBackCamera() = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)

}


