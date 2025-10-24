package com.example.bloodcelldetector

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.min

data class YoloDetection(
    val box: RectF,
    val score: Float,
    val label: String
)

class YoloDetector(context: Context) {

    private var interpreter: Interpreter
    private val labels: List<String>


    private val confidenceThreshold = 0.5f
    private val nmsThreshold = 0.45f

    init {
        interpreter = Interpreter(loadModelFile(context, "bloodcells.tflite"))
        labels = loadLabels(context)
        Log.d("YoloDetector", "✅ Model and labels loaded successfully (labels=${labels.size})")
    }

    private fun loadModelFile(context: Context, filename: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    private fun loadLabels(context: Context): List<String> {
        return context.assets.open("labels.txt")
            .bufferedReader().useLines { it.toList() }
    }

    fun detect(bitmap: Bitmap): List<YoloDetection> {
        val resized = if (bitmap.width != 640 || bitmap.height != 640) {
            Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        } else bitmap

        val inputBuffer = bitmapToFloatBuffer(resized)
        val output = Array(1) { Array(12) { FloatArray(8400) } }
        inputBuffer.rewind()
        interpreter.run(inputBuffer, output)


        val detections = parseOutput(output, resized.width, resized.height)
        val filtered = applyNms(detections, nmsThreshold)

        Log.d("YoloDetector", "Final detections after NMS: ${filtered.size}")
        return filtered
    }

    private fun bitmapToFloatBuffer(bitmap: Bitmap): ByteBuffer {
        val inputSize = 640
        val channels = 3
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * channels)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        bitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize)

        var pixel = 0
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255f)
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255f)
                byteBuffer.putFloat((value and 0xFF) / 255f)
            }
        }
        byteBuffer.rewind()
        return byteBuffer
    }

    private fun parseOutput(output: Array<Array<FloatArray>>, imgWidth: Int, imgHeight: Int): List<YoloDetection> {
        val detections = mutableListOf<YoloDetection>()
        if (output.isEmpty()) return detections
        val data = output[0]
        val gridCount = data[0].size
        val classCount = labels.size

        for (i in 0 until gridCount) {
            val x = data[0][i]
            val y = data[1][i]
            val w = data[2][i]
            val h = data[3][i]

            var bestClassIdx = -1
            var bestClassScore = 0f
            for (c in 0 until classCount) {
                val clsScore = data[4 + c][i]
                if (clsScore > bestClassScore) {
                    bestClassScore = clsScore
                    bestClassIdx = c
                }
            }

            if (bestClassScore < confidenceThreshold || bestClassIdx !in labels.indices) continue

            val cx = x * imgWidth
            val cy = y * imgHeight
            val bw = w * imgWidth
            val bh = h * imgHeight

            val rect = RectF(
                (cx - bw / 2).coerceAtLeast(0f),
                (cy - bh / 2).coerceAtLeast(0f),
                (cx + bw / 2).coerceAtMost(imgWidth.toFloat()),
                (cy + bh / 2).coerceAtMost(imgHeight.toFloat())
            )

            detections.add(
                YoloDetection(
                    box = rect,
                    score = bestClassScore,
                    label = labels[bestClassIdx]
                )
            )
        }

        Log.d("YoloDetector", "Raw detections before NMS: ${detections.size}")
        return detections
    }


    private fun applyNms(detections: List<YoloDetection>, iouThreshold: Float): List<YoloDetection> {
        val finalDetections = mutableListOf<YoloDetection>()

        // Group by label
        val grouped = detections.groupBy { it.label }

        for ((label, group) in grouped) {
            val sorted = group.sortedByDescending { it.score }.toMutableList()

            while (sorted.isNotEmpty()) {
                val best = sorted.removeAt(0)
                finalDetections.add(best)

                val iterator = sorted.iterator()
                while (iterator.hasNext()) {
                    val other = iterator.next()
                    if (iou(best.box, other.box) > iouThreshold) {
                        iterator.remove()
                    }
                }
            }
        }
        return finalDetections
    }


    private fun iou(a: RectF, b: RectF): Float {
        val x1 = max(a.left, b.left)
        val y1 = max(a.top, b.top)
        val x2 = min(a.right, b.right)
        val y2 = min(a.bottom, b.bottom)

        val intersection = max(0f, x2 - x1) * max(0f, y2 - y1)
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        val union = areaA + areaB - intersection

        return if (union <= 0f) 0f else intersection / union
    }

    fun close() {
        try {
            interpreter.close()
        } catch (e: Exception) {
            Log.w("YoloDetector", "interpreter close failed: ${e.message}")
        }
    }
}
