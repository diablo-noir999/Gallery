package org.fossify.gallery.svg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.PictureDrawable
import android.net.Uri

import com.bumptech.glide.Glide
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.caverock.androidsvg.SVG

import org.fossify.gallery.helpers.VideoThumbnailDecoder

import java.io.InputStream

@GlideModule
class SvgModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        // Prepend our custom video thumbnail decoder so it runs BEFORE Glide's default.
        // This ensures we always grab the frame at timestamp 0 (the cover frame).
        registry.prepend(
            Uri::class.java,
            Bitmap::class.java,
            VideoThumbnailDecoder()
        )

        registry.register(SVG::class.java, PictureDrawable::class.java, SvgDrawableTranscoder()).append(InputStream::class.java, SVG::class.java, SvgDecoder())
    }

    override fun isManifestParsingEnabled() = false
}
