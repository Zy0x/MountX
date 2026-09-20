package app.mountx.data.model

/**
 * Strategy for resolving file and directory conflicts during data migration between
 * internal storage and external storage (MicroSD/USB-OTG).
 */
enum class ConflictStrategy {
    /**
     * Overwrite existing files at destination with source data (default & recommended).
     */
    OVERWRITE,

    /**
     * Merge files and only copy files that are newer or not present at destination.
     */
    MERGE,

    /**
     * Rename the existing destination directory to a backup before copying source data.
     */
    BACKUP_FIRST
}
