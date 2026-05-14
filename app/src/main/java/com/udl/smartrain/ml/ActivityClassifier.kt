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
    val modelLabel: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

object ActivityLabelMapper {
    val modelLabels = listOf(
        "Caminar",
        "Pujar escales",
        "Baixar escales",
        "Seure",
        "Dret",
        "Estirat"
    )

    private val smarTrainLabels = listOf(
        "Desplacament suau",
        "Alta intensitat",
        "Alta intensitat",
        "Repos",
        "Repos",
        "Repos"
    )

    fun modelLabelFor(classIndex: Int): String {
        return modelLabels.getOrElse(classIndex) { "Desconeguda" }
    }

    fun smarTrainLabelFor(classIndex: Int): String {
        return smarTrainLabels.getOrElse(classIndex) { "Desconeguda" }
    }
}

class ActivityClassifier(context: Context) {

    private val interpreter: Interpreter

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
        val output = Array(1) { FloatArray(ActivityLabelMapper.modelLabels.size) }

        interpreter.run(inputData, output)

        val classIndex = output[0].indices.maxByOrNull { output[0][it] } ?: -1
        val confidence = output[0].getOrNull(classIndex) ?: 0f
        val modelLabel = ActivityLabelMapper.modelLabelFor(classIndex)

        return ActivityPrediction(
            classIndex = classIndex,
            label = ActivityLabelMapper.smarTrainLabelFor(classIndex),
            confidence = confidence,
            modelLabel = modelLabel
        )
    }
}
