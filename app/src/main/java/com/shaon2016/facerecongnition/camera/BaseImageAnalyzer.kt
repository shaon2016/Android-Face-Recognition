package com.shaon2016.facerecongnition.camera

import android.annotation.SuppressLint
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage

abstract class BaseImageAnalyzer<T> : ImageAnalysis.Analyzer {

    abstract val graphicOverlay: GraphicOverlay

    @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        mediaImage?.let {
            faceDetectInImage(InputImage.fromMediaImage(it, imageProxy.imageInfo.rotationDegrees))
                .addOnSuccessListener { faces ->
                    onSuccess(
                        faces,
                        it.cropRect
                    )
                }
                .addOnFailureListener {
                    onFailure(it)
                }.addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    protected abstract fun faceDetectInImage(image: InputImage): Task<T>

    abstract fun stop()

    protected abstract fun onSuccess(
        results: T,
        rect: Rect
    )

    protected abstract fun onFailure(e: Exception)

}