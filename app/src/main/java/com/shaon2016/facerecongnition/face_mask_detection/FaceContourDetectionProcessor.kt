package com.shaon2016.facerecongnition.face_mask_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.shaon2016.facerecongnition.camera.BaseImageAnalyzer
import com.shaon2016.facerecongnition.camera.GraphicOverlay
import java.io.IOException

class FaceContourDetectionProcessor(
    private val overlay: GraphicOverlay,
    private val context: Context
) :
    BaseImageAnalyzer<List<Face>>() {

    private var blinkCount = 0

    private val realTimeOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
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

    override fun onSuccess(
        results: List<Face>,
        graphicOverlay: GraphicOverlay,
        rect: Rect, bitmap: Bitmap
    ) {
        graphicOverlay.clear()
        results.forEach {
            val faceGraphic = FaceContourGraphic(graphicOverlay, it, rect)
            graphicOverlay.add(faceGraphic)

            detectBlink(it, faceGraphic)
        }
        graphicOverlay.postInvalidate()

        Log.d("DATATAG", "Detected Faces: ${results.size}")
    }

    private fun detectBlink(face: Face, faceGraphic: FaceContourGraphic) {
        val leftEyeOpenProbability = face.leftEyeOpenProbability
        val rightEyeOpenProbability = face.rightEyeOpenProbability

        if (rightEyeOpenProbability != null && leftEyeOpenProbability != null) {
            Log.d(
                "DATATAG",
                "Left Eye Probability: $leftEyeOpenProbability and Right Eye Probability: $rightEyeOpenProbability"
            )

            if (leftEyeOpenProbability < 0.4 && rightEyeOpenProbability < 0.4) {
                blinkCount++
            }

            faceGraphic.blinkCount = "Blink: ${blinkCount}"
        }
    }

    override fun onFailure(e: Exception) {
        Log.w(TAG, "Face Detector failed.$e")
    }

    companion object {
        private const val TAG = "FaceDetectorProcessor"
    }

}