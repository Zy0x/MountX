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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * High-performance 120 FPS application icon loader, disk cache, and memory cache for MountX.
 * - In-memory ApplicationInfo cache eliminates repeated Binder IPC calls to system_server
 * - Multi-tiered caching: L1 (1024-slot LruCache in RAM) -> L2 (local persistent PNG disk cache) -> PackageManager
 * - Balanced 3-worker IO dispatcher for multi-threaded decoding without thread pool saturation
 * - Background pre-warming mechanism with thread priority management
 */
object AppIconManager {
    private val appInfoCache = java.util.concurrent.ConcurrentHashMap<String, android.content.pm.ApplicationInfo>()
    val iconCache = LruCache<String, ImageBitmap>(1024)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val iconDispatcher = Dispatchers.IO.limitedParallelism(3)

    private fun getIconCacheDir(context: Context): File {
        val dir = File(context.cacheDir, "app_icons")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun registerAppInfos(apps: List<android.content.pm.ApplicationInfo>) {
        for (app in apps) {
            appInfoCache[app.packageName] = app
        }
    }

    fun getCached(packageName: String): ImageBitmap? = iconCache.get(packageName)

    fun loadIcon(context: Context, packageName: String): ImageBitmap? {
        // L1: Memory Cache
        val cached = iconCache.get(packageName)
        if (cached != null) return cached

        // L2: Persistent Disk Cache
        try {
            val diskFile = File(getIconCacheDir(context), "$packageName.png")
            if (diskFile.exists() && diskFile.length() > 0) {
                val diskBitmap = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (diskBitmap != null) {
                    val imageBitmap = diskBitmap.asImageBitmap()
                    iconCache.put(packageName, imageBitmap)
                    return imageBitmap
                }
            }
        } catch (_: Exception) {}

        // L3: PackageManager IPC
        return try {
            val pm = context.packageManager
            val appInfo = appInfoCache[packageName] ?: pm.getApplicationInfo(packageName, 0)
            val drawable = appInfo.loadIcon(pm)
            val bitmap = drawableToBitmap(drawable)
            val imageBitmap = bitmap.asImageBitmap()
            iconCache.put(packageName, imageBitmap)

            // Asynchronously / safely persist to disk cache
            try {
                val diskFile = File(getIconCacheDir(context), "$packageName.png")
                FileOutputStream(diskFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
            } catch (_: Exception) {}

            imageBitmap
        } catch (_: Exception) {
            null
        }
    }

    suspend fun prewarmIcons(context: Context, packageNames: List<String>) {
        withContext(iconDispatcher) {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            // Prewarm top 35 apps for instant buttery scroll
            for (pkg in packageNames.take(35)) {
                if (iconCache.get(pkg) == null) {
                    loadIcon(context, pkg)
                }
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        val targetSize = 96
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            val orig = drawable.bitmap
            if (orig.width <= targetSize && orig.height <= targetSize && orig.config == Bitmap.Config.ARGB_8888) {
                return orig
            }
            return Bitmap.createScaledBitmap(orig, targetSize, targetSize, true)
        }

        val bitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, targetSize, targetSize)
        drawable.draw(canvas)
        return bitmap
    }
}

/**
 * High-performance App Icon Composable.
 * Keys strictly on packageName to guarantee zero-recomposition during fling scrolls.
 */
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



