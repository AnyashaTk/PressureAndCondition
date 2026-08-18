package com.local.statetracker.data

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TimeBucket(val start:LocalDate,val endExclusive:LocalDate,val label:String){
    fun contains(date:LocalDate)=date>=start&&date<endExclusive
}

object TimeDomain {
    fun buckets(rangeStart:LocalDate,rangeEnd:LocalDate,grouping:Grouping):List<TimeBucket>{
        require(!rangeEnd.isBefore(rangeStart))
        return when(grouping){
            Grouping.RAW,Grouping.DAY->generateSequence(rangeStart){it.plusDays(1)}.takeWhile{it<=rangeEnd}.map{TimeBucket(it,it.plusDays(1),it.format(DateTimeFormatter.ofPattern("EEE, d",Locale.forLanguageTag("ru"))))}.toList()
            Grouping.WEEK->{val first=rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));generateSequence(first){it.plusWeeks(1)}.takeWhile{it<=rangeEnd}.map{start->val end=start.plusWeeks(1);TimeBucket(start,end,"${start.dayOfMonth}–${end.minusDays(1).dayOfMonth} ${end.minusDays(1).format(DateTimeFormatter.ofPattern("MMM",Locale.forLanguageTag("ru")))}")}.toList()}
            Grouping.MONTH->{val first=rangeStart.withDayOfMonth(1);generateSequence(first){it.plusMonths(1)}.takeWhile{it<=rangeEnd}.map{start->TimeBucket(start,start.plusMonths(1),start.format(DateTimeFormatter.ofPattern("MMM yyyy",Locale.forLanguageTag("ru"))))}.toList()}
        }
    }
    fun indexOf(buckets:List<TimeBucket>,date:LocalDate)=buckets.indexOfFirst{it.contains(date)}
    fun selectByFraction(buckets:List<TimeBucket>,fraction:Float):Int=(fraction.coerceIn(0f,.999999f)*buckets.size).toInt()
}

object ChartSegments{
    fun segments(points:List<Pair<Float,Float>>)=points.sortedBy{it.first}.zipWithNext().filter{(a,b)->b.first.toInt()-a.first.toInt()<=1}
}

object CycleDayCalculator{
    fun day(date:LocalDate,starts:List<LocalDate>):Int?=starts.filter{it<=date}.maxOrNull()?.let{java.time.temporal.ChronoUnit.DAYS.between(it,date).toInt()+1}
}
object CycleOverlay{
    fun events(buckets:List<TimeBucket>,starts:List<LocalDate>,enabled:Boolean):List<LocalDate>{if(!enabled||buckets.isEmpty())return emptyList();return starts.filter{it>=buckets.first().start&&it<buckets.last().endExclusive}.sorted()}
}
