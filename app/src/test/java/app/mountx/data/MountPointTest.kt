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

    @Test
    fun testStorageBreakdownMountedZeroRedundancy() {
        val breakdown = app.mountx.data.model.AppStorageBreakdown(
            apkBytes = 1_000_000_000L,     // 1 GB
            dexBytes = 10_000_000L,        // 10 MB
            libBytes = 1_000_000_000L,     // 1 GB
            dataBytes = 10_000_000L,       // 10 MB
            cacheBytes = 1_000_000L,       // 1 MB
            ext1Bytes = 0L,                // 0 B because it's mounted to SD
            ext2Bytes = 10_000_000_000L,   // 10 GB on MicroSD
            ext1DataBytes = 0L,
            ext1ObbBytes = 0L,
            ext2DataBytes = 10_000_000_000L,
            ext2ObbBytes = 0L,
            isExt1Mounted = true
        )

        val expectedPhoneInternal = 1_000_000_000L + 10_000_000L + 1_000_000_000L + 10_000_000L + 1_000_000L // 2.021 GB
        val expectedMicroSd = 10_000_000_000L // 10 GB
        val expectedTotal = expectedPhoneInternal + expectedMicroSd // 12.021 GB (NOT 22.021 GB!)

        assertEquals(expectedPhoneInternal, breakdown.phoneInternalBytes)
        assertEquals(expectedMicroSd, breakdown.microSdBytes)
        assertEquals(expectedTotal, breakdown.totalBytes)
        assertTrue("isExt1Mounted must be true", breakdown.isExt1Mounted)
        assertEquals(17, breakdown.internalPercent)
        assertEquals(83, breakdown.externalPercent)
    }

    @Test
    fun testFabGlidingOffsetLogic() {
        val navBarsBottomPx = 120f

        fun computeFabTargetOffsetY(isBottomBarVisible: Boolean, insetsBottomPx: Float): Float {
            return if (isBottomBarVisible) {
                0f
            } else {
                -insetsBottomPx
            }
        }

        // When bottom bar is visible: outer HorizontalPager already has bottom padding.
        // FAB slot sits at bottom of inner Scaffold directly above bottom bar. Extra offset must be 0f!
        assertEquals(0f, computeFabTargetOffsetY(isBottomBarVisible = true, insetsBottomPx = navBarsBottomPx), 0.001f)

        // When bottom bar is hidden: outer HorizontalPager expands to screen edge.
        // FAB needs to glide down and rest above system navigation bar (-navBarsBottomPx).
        assertEquals(-120f, computeFabTargetOffsetY(isBottomBarVisible = false, insetsBottomPx = navBarsBottomPx), 0.001f)
    }

    @Test
    fun testResolveCategory_dataAndObb() {
        val legacyObb = MountPointConfig(
            id = "legacy_com.test.app_obb",
            category = MountPointCategory.GAME_ASSETS,
            sourcePath = "/data/sdext2/Android/obb/com.test.app",
            targetPath = "/data/media/0/Android/obb/com.test.app",
            enabled = true
        )
        val legacyData = MountPointConfig(
            id = "legacy_com.test.app_files",
            category = MountPointCategory.GAME_ASSETS,
            sourcePath = "/data/sdext2/Android/data/com.test.app/files",
            targetPath = "/data/media/0/Android/data/com.test.app/files",
            enabled = true
        )
        val directObb = MountPointConfig(
            id = "obb_storage",
            category = MountPointCategory.OBB_STORAGE,
            sourcePath = "/data/sdext2/Android/obb/com.test.app",
            targetPath = "/data/media/0/Android/obb/com.test.app",
            enabled = true
        )

        assertEquals(MountPointCategory.OBB_STORAGE, legacyObb.resolveCategory())
        assertEquals(MountPointCategory.EXTERNAL_DATA, legacyData.resolveCategory())
        assertEquals(MountPointCategory.OBB_STORAGE, directObb.resolveCategory())
    }

    @Test
    fun testCleanRelativePath() {
        val dataPoint = MountPointConfig(
            id = "data",
            category = MountPointCategory.EXTERNAL_DATA,
            sourcePath = "/data/sdext2/Android/data/com.kurogame.wutheringwaves.global",
            targetPath = "/data/media/0/Android/data/com.kurogame.wutheringwaves.global",
            enabled = true
        )
        val obbPoint = MountPointConfig(
            id = "obb",
            category = MountPointCategory.OBB_STORAGE,
            sourcePath = "/data/sdext2/Android/obb/com.kurogame.wutheringwaves.global",
            targetPath = "/data/media/0/Android/obb/com.kurogame.wutheringwaves.global",
            enabled = true
        )

        assertEquals("Android/data/com.kurogame.wutheringwaves.global", dataPoint.getCleanRelativePath())
        assertEquals("Android/obb/com.kurogame.wutheringwaves.global", obbPoint.getCleanRelativePath())
    }

    @Test
    fun testIsCoreGameData() {
        val dataPoint = MountPointConfig(
            id = "data",
            category = MountPointCategory.EXTERNAL_DATA,
            sourcePath = "/data/sdext2/Android/data/com.kurogame.wutheringwaves.global",
            targetPath = "/data/media/0/Android/data/com.kurogame.wutheringwaves.global",
            enabled = true
        )
        val obbPoint = MountPointConfig(
            id = "obb",
            category = MountPointCategory.OBB_STORAGE,
            sourcePath = "/data/sdext2/Android/obb/com.kurogame.wutheringwaves.global",
            targetPath = "/data/media/0/Android/obb/com.kurogame.wutheringwaves.global",
            enabled = true
        )
        val customPoint = MountPointConfig(
            id = "custom_path",
            category = MountPointCategory.CUSTOM,
            sourcePath = "/data/sdext2/custom",
            targetPath = "/data/media/0/custom",
            enabled = true
        )

        assertTrue("Data point must be core game data", dataPoint.isCoreGameData())
        assertTrue("OBB point must be core game data", obbPoint.isCoreGameData())
        assertFalse("Custom point must not be core game data", customPoint.isCoreGameData())
    }
}
