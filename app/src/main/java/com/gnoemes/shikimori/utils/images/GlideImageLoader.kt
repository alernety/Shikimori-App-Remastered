package com.gnoemes.shikimori.utils.images

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.bumptech.glide.request.target.Target
import com.gnoemes.shikimori.R
import com.gnoemes.shikimori.entity.common.domain.Type
import com.gnoemes.shikimori.utils.images.blur.BlurTransformation
import javax.inject.Inject

class GlideImageLoader @Inject constructor(
        private val context: Context,
        private val batcher: ImageQueryBatcher
) : ImageLoader {

    companion object {
        private val imageCache = HashMap<Pair<String, Long>, String?>()

        fun entityType(type: Type): String = when (type) {
            Type.RANOBE -> "manga"
            else -> type.name.lowercase()
        }
    }

    val glide = Glide.with(context)

    override fun setCircleImage(image: ImageView, url: String?) {
        Glide.with(image)
                .asDrawable()
                .load(url)
                .dontAnimate()
                .into(image)
    }

    override fun setImageWithPlaceHolder(image: ImageView, url: String?, entityType: String?, entityId: Long?) {
        if (entityType != null && entityId != null) {
            resolveAndLoad(image, url, entityType, entityId)
        } else {
            loadDirect(image, url)
        }
    }

    override fun setImageListItem(image: ImageView, url: String?, entityType: String?, entityId: Long?) {
        if (entityType != null && entityId != null) {
            resolveAndLoad(image, url, entityType, entityId)
        } else {
            loadListItemDirect(image, url)
        }
    }

    override fun setBlurredImage(image: ImageView, url: String?, radius : Int, sampling : Int) {
        Glide.with(image)
                .asBitmap()
                .load(url)
                .transform(BlurTransformation(radius, sampling))
                .priority(Priority.HIGH)
                .dontAnimate()
                .into(image)
    }

    override fun setBlurredCircleImage(image: ImageView, url: String?, radius: Int, sampling: Int) {
        Glide.with(image)
                .asDrawable()
                .load(url)
                .transform(BlurTransformation(radius, sampling))
                .dontAnimate()
                .into(image)
    }

    override fun clearImage(image: ImageView) {
        glide.clear(image)
    }

    private fun resolveAndLoad(image: ImageView, restFallback: String?, entityType: String, entityId: Long) {
        val key = Pair(entityType, entityId)
        image.setTag(R.id.glide_entity_tag, entityId)
        if (imageCache.containsKey(key)) {
            val cached = imageCache[key]
            if (cached != null) {
                loadListItemDirect(image, cached)
            } else {
                loadListItemDirect(image, restFallback)
            }
            return
        }

        // Load the REST URL immediately so the user sees something
        if (restFallback != null) {
            loadListItemDirect(image, restFallback)
        }

        // Then try to upgrade to a higher-quality URL via GraphQL batcher
        batcher.resolve(entityType, entityId) { gqlUrl ->
            // Guard: skip if the ImageView was recycled and repurposed for a different entity
            if (image.getTag(R.id.glide_entity_tag) != entityId) return@resolve
            if (gqlUrl != null) {
                imageCache[key] = gqlUrl
                // Only upgrade if the batcher found a different (better) URL
                if (gqlUrl != restFallback) {
                    loadListItemDirect(image, gqlUrl)
                }
            }
        }
    }

    private fun loadDirect(image: ImageView, url: String?) {
        Glide.with(image)
                .asBitmap()
                .load(url)
                .error(R.drawable.missing_original)
                .centerCrop()
                .into(image)
    }

    private fun loadListItemDirect(image: ImageView, url: String?) {
        val overrideWidth = if (image.measuredWidth > 0) image.measuredWidth / 2 else Target.SIZE_ORIGINAL
        val overrideHeight = if (image.measuredHeight > 0) image.measuredHeight / 2 else Target.SIZE_ORIGINAL
        Glide.with(image)
                .asBitmap()
                .dontAnimate()
                .error(R.drawable.missing_original)
                .centerCrop()
                .load(url)
                .override(overrideWidth, overrideHeight)
                .into(BitmapImageViewTarget(image).apply { waitForLayout() })
    }
}
