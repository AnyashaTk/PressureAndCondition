package com.local.statetracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MascotAnimationTest {
    @Test fun stationaryFramesAreThreeTimesSlower(){assertEquals(WALK_FRAME_MILLIS*3,STATIONARY_FRAME_MILLIS)}
    @Test fun stationaryTickDoesNotMove(){assertEquals(42f,MascotMotion.nextX(42f,1,false,100f))}
    @Test fun walkingTickMoves(){assertEquals(46f,MascotMotion.nextX(42f,1,true,100f))}
}
