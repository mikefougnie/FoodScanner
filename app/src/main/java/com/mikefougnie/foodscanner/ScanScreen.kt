package com.mikefougnie.foodscanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class Mode { BARCODE, LABEL }

private suspend fun cameraProvider(ctx: Context): ProcessCameraProvider = suspendCoroutine { cont ->
    val f = ProcessCameraProvider.getInstance(ctx)
    f.addListener({ cont.resume(f.get()) }, ContextCompat.getMainExecutor(ctx))
}

@OptIn(ExperimentalGetImage::class, ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    paused: Boolean,
    onBarcode: (String) -> Unit,
    onReadLabel: () -> Unit,
    onLabelText: (String?) -> Unit,
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mode by rememberSaveable { mutableStateOf(Mode.BARCODE) }
    var torch by remember { mutableStateOf(false) }
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val barcodeScanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                .build()
        )
    }
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val pausedNow by rememberUpdatedState(paused)
    val onBarcodeNow by rememberUpdatedState(onBarcode)
    val onLabelTextNow by rememberUpdatedState(onLabelText)

    LaunchedEffect(mode, granted) {
        if (!granted) return@LaunchedEffect
        val provider = cameraProvider(ctx)
        provider.unbindAll()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val useCase: UseCase = if (mode == Mode.BARCODE) {
            ImageAnalysis.Builder()
                .setResolutionSelector(
                    ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)
                    ).build()
                )
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(analysisExecutor) { proxy: ImageProxy ->
                        val media = proxy.image
                        if (media == null || pausedNow) {
                            proxy.close()
                            return@setAnalyzer
                        }
                        barcodeScanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                            .addOnSuccessListener { codes ->
                                codes.firstNotNullOfOrNull { it.rawValue }?.let { code ->
                                    if (!pausedNow) onBarcodeNow(code)
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                }
        } else {
            imageCapture
        }
        camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, useCase)
    }

    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }

    DisposableEffect(Unit) {
        onDispose {
            try { ProcessCameraProvider.getInstance(ctx).get().unbindAll() } catch (_: Exception) {}
            analysisExecutor.shutdown()
        }
    }

    fun readLabel() {
        onReadLabel()
        imageCapture.takePicture(ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val media = image.image
                if (media == null) {
                    image.close(); onLabelTextNow(null); return
                }
                textRecognizer.process(InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees))
                    .addOnSuccessListener { onLabelTextNow(it.text) }
                    .addOnFailureListener { onLabelTextNow(null) }
                    .addOnCompleteListener { image.close() }
            }

            override fun onError(exception: ImageCaptureException) { onLabelTextNow(null) }
        })
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Clean Food Scanner", style = MaterialTheme.typography.headlineMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Mode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = mode == m, onClick = { mode = m },
                    shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                ) { Text(if (m == Mode.BARCODE) "Barcode" else "Ingredients label") }
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp))) {
            if (granted) {
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                ScanFrame(Modifier.fillMaxSize())
                Text(
                    if (mode == Mode.BARCODE) "Point at the barcode. It reads automatically."
                    else "Fill the frame with the ingredients list, then tap Read label.",
                    color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                )
            } else {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("The camera is needed to scan. You can also type ingredients on the Type tab.", textAlign = TextAlign.Center)
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(top = 12.dp)) {
                        Text("Allow camera")
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = torch, onClick = { torch = !torch }, label = { Text("Light") })
            if (mode == Mode.LABEL) {
                Button(onClick = ::readLabel, enabled = granted && !paused, modifier = Modifier.weight(1f).height(52.dp)) {
                    Text("Read label", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Corner brackets over the camera view. */
@Composable
private fun ScanFrame(modifier: Modifier) {
    Canvas(modifier) {
        val inset = 24.dp.toPx()
        val len = 36.dp.toPx()
        val stroke = 5.dp.toPx()
        val l = inset; val t = inset; val r = size.width - inset; val b = size.height - inset
        val c = Color.White
        fun seg(a: Offset, z: Offset) = drawLine(c, a, z, stroke, StrokeCap.Round)
        seg(Offset(l, t), Offset(l + len, t)); seg(Offset(l, t), Offset(l, t + len))
        seg(Offset(r, t), Offset(r - len, t)); seg(Offset(r, t), Offset(r, t + len))
        seg(Offset(l, b), Offset(l + len, b)); seg(Offset(l, b), Offset(l, b - len))
        seg(Offset(r, b), Offset(r - len, b)); seg(Offset(r, b), Offset(r, b - len))
    }
}
