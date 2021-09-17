package com.shaon2016.facerecongnition.face_mask_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import android.util.Pair
import com.google.mlkit.vision.face.Face
import com.shaon2016.facerecongnition.camera.GraphicOverlay
import com.shaon2016.facerecongnition.ml.MaskDetector
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

class FaceRecognitionProcessor(
    private val context: Context,
    private val overlay: GraphicOverlay
) {

    private val TF_FR_API_INPUT_SIZE = 224

    private val IMAGE_MEAN = 128f
    private val IMAGE_STD = 128f

    // Face recognition Tflite model
    private val maskDetector = MaskDetector.newInstance(context)
    private val inputFeature =
        TensorBuffer.createFixedSize(
            intArrayOf(1, TF_FR_API_INPUT_SIZE, TF_FR_API_INPUT_SIZE, 3),
            DataType.FLOAT32
        )


    fun detectMask(face: Face, bitmap: Bitmap, faceGraphic: FaceContourGraphic) {
        val rect = face.boundingBox

        if (rect.left > 0 && rect.right > 0 && rect.top > 0 && rect.bottom > 0
            && rect.width() + rect.left <= bitmap.width && rect.height() + rect.top <= bitmap.height
        ) {
            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                rect.left,
                rect.top,
                rect.width(),
                rect.height()
            )

            val resizedBmp = Bitmap.createScaledBitmap(
                croppedBitmap,
                TF_FR_API_INPUT_SIZE,
                TF_FR_API_INPUT_SIZE,
                false
            )

            val buffer = convertBitmapToByteBuffer(resizedBmp, TF_FR_API_INPUT_SIZE)
            inputFeature.loadBuffer(buffer)

            val outputs = maskDetector.process(inputFeature)
            val outputFeature = outputs.outputFeature0AsTensorBuffer




        }

    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap, INPUT_SIZE: Int): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * INPUT_SIZE * INPUT_SIZE * 3)

        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        var pixel = 0
        for (i in 0 until INPUT_SIZE) {
            for (j in 0 until INPUT_SIZE) {
                val `val` = intValues[pixel++]
                byteBuffer.putFloat(((`val` shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                byteBuffer.putFloat(((`val` shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                byteBuffer.putFloat(((`val` and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
            }
        }

        return byteBuffer
    }

}