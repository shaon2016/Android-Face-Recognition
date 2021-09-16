package com.shaon2016.facerecongnition.face_detection

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.util.Pair
import com.google.mlkit.vision.face.Face
import com.shaon2016.facerecongnition.ml.MobileFaceNet
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.ArrayList

class FaceRecognitionProcessor(private val context: Context) {
    private val TF_FR_API_INPUT_SIZE = 112

    private val IMAGE_MEAN = 128f
    private val IMAGE_STD = 128f

    // Face recognition Tflite model
    private val mobileFaceNet = MobileFaceNet.newInstance(context)
    private val inputFeature =
        TensorBuffer.createFixedSize(
            intArrayOf(1, TF_FR_API_INPUT_SIZE, TF_FR_API_INPUT_SIZE, 3),
            DataType.FLOAT32
        )

    fun recognize(face: Face, bitmap: Bitmap) {
        val rect = face.boundingBox

        if (rect.width() <= bitmap.width && rect.height() <= bitmap.height) {
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

            val outputs = mobileFaceNet.process(inputFeature)
            val outputFeature = outputs.outputFeature0AsTensorBuffer

            val embeddings = outputFeature.floatArray

            doRecognition(embeddings)
        }

    }

    private val registered = HashMap<String, Recognition>()

    fun register(name: String, rec: Recognition) {
        registered[name] = rec
    }

    private fun doRecognition(embeddings: FloatArray) {
        var distance = Float.MAX_VALUE
        var id = "0"
        var title = "?"

        if (registered.size > 0) {
            val nearest: Pair<String, Float>? = findNearest(embeddings)
            if (nearest != null) {
                val name = nearest.first
                title = name
                distance = nearest.second

            }
        }

        val resultRecognition = Recognition(id, title, distance, embeddings)

        val conf = resultRecognition.distance

        if (conf < 1.0f) {
            // Recognized

        }
    }

    // looks for the nearest embedding in the dataset (using L2 norm)
    // and returns the pair <id, distance>
    private fun findNearest(emb: FloatArray): Pair<String, Float>? {
        var ret: Pair<String, Float>? = null
        for ((name, value) in registered) {
            val knownEmb = (value.extra as Array<FloatArray>)[0]
            var distance = 0f
            for (i in emb.indices) {
                val diff = emb[i] - knownEmb[i]
                distance += diff * diff
            }
            distance = Math.sqrt(distance.toDouble()).toFloat()
            if (ret == null || distance < ret.second) {
                ret = Pair(name, distance)
            }
        }
        return ret
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