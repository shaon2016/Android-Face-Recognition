package com.shaon2016.facerecongnition.face_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import android.view.View
import androidx.core.graphics.toRectF
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.shaon2016.facerecongnition.camera.BaseImageAnalyzer
import com.shaon2016.facerecongnition.camera.GraphicOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class FaceContourDetectionProcessor(
    private val overlay: GraphicOverlay,
    private val fabAdd: View,
    private val context: Context
) :
    BaseImageAnalyzer<List<Face>>() {

    private val faceRecognitionProcessor by lazy {
        FaceRecognitionProcessor(
            context,
            overlay,
            fabAdd
        )
    }

    private val realTimeOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .build()

    private val detector = FaceDetection.getClient(realTimeOpts)

    override val graphicOverlay: GraphicOverlay get() = overlay

    override fun faceDetectInImage(image: InputImage): Task<List<Face>> {
        return detector.process(image)
    }

    override fun stop() {
        try {
            detector.close()
        } catch (e: IOException) {
            Log.e(TAG, "Exception thrown while trying to close Face Detector: $e")
        }
    }

    override  suspend fun onSuccess(
        results: List<Face>,
        graphicOverlay: GraphicOverlay,
        bitmap: Bitmap
    ) {
        graphicOverlay.clear()

        withContext(Dispatchers.Default) {
            results.forEach {
                val faceGraphic = FaceContourGraphic(graphicOverlay, it)
                if (!overlay.areDimsInit) {
                    faceGraphic.frameHeight = bitmap.height
                    faceGraphic.frameWidth = bitmap.width
                }
                graphicOverlay.add(faceGraphic)

                // Recognize
                faceRecognitionProcessor.recognize(it, bitmap, faceGraphic)
            }
        }

        graphicOverlay.postInvalidate()
        isProcessing = false
        Log.d("Processing", "Recognize Process Complete")
    }

    override fun onFailure(e: Exception) {
        Log.w(TAG, "Face Detector failed.$e")
    }

    companion object {
        private const val TAG = "FaceDetectorProcessor"
    }

}