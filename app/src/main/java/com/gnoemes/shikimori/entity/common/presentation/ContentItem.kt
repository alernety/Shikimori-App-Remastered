package com.gnoemes.shikimori.entity.common.presentation

import com.gnoemes.shikimori.entity.common.domain.Image
import com.gnoemes.shikimori.entity.common.domain.Type

data class ContentItem(
        val name: String,
        val image: Image,
        val description: CharSequence?,
        val raw : Any,
        val entityType: Type? = null,
        val entityId: Long? = null
)