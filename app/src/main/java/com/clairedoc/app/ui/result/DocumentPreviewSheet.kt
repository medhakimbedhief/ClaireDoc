package com.clairedoc.app.ui.result

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.clairedoc.app.data.model.SourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentPreviewSheet(
    imageUri: String,
    sourceType: SourceType?,
    sessionId: String,
    documentTitle: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val cachedFile = remember(sessionId) {
        File(context.filesDir, "qa_images/$sessionId.jpg")
    }

    // For PDFs where no JPEG cache exists: render first page on-demand
    var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPdf by remember { mutableStateOf(false) }

    val needsPdfRender = sourceType == SourceType.PDF &&
            (!cachedFile.exists() || cachedFile.length() == 0L)

    LaunchedEffect(imageUri, needsPdfRender) {
        if (needsPdfRender && imageUri.isNotBlank()) {
            isLoadingPdf = true
            pdfBitmap = withContext(Dispatchers.IO) {
                renderPdfFirstPage(context, Uri.parse(imageUri))
            }
            isLoadingPdf = false
        }
    }

    // Resolve the display URI: prefer cached JPEG, then raw URI for non-PDF
    val displayUri: Uri? = remember(sessionId, imageUri) {
        when {
            cachedFile.exists() && cachedFile.length() > 0L -> Uri.fromFile(cachedFile)
            sourceType != SourceType.PDF && imageUri.isNotBlank() -> Uri.parse(imageUri)
            else -> null  // PDF branch uses pdfBitmap instead
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            // ── Sheet header ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = documentTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close preview")
                }
            }

            // ── Image / PDF preview ───────────────────────────────────
            ZoomableDocumentImage(
                displayUri = displayUri,
                pdfBitmap = pdfBitmap,
                isLoading = isLoadingPdf,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 300.dp, max = 600.dp)
            )

            // ── Action buttons ────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { shareDocument(context, cachedFile, imageUri) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share")
                }
                OutlinedButton(
                    onClick = { openDocument(context, cachedFile, imageUri) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open")
                }
            }
        }
    }
}

// ── ZoomableDocumentImage ─────────────────────────────────────────────────────

@Composable
fun ZoomableDocumentImage(
    displayUri: Uri?,
    pdfBitmap: Bitmap?,
    isLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> CircularProgressIndicator(modifier = Modifier.size(40.dp))

            pdfBitmap != null -> Image(
                bitmap = pdfBitmap.asImageBitmap(),
                contentDescription = "Scanned document",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.8f, 5f)
                            offsetX += pan.x * scale
                            offsetY += pan.y * scale
                        }
                    }
            )

            displayUri != null -> AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(displayUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Scanned document",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.8f, 5f)
                            offsetX += pan.x * scale
                            offsetY += pan.y * scale
                        }
                    }
            )

            else -> Text(
                "Document image not available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Reset zoom button — visible only when user has zoomed in
        if (scale > 1.1f) {
            SmallFloatingActionButton(
                onClick = { scale = 1f; offsetX = 0f; offsetY = 0f },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Icon(
                    Icons.Default.ZoomOut,
                    contentDescription = "Reset zoom",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

/** Renders the first page of a PDF URI to a Bitmap. Returns null on any failure. */
private fun renderPdfFirstPage(context: Context, uri: Uri): Bitmap? =
    runCatching {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        pfd.use { parcelFd ->
            PdfRenderer(parcelFd).use { renderer ->
                if (renderer.pageCount == 0) return null
                renderer.openPage(0).use { page ->
                    val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    }.getOrNull()

/**
 * Resolves a shareable content URI.
 * Prefers the permanent JPEG cache (wrapped via FileProvider for filesDir access),
 * falls back to the raw content:// URI when no cache is available.
 */
private fun resolveShareUri(context: Context, cachedFile: File, imageUri: String): Uri? =
    when {
        cachedFile.exists() && cachedFile.length() > 0L ->
            runCatching {
                FileProvider.getUriForFile(
                    context, "${context.packageName}.fileprovider", cachedFile
                )
            }.getOrNull()
        imageUri.isNotBlank() -> Uri.parse(imageUri)
        else -> null
    }

private fun shareDocument(context: Context, cachedFile: File, imageUri: String) {
    val uri = resolveShareUri(context, cachedFile, imageUri) ?: run {
        Toast.makeText(context, "Document image no longer available", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, "Share document"))
    }.onFailure {
        Toast.makeText(context, "No app available to share this file", Toast.LENGTH_SHORT).show()
    }
}

private fun openDocument(context: Context, cachedFile: File, imageUri: String) {
    val uri = resolveShareUri(context, cachedFile, imageUri) ?: run {
        Toast.makeText(context, "Document image no longer available", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "image/jpeg")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "No app available to open this file", Toast.LENGTH_SHORT).show()
    }
}
