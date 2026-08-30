package com.local.statetracker.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MascotAnimationTest {
    @Test fun requiredFrameCadenceIsConfigured(){assertEquals(400L,WALK_FRAME_MILLIS);assertEquals(900L,STATIONARY_FRAME_MILLIS)}
    @Test fun stationaryFrameTickDoesNotMove(){assertEquals(42f,MascotMotion.afterAnimationTick(42f,false))}
    @Test fun movementRateRemainsEquivalentToOldFourDpPer150ms(){assertEquals(4f,MOVEMENT_PER_TICK*(150f/MOVEMENT_TICK_MILLIS))}
    @Test fun overlapUsesTheSmallerMascotAsReference(){assertEquals(.625f,MascotMotion.overlapRatio(0f,100f,50f,80f),.001f)}
    @Test fun separatedMascotsHaveNoOverlap(){assertEquals(0f,MascotMotion.overlapRatio(0f,100f,101f,80f),.001f)}
    @Test fun teaDurationBoundsAreThreeToTenSeconds(){assertEquals(3_000L,TEA_MIN_DURATION_MILLIS);assertEquals(10_000L,TEA_MAX_DURATION_MILLIS)}
}
