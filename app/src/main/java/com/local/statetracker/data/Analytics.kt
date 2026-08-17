package com.local.statetracker.data

import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

enum class Grouping { RAW, DAY, WEEK, MONTH }
enum class PressureAggregation(val ru:String){MEAN("Среднее"),MIN("Минимум"),MAX("Максимум");fun apply(values:List<Float>)=when(this){MEAN->values.average().toFloat();MIN->values.min();MAX->values.max()}}
data class AnalyticsPoint(val bucket:String,val slot:CheckInSlot,val value:Double)

object AnalyticsAggregator {
    fun aggregate(items:List<CheckInWithObservations>,metric:MetricDefinitionEntity,slots:Set<CheckInSlot>,grouping:Grouping):List<AnalyticsPoint>{
        val raw=items.filter{it.checkIn.slot in slots}.mapNotNull{c->c.observations.find{it.metricId==metric.id}?.let{o->val v=if(metric.type==MetricType.SCALE)o.numericValue else o.booleanValue?.let{if(it)1.0 else 0.0};v?.let{Triple(c.checkIn,c.checkIn.slot,it)}}}
        if(grouping==Grouping.RAW)return raw.map{AnalyticsPoint(it.first.reportedForAt.toString(),it.second,it.third)}
        fun bucket(date:LocalDate)=when(grouping){Grouping.DAY->date.toString();Grouping.WEEK->{val wf=WeekFields.ISO;"${date.get(wf.weekBasedYear())}-W${date.get(wf.weekOfWeekBasedYear()).toString().padStart(2,'0')}"};Grouping.MONTH->date.toString().take(7);Grouping.RAW->error("raw")}
        return raw.groupBy{bucket(LocalDate.parse(it.first.targetDate)) to it.second}.map{(key,values)->AnalyticsPoint(key.first,key.second,values.map{it.third}.average())}.sortedBy{it.bucket}
    }
}
