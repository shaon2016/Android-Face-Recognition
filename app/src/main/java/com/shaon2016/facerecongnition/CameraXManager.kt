package com.shaon2016.facerecongnition

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.hardware.camera2.*
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.*
import androidx.camera.core.impl.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.shaon2016.facerecongnition.Helper.toBitmap
import com.shaon2016.facerecongnition.ml.MobileFaceNet
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


class CameraXManager(
    private val context: Context,
    private val viewFinder: PreviewView
) : LifecycleOwner {

    private val TAG = "CameraXManager"

    // Lifecycle
    private var lifecycleRegistry: LifecycleRegistry = LifecycleRegistry(this)
    override fun getLifecycle() = lifecycleRegistry

    // CameraX
    private var orientationEventListener: OrientationEventListener? = null
    private var imageCapture: ImageCapture? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_FRONT
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private lateinit var cameraProvider: ProcessCameraProvider
    private var displayId: Int = -1
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null

    // Front Camera Issue
    private var needToFlipForFrontCamera = false
    private var imageInFrontCameraVertical = false

    private val INPUT_SIZE = 160

    private val displayManager by lazy {
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit


        override fun onDisplayChanged(displayId: Int) {
            if (displayId == this@CameraXManager.displayId) {
                Log.d(TAG, "Rotation changed: ${viewFinder.display.rotation}")
                imageCapture?.targetRotation = viewFinder.display.rotation
                imageAnalyzer?.targetRotation = viewFinder.display.rotation

            }
        }
    }


    init {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED

        // Every time the orientation of device changes, update rotation for use cases
        displayManager.registerDisplayListener(displayListener, null)
    }

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
        // Get screen metrics used to setup camera for full screen resolution
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)

        // Preview
        val preview = configurePreviewUseCase(screenAspectRatio)

        imageCapture = configureImageCapture(screenAspectRatio)

        imageAnalyzer = configureImageAnalyzer(screenAspectRatio)

        orientationEventListener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                Log.d(TAG, "Orientation changed: $orientation")

                // Monitors orientation values to determine the target rotation value
                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                    needToFlipForFrontCamera = true

                    if (orientation == 0) imageInFrontCameraVertical = true
                    else if (orientation == 1) imageInFrontCameraVertical = false
                }

                imageCapture?.targetRotation = rotation
                imageAnalyzer?.targetRotation = rotation

            }
        }
        orientationEventListener?.enable()


        // Select front camera as a default
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        try {
            // Unbind use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageCapture, imageAnalyzer
            )

        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun configureImageAnalyzer(screenAspectRatio: Int): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setTargetRotation(viewFinder.display.rotation)
            .setTargetResolution(Size(viewFinder.width, viewFinder.height))
            .build()
            .also {
                it.setAnalyzer(cameraExecutor, ImageAnalyzer(context))
            }
    }


    inner class ImageAnalyzer(
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
        val inputFeature = TensorBuffer.createFixedSize(intArrayOf(1, 112, 112, 3), DataType.FLOAT32)

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
                                rect.width() ,
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


    }

    private fun configureImageCapture(screenAspectRatio: Int) = ImageCapture.Builder()
//        .setTargetAspectRatio(screenAspectRatio)
        .setTargetRotation(viewFinder.display.rotation)
        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
        .build()

    private fun configurePreviewUseCase(screenAspectRatio: Int) = Preview.Builder()
        .setTargetResolution(Size(viewFinder.width, viewFinder.height))
//        .setTargetResolution(Size(480, 360))
        .build()
        .also {
            it.setSurfaceProvider(viewFinder.surfaceProvider)
        }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera() = cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
    private fun hasBackCamera() = cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    fun onStopped() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED

        orientationEventListener?.disable()
        orientationEventListener = null

        cameraExecutor.shutdown()
        displayManager.unregisterDisplayListener(displayListener)


        imageAnalyzer?.clearAnalyzer()
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
//        setupCamera()
    }

    companion object {
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
        val MAX_RESULT_DISPLAY = 3
    }

}


