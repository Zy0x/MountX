package app.mountx.data.db

import androidx.room.TypeConverter
import app.mountx.data.model.MountPointCategory
import app.mountx.data.model.MountPointConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Room TypeConverter for List<MountPointConfig>.
 * Uses direct Android org.json parsing to guarantee zero reflection overhead
 * and complete immunity against R8/ProGuard generic signature stripping.
 */
class Converters {

    @TypeConverter
    fun fromMountPointsList(value: List<MountPointConfig>?): String {
        if (value.isNullOrEmpty()) return "[]"
        val array = JSONArray()
        for (item in value) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("category", item.category.name)
                put("sourcePath", item.sourcePath)
                put("targetPath", item.targetPath)
                put("enabled", item.enabled)
                put("isVirtualContainer", item.isVirtualContainer)
                if (item.containerImgPath != null) {
                    put("containerImgPath", item.containerImgPath)
                } else {
                    put("containerImgPath", JSONObject.NULL)
                }
                put("sizeBytes", item.sizeBytes)
                if (item.diskUuid != null) {
                    put("diskUuid", item.diskUuid)
                } else {
                    put("diskUuid", JSONObject.NULL)
                }
                if (item.label != null) {
                    put("label", item.label)
                } else {
                    put("label", JSONObject.NULL)
                }
                put("preserveMedia", item.preserveMedia)
            }
            array.put(obj)
        }
        return array.toString()
    }

    @TypeConverter
    fun toMountPointsList(value: String?): List<MountPointConfig> {
        if (value.isNullOrBlank() || value == "[]") return emptyList()
        val list = mutableListOf<MountPointConfig>()
        try {
            val array = JSONArray(value)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val catName = obj.optString("category", MountPointCategory.GAME_ASSETS.name)
                val cat = try {
                    MountPointCategory.valueOf(catName)
                } catch (_: Exception) {
                    MountPointCategory.GAME_ASSETS
                }
                val config = MountPointConfig(
                    id = obj.optString("id", "mp_$i"),
                    category = cat,
                    sourcePath = obj.optString("sourcePath", ""),
                    targetPath = obj.optString("targetPath", ""),
                    enabled = obj.optBoolean("enabled", true),
                    isVirtualContainer = obj.optBoolean("isVirtualContainer", false),
                    containerImgPath = if (obj.has("containerImgPath") && !obj.isNull("containerImgPath")) obj.getString("containerImgPath") else null,
                    sizeBytes = obj.optLong("sizeBytes", 0L),
                    diskUuid = if (obj.has("diskUuid") && !obj.isNull("diskUuid")) obj.getString("diskUuid") else null,
                    label = if (obj.has("label") && !obj.isNull("label")) obj.getString("label") else null,
                    preserveMedia = obj.optBoolean("preserveMedia", false)
                )
                list.add(config.copy(category = config.resolveCategory()))
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return list
    }
}
