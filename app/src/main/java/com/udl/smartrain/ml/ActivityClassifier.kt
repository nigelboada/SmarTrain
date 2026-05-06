package com.udl.smartrain.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

data class ActivityPrediction(
    val classIndex: Int,
    val label: String,
    val confidence: Float,
    val timestampMillis: Long = System.currentTimeMillis()
)

class ActivityClassifier(context: Context) {

    private val interpreter: Interpreter

    private val labels = listOf(
        "Caminar",
        "Pujar escales",
        "Baixar escales",
        "Seure",
        "Dret",
        "Repos"
    )

    init {
        val modelBuffer = loadModelFile(context, "model_v1.tflite")
        interpreter = Interpreter(modelBuffer)
    }

    private fun loadModelFile(context: Context, fileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    fun classify(inputData: Array<Array<FloatArray>>): ActivityPrediction {
        val output = Array(1) { FloatArray(labels.size) }

        interpreter.run(inputData, output)

        val classIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
        val confidence = output[0].getOrNull(classIndex) ?: 0f

        return ActivityPrediction(
            classIndex = classIndex,
            label = labels.getOrElse(classIndex) { "Desconeguda" },
            confidence = confidence
        )
    }
}
