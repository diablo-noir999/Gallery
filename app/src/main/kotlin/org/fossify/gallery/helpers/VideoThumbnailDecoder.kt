package org.fossify.gallery.helpers

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import java.io.File

/**
 * Custom Glide decoder that extracts the FIRST frame (timestamp 0) of a video,
 * instead of Glide's default which seeks to the nearest keyframe (OPTION_CLOSEST_SYNC).
 *
 * OPTION_CLOSEST decodes the actual pixel frame closest to the given timestamp,
 * ensuring the cover frame is always grabbed as the thumbnail.
 */
class VideoThumbnailDecoder : ResourceDecoder<Uri, Bitmap> {

    override fun handles(source: Uri, options: Options): Boolean {
        if (source.scheme != "file" && source.scheme != null) return false
        val path = source.path ?: return false
        if (!File(path).exists()) return false
        val lower = path.lowercase()
        return VIDEO_EXTENSIONS.any { lower.endsWith(it) }
    }

    override fun decode(
        source: Uri,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val path = source.path ?: return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            // OPTION_CLOSEST decodes the actual frame closest to timestamp 0,
            // instead of OPTION_CLOSEST_SYNC which jumps to the nearest keyframe (I-frame).
            // This ensures the cover frame is grabbed even if it's not on a keyframe boundary.
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: return null

            // Scale down if needed to avoid OOM
            val scaled = if (width > 0 && height > 0) {
                val ratio = minOf(
                    width.toFloat() / bitmap.width,
                    height.toFloat() / bitmap.height,
                    1f
                )
                if (ratio < 1f) {
                    val newW = (bitmap.width * ratio).toInt()
                    val newH = (bitmap.height * ratio).toInt()
                    Bitmap.createScaledBitmap(bitmap, newW, newH, true).also {
                        if (it !== bitmap) bitmap.recycle()
                    }
                } else {
                    bitmap
                }
            } else {
                bitmap
            }

            object : Resource<Bitmap> {
                override fun get(): Bitmap = scaled
                override fun getResourceClass(): Class<Bitmap> = Bitmap::class.java
                override fun getSize(): Int = scaled.byteCount
                override fun recycle() {
                    if (!scaled.isRecycled) scaled.recycle()
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    companion object {
        private val VIDEO_EXTENSIONS = listOf(
            ".mp4", ".mkv", ".avi", ".mov", ".webm", ".flv", ".wmv",
            ".3gp", ".3g2", ".m4v", ".ts", ".m2ts", ".mts",
            ".vob", ".ogv", ".rm", ".rmvb", ".asf", ".divx"
        )
    }
}
