package com.codenzi.payday

import androidx.annotation.StringRes

data class Achievement(
    val id: String,
    @StringRes val titleResId: Int,
    @StringRes val descriptionResId: Int,
    var isUnlocked: Boolean = false,
    val iconResId: Int
)