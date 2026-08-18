package com.local.statetracker.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TimeDomainTest{
    private val start=LocalDate.of(2026,8,17);private val end=LocalDate.of(2026,8,23)
    @Test fun dailyDomainContainsMissingDays(){val b=TimeDomain.buckets(start,end,Grouping.DAY);assertEquals(7,b.size);assertEquals(LocalDate.of(2026,8,18),b[1].start);assertEquals(3,TimeDomain.indexOf(b,LocalDate.of(2026,8,20)))}
    @Test fun emptyBucketSelectionDoesNotSnap(){val b=TimeDomain.buckets(start,end,Grouping.DAY);val observations=setOf(start,end);val selected=b[1];assertEquals(LocalDate.of(2026,8,18),selected.start);assertFalse(selected.start in observations)}
    @Test fun coordinateSelectionUsesDomainNotObservations(){val b=TimeDomain.buckets(start,end,Grouping.DAY);assertEquals(1,TimeDomain.selectByFraction(b,1.5f/7f));assertEquals(LocalDate.of(2026,8,18),b[TimeDomain.selectByFraction(b,1.5f/7f)].start)}
    @Test fun missingBucketBreaksLine(){val points=listOf(.5f to 4f,2.5f to 7f);assertTrue(ChartSegments.segments(points).isEmpty())}
    @Test fun groupingChangesBoundaries(){val day=TimeDomain.buckets(start,end,Grouping.DAY);val week=TimeDomain.buckets(start,end.plusWeeks(2),Grouping.WEEK);val month=TimeDomain.buckets(start.minusMonths(3),end,Grouping.MONTH);assertEquals(7,day.size);assertTrue(week.all{it.endExclusive==it.start.plusWeeks(1)});assertTrue(month.all{it.endExclusive==it.start.plusMonths(1)})}
    @Test fun monthDomainKeepsEmptyMonths(){val b=TimeDomain.buckets(LocalDate.of(2026,5,10),LocalDate.of(2026,8,20),Grouping.MONTH);assertEquals(listOf(5,6,7,8),b.map{it.start.monthValue})}
    @Test fun cycleDayResets(){val starts=listOf(LocalDate.of(2026,8,10),LocalDate.of(2026,9,7));assertNull(CycleDayCalculator.day(LocalDate.of(2026,8,9),starts));assertEquals(1,CycleDayCalculator.day(LocalDate.of(2026,8,10),starts));assertEquals(8,CycleDayCalculator.day(LocalDate.of(2026,8,17),starts));assertEquals(1,CycleDayCalculator.day(LocalDate.of(2026,9,7),starts))}
    @Test fun cycleOverlayToggleAndMultipleEvents(){val domain=TimeDomain.buckets(start,end,Grouping.DAY);val starts=listOf(start,start.plusDays(3));assertTrue(CycleOverlay.events(domain,starts,false).isEmpty());assertEquals(starts,CycleOverlay.events(domain,starts,true))}
}
