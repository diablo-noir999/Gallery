package org.fossify.gallery.helpers

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import java.io.File
import java.io.FileDescriptor

/**
 * Custom Glide decoder that extracts the FIRST frame (timestamp 0) of a video,
 * instead of Glide's default which seeks to the nearest keyframe.
 *
 * This ensures videos with a static cover frame at the start always show that
 * cover as the thumbnail.
 */
class VideoThumbnailDecoder : ResourceDecoder<Uri, Bitmap> {

    override fun handles(source: Uri, options: Options): Boolean {
        if (source.scheme != "file" && source.scheme != null) return false
        val path = source.path ?: return false
        val file = File(path)
        if (!file.exists()) return false
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
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null

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
                    val scaledBmp = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
                    if (scaledBmp !== bitmap) bitmap.recycle()
                    scaledBmp
                } else {
                    bitmap
                }
            } else {
                bitmap
            }

            // Wrap in a simple Resource that cleans up the bitmap on recycle
            object : Resource<Bitmap> {
                override fun get(): Bitmap = scaled
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
