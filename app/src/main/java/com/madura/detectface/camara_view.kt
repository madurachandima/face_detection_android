package com.madura.detectface

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


@Composable
fun CameraPreviewScreen() {

    val lensFacing = CameraSelector.LENS_FACING_BACK
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val preview = Preview.Builder().build()
    val previewView = remember {
        PreviewView(context)
    }
    lateinit var imageAnalysis: ImageAnalysis
    val executor = remember { Executors.newSingleThreadExecutor() }


    val cameraxSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
    val imageCapture = remember {
        ImageCapture.Builder().build()
    }

    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraxSelector,
            preview,
            imageAnalysis,
            imageCapture
        )
        preview.setSurfaceProvider(previewView.surfaceProvider)

    }

    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { _ ->

            val faceDetectorOptions = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()

            val faceDetector = FaceDetection.getClient(faceDetectorOptions)

            imageAnalysis =
                ImageAnalysis.Builder().setTargetResolution(android.util.Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
                        it.setAnalyzer(executor) { imageProxy ->
                            detectFace(imageProxy, faceDetector, imageCapture, context)
                        }
                    }

            previewView


        }, modifier = Modifier.fillMaxSize()) {

        }
//        Button(onClick = { captureImage(imageCapture, context) }) {
//            Text(text = "Capture Image")
//        }
    }

}

@OptIn(ExperimentalGetImage::class)
private fun detectFace(
    imageProxy: ImageProxy,
    faceDetector: com.google.mlkit.vision.face.FaceDetector,
    imageCapture: ImageCapture,
    context: Context,
) {
    val mediaImage = imageProxy.image
    Log.d("detectFace", "mediaImage is null ${mediaImage == null}")
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        faceDetector.process(image).addOnSuccessListener { faces ->
            if (faces.isNotEmpty()) {
                captureImage(imageCapture, context,imageProxy)
            }
        }.addOnFailureListener { e ->
            Log.e("detectFace", "Face detection failed $e")
            imageProxy.close()
        }
            .addOnCompleteListener {
                imageProxy.close()
            }.addOnCanceledListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }

}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener(
                { continuation.resume(cameraProvider.get()) },
                ContextCompat.getMainExecutor(this)
            )
        }
    }

private fun captureImage(imageCapture: ImageCapture, context: Context,imageProxy: ImageProxy) {
    val name = "${java.sql.Timestamp(System.currentTimeMillis())}" + ".jpeg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
        }

    }

    val outputOptions = ImageCapture.OutputFileOptions.Builder(
        context.contentResolver,
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    ).build()


    imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                print("success")
                try {
                 val path =   outputFileResults.savedUri!!.path
                    Log.d("captureImage", "onImageSaved: $path")
                }catch (e:Exception){
                    e.printStackTrace()
                }

                imageProxy.close()
            }

            override fun onError(exception: ImageCaptureException) {
                println("Failed $exception")
            }

        })
}
