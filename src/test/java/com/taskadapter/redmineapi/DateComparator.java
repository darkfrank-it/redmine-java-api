package com.taskadapter.redmineapi;


import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DateComparator {
    public static void testLongDate(int year, int month, int day, int hour, int min, int sec, String timeZone, Date expectedDate) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.YEAR, year);
        c.set(Calendar.MONTH, month);
        c.set(Calendar.DAY_OF_MONTH, day);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, min);
        c.set(Calendar.SECOND, sec);
        c.set(Calendar.MILLISECOND, 0);
        if (!timeZone.isEmpty()) {
            c.setTimeZone(TimeZone.getTimeZone(timeZone));
        }
        Date actualDate = c.getTime();
        assertEquals(actualDate, expectedDate,"Checking date");
    }

    public static void testShortDate(int year, int month, int day, Date expectedDate) {
        testLongDate(year, month, day, 0, 0, 0, "", expectedDate);
    }
}
