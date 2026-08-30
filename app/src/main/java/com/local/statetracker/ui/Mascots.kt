package com.local.statetracker.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.local.statetracker.R
import kotlinx.coroutines.delay
import kotlin.random.Random

const val WALK_FRAME_MILLIS = 400L
const val STATIONARY_FRAME_MILLIS = 900L
const val MOVEMENT_TICK_MILLIS = 50L
const val MOVEMENT_PER_TICK = 4f * MOVEMENT_TICK_MILLIS / 150f
const val MAX_STATIONARY_OVERLAP = .30f
const val TEA_MIN_DURATION_MILLIS = 3_000L
const val TEA_MAX_DURATION_MILLIS = 10_000L
val MASCOT_RENDERED_SIZE = 141.dp

data class MascotAnimation(
    val name: String,
    val frames: List<Int>,
    val frameDurationMillis: Long,
    val movesCharacter: Boolean,
)

data class MascotDefinition(
    val id: String,
    @DrawableRes val idle: Int,
    val animations: List<MascotAnimation>,
    val scale: Float = 1f,
) {
    val walking get() = animations.first { it.movesCharacter }
    val stationary get() = animations.filterNot { it.movesCharacter }
}

private data class MascotPosition(
    val x: Float,
    val width: Float,
    val stationary: Boolean,
)

private fun walk(vararg frames: Int) =
    MascotAnimation("walk", frames.toList(), WALK_FRAME_MILLIS, true)

private fun stationary(name: String, vararg frames: Int) =
    MascotAnimation(name, frames.toList(), STATIONARY_FRAME_MILLIS, false)

private val LocalMascots = listOf(
    MascotDefinition(
        "girl_blue",
        R.drawable.mascot_blue_idle,
        listOf(
            walk(R.drawable.mascot_blue_walk_1, R.drawable.mascot_blue_walk_2),
            stationary("tea", R.drawable.mascot_blue_tea_1, R.drawable.mascot_blue_tea_2),
            stationary("fun", R.drawable.mascot_blue_fun_1, R.drawable.mascot_blue_fun_2),
        ),
        .92f,
    ),
    MascotDefinition(
        "girl_pink",
        R.drawable.mascot_pink_idle,
        listOf(
            walk(R.drawable.mascot_pink_walk_1, R.drawable.mascot_pink_walk_2),
            stationary("tea", R.drawable.mascot_pink_tea_1, R.drawable.mascot_pink_tea_2),
        ),
        .86f,
    ),
)

object MascotMotion {
    fun nextX(x: Float, direction: Int, maxX: Float) =
        (x + direction * MOVEMENT_PER_TICK).coerceIn(0f, maxX)

    fun afterAnimationTick(x: Float, moves: Boolean) =
        if (moves) x + MOVEMENT_PER_TICK else x

    fun overlapRatio(firstX: Float, firstWidth: Float, secondX: Float, secondWidth: Float): Float {
        val overlap = (minOf(firstX + firstWidth, secondX + secondWidth) - maxOf(firstX, secondX))
            .coerceAtLeast(0f)
        return overlap / minOf(firstWidth, secondWidth)
    }
}

@Composable
fun MascotGarden(modifier: Modifier = Modifier) {
    val owner = LocalLifecycleOwner.current
    var foreground by remember {
        mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { source, _ ->
            // ON_PAUSE still means STARTED. Navigation inside the running app must not freeze actors.
            foreground = source.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val positions = remember { mutableStateMapOf<String, MascotPosition>() }
    BoxWithConstraints(modifier.height(240.dp).fillMaxWidth()) {
        val travel = (maxWidth - MASCOT_RENDERED_SIZE).coerceAtLeast(1.dp)
        LocalMascots.forEachIndexed { index, definition ->
            MascotActor(definition, foreground, travel.value, index, positions)
        }
    }
}

@Composable
private fun BoxScope.MascotActor(
    definition: MascotDefinition,
    active: Boolean,
    maxX: Float,
    index: Int,
    positions: MutableMap<String, MascotPosition>,
) {
    var x by rememberSaveable(definition.id) {
        mutableFloatStateOf(if (index == 0) 0f else maxX * .62f)
    }
    var direction by rememberSaveable(definition.id) {
        mutableIntStateOf(if (index == 0) 1 else -1)
    }
    var animation by remember { mutableStateOf<MascotAnimation?>(null) }
    var frame by remember { mutableIntStateOf(0) }
    val visualWidth = MASCOT_RENDERED_SIZE.value * definition.scale
    fun visualX() = x + (MASCOT_RENDERED_SIZE.value - visualWidth) / 2f

    fun publish(stationary: Boolean) {
        positions[definition.id] = MascotPosition(visualX(), visualWidth, stationary)
    }

    fun canStopHere(): Boolean = positions
        .filterKeys { it != definition.id }
        .values
        .filter { it.stationary }
        .none { other ->
            MascotMotion.overlapRatio(visualX(), visualWidth, other.x, other.width) > MAX_STATIONARY_OVERLAP
        }

    DisposableEffect(definition.id) {
        publish(stationary = false)
        onDispose { positions.remove(definition.id) }
    }

    LaunchedEffect(active, maxX, animation?.movesCharacter) {
        if (!active || animation?.movesCharacter != true) return@LaunchedEffect
        while (true) {
            x = MascotMotion.nextX(x, direction, maxX)
            if (x <= 0f) direction = 1 else if (x >= maxX) direction = -1
            publish(stationary = false)
            delay(MOVEMENT_TICK_MILLIS)
        }
    }

    LaunchedEffect(active, maxX) {
        if (!active) {
            animation = null
            frame = 0
            publish(stationary = false)
            return@LaunchedEffect
        }
        delay((index * 450).toLong())
        while (true) {
            val walking = definition.walking
            animation = walking
            frame = 0
            publish(stationary = false)
            repeat(Random.nextInt(9, 24)) {
                delay(walking.frameDurationMillis)
                frame = (frame + 1) % walking.frames.size
            }
            if (Random.nextBoolean()) direction *= -1

            val available = definition.stationary
            if (available.isEmpty() || !canStopHere()) continue

            val still = available.random()
            animation = still
            frame = 0
            publish(stationary = true)
            if (still.name == "tea") {
                var remaining = Random.nextLong(
                    TEA_MIN_DURATION_MILLIS,
                    TEA_MAX_DURATION_MILLIS + 1,
                )
                while (remaining > 0L) {
                    val frameTime = minOf(still.frameDurationMillis, remaining)
                    delay(frameTime)
                    remaining -= frameTime
                    if (remaining > 0L) frame = (frame + 1) % still.frames.size
                }
            } else {
                val ticks = Random.nextInt(still.frames.size * 2, still.frames.size * 4 + 1)
                repeat(ticks) {
                    delay(still.frameDurationMillis)
                    frame = (frame + 1) % still.frames.size
                }
            }
            publish(stationary = false)
        }
    }

    val drawable = animation?.frames?.getOrNull(frame) ?: definition.idle
    Image(
        painter = painterResource(drawable),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .size(MASCOT_RENDERED_SIZE)
            .align(Alignment.BottomStart)
            .graphicsLayer {
                translationX = x.dp.toPx()
                transformOrigin = TransformOrigin(.5f, 1f)
                scaleX = direction.toFloat() * definition.scale
                scaleY = definition.scale
            },
    )
}
