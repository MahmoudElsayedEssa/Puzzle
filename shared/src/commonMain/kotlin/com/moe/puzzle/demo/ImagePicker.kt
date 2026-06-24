package com.moe.puzzle.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.ImageBitmap

/** Lets the user choose an image from their device to use as the puzzle picture. */
@Stable
interface ImagePicker {
    /** The most recently picked image, or null if the user hasn't chosen one. */
    val image: ImageBitmap?

    /** Launches the platform image picker. */
    fun pick()
}

/** Remembers a platform [ImagePicker]. Android shows the system photo picker; iOS is a stub for now. */
@Composable
expect fun rememberImagePicker(): ImagePicker
