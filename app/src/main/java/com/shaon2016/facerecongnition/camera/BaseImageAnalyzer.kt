package com.shaon2016.facerecongnition.camera

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.shaon2016.facerecongnition.util.BitmapUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class BaseImageAnalyzer<T> : ImageAnalysis.Analyzer {
    var isProcessing = false
    private var faces: ArrayList<Face>? = null

    abstract val graphicOverlay: GraphicOverlay

    @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        val bitmap = BitmapUtils.getBitmap(imageProxy)

        if (isProcessing || faces?.size == 0) {
            Log.d("Processing", "Still Processing")
            imageProxy.close()
            return
        }
        isProcessing = true
        mediaImage?.let { image ->
            faceDetectInImage(
                InputImage.fromMediaImage(
                    image,
                    imageProxy.imageInfo.rotationDegrees
                )
            )
                .addOnSuccessListener { faces ->
                    bitmap?.let {
                        CoroutineScope(Dispatchers.Main).launch {
                            onSuccess(
                                faces,
                                graphicOverlay,
                                bitmap
                            )
                        }
                    }
                }
                .addOnFailureListener {
                    graphicOverlay.clear()
                    graphicOverlay.postInvalidate()
                    onFailure(it)
                }.addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    protected abstract fun faceDetectInImage(image: InputImage): Task<T>

    abstract fun stop()

    protected abstract suspend
    fun onSuccess(
        results: T,
        graphicOverlay: GraphicOverlay,
        bitmap: Bitmap
    )

    protected abstract fun onFailure(e: Exception)

}