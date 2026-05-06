package com.udl.smartrain.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ActivityClassifier(context: Context) {

    private var interpreter: Interpreter? = null

    init {
        // Carreguem el model des de la carpeta assets
        val modelBuffer = loadModelFile(context, "model_v1.tflite")
        interpreter = Interpreter(modelBuffer)
    }

    private fun loadModelFile(context: Context, fileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    fun classify(inputData: Array<Array<FloatArray>>): Int {
        // Tenim 6 classes. Sortida de l'intèrpret: [1, 6]
        val output = Array(1) { FloatArray(6) }

        interpreter?.run(inputData, output)

        // Retornem l'índex de la classe amb més probabilitat (argmax)
        return output[0].indices.maxByOrNull { output[0][it] } ?: -1
    }
}