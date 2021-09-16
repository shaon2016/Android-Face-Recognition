package com.shaon2016.facerecongnition.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.util.Size
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.shaon2016.facerecongnition.face_detection.FaceContourDetectionProcessor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class CameraXManager(
    private val context: Context,
    private val viewFinder: PreviewView,
    private val lifecycleOwner: LifecycleOwner,
    private val graphicOverlay: GraphicOverlay,

    ) {

    private val TAG = "CameraXManager"

    // CameraX
    private var lensFacing: Int = CameraSelector.LENS_FACING_FRONT
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
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
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
            .setTargetResolution(Size(viewFinder.width, viewFinder.height))
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, selectAnalyzer())
            }
    }

    private fun selectAnalyzer(): ImageAnalysis.Analyzer {
        return FaceContourDetectionProcessor(graphicOverlay)
    }

    private fun configurePreviewUseCase() = Preview.Builder()
        .setTargetResolution(Size(viewFinder.width, viewFinder.height))
        .build()
        .also {
            it.setSurfaceProvider(viewFinder.surfaceProvider)
        }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera() = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
    private fun hasBackCamera() = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)


    fun onStopped() {
        cameraExecutor.shutdown()
        imageAnalyzer?.clearAnalyzer()
    }

  /*  inner class ImageAnalyzer(
        private val ctx: Context
    ) :
        ImageAnalysis.Analyzer {

        // MLKit face detection
        private val highAccuracyOpts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()

        private val detector = FaceDetection.getClient(highAccuracyOpts)

        // Face recognition Tflite model
        val mobileFaceNet = MobileFaceNet.newInstance(context)
        val inputFeature =
            TensorBuffer.createFixedSize(intArrayOf(1, 112, 112, 3), DataType.FLOAT32)

        @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
        override fun analyze(imageProxy: ImageProxy) {
            val toBitmap = imageProxy.toBitmap(ctx)

            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image =
                    InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                detector.process(image).addOnSuccessListener { faces ->
                    faces.forEach {
                        val rect = it.boundingBox

                        if (rect.width() <= toBitmap!!.width && rect.height() <= toBitmap.height) {
                            val croppedBitmap = Bitmap.createBitmap(
                                toBitmap,
                                rect.left,
                                rect.top,
                                rect.width(),
                                rect.height()
                            )

                            val resizedBitmap =
                                Bitmap.createScaledBitmap(croppedBitmap, 112, 112, false)

                            val buffer = Helper.convertBitmapToByteBuffer(resizedBitmap, 112)
                            inputFeature.loadBuffer(buffer)

                            val outputs = mobileFaceNet.process(inputFeature)
                            val outputFeature = outputs.outputFeature0AsTensorBuffer

                            if (outputFeature.floatArray.isNotEmpty()) {
                                Log.d("DATATAG", "TFLite model data: ")
                            }

                        }
                    }
                }.addOnFailureListener {


                }.addOnCompleteListener {
                    imageProxy.close()
                }
            }
        }
    }*/

}


