package cn.nanoturtle.rootmys9280.ui.ambience

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.toSize
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.android.awaitFrame

/**
 * The header's living background.
 *
 * Draws whichever [AmbienceRenderer] is selected and hands it taps. It sits behind the header's
 * content, so the controls above keep working normally — only the open space responds. The frame
 * loop parks itself whenever the renderer reports nothing moving, and [AmbienceKind.None] skips the
 * loop entirely.
 *
 * Persistence of scale/speed/variant is injected via [settings]; the default keeps no state.
 */
@Composable
fun AmbientSurface(
    kind: AmbienceKind,
    tint: Color,
    modifier: Modifier = Modifier,
    settings: AmbienceSettings = AmbienceSettings.Ephemeral,
) {
    val renderer =
        remember(kind) {
            rendererFor(kind)?.apply {
                scale = settings.scale(kind.key)
                speed = settings.speed(kind.key)
                variant = settings.variant(kind.key)
            }
        } ?: return
    val haptics = LocalHapticFeedback.current

    var frame by remember(kind) { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(kind) {
        var last = 0L
        // Whether the "animations are off" static frame has been painted. It has to be remembered
        // rather than inferred from `last`, or the loop would repaint the same frame every vsync.
        var paintedStatic = false
        while (true) {
            // A hand-written frame loop is the one kind of animation AnimationSpec's duration scale
            // does not reach. The system's animator scale is in this effect's coroutine context, so
            // read it: at 0 (or "remove animations") the surface paints one static frame and holds
            // it, the same end state every Compose animation snaps to.
            val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
            if (scale == 0f) {
                // Wait for a real size before painting: the first tick can land before the Canvas has
                // reported one, and a frame drawn at zero size would be the only one there is.
                if (!paintedStatic && canvasSize != Size.Zero) {
                    renderer.update(16f, canvasSize)
                    frame += 1f
                    paintedStatic = true
                }
                awaitFrame()
                last = 0L
                continue
            }
            paintedStatic = false
            if (!renderer.isAnimating) {
                awaitFrame()
                last = 0L
                continue
            }
            withFrameNanos { now ->
                val dt = if (last == 0L) 16f else (now - last) / 1_000_000f
                last = now
                renderer.update(dt.coerceAtMost(64f), canvasSize)
                frame += 1f
            }
        }
    }

    val measurer = rememberTextMeasurer()
    (renderer as? MatrixRenderer)?.textMeasurer = measurer

    Canvas(
        modifier =
            modifier
                .clearAndSetSemantics {}
                .pointerInput(kind) {
                    detectTapGestures(
                        onDoubleTap =
                            if (!renderer.hasVariants) null
                            else {
                                {
                                    renderer.onDoubleTap()
                                    settings.setVariant(kind.key, renderer.variant)
                                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                                }
                            },
                        onTap = { offset ->
                            renderer.onTap(offset, size.toSize())
                            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                        },
                        onLongPress = { offset ->
                            renderer.onLongPress(offset, size.toSize())
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onPress = {
                            tryAwaitRelease()
                            renderer.onRelease()
                        },
                    )
                }
                .pointerInput(kind) {
                    detectTransformGestures(panZoomLock = false) { centroid, pan, gestureZoom, _ ->
                        if (gestureZoom != 1f) {
                            renderer.scale *= gestureZoom
                            settings.setScale(kind.key, renderer.scale)
                        }
                        if (pan != Offset.Zero) {
                            renderer.onDrag(pan, centroid, size.toSize())
                            settings.setSpeed(kind.key, renderer.speed)
                        }
                    }
                }
    ) {
        canvasSize = size
        @Suppress("UNUSED_EXPRESSION") frame
        with(renderer) { render(tint) }
    }
}
