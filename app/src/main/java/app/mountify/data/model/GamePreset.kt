package app.mountify.data.model

/**
 * Metadata for intelligent mount mode recommendation for popular Android games.
 */
data class GamePreset(
    val packagePattern: String,
    val recommendedMode: MountMode,
    val reason: String
)

object SmartGamePresets {
    private val PRESETS = listOf(
        // HoYoverse titles (require FILES mode to avoid auth/db corruption)
        GamePreset("com.miHoYo.GenshinImpact", MountMode.FILES, "Preserves internal SQLite database while offloading game assets"),
        GamePreset("com.mihoyo.genshinimpact", MountMode.FILES, "Preserves internal SQLite database while offloading game assets"),
        GamePreset("com.HoYoverse.hkrpgoversea", MountMode.FILES, "Preserves internal database while offloading Star Rail assets"),
        GamePreset("com.HoYoverse.Nap", MountMode.FILES, "Preserves internal database while offloading ZZZ assets"),
        GamePreset("com.miHoYo.bh3global", MountMode.FILES, "Preserves internal database while offloading Honkai 3rd assets"),
        GamePreset("com.miHoYo.bh3oversea", MountMode.FILES, "Preserves internal database while offloading Honkai 3rd assets"),

        // Kuro Games (Full PKG mount)
        GamePreset("com.kurogame.wutheringwaves", MountMode.PKG, "Full package mount streams Unreal Engine assets seamlessly"),
        GamePreset("com.kurogame.kjssm", MountMode.PKG, "Full package mount streams Unreal Engine assets seamlessly"),
        GamePreset("com.kurogame.haru", MountMode.PKG, "Full package mount for Punishing Gray Raven data"),

        // Battle Royale & Shooters
        GamePreset("com.tencent.ig", MountMode.PKG, "Full package mount offloads maps and HD skin packs"),
        GamePreset("com.pubg.krmobile", MountMode.PKG, "Full package mount offloads maps and HD skin packs"),
        GamePreset("com.vng.pubgmobile", MountMode.PKG, "Full package mount offloads maps and HD skin packs"),
        GamePreset("com.activision.callofduty.shooter", MountMode.PKG, "Full package mount offloads high-res game textures"),
        GamePreset("com.garena.game.codm", MountMode.PKG, "Full package mount offloads high-res game textures"),
        GamePreset("com.ea.gp.apexmobile", MountMode.PKG, "Full package mount for Apex Mobile assets"),

        // MOBAs & Common Titles
        GamePreset("com.mobile.legends", MountMode.PKG, "Full package mount offloads custom hero assets and sound packs"),
        GamePreset("com.dts.freefireth", MountMode.PKG, "Full package mount offloads map extensions"),
        GamePreset("com.dts.freefiremax", MountMode.PKG, "Full package mount offloads MAX HD textures")
    )

    fun findPreset(packageName: String): GamePreset? {
        val lower = packageName.lowercase()
        return PRESETS.firstOrNull { lower.contains(it.packagePattern.lowercase()) }
    }
}
