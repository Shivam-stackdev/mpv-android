package `is`.xyz.mpv

data class VideoItem(
    val id: Long,
    val title: String,
    val path: String,
    val durationMs: Long,
    val sizeBytes: Long
) {
    fun formattedDuration(): String {
        val totalSec = durationMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s)
        else "%d:%02d".format(m, s)
    }

    fun formattedSize(): String {
        val mb = sizeBytes / (1024.0 * 1024.0)
        return if (mb >= 1024) "%.1f GB".format(mb / 1024) else "%.1f MB".format(mb)
    }
}