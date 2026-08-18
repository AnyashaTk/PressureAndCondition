package com.local.statetracker.data

import org.junit.Assert.assertEquals
import org.junit.Test

class PressureChartScaleTest {
    @Test fun defaultRangeDoesNotAutoscaleWithinBounds() = assertEquals(ChartYRange(40f,150f), PressureChartScale.forVisible(listOf(63f,76f,100f,121f)))
    @Test fun expandsBelowDefault() = assertEquals(ChartYRange(30f,150f), PressureChartScale.forVisible(listOf(39f,100f)))
    @Test fun expandsAboveDefault() = assertEquals(ChartYRange(40f,160f), PressureChartScale.forVisible(listOf(100f,151f)))
    @Test fun expandsBothDirectionsToTens() = assertEquals(ChartYRange(30f,170f), PressureChartScale.forVisible(listOf(34f,167f)))
    @Test fun emptyDataKeepsDefault() = assertEquals(ChartYRange(40f,150f), PressureChartScale.forVisible(emptyList()))
}
