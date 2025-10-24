package com.example.bloodcelldetector

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ImageDetectionActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private lateinit var overlayView: OverlayView
    private lateinit var interpreter: Interpreter
    private lateinit var labels: List<String>

    private val inferenceExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            if (uri == null) {
                Toast.makeText(this, "No image selected", Toast.LENGTH_SHORT).show()
                return@registerForActivityResult
            }
            detectFromUri(uri)
        }

    private val cellInfoMap = mapOf(
        "Abnormal_WBC" to Triple(
            "White blood cells showing atypical morphology due to infection, leukemia, or other disorders.",
            "Irregular size/shape, abnormal nuclei, uneven chromatin, or atypical granules.",
            "Look for irregular contours, hyper- or hypo-segmented nuclei, and inconsistent staining patterns unlike normal WBCs."
        ),
        "BASOPHIL" to Triple(
            "Rare granulocyte involved in allergic and inflammatory responses.",
            "12–15 µm; bi-lobed or S-shaped nucleus; dense, dark-purple granules that often obscure the nucleus.",
            "Coarse blue-purple granules covering nucleus; far fewer than other WBCs (<1%); strong basic dye affinity."
        ),
        "EOSINOPHIL" to Triple(
            "Granulocyte active in parasitic infections and allergy regulation.",
            "12–17 µm; bilobed nucleus; large red-orange cytoplasmic granules (acidic dye affinity).",
            "Red-orange granules, distinct bilobed nucleus; cleaner cytoplasm compared with basophils."
        ),
        "LYMPHOCYTE" to Triple(
            "Key immune cell (B, T, NK cells) responsible for adaptive response.",
            "7–10 µm (small); round, dark-staining nucleus; thin rim of pale blue cytoplasm; agranular.",
            "Very high nucleus-to-cytoplasm ratio; smooth edges; no visible granules."
        ),
        "MONOCYTE" to Triple(
            "Largest WBC; precursor of macrophages and dendritic cells.",
            "15–20 µm; kidney- or horseshoe-shaped nucleus; gray-blue cytoplasm; fine azurophilic granules.",
            "Largest cell in smear; nucleus not segmented; grayish cytoplasm often with vacuoles."
        ),
        "NEUTROPHIL" to Triple(
            "Most abundant WBC; first responder to infection.",
            "12–15 µm; multi-lobed nucleus (2–5 lobes); fine pink-lilac granules in pale cytoplasm.",
            "Multi-lobed nucleus; pinkish granules; balanced staining — not overly red or blue."
        ),
        "Platelets" to Triple(
            "Cell fragments that help in blood clotting.",
            "2–4 µm; no nucleus; small purple fragments or discs.",
            "Much smaller than RBCs/WBCs; irregular shape; clustered in groups."
        ),
        "RBC" to Triple(
            "Biconcave cells responsible for oxygen transport.",
            "6–8 µm; no nucleus; uniform pinkish-red color with central pallor.",
            "Perfectly round, no nucleus, smooth edges, and central pale area (light center)."
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_detection)

        imageView = findViewById(R.id.inputImageView)
        overlayView = findViewById(R.id.overlayView)

        try {
            labels = assets.open("labels.txt").bufferedReader().readLines()
        } catch (e: Exception) {
            Log.e("ImageDetection", "Failed to load labels.txt: ${e.message}", e)
            labels = emptyList()
        }

        try {
            interpreter = Interpreter(loadModelFile("bloodcells.tflite"))
        } catch (e: Exception) {
            Log.e("ImageDetection", "Failed to load model: ${e.message}", e)
            Toast.makeText(this, "Model load failed", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        pickImage.launch("image/*")
    }

    private fun loadModelFile(filename: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(filename)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    private fun detectFromUri(uri: Uri) {
        if (uri == Uri.EMPTY) {
            Toast.makeText(this, "Invalid image URI", Toast.LENGTH_SHORT).show()
            return
        }

        inferenceExecutor.execute {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bitmap = inputStream.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                }

                if (bitmap == null) {
                    runOnUiThread {
                        Toast.makeText(this, "Failed to decode image", Toast.LENGTH_SHORT).show()
                    }
                    return@execute
                }

                runOnUiThread { imageView.setImageBitmap(bitmap) }

                val tensorImage = TensorImage.fromBitmap(bitmap)
                val imageProcessor = ImageProcessor.Builder()
                    .add(ResizeOp(640, 640, ResizeOp.ResizeMethod.BILINEAR))
                    .add(NormalizeOp(0f, 255f))
                    .build()
                val processedImage = imageProcessor.process(tensorImage)


                val output = Array(1) { Array(labels.size + 4) { FloatArray(8400) } }

                interpreter.run(processedImage.buffer, output)

                val detections = parseDetections(output)
                Log.d("ImageDetection", "Found ${detections.size} detections")

                runOnUiThread {
                    overlayView.setDetections(detections, 640, 640)
                    updateInfoPanel(detections)
                }

            } catch (e: Exception) {
                Log.e("ImageDetection", "Detection error: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this, "Detection error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateInfoPanel(detections: List<YoloDetection>) {
        val container = findViewById<LinearLayout?>(R.id.infoContainer)
        if (container == null) {
            Log.w("ImageDetection", "infoContainer not found in layout")
            return
        }

        container.removeAllViews()
        if (detections.isEmpty()) {
            container.isVisible = false
            return
        }
        container.isVisible = true


        val uniqueLabels = detections.map { it.label }.toSet()

        for (label in uniqueLabels) {
            val info = cellInfoMap[label]

            val title = TextView(this).apply {
                textSize = 16f
                setPadding(0, 8, 0, 0)
                text = label
            }
            val shortDesc = TextView(this).apply {
                textSize = 14f
                setPadding(0, 4, 0, 0)
                text = info?.first ?: "Properties not found"
            }
            val properties = TextView(this).apply {
                textSize = 14f
                setPadding(0, 4, 0, 0)
                text = info?.second ?: "Properties not found"
            }
            val distinguish = TextView(this).apply {
                textSize = 14f
                setPadding(0, 2, 0, 8)
                text = info?.third ?: "Distinguishing info not found"
            }

            container.addView(title)
            container.addView(shortDesc)
            container.addView(properties)
            container.addView(distinguish)
        }
    }


    private fun parseDetections(output: Array<Array<FloatArray>>): List<YoloDetection> {
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

            // find best class
            var bestClassIdx = -1
            var bestClassScore = 0f
            for (c in 0 until classCount) {
                val clsScore = data[4 + c][i]
                if (clsScore > bestClassScore) {
                    bestClassScore = clsScore
                    bestClassIdx = c
                }
            }

            if (bestClassScore < 0.5f || bestClassIdx !in labels.indices) continue

            val cx = x * 640f
            val cy = y * 640f
            val bw = w * 640f
            val bh = h * 640f

            val rect = android.graphics.RectF(
                (cx - bw / 2).coerceAtLeast(0f),
                (cy - bh / 2).coerceAtLeast(0f),
                (cx + bw / 2).coerceAtMost(640f),
                (cy + bh / 2).coerceAtMost(640f)
            )

            detections.add(YoloDetection(rect, bestClassScore, labels[bestClassIdx]))
        }

        return detections
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            interpreter.close()
        } catch (e: Exception) {
            Log.w("ImageDetection", "Interpreter close failed: ${e.message}")
        }
        inferenceExecutor.shutdownNow()
    }
}
