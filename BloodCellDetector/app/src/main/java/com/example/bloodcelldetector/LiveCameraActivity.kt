package com.example.bloodcelldetector

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class LiveCameraActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var detector: YoloDetector
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private lateinit var infoShort: TextView
    private lateinit var infoProperties: TextView
    private lateinit var infoDistinguish: TextView

    private val isProcessing = AtomicBoolean(false) // avoid flooding frames

    private val cellInfoMap = mapOf(
        "Abnormal_WBC" to CellInfo(
            shortDesc = "White blood cells showing atypical morphology due to infection, leukemia, or other disorders.",
            properties = "Irregular size/shape, abnormal nuclei, uneven chromatin, or atypical granules.",
            distinguish = "Look for irregular contours, hyper- or hypo-segmented nuclei, and inconsistent staining patterns unlike normal WBCs."
        ),
        "BASOPHIL" to CellInfo(
            shortDesc = "Rare granulocyte involved in allergic and inflammatory responses.",
            properties = "12–15 µm; bi-lobed or S-shaped nucleus; dense, dark-purple granules that often obscure the nucleus.",
            distinguish = "Coarse blue-purple granules covering nucleus; far fewer than other WBCs (<1%); strong basic dye affinity."
        ),
        "EOSINOPHIL" to CellInfo(
            shortDesc = "Granulocyte active in parasitic infections and allergy regulation.",
            properties = "12–17 µm; bilobed nucleus; large red-orange cytoplasmic granules (acidic dye affinity).",
            distinguish = "Red-orange granules, distinct bilobed nucleus; cleaner cytoplasm compared with basophils."
        ),
        "LYMPHOCYTE" to CellInfo(
            shortDesc = "Key immune cell (B, T, NK cells) responsible for adaptive response.",
            properties = "7–10 µm (small); round, dark-staining nucleus; thin rim of pale blue cytoplasm; agranular.",
            distinguish = "Very high nucleus-to-cytoplasm ratio; smooth edges; no visible granules."
        ),
        "MONOCYTE" to CellInfo(
            shortDesc = "Largest WBC; precursor of macrophages and dendritic cells.",
            properties = "15–20 µm; kidney- or horseshoe-shaped nucleus; gray-blue cytoplasm; fine azurophilic granules.",
            distinguish = "Largest cell in smear; nucleus not segmented; grayish cytoplasm often with vacuoles."
        ),
        "NEUTROPHIL" to CellInfo(
            shortDesc = "Most abundant WBC; first responder to infection.",
            properties = "12–15 µm; multi-lobed nucleus (2–5 lobes); fine pink-lilac granules in pale cytoplasm.",
            distinguish = "Multi-lobed nucleus; pinkish granules; balanced staining — not overly red or blue."
        ),
        "Platelets" to CellInfo(
            shortDesc = "Cell fragments that help in blood clotting.",
            properties = "2–4 µm; no nucleus; small purple fragments or discs.",
            distinguish = "Much smaller than RBCs/WBCs; irregular shape; clustered in groups."
        ),
        "RBC" to CellInfo(
            shortDesc = "Biconcave cells responsible for oxygen transport.",
            properties = "6–8 µm; no nucleus; uniform pinkish-red color with central pallor.",
            distinguish = "Perfectly round, no nucleus, smooth edges, and central pale area (light center)."
        )
    )

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                initYoloDetector()
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_camera)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        infoShort = findViewById(R.id.info_short)
        infoProperties = findViewById(R.id.info_properties)
        infoDistinguish = findViewById(R.id.info_distinguish)

        checkAndRequestCameraPermission()
    }

    private fun checkAndRequestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                initYoloDetector()
                startCamera()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) -> {
                Toast.makeText(this, "Camera permission is needed to run live detection.", Toast.LENGTH_LONG).show()
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun initYoloDetector() {
        detector = YoloDetector(this)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetResolution(Size(1280, 720))
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                if (!isProcessing.compareAndSet(false, true)) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                try {
                    analyzeImage(imageProxy)
                } catch (e: Exception) {
                    Log.e("LiveCamera", "Analyze failed: ${e.message}", e)
                    isProcessing.set(false)
                    imageProxy.close()
                }
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
            Log.d("LiveCamera", "📸 Camera started successfully")
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeImage(imageProxy: ImageProxy) {
        try {
            // convert to scaled 640x640 bitmap
            val bitmap: Bitmap? = YuvToRgbConverter.imageProxyToBitmap(imageProxy)
            if (bitmap == null) {
                imageProxy.close()
                isProcessing.set(false)
                return
            }

            // run detection (this runs on cameraExecutor thread)
            val detections = detector.detect(bitmap)

            // update UI
            runOnUiThread {
                overlayView.setDetections(detections, 640, 640)

                val uniqueLabels = detections.map { it.label }.toSet()
                if (uniqueLabels.isNotEmpty()) {
                    val builder = StringBuilder()
                    for (label in uniqueLabels) {
                        cellInfoMap[label]?.let { info ->
                            builder.append("🔹 ${label}\n")
                            builder.append("• Short: ${info.shortDesc}\n")
                            builder.append("• Properties: ${info.properties}\n")
                            builder.append("• Distinguish: ${info.distinguish}\n\n")
                        }
                    }
                    infoShort.text = builder.toString().trim()
                    infoProperties.text = ""
                    infoDistinguish.text = ""
                } else {
                    infoShort.text = "No cells detected"
                    infoProperties.text = ""
                    infoDistinguish.text = ""
                }
            }
        } catch (e: Exception) {
            Log.e("LiveCamera", "Detection error: ${e.message}", e)
        } finally {
            imageProxy.close()
            isProcessing.set(false)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        detector.close()
    }
}
