package com.zirakusa.camara

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.zirakusa.camara.data.Mode
import com.zirakusa.camara.data.SharedValues
import com.zirakusa.camara.data.Shot
import com.zirakusa.camara.data.Shots
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The line of «Hoy» for the family summary: how many photos and videos were made today. Pure. */
object SummaryLines {
    fun lines(shots: List<Shot>, today: LocalDate, zone: ZoneId, line: (photos: Int, videos: Int) -> String): List<String> {
        val todays = shots.filter { Instant.ofEpochMilli(it.at).atZone(zone).toLocalDate() == today }
        return if (todays.isEmpty()) emptyList() else listOf(line(todays.count { it.mode == Mode.PHOTO }, todays.count { it.mode == Mode.VIDEO }))
    }
}

/** The family summary (see Inicio's FAMILIA.md) for Cámara's card in «Hoy». Only apps signed by the same developer may ask. */
class SummaryProvider : ContentProvider() {
    override fun onCreate() = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != "summary") return null
        val context = context ?: return null
        val caller = callingPackage ?: return null
        if (context.packageManager.checkSignatures(context.packageName, caller) != PackageManager.SIGNATURE_MATCH) return null
        val r = context.resources
        val lines = SummaryLines.lines(Shots(SharedValues(context)).list(), LocalDate.now(), ZoneId.systemDefault()) { p, v ->
            listOfNotNull(if (p > 0) r.getQuantityString(R.plurals.photos, p, p) else null, if (v > 0) r.getQuantityString(R.plurals.videos, v, v) else null)
                .joinToString(" · ")
        }
        return Bundle().apply {
            putString("title", context.getString(R.string.summary_title))
            putStringArrayList("lines", ArrayList(lines))
            putString("empty", context.getString(R.string.summary_empty))
        }
    }
    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0
}
