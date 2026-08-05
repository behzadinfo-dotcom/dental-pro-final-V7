package com.example.ui.screens
import androidx.core.content.FileProvider
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp


fun saveBitmapToLocalFile(context: Context, bitmap: Bitmap, maxDim: Int = 800): String {
    val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val (w, h) = if (ratio > 1) {
            Pair(maxDim, (maxDim / ratio).toInt())
        } else {
            Pair((maxDim * ratio).toInt(), maxDim)
        }
        Bitmap.createScaledBitmap(bitmap, w, h, true)
    } else {
        bitmap
    }
    
    val dir = File(context.filesDir, "images")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "${UUID.randomUUID()}.jpg")
    
    FileOutputStream(file).use { out ->
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
    }
    return file.absolutePath
}

fun saveUriToLocalFile(context: Context, uri: Uri): String? {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        if (inputStream != null) {
            val dir = File(context.filesDir, "images")
            if (!dir.exists()) dir.mkdirs()
            val ext = context.contentResolver.getType(uri)?.split("/")?.lastOrNull() ?: "jpg"
            val file = File(dir, "${UUID.randomUUID()}.$ext")
            FileOutputStream(file).use { out ->
                val buffer = ByteArray(4096)
                var read: Int
                while (inputStream.read(buffer).also { read = it } != -1) {
                    out.write(buffer, 0, read)
                }
                out.flush()
            }
            inputStream.close()
            file.absolutePath
        } else null
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun base64ToBitmap(base64Str: String): Bitmap? {
    if (base64Str.startsWith("/")) {
        return try {
            BitmapFactory.decodeFile(base64Str)
        } catch(e: Exception) { null }
    }
    return try {
        val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        null
    }
}

@Composable
fun SmartImage(model: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    if (model.startsWith("/") || model.startsWith("file://") || model.startsWith("content://")) {
        AsyncImage(
            model = model,
            contentDescription = "Photo",
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        val bitmap = remember(model) { base64ToBitmap(model) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Photo",
                modifier = modifier,
                contentScale = contentScale
            )
        }
    }
}


fun getTempCameraUri(context: Context): Pair<Uri, String> {
    val dir = File(context.cacheDir, "camera")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "temp_${UUID.randomUUID()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return Pair(uri, file.absolutePath)
}

/**
 * راه‌انداز امن دوربین: از کرش برنامه هنگام باز کردن دوربین سیستم جلوگیری می‌کند.
 * از آنجا که مجوز android.permission.CAMERA در Manifest اعلام شده، طبق مستندات رسمی اندروید
 * (developer.android.com/training/camera/photobasics) اگر این مجوز پیش از فراخوانی Intent دوربین
 * به‌صورت runtime گرفته نشود، دوربین سیستم (یا اندروید) با SecurityException کرش می‌کند.
 * این تابع همیشه ابتدا مجوز را بررسی/درخواست می‌کند و فقط پس از تایید، دوربین را باز می‌کند.
 */
@Composable
fun rememberSafeCameraLauncher(
    context: Context,
    onImageCaptured: (String) -> Unit
): () -> Unit {
    var pendingTempPath by remember { mutableStateOf<String?>(null) }
    var launchAfterPermission by remember { mutableStateOf(false) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && pendingTempPath != null) {
            onImageCaptured(pendingTempPath!!)
        }
        pendingTempPath = null
    }

    fun launchCameraNow() {
        val (uri, path) = getTempCameraUri(context)
        pendingTempPath = path
        cameraLauncher.launch(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && launchAfterPermission) {
            launchCameraNow()
        }
        launchAfterPermission = false
    }

    return {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            launchCameraNow()
        } else {
            launchAfterPermission = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}


@Composable
fun FullScreenImageViewer(imageStr: String, onDismiss: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isMarkupMode by remember { mutableStateOf(false) }
    var paths by remember { mutableStateOf(listOf<Path>()) }
    var currentPath by remember { mutableStateOf<Path?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isMarkupMode) {
                        if (!isMarkupMode) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                if (scale > 1f) {
                                    offset += pan
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        } else {
                            detectDragGestures(
                                onDragStart = { currentPath = Path().apply { moveTo(it.x, it.y) } },
                                onDrag = { change, _ -> currentPath?.lineTo(change.position.x, change.position.y) },
                                onDragEnd = {
                                    currentPath?.let { paths = paths + it }
                                    currentPath = null
                                }
                            )
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            ) {
                SmartImage(
                    model = imageStr,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                
                Canvas(modifier = Modifier.fillMaxSize()) {
                    paths.forEach { drawPath(it, color = Color.Red, style = Stroke(width = 8f)) }
                    currentPath?.let { drawPath(it, color = Color.Red, style = Stroke(width = 8f)) }
                }
            }
            
            Row(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                IconButton(onClick = { isMarkupMode = !isMarkupMode }) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = if (isMarkupMode) Color.Red else Color.White)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}
