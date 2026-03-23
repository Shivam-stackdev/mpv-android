package `is`.xyz.mpv

import android.content.Context
import android.preference.PreferenceManager

object VideoHistory {

    private const val PREFIX = "vh_pos_"

    // key: sanitized path, value: position in seconds (Long)
    private fun key(path: String) = PREFIX + path.hashCode().toString()

    fun savePosition(context: Context, path: String, positionSec: Long) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putLong(key(path), positionSec)
            .apply()
    }

    fun getPosition(context: Context, path: String): Long {
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getLong(key(path), 0L)
    }

    fun clearPosition(context: Context, path: String) {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .remove(key(path))
            .apply()
    }
}