package com.local.statetracker.notifications

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderLogicTest {
    @Test fun beforeReminderUsesSameTargetDate(){val now=ZonedDateTime.of(2026,8,18,12,0,0,0,ZoneId.of("Europe/Moscow"));assertEquals(now.toLocalDate(),ReminderPlanner.next(now,13).toLocalDate())}
    @Test fun afterReminderUsesNextTargetDate(){val now=ZonedDateTime.of(2026,8,18,14,0,0,0,ZoneId.of("Europe/Moscow"));assertEquals(now.toLocalDate().plusDays(1),ReminderPlanner.next(now,13).toLocalDate())}
    @Test fun customMinutesArePartOfScheduledInstant(){val now=ZonedDateTime.of(2026,8,18,12,0,0,0,ZoneId.of("Europe/Moscow"));val next=ReminderPlanner.next(now,13,37);assertEquals(13,next.hour);assertEquals(37,next.minute)}
    @Test fun filledSlotSuppressesAndUnfilledPosts(){assertFalse(ReminderDecision.shouldPost(true));assertTrue(ReminderDecision.shouldPost(false))}
}
