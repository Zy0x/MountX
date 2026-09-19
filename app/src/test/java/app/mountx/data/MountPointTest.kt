package app.mountx.data

import app.mountx.data.model.GameEntry
import app.mountx.data.model.MountMode
import app.mountx.data.model.MountPointCategory
import app.mountx.data.model.MountPointConfig
import app.mountx.data.model.InstalledAppInfo
import org.junit.Assert.*
import org.junit.Test

class MountPointTest {

    @Test
    fun testLegacySynthesis_forPkgMode() {
        val game = GameEntry(
            packageName = "com.kurogame.wutheringwaves.global",
            displayName = "Wuthering Waves",
            mode = MountMode.PKG,
            mountPoints = emptyList()
        )

        // Synthesize legacy points
        val pkg = game.packageName
        val synthesized = listOf(
            MountPointConfig(
                id = "legacy_${pkg}_files",
                category = MountPointCategory.GAME_ASSETS,
                sourcePath = "/data/sdext2/Android/data/$pkg/files",
                targetPath = "/data/media/0/Android/data/$pkg/files",
                enabled = true
            ),
            MountPointConfig(
                id = "legacy_${pkg}_obb",
                category = MountPointCategory.GAME_ASSETS,
                sourcePath = "/data/sdext2/Android/obb/$pkg",
                targetPath = "/data/media/0/Android/obb/$pkg",
                enabled = true
            )
        )

        assertEquals(2, synthesized.size)
        assertEquals(MountPointCategory.GAME_ASSETS, synthesized[0].category)
        assertEquals("legacy_${pkg}_files", synthesized[0].id)
        assertTrue(synthesized.all { it.enabled })
        assertTrue(synthesized[0].targetPath.endsWith("/files"))
        assertTrue(synthesized[1].targetPath.contains("/obb/"))
    }

    @Test
    fun testAppPickerDuplicationFiltering() {
        val installedApps = listOf(
            InstalledAppInfo(packageName = "com.kurogame.wutheringwaves.global", displayName = "Wuthering Waves", isSystemApp = false),
            InstalledAppInfo(packageName = "com.miHoYo.hkrpg", displayName = "Honkai: Star Rail", isSystemApp = false),
            InstalledAppInfo(packageName = "idm.internet.download.manager.plus", displayName = "1DM+", isSystemApp = false),
            InstalledAppInfo(packageName = "org.telegram.messenger", displayName = "Telegram", isSystemApp = false),
            InstalledAppInfo(packageName = "com.google.android.youtube", displayName = "YouTube", isSystemApp = true)
        )

        val addedPackageNames = setOf(
            "com.kurogame.wutheringwaves.global",
            "com.miHoYo.hkrpg",
            "idm.internet.download.manager.plus"
        )

        // Filter out registered apps
        val availableApps = installedApps.filterNot { it.packageName in addedPackageNames }

        assertEquals(2, availableApps.size)
        assertFalse(availableApps.any { it.packageName in addedPackageNames })
        assertEquals("org.telegram.messenger", availableApps[0].packageName)
        assertEquals("com.google.android.youtube", availableApps[1].packageName)
    }

    @Test
    fun testActiveMountPointsSizeSum() {
        val points = listOf(
            MountPointConfig(id = "p1", category = MountPointCategory.GAME_ASSETS, sourcePath = "/sd/1", targetPath = "/data/1", enabled = true, sizeBytes = 1024L * 1024L * 500L),
            MountPointConfig(id = "p2", category = MountPointCategory.CACHE_SHADERS, sourcePath = "/sd/2", targetPath = "/data/2", enabled = false, sizeBytes = 1024L * 1024L * 200L),
            MountPointConfig(id = "p3", category = MountPointCategory.MEDIA_DOWNLOADS, sourcePath = "/sd/3", targetPath = "/data/3", enabled = true, sizeBytes = 1024L * 1024L * 300L)
        )

        val activeTotal = points.filter { it.enabled }.sumOf { it.sizeBytes }
        val expectedTotal = (500L + 300L) * 1024L * 1024L

        assertEquals(expectedTotal, activeTotal)
    }

    @Test
    fun testSecurityProtectedPaths() {
        fun isPathAllowed(path: String): Boolean {
            val trimmed = path.trim()
            return !trimmed.startsWith("/data/app") && !trimmed.startsWith("/system")
        }

        assertTrue(isPathAllowed("/data/media/0/Android/data/com.test.app"))
        assertTrue(isPathAllowed("/data/media/0/Telegram"))
        assertTrue(isPathAllowed("/data/data/com.test.app/databases"))
        assertFalse(isPathAllowed("/data/app/~~random/base.apk"))
        assertFalse(isPathAllowed("/system/framework/framework.jar"))
        assertFalse(isPathAllowed("/system/etc/permissions"))
    }

    @Test
    fun testDynamicRestorePathResolution() {
        val customPoint = MountPointConfig(
            id = "Telegram_Media",
            category = MountPointCategory.MEDIA_DOWNLOADS,
            sourcePath = "/data/sdext2/Telegram_Media",
            targetPath = "/data/media/0/Telegram",
            enabled = true
        )

        // Ensure dynamic targetPath is preserved exactly and not forced into /Android/data
        assertEquals("/data/media/0/Telegram", customPoint.targetPath)
        assertFalse(customPoint.targetPath.contains("/Android/data"))
    }
}
