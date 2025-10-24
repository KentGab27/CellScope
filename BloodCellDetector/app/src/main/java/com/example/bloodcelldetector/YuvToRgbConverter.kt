package com.example.bloodcelldetector

import android.graphics.Bitmap
import android.media.Image
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.nio.ByteOrder

object YuvToRgbConverter {

    private const val MODEL_INPUT_SIZE = 640
    private const val MODEL_CHANNELS = 3


    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        // create ARGB bitmap matching source size
        val bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        yuvToRgb(image, bitmap)
        // scale to model size
        val scaled = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, true)
        return scaled
    }


    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    fun imageProxyToByteBuffer(imageProxy: ImageProxy): ByteBuffer? {
        val bmp = imageProxyToBitmap(imageProxy) ?: return null
        return bitmapToByteBuffer(bmp)
    }


    private fun yuvToRgb(image: Image, output: Bitmap) {
        val width = image.width
        val height = image.height

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val yRowStride = image.planes[0].rowStride
        val uvRowStride = image.planes[1].rowStride
        val uvPixelStride = image.planes[1].pixelStride

        val argb = IntArray(width * height)

        var yp = 0
        for (j in 0 until height) {
            val pY = j * yRowStride
            val pUV = (j shr 1) * uvRowStride

            for (i in 0 until width) {
                val y = 0xFF and yBuffer.get(pY + i).toInt()
                val uvOffset = pUV + (i shr 1) * uvPixelStride
                val u = 0xFF and uBuffer.get(uvOffset).toInt()
                val v = 0xFF and vBuffer.get(uvOffset).toInt()

                var r = (y + 1.370705f * (v - 128)).toInt()
                var g = (y - 0.337633f * (u - 128) - 0.698001f * (v - 128)).toInt()
                var b = (y + 1.732446f * (u - 128)).toInt()

                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)

                argb[yp++] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        }

        output.setPixels(argb, 0, width, 0, 0, width, height)
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * MODEL_INPUT_SIZE * MODEL_INPUT_SIZE * MODEL_CHANNELS)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(MODEL_INPUT_SIZE * MODEL_INPUT_SIZE)
        bitmap.getPixels(intValues, 0, MODEL_INPUT_SIZE, 0, 0, MODEL_INPUT_SIZE, MODEL_INPUT_SIZE)

        var pixel = 0
        for (y in 0 until MODEL_INPUT_SIZE) {
            for (x in 0 until MODEL_INPUT_SIZE) {
                val value = intValues[pixel++]
                byteBuffer.putFloat(((value shr 16) and 0xFF) / 255f)
                byteBuffer.putFloat(((value shr 8) and 0xFF) / 255f)
                byteBuffer.putFloat((value and 0xFF) / 255f)
            }
        }
        byteBuffer.rewind()
        return byteBuffer
    }
}
