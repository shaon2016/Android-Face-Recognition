package com.shaon2016.facerecongnition


import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import com.shaon2016.facerecongnition.util.C
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.io.IOException
import java.lang.Exception
import java.lang.RuntimeException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.HashMap

class TfliteClassifierHelper(private val context: Context) {

    private val TAG = TfliteClassifierHelper::class.java.simpleName

    // Initialization

    // Float model
    private val IMAGE_MEAN = 128f
    private val IMAGE_STD = 128f

    private val NUM_THREADS = 4
    private var isModelQuantized = false
    private var INPUT_SIZE = 160
    private var OUTPUT_SIZE = 192

    private lateinit var outputLocations: Array<Array<FloatArray>>
    private lateinit var outputClasses: Array<FloatArray>
    private lateinit var outputScores: Array<FloatArray>

    private lateinit var interpreter: Interpreter

    /** Memory-map the model file in Assets.  */
    @Throws(IOException::class)
    private fun loadModelFile(assets: AssetManager, modelFilename: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelFilename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    init {
        val modelFile: MappedByteBuffer = loadModelFile(context.assets, C.FACE_RECONGNITION_TFLITE_MODEL_NAME)

        val compatList = CompatibilityList()
        val options = Interpreter.Options().apply {
            if(compatList.isDelegateSupportedOnThisDevice){
                // if the device has a supported GPU, add the GPU delegate
                val delegateOptions = compatList.bestOptionsForThisDevice
                this.addDelegate(GpuDelegate(delegateOptions))
            } else {
                // if the GPU is not supported, run on 4 threads
                this.setNumThreads(NUM_THREADS)
            }
        }
        interpreter = Interpreter(modelFile, options)
    }

    fun process( resizedBitmap: Bitmap): FloatArray {
        try {
            val buffer = convertBitmapToByteBuffer(resizedBitmap)

            outputLocations = Array(1) { Array(1) {FloatArray(4)} }
            outputClasses = Array(1) { FloatArray(3)  }
            outputScores = Array(1) { FloatArray(136) }

            val outputMap: MutableMap<Int, Any> = HashMap()
            outputMap[0] = outputLocations
            outputMap[1] = outputClasses
            outputMap[2] = outputScores

            val inputArray = arrayOf<Any>(buffer)
            interpreter.runForMultipleInputsOutputs(inputArray, outputMap)

        } catch (e: Exception) {
            throw RuntimeException(e)
        }

        return outputScores[0]
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
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

    fun onDestroy() {
        interpreter.close()
    }

}