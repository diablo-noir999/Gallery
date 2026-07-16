package org.fossify.gallery.helpers

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import java.io.FileDescriptor

/**
 * Custom Glide decoder that extracts the FIRST frame (timestamp 0) of a video,
 * instead of Glide's default VideoDecoder which uses OPTION_CLOSEST_SYNC
 * (seeks to nearest keyframe, skipping the cover frame).
 *
 * OPTION_CLOSEST decodes the actual pixel frame closest to timestamp 0,
 * ensuring the cover frame is always grabbed as the thumbnail.
 *
 * Registered as ParcelFileDescriptor → Bitmap so it intercepts Glide's actual
 * video loading pipeline (String → Uri → PFD → Bitmap).
 */
class VideoThumbnailDecoder : ResourceDecoder<ParcelFileDescriptor, Bitmap> {

    override fun handles(source: ParcelFileDescriptor, options: Options): Boolean {
        // We can't easily check file extension from a ParcelFileDescriptor,
        // but Glide only routes video files here, and we always want to override.
        return true
    }

    override fun decode(
        source: ParcelFileDescriptor,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val fd: FileDescriptor = source.fileDescriptor ?: return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(fd)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: return null

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
}
