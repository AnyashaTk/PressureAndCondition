package com.local.statetracker.data

import kotlin.math.ceil
import kotlin.math.floor

data class ChartYRange(val min: Float, val max: Float)

object PressureChartScale {
    fun forVisible(values: Iterable<Float>): ChartYRange {
        val finite = values.filter(Float::isFinite)
        val minimum = finite.minOrNull()
        val maximum = finite.maxOrNull()
        return ChartYRange(
            min = minimum?.takeIf { it < 40f }?.let { floor(it / 10f) * 10f } ?: 40f,
            max = maximum?.takeIf { it > 150f }?.let { ceil(it / 10f) * 10f } ?: 150f,
        )
    }
}
