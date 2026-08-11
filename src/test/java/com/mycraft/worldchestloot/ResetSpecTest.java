package com.mycraft.worldchestloot;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ResetSpecTest {
    private TimeZone originalTimeZone;

    @Before
    public void useUtc() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @After
    public void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    public void parsesEquivalentDurations() {
        assertEquals(7200, ResetSpec.parse("0d+2h+0m+0s").editorSeconds());
        assertEquals(7200, ResetSpec.parse("2h").editorSeconds());
        assertEquals(9000, ResetSpec.parse("2.5h").editorSeconds());
        assertEquals(9000, ResetSpec.parse("2h+30m").editorSeconds());
        assertEquals(9000, ResetSpec.parse("2h+1800s").editorSeconds());
    }

    @Test
    public void calculatesNextDailyReset() {
        ResetSpec reset = ResetSpec.parse("daily;19:0:0");
        long before = timestamp(2026, 8, 11, 18, 0, 0);
        long after = timestamp(2026, 8, 11, 20, 0, 0);
        assertEquals(timestamp(2026, 8, 11, 19, 0, 0), reset.nextResetAt(before, false));
        assertEquals(timestamp(2026, 8, 12, 19, 0, 0), reset.nextResetAt(after, false));
    }

    @Test
    public void calculatesNextWeeklyReset() {
        ResetSpec reset = ResetSpec.parse("weekly,2;19:0:0");
        long monday = timestamp(2026, 8, 10, 20, 0, 0);
        long tuesdayAfterReset = timestamp(2026, 8, 11, 20, 0, 0);
        assertEquals(timestamp(2026, 8, 11, 19, 0, 0), reset.nextResetAt(monday, false));
        assertEquals(timestamp(2026, 8, 18, 19, 0, 0), reset.nextResetAt(tuesdayAfterReset, false));
        assertEquals("weekly,2;19:0:0", reset.serialize());
    }

    @Test
    public void calculatesNextMonthlyReset() {
        ResetSpec reset = ResetSpec.parse("monthly,20;15:0:0");
        long before = timestamp(2026, 8, 11, 12, 0, 0);
        long after = timestamp(2026, 8, 20, 16, 0, 0);
        assertEquals(timestamp(2026, 8, 20, 15, 0, 0), reset.nextResetAt(before, false));
        assertEquals(timestamp(2026, 9, 20, 15, 0, 0), reset.nextResetAt(after, false));
    }

    @Test
    public void choosesOneTimestampWhenSchedulesOverlap() {
        ResetSpec reset = ResetSpec.parse(Arrays.asList("daily;7:0:0", "weekly,2;7:0:0"));
        long mondayAfterDaily = timestamp(2026, 8, 10, 8, 0, 0);
        assertEquals(timestamp(2026, 8, 11, 7, 0, 0), reset.nextResetAt(mondayAfterDaily, false));
        assertEquals(Arrays.asList("daily;7:0:0", "weekly,2;7:0:0"), reset.serialize());
    }

    @Test
    public void rejectsInvalidFixedReset() {
        assertNull(ResetSpec.parse("weekly,0,8;19:0:0"));
        assertNull(ResetSpec.parse("weekly,2,4;19:0:0"));
        assertNull(ResetSpec.parse("monthly,32;19:0:0"));
        assertNull(ResetSpec.parse("daily;25:0:0"));
        assertNull(ResetSpec.parse("2hours"));
    }

    private long timestamp(int year, int month, int day, int hour, int minute, int second) {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, ZoneId.of("UTC"))
                .toInstant().toEpochMilli();
    }
}
