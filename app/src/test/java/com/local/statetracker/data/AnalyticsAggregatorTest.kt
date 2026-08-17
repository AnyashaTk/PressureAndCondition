package com.local.statetracker.data

import org.junit.Assert.*
import org.junit.Test

class AnalyticsAggregatorTest {
    private val metric=MetricDefinitionEntity("energy","Энергия",MetricType.SCALE,0,10,sortOrder=0)
    private fun item(id:String,date:String,slot:CheckInSlot,value:Double?)=CheckInWithObservations(CheckInEntity(id,date,slot,if(slot==CheckInSlot.EXTRA)null else slot.name,0,0,isBackfilled=false),if(value==null)emptyList() else listOf(ObservationEntity(id,"energy",numericValue=value)))
    @Test fun missingIsNotZero(){val result=AnalyticsAggregator.aggregate(listOf(item("1","2026-08-01",CheckInSlot.DAY,null),item("2","2026-08-01",CheckInSlot.EVENING,4.0)),metric,setOf(CheckInSlot.DAY,CheckInSlot.EVENING),Grouping.DAY);assertEquals(4.0,result.single().value,0.0)}
    @Test fun slotsRemainSeparate(){val result=AnalyticsAggregator.aggregate(listOf(item("1","2026-08-01",CheckInSlot.DAY,2.0),item("2","2026-08-01",CheckInSlot.EVENING,8.0)),metric,setOf(CheckInSlot.DAY,CheckInSlot.EVENING),Grouping.DAY);assertEquals(2,result.size)}
    @Test fun slotFilteringWorks(){val result=AnalyticsAggregator.aggregate(listOf(item("1","2026-08-01",CheckInSlot.DAY,2.0),item("2","2026-08-01",CheckInSlot.EVENING,8.0)),metric,setOf(CheckInSlot.DAY),Grouping.DAY);assertEquals(2.0,result.single().value,0.0)}
    @Test fun booleanMissingIsNotFalse(){val bool=metric.copy(id="f",type=MetricType.BOOLEAN);val a=item("1","2026-08-01",CheckInSlot.DAY,null);val b=CheckInWithObservations(item("2","2026-08-01",CheckInSlot.DAY,null).checkIn,listOf(ObservationEntity("2","f",booleanValue=true)));assertEquals(1.0,AnalyticsAggregator.aggregate(listOf(a,b),bool,setOf(CheckInSlot.DAY),Grouping.DAY).single().value,0.0)}
    @Test fun pressureAggregationsAreIndependent(){val values=listOf(118f,125f,121f);assertEquals(121.333f,PressureAggregation.MEAN.apply(values),.01f);assertEquals(118f,PressureAggregation.MIN.apply(values));assertEquals(125f,PressureAggregation.MAX.apply(values))}
}
