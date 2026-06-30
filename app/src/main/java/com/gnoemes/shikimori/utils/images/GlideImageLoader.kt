package com.gnoemes.shikimori.utils.images

import android.content.Context
import android.widget.ImageView
import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.Optional
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.request.target.BitmapImageViewTarget
import com.gnoemes.shikimori.R
import com.gnoemes.shikimori.data.graphql.AnimeByIdQuery
import com.gnoemes.shikimori.data.graphql.CharacterListQuery
import com.gnoemes.shikimori.data.graphql.MangaByIdQuery
import com.gnoemes.shikimori.data.graphql.PersonListQuery
import com.gnoemes.shikimori.data.graphql.rxQuery
import com.gnoemes.shikimori.entity.common.domain.Type
import com.gnoemes.shikimori.utils.appendHostIfNeed
import com.gnoemes.shikimori.utils.images.blur.BlurTransformation
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class GlideImageLoader @Inject constructor(
        private val context: Context,
        private val apolloClient: ApolloClient
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
        val cached = imageCache[key]
        if (cached != null) {
            loadListItemDirect(image, cached)
            return
        }
        val query = when (entityType) {
            "anime" -> AnimeByIdQuery(ids = entityId.toString())
            "manga" -> MangaByIdQuery(ids = entityId.toString())
            "character" -> CharacterListQuery(ids = Optional.presentIfNotNull(entityId.toString()))
            "person" -> PersonListQuery(ids = Optional.presentIfNotNull(listOf(entityId.toString())))
            else -> return
        }
        var disposable: Disposable? = null
        disposable = apolloClient.rxQuery(query)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ data ->
                    val gqlUrl = extractImageUrl(entityType, data)
                    imageCache[key] = gqlUrl
                    loadListItemDirect(image, gqlUrl ?: restFallback)
                }, {
                    imageCache[key] = null
                    loadListItemDirect(image, restFallback)
                })
        val previous = image.getTag(R.id.glide_disposable)
        if (previous is Disposable) previous.dispose()
        image.setTag(R.id.glide_disposable, disposable)
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
        Glide.with(image)
                .asBitmap()
                .dontAnimate()
                .error(R.drawable.missing_original)
                .centerCrop()
                .load(url)
                .override(image.measuredWidth / 2, image.measuredHeight / 2)
                .into(BitmapImageViewTarget(image).apply { waitForLayout() })
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractImageUrl(entityType: String, data: Any): String? {
        return when (entityType) {
            "anime" -> {
                val response = data as? AnimeByIdQuery.Data ?: return null
                response.animes.firstOrNull()?.poster?.originalUrl?.appendHostIfNeed()
            }
            "manga" -> {
                val response = data as? MangaByIdQuery.Data ?: return null
                response.mangas.firstOrNull()?.poster?.originalUrl?.appendHostIfNeed()
            }
            "character" -> {
                val response = data as? CharacterListQuery.Data ?: return null
                response.characters.firstOrNull()?.poster?.mainUrl?.appendHostIfNeed()
            }
            "person" -> {
                val response = data as? PersonListQuery.Data ?: return null
                response.people.firstOrNull()?.poster?.mainUrl?.appendHostIfNeed()
            }
            else -> null
        }
    }
}
