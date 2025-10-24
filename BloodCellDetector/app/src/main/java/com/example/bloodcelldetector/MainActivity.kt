package com.example.bloodcelldetector

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.bloodcelldetector.ui.theme.BloodCellDetectorTheme

class MainActivity : ComponentActivity() {


    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startActivity(Intent(this, LiveCameraActivity::class.java))
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BloodCellDetectorTheme {
                MainScreen(
                    onLiveCameraClick = { checkCameraPermissionAndLaunch() },
                    onImageDetectionClick = {
                        startActivity(Intent(this, ImageDetectionActivity::class.java))
                    }
                )
            }
        }
    }


    private fun checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startActivity(Intent(this, LiveCameraActivity::class.java))
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }
}

@Composable
fun MainScreen(
    onLiveCameraClick: () -> Unit,
    onImageDetectionClick: () -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2C003E))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // 🔳 Logo placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(150.dp)
                    .background(Color(0xFF6A1B9A), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "LOGO", color = Color.White)
            }

            Spacer(modifier = Modifier.height(40.dp))

            // 🎥 Live Camera Button
            Button(
                onClick = onLiveCameraClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A)), // purple button
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = "Live Camera", color = Color.White)
            }

            Spacer(modifier = Modifier.height(24.dp))


            Button(
                onClick = onImageDetectionClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6A1B9A)), // purple button
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(60.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(text = "Upload", color = Color.White)
            }
        }
    }
}
