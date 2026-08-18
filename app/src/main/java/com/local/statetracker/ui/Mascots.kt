package com.local.statetracker.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

const val WALK_FRAME_MILLIS = 150L
const val STATIONARY_FRAME_MILLIS = WALK_FRAME_MILLIS * 3
val MASCOT_RENDERED_SIZE = 94.dp

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

private fun walk(vararg frames:Int)=MascotAnimation("walk",frames.toList(),WALK_FRAME_MILLIS,true)
private fun stationary(name:String,vararg frames:Int)=MascotAnimation(name,frames.toList(),STATIONARY_FRAME_MILLIS,false)

private val LocalMascots=listOf(
    MascotDefinition("girl_blue",R.drawable.mascot_blue_idle,listOf(
        walk(R.drawable.mascot_blue_walk_1,R.drawable.mascot_blue_walk_2),
        stationary("tea",R.drawable.mascot_blue_tea_1,R.drawable.mascot_blue_tea_2),
        stationary("fun",R.drawable.mascot_blue_fun_1,R.drawable.mascot_blue_fun_2),
    ),.92f),
    MascotDefinition("girl_pink",R.drawable.mascot_pink_idle,listOf(
        walk(R.drawable.mascot_pink_walk_1,R.drawable.mascot_pink_walk_2),
        stationary("tea",R.drawable.mascot_pink_tea_1,R.drawable.mascot_pink_tea_2),
    ),.86f),
)

object MascotMotion { fun nextX(x:Float,direction:Int,moves:Boolean,maxX:Float)=if(moves)(x+direction*4f).coerceIn(0f,maxX) else x }

@Composable fun MascotGarden(modifier:Modifier=Modifier){
    val owner=LocalLifecycleOwner.current;var foreground by remember{mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    DisposableEffect(owner){val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_START,Lifecycle.Event.ON_RESUME->foreground=true;Lifecycle.Event.ON_STOP,Lifecycle.Event.ON_PAUSE->foreground=false;else->{}}};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}}
    BoxWithConstraints(modifier.height(190.dp).fillMaxWidth()){
        val travel=(maxWidth-MASCOT_RENDERED_SIZE).coerceAtLeast(1.dp)
        LocalMascots.forEachIndexed{index,definition->MascotActor(definition,foreground,travel.value,index)}
    }
}

@Composable private fun BoxScope.MascotActor(definition:MascotDefinition,active:Boolean,maxX:Float,index:Int){
    var x by remember(definition.id){mutableFloatStateOf(if(index==0)0f else maxX*.62f)};var direction by remember{mutableIntStateOf(if(index==0)1 else -1)};var animation by remember{mutableStateOf<MascotAnimation?>(null)};var frame by remember{mutableIntStateOf(0)}
    LaunchedEffect(active,maxX){if(!active){animation=null;frame=0;return@LaunchedEffect};delay((index*450).toLong());while(true){val walking=definition.walking;animation=walking;repeat(Random.nextInt(12,30)){x=MascotMotion.nextX(x,direction,true,maxX);if(x<=0f)direction=1 else if(x>=maxX)direction=-1;frame=(frame+1)%walking.frames.size;delay(walking.frameDurationMillis)};if(Random.nextBoolean())direction*=-1;val available=definition.stationary;if(available.isEmpty()){animation=null;frame=0;delay(Random.nextLong(1350,2700))}else{val still=available.random();animation=still;frame=0;val ticks=Random.nextInt(still.frames.size*2,still.frames.size*5+1);repeat(ticks){delay(still.frameDurationMillis);frame=(frame+1)%still.frames.size}}}}
    val drawable=animation?.frames?.getOrNull(frame)?:definition.idle
    Image(painterResource(drawable),contentDescription=null,contentScale=ContentScale.Fit,modifier=Modifier.size(MASCOT_RENDERED_SIZE).align(Alignment.BottomStart).graphicsLayer{translationX=x.dp.toPx();scaleX=direction.toFloat()*definition.scale;scaleY=definition.scale})
}
