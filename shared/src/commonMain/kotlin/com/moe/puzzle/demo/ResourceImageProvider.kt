package com.moe.puzzle.demo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import com.moe.puzzle.feature.puzzle.domain.ImageProvider
import org.jetbrains.compose.resources.imageResource
import puzzle.shared.generated.resources.Res
import puzzle.shared.generated.resources.puzzle_demo

/**
 * Demo [ImageProvider] backed by a compose resource.
 * [imageResource] is a @Composable that returns the decoded [ImageBitmap] from the bundled
 * puzzle_demo.png. Returns the same instance on recomposition (stable).
 *
 * Real implementations resolve auth'd CDN URLs supplied by the host — never networking here.
 */
@Composable
fun rememberDemoImageProvider(): ImageProvider {
    val bitmap: ImageBitmap = imageResource(Res.drawable.puzzle_demo)
    return remember(bitmap) { ImageProvider { bitmap } }
}
