package com.local.statetracker.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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

data class MascotDefinition(val id:String,@DrawableRes val idle:Int,val walkFrames:List<Int>,val scale:Float=1f)

private val LocalMascots=listOf(
    MascotDefinition("girl_blue",R.drawable.mascot_blue_idle,listOf(R.drawable.mascot_blue_walk_1,R.drawable.mascot_blue_walk_2),.92f),
    MascotDefinition("girl_pink",R.drawable.mascot_pink_idle,listOf(R.drawable.mascot_pink_walk_1,R.drawable.mascot_pink_walk_2),.86f)
)

@Composable fun MascotGarden(modifier:Modifier=Modifier){
    val owner=LocalLifecycleOwner.current;var foreground by remember{mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))}
    DisposableEffect(owner){val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME->foreground=true;Lifecycle.Event.ON_STOP, Lifecycle.Event.ON_PAUSE->foreground=false;else->{}}};owner.lifecycle.addObserver(observer);onDispose{owner.lifecycle.removeObserver(observer)}}
    BoxWithConstraints(modifier.height(150.dp).fillMaxWidth()){
        val travel=(maxWidth-72.dp).coerceAtLeast(1.dp)
        LocalMascots.forEachIndexed{index,definition->MascotActor(definition,foreground,travel.value,index)}
    }
}

@Composable private fun BoxScope.MascotActor(definition:MascotDefinition,active:Boolean,maxX:Float,index:Int){
    var x by remember(definition.id){mutableFloatStateOf(if(index==0)0f else maxX*.62f)};var direction by remember{mutableIntStateOf(if(index==0)1 else -1)};var moving by remember{mutableStateOf(false)};var frame by remember{mutableIntStateOf(0)}
    LaunchedEffect(active,maxX){if(!active){moving=false;return@LaunchedEffect};delay((index*450).toLong());while(true){moving=true;val steps=Random.nextInt(12,30);repeat(steps){x=(x+direction*4f).coerceIn(0f,maxX);if(x<=0f)direction=1 else if(x>=maxX)direction=-1;frame=(frame+1)%definition.walkFrames.size;delay(150)};moving=false;if(Random.nextBoolean())direction*=-1;delay(Random.nextLong(700,1800))}}
    Image(painterResource(if(moving)definition.walkFrames[frame]else definition.idle),contentDescription=null,contentScale=ContentScale.Fit,modifier=Modifier.size(72.dp).align(Alignment.BottomStart).graphicsLayer{translationX=x.dp.toPx();scaleX=direction.toFloat()*definition.scale;scaleY=definition.scale})
}
