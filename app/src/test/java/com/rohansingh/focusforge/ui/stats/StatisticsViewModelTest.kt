package com.rohansingh.focusforge.ui.stats

import com.rohansingh.focusforge.data.repository.StatisticsRepository
import com.rohansingh.focusforge.domain.models.TimePeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class StatisticsViewModelTest {

    @Test
    fun testCalculateTimeRange_today() {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.AUGUST, 20, 14, 30, 0)
        val range = StatisticsRepository.calculateTimeRange(TimePeriod.TODAY, calendar)

        val calStart = Calendar.getInstance().apply { timeInMillis = range.startTimeMs }
        val calEnd = Calendar.getInstance().apply { timeInMillis = range.endTimeMs }

        assertEquals(2026, calStart.get(Calendar.YEAR))
        assertEquals(Calendar.AUGUST, calStart.get(Calendar.MONTH))
        assertEquals(20, calStart.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, calStart.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calStart.get(Calendar.MINUTE))
        assertEquals(0, calStart.get(Calendar.SECOND))

        assertEquals(2026, calEnd.get(Calendar.YEAR))
        assertEquals(Calendar.AUGUST, calEnd.get(Calendar.MONTH))
        assertEquals(20, calEnd.get(Calendar.DAY_OF_MONTH))
        assertEquals(23, calEnd.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, calEnd.get(Calendar.MINUTE))
        assertEquals(59, calEnd.get(Calendar.SECOND))
    }

    @Test
    fun testCalculateTimeRange_last7Days() {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.AUGUST, 20, 14, 30, 0)
        val range = StatisticsRepository.calculateTimeRange(TimePeriod.LAST_7_DAYS, calendar)

        val calStart = Calendar.getInstance().apply { timeInMillis = range.startTimeMs }
        val calEnd = Calendar.getInstance().apply { timeInMillis = range.endTimeMs }

        // Start should be Aug 14, End should be Aug 20 (7 calendar days total)
        assertEquals(14, calStart.get(Calendar.DAY_OF_MONTH))
        assertEquals(20, calEnd.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testCalculateTimeRange_last30Days() {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.AUGUST, 20, 14, 30, 0)
        val range = StatisticsRepository.calculateTimeRange(TimePeriod.LAST_30_DAYS, calendar)

        val calStart = Calendar.getInstance().apply { timeInMillis = range.startTimeMs }
        val calEnd = Calendar.getInstance().apply { timeInMillis = range.endTimeMs }

        // 30 days total inclusive: July 22 to August 20 (since July has 31 days)
        assertEquals(22, calStart.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.JULY, calStart.get(Calendar.MONTH))
        assertEquals(20, calEnd.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testCalculateTimeRange_allTime() {
        val range = StatisticsRepository.calculateTimeRange(TimePeriod.ALL_TIME)
        assertEquals(0L, range.startTimeMs)
        assertEquals(Long.MAX_VALUE, range.endTimeMs)
    }

    @Test
    fun testZeroFilledSeries_last7Days_produces7Items() {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.AUGUST, 20, 12, 0, 0)
        val series = StatisticsRepository.generateZeroFilledDateSeries(TimePeriod.LAST_7_DAYS, calendar)
        assertEquals(7, series.size)
        assertEquals("2026-08-14", series.first().first)
        assertEquals("2026-08-20", series.last().first)
    }

    @Test
    fun testZeroFilledSeries_last30Days_produces30Items() {
        val calendar = Calendar.getInstance()
        calendar.set(2026, Calendar.AUGUST, 20, 12, 0, 0)
        val series = StatisticsRepository.generateZeroFilledDateSeries(TimePeriod.LAST_30_DAYS, calendar)
        assertEquals(30, series.size)
        assertEquals("2026-07-22", series.first().first)
        assertEquals("2026-08-20", series.last().first)
    }
}
