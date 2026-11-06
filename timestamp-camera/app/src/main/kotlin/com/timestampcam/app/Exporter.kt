package com.timestampcam.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.timestampcam.core.Session
import com.timestampcam.core.SrtWriter
import java.io.File

object Exporter {
    fun shareVideo(context: Context, uri: Uri) {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "分享视频"))
    }

    fun exportSrt(context: Context, session: Session): String {
        if (session.cues.isEmpty()) return "时间线还没有字幕"
        val text = SrtWriter.write(session.cues, session.durationMs.coerceAtLeast(1L))
        val name = session.displayName.removeSuffix(".mp4") + ".srt"
        val cache = File(context.cacheDir, name)
        cache.writeText(text)
        val saved = saveToDownloads(context, name, text)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", cache)
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "application/x-subrip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(share, "导出字幕"))
        return if (saved) "字幕已保存到下载目录，也可以从分享面板发出" else "请选择把字幕保存到哪里"
    }

    private fun saveToDownloads(context: Context, name: String, text: String): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/x-subrip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TimestampCam")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return false
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
            } ?: return false
            true
        } catch (error: Exception) {
            Log.w("Exporter", "save srt", error)
            false
        }
    }
}
