package com.firefly.feature

import android.content.Context
import android.content.res.Resources
import android.graphics.Rect
import android.widget.TextView
import androidx.annotation.WorkerThread
import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.ImageSize
import io.noties.markwon.image.ImageSizeResolver
import io.noties.markwon.image.glide.GlideImagesPlugin

object MarkdownRenderer {

    private var markwon: Markwon? = null

    @WorkerThread
    fun init(context: Context) {
        if (markwon != null) return

        val glide: RequestManager = Glide.with(context)

        markwon = Markwon.builder(context)
            .usePlugin(GlideImagesPlugin.create(glide))
            .usePlugin(CorePlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(HtmlPlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.imageSizeResolver(object : ImageSizeResolver() {
                        override fun resolveImageSize(drawable: AsyncDrawable): Rect {
                            val imageSize: ImageSize? = drawable.imageSize
                            val intrinsicWidth = drawable.intrinsicWidth
                            val intrinsicHeight = drawable.intrinsicHeight
                            val displayMetrics = Resources.getSystem().displayMetrics
                            val screenWidth = displayMetrics.widthPixels
                            val margin = 16.dp * 4
                            val maxWidth = screenWidth - margin

                            val ratio = if (intrinsicWidth > 0 && intrinsicHeight > 0) {
                                intrinsicHeight.toFloat() / intrinsicWidth
                            } else {
                                9f / 16f
                            }

                            val width = maxWidth
                            val height = (width * ratio).toInt()

                            return Rect(0, 0, width, height)
                        }
                    })
                }

                override fun configureTheme(builder: io.noties.markwon.core.MarkwonTheme.Builder) {
                    builder.headingBreakHeight(0)
                        .headingBreakColor(0x00000000.toInt())
                }
            })
            .build()
    }

    fun render(textView: TextView, markdown: String) {
        checkNotNull(markwon) { "MarkdownRenderer not initialized. Call init() first." }
        markwon?.setMarkdown(textView, markdown)
    }
}