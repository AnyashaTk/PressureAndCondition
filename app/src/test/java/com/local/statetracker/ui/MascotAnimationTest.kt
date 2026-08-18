package com.local.statetracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MascotAnimationTest {
    @Test fun requiredFrameCadenceIsConfigured(){assertEquals(200L,WALK_FRAME_MILLIS);assertEquals(900L,STATIONARY_FRAME_MILLIS)}
    @Test fun stationaryFrameTickDoesNotMove(){assertEquals(42f,MascotMotion.afterAnimationTick(42f,false))}
    @Test fun movementRateRemainsEquivalentToOldFourDpPer150ms(){assertEquals(4f,MOVEMENT_PER_TICK*(150f/MOVEMENT_TICK_MILLIS))}
}
