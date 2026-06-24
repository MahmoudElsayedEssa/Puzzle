package com.moe.puzzle.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap

// iOS stub — device image picking is Android-only for now; the demo falls back to the bundled image.
@Composable
actual fun rememberImagePicker(): ImagePicker = remember {
    object : ImagePicker {
        override val image: ImageBitmap? = null
        override fun pick() {}
    }
}
