package com.shaon2016.facerecongnition.face_detection

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import android.util.Pair
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import com.google.mlkit.vision.face.Face
import com.shaon2016.facerecongnition.R
import com.shaon2016.facerecongnition.ml.MobileFaceNet
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FaceRecognitionProcessor(private val context: Context, private val fabAdd: View) {
    var addAFacePendingToRecognize = false

    init {
        fabAdd.setOnClickListener {
            addAFacePendingToRecognize = true
        }
    }

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

        Log.d("DATATAG", "RECT: ${rect}")

        if (rect.left > 0 && rect.right > 0 && rect.top > 0 && rect.bottom > 0 && rect.width() + rect.left <= bitmap.width && rect.height() + rect.top <= bitmap.height) {
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


            val resultRecognition = Recognition("0", "?", Float.MAX_VALUE)
            resultRecognition.extra = embeddings
            resultRecognition.crop = croppedBitmap

            if (addAFacePendingToRecognize) {
                addAFacePendingToRecognize = false

                showAddFaceDialog(resultRecognition)
            }

            doRecognition(resultRecognition)
        }

    }

    private val registered = HashMap<String, Recognition>()

    private fun register(name: String, rec: Recognition) {
        registered[name] = rec
    }

    private fun doRecognition(resultRecognition: Recognition) {

        if (registered.size > 0) {
            val nearest: Pair<String, Float>? = findNearest(resultRecognition.extra as FloatArray)
            if (nearest != null) {
                val name = nearest.first
                resultRecognition.title = name
                resultRecognition.distance = nearest.second

                Log.d(
                    "DATATAG",
                    "Nearest: Title: ${resultRecognition.title} and Distance: ${resultRecognition.title}"
                )
            }
        }


        val conf = resultRecognition.distance

        Log.d("DATATAG", "Confidence: $conf")

        if (conf < 1.0f) {
            if (resultRecognition.id == "0") {
                resultRecognition.color = Color.GREEN
            } else {
                resultRecognition.color = Color.RED
            }
        }


    }

    private fun showAddFaceDialog(rec: Recognition) {
        val builder = AlertDialog.Builder(context)
        val dialogLayout = View.inflate(context, R.layout.image_edit_dialog, null)

        val ivFace = dialogLayout.findViewById<ImageView>(R.id.dlg_image)
        val tvTitle = dialogLayout.findViewById<TextView>(R.id.dlg_title)
        val etName = dialogLayout.findViewById<EditText>(R.id.dlg_input)

        tvTitle.text = "Add Face"
        ivFace.setImageBitmap(rec.crop)
        etName.hint = "Input name"
        builder.setPositiveButton("OK", DialogInterface.OnClickListener { dlg, i ->
            val name = etName.text.toString()
            if (name.isEmpty()) {
                return@OnClickListener
            }
            register(name, rec)

            dlg.dismiss()
        })
        builder.setView(dialogLayout)
        builder.show()
    }

    // looks for the nearest embedding in the dataset (using L2 norm)
    // and returns the pair <id, distance>
    private fun findNearest(emb: FloatArray): Pair<String, Float>? {
        var ret: Pair<String, Float>? = null
        for ((name, value) in registered) {
            val knownEmb = (value.extra as FloatArray)
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