package app.mountx.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High-performance 120 FPS application icon loader and memory cache for Mountify.
 * - 1024-slot in-memory LruCache for instant synchronous rendering
 * - Throttled background IO dispatcher (limitedParallelism) preventing Binder IPC congestion
 * - Proactive background pre-warming mechanism
 */
object AppIconManager {
    val iconCache = LruCache<String, ImageBitmap>(1024)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val iconDispatcher = Dispatchers.IO.limitedParallelism(3)

    fun getCached(packageName: String): ImageBitmap? = iconCache.get(packageName)

    fun loadIcon(context: Context, packageName: String): ImageBitmap? {
        val cached = iconCache.get(packageName)
        if (cached != null) return cached

        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            val drawable = appInfo.loadIcon(pm)
            val bitmap = drawableToImageBitmap(drawable)
            iconCache.put(packageName, bitmap)
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    suspend fun prewarmIcons(context: Context, packageNames: List<String>) {
        withContext(iconDispatcher) {
            val topList = packageNames.take(50)
            for (pkg in topList) {
                if (iconCache.get(pkg) == null) {
                    loadIcon(context, pkg)
                }
            }
            val remaining = packageNames.drop(50)
            if (remaining.isNotEmpty()) {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                for (pkg in remaining) {
                    if (iconCache.get(pkg) == null) {
                        loadIcon(context, pkg)
                    }
                }
            }
        }
    }

    private fun drawableToImageBitmap(drawable: Drawable): ImageBitmap {
        val targetSize = 96
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val orig = drawable.bitmap
            if (orig.width <= targetSize && orig.height <= targetSize && orig.config == Bitmap.Config.ARGB_8888) {
                return orig.asImageBitmap()
            }
            val scaled = Bitmap.createScaledBitmap(orig, targetSize, targetSize, true)
            return scaled.asImageBitmap()
        }

        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, targetSize, targetSize)
        drawable.draw(canvas)
        return bitmap.asImageBitmap()
    }
}

@Composable
fun AppIconImage(
    packageName: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp
) {
    val context = LocalContext.current
    var iconBitmap by remember(packageName) {
        mutableStateOf(AppIconManager.getCached(packageName))
    }

    LaunchedEffect(packageName) {
        if (iconBitmap == null) {
            val loaded = withContext(AppIconManager.iconDispatcher) {
                AppIconManager.loadIcon(context, packageName)
            }
            if (loaded != null) {
                iconBitmap = loaded
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.24f)),
        contentAlignment = Alignment.Center
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap!!,
                contentDescription = null,
                modifier = Modifier.matchParentSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(size * 0.58f)
                )
            }
        }
    }
}
