package com.example.ui.onboarding

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.pill
import androidx.graphics.shapes.star
import kotlin.math.min

/**
 * Material 3 Expressive Shape Morphing Pattern Background.
 *
 * Renders morphing [RoundedPolygon] shapes via [Morph] as vivid filled + outlined blobs.
 *
 * Root cause of previous invisibility: the old code used withTransform { scale(size, size) }
 * and then drew Brush.radialGradient(radius = 1.2f) and Stroke(width = 0.004f) INSIDE that
 * transform, so those values were in normalised [-1,1] units — effectively sub-pixel on screen.
 * This version builds a Matrix manually, transforms a copy of the Path into screen space,
 * then draws with solid fill colours at 38-55% alpha and 2-3 px strokes.
 */
@Composable
fun ShapeMorphingPatternBackground(
    modifier: Modifier = Modifier,
    currentStep: Int = 0
) {
    val colorScheme = MaterialTheme.colorScheme

    val shapes = remember {
        listOf(
            RoundedPolygon.circle(numVertices = 12),
            RoundedPolygon.star(numVerticesPerRadius = 12, innerRadius = 0.92f, rounding = CornerRounding(0.18f)),
            RoundedPolygon.star(numVerticesPerRadius = 4,  innerRadius = 0.38f, rounding = CornerRounding(0.32f)),
            RoundedPolygon.star(numVerticesPerRadius = 8,  innerRadius = 0.72f, rounding = CornerRounding(0.22f)),
            RoundedPolygon.pill(width = 1.5f, height = 1f),
            RoundedPolygon.star(numVerticesPerRadius = 3,  innerRadius = 0.84f, rounding = CornerRounding(0.42f))
        )
    }

    val morphs = remember(shapes) {
        shapes.indices.map { i -> Morph(shapes[i], shapes[(i + 1) % shapes.size]) }
    }

    val path1 = remember { Path() }
    val path2 = remember { Path() }
    val path3 = remember { Path() }

    val transition = rememberInfiniteTransition(label = "morph_bg")

    val morphPhase by transition.animateFloat(
        initialValue = 0f, targetValue = morphs.size.toFloat(),
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase"
    )
    val rot1 by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(48000, easing = LinearEasing), RepeatMode.Restart),
        label = "rot1"
    )
    val rot2 by transition.animateFloat(
        initialValue = 360f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(36000, easing = LinearEasing), RepeatMode.Restart),
        label = "rot2"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.95f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val stepShift by animateFloatAsState(
        targetValue = if (currentStep == 0) 0f else 1f,
        animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
        label = "step"
    )

    val primary          = colorScheme.primary
    val primaryContainer = colorScheme.primaryContainer
    val secondary          = colorScheme.secondary
    val secondaryContainer = colorScheme.secondaryContainer
    val tertiary          = colorScheme.tertiary
    val tertiaryContainer = colorScheme.tertiaryContainer
    val outlineVariant    = colorScheme.outlineVariant

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val base  = min(w, h)
        val count = morphs.size

        drawDotGrid(outlineVariant.copy(alpha = 0.18f), spacing = 36f)

        // Layer 1 — large top-right blob
        val i1 = morphPhase.toInt() % count
        val p1 = (morphPhase - morphPhase.toInt()).coerceIn(0f, 1f)
        morphs[i1].toComposePath(p1, path1)
        drawMorphBlob(path1, w * (0.82f - 0.10f * stepShift), h * (0.18f + 0.06f * stepShift),
            base * 0.72f * pulse, rot1, primaryContainer.copy(alpha = 0.55f), primary.copy(alpha = 0.30f), 3f)

        // Layer 2 — large bottom-left blob
        val op2 = (morphPhase + count * 0.5f) % count
        val i2  = op2.toInt() % count
        val p2  = (op2 - op2.toInt()).coerceIn(0f, 1f)
        morphs[i2].toComposePath(p2, path2)
        drawMorphBlob(path2, w * (0.14f + 0.08f * stepShift), h * (0.82f - 0.08f * stepShift),
            base * 0.80f * (2f - pulse), rot2, secondaryContainer.copy(alpha = 0.48f), secondary.copy(alpha = 0.24f), 2.5f)

        // Layer 3 — mid-size floating shape
        val op3 = (morphPhase + count * 0.25f) % count
        val i3  = op3.toInt() % count
        val p3  = (op3 - op3.toInt()).coerceIn(0f, 1f)
        morphs[i3].toComposePath(p3, path3)
        drawMorphBlob(path3, w * (0.22f + 0.38f * stepShift), h * (0.40f + 0.18f * stepShift),
            base * 0.30f, rot1 * 1.4f, tertiaryContainer.copy(alpha = 0.38f), tertiary.copy(alpha = 0.42f), 2f)

        // Layer 4 — small accent outline
        drawMorphBlob(path3, w * (0.78f - 0.22f * stepShift), h * (0.62f - 0.10f * stepShift),
            base * 0.22f, rot2 * 1.6f, Color.Transparent, primary.copy(alpha = 0.28f), 2f)
    }
}

private fun DrawScope.drawMorphBlob(
    path: Path,
    centerX: Float, centerY: Float,
    size: Float, rotationDeg: Float,
    fillColor: Color, strokeColor: Color, strokeWidth: Float
) {
    val matrix = Matrix()
    matrix.translate(centerX, centerY)
    matrix.rotateZ(rotationDeg)
    matrix.scale(size, size)

    val transformed = Path().apply {
        addPath(path)
        transform(matrix)
    }
    if (fillColor != Color.Transparent) {
        drawPath(transformed, fillColor)
    }
    drawPath(transformed, strokeColor, style = Stroke(strokeWidth))
}

private fun DrawScope.drawDotGrid(color: Color, spacing: Float) {
    val cols = (size.width / spacing).toInt() + 1
    val rows = (size.height / spacing).toInt() + 1
    val sx = (size.width  - (cols - 1) * spacing) / 2f
    val sy = (size.height - (rows - 1) * spacing) / 2f
    for (i in 0 until cols) for (j in 0 until rows) {
        drawCircle(color, radius = 1.5f, center = Offset(sx + i * spacing, sy + j * spacing))
    }
}

fun Morph.toComposePath(progress: Float, targetPath: Path = Path()): Path {
    targetPath.reset()
    var first = true
    forEachCubic(progress) { c ->
        if (first) { targetPath.moveTo(c.anchor0X, c.anchor0Y); first = false }
        targetPath.cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
    }
    targetPath.close()
    return targetPath
}
