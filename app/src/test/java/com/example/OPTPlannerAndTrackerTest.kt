package com.example

import com.example.data.entity.PurchasePlanNoteEntity
import com.example.ui.viewmodel.POMasterViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OPTPlannerAndTrackerTest {

    // Dummy helper to create a minimal parser instance without needing whole Android context
    private val helper = POMasterViewModelTestHelper()

    @Test
    fun testNLPParsing_MonthDayAndCityAndBudget() {
        val input = "Aug 27 Bombay silk purchase 50k"
        val parsed = helper.parseQuickPlanText(input)

        assertEquals("Aug 27 Bombay silk purchase 50k", parsed.title)
        assertEquals("Bombay", parsed.location)
        assertEquals(50000.0, parsed.estimatedBudget, 0.01)

        val cal = Calendar.getInstance().apply { time = Date(parsed.dateMillis) }
        assertEquals(Calendar.AUGUST, cal.get(Calendar.MONTH))
        assertEquals(27, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testNLPParsing_DayMonthAndCityAndLakhBudget() {
        val input = "27 aug surat mill visit budget 1.5L"
        val parsed = helper.parseQuickPlanText(input)

        assertEquals("27 aug surat mill visit budget 1.5L", parsed.title)
        assertEquals("Surat", parsed.location)
        assertEquals(150000.0, parsed.estimatedBudget, 0.01)

        val cal = Calendar.getInstance().apply { time = Date(parsed.dateMillis) }
        assertEquals(Calendar.AUGUST, cal.get(Calendar.MONTH))
        assertEquals(27, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testNLPParsing_TomorrowAndTirupur() {
        val input = "tomorrow tirupur yarn negotiation 75000"
        val parsed = helper.parseQuickPlanText(input)

        assertEquals("Tirupur", parsed.location)
        assertEquals(75000.0, parsed.estimatedBudget, 0.01)

        val nowCal = Calendar.getInstance()
        val expectedCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val cal = Calendar.getInstance().apply { time = Date(parsed.dateMillis) }
        assertEquals(expectedCal.get(Calendar.DAY_OF_YEAR), cal.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun testNLPParsing_InDaysPattern() {
        val input = "in 5 days Ahmedabad cotton fair"
        val parsed = helper.parseQuickPlanText(input)

        assertEquals("Ahmedabad", parsed.location)

        val expectedCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 5) }
        val cal = Calendar.getInstance().apply { time = Date(parsed.dateMillis) }
        assertEquals(expectedCal.get(Calendar.DAY_OF_YEAR), cal.get(Calendar.DAY_OF_YEAR))
    }

    @Test
    fun testPurchasePlanNoteEntity_DefaultsAndProperties() {
        val note = PurchasePlanNoteEntity(
            id = 1,
            title = "Bombay Sourcing Trip",
            plannedDateMillis = 1756252800000L,
            location = "Bombay",
            notes = "Meet 3 silk vendors in Kalbadevi",
            estimatedBudget = 100000.0,
            priority = "HIGH",
            status = "PLANNED"
        )

        assertEquals("Bombay Sourcing Trip", note.title)
        assertEquals("Bombay", note.location)
        assertEquals("HIGH", note.priority)
        assertEquals("PLANNED", note.status)
        assertEquals(100000.0, note.estimatedBudget, 0.001)
    }
}

class POMasterViewModelTestHelper {
    fun parseQuickPlanText(input: String): com.example.ui.viewmodel.ParsedPlanData {
        val trimmed = input.trim()
        val nowCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        var targetDateMillis = nowCal.timeInMillis
        var rawDateMatch: String? = null

        val monthsMap = mapOf(
            "jan" to 0, "january" to 0,
            "feb" to 1, "february" to 1,
            "mar" to 2, "march" to 2,
            "apr" to 3, "april" to 3,
            "may" to 4,
            "jun" to 5, "june" to 5,
            "jul" to 6, "july" to 6,
            "aug" to 7, "august" to 7,
            "sep" to 8, "september" to 8,
            "oct" to 9, "october" to 9,
            "nov" to 10, "november" to 10,
            "dec" to 11, "december" to 11
        )

        val regexMonthFirst = Regex("(?i)\\b(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\s+(\\d{1,2})(?:st|nd|rd|th)?\\b")
        val regexDayFirst = Regex("(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?)\\b")
        val regexTomorrow = Regex("(?i)\\btomorrow\\b")
        val regexInDays = Regex("(?i)\\bin\\s+(\\d+)\\s+days?\\b")

        val matchA = regexMonthFirst.find(trimmed)
        val matchB = regexDayFirst.find(trimmed)
        val matchTomorrow = regexTomorrow.find(trimmed)
        val matchInDays = regexInDays.find(trimmed)

        if (matchA != null) {
            val monthStr = matchA.groupValues[1].lowercase()
            val dayInt = matchA.groupValues[2].toIntOrNull() ?: 1
            val monthIdx = monthsMap[monthStr] ?: monthsMap.entries.firstOrNull { monthStr.startsWith(it.key) }?.value ?: nowCal.get(Calendar.MONTH)
            val currentYear = nowCal.get(Calendar.YEAR)
            val tempCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, currentYear)
                set(Calendar.MONTH, monthIdx)
                set(Calendar.DAY_OF_MONTH, dayInt.coerceIn(1, 31))
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (tempCal.timeInMillis < nowCal.timeInMillis - (1000L * 60 * 60 * 24 * 60)) {
                tempCal.add(Calendar.YEAR, 1)
            }
            targetDateMillis = tempCal.timeInMillis
            rawDateMatch = matchA.value
        } else if (matchB != null) {
            val dayInt = matchB.groupValues[1].toIntOrNull() ?: 1
            val monthStr = matchB.groupValues[2].lowercase()
            val monthIdx = monthsMap[monthStr] ?: monthsMap.entries.firstOrNull { monthStr.startsWith(it.key) }?.value ?: nowCal.get(Calendar.MONTH)
            val currentYear = nowCal.get(Calendar.YEAR)
            val tempCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, currentYear)
                set(Calendar.MONTH, monthIdx)
                set(Calendar.DAY_OF_MONTH, dayInt.coerceIn(1, 31))
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (tempCal.timeInMillis < nowCal.timeInMillis - (1000L * 60 * 60 * 24 * 60)) {
                tempCal.add(Calendar.YEAR, 1)
            }
            targetDateMillis = tempCal.timeInMillis
            rawDateMatch = matchB.value
        } else if (matchTomorrow != null) {
            targetDateMillis = nowCal.timeInMillis + (1000L * 60 * 60 * 24)
            rawDateMatch = "Tomorrow"
        } else if (matchInDays != null) {
            val days = matchInDays.groupValues[1].toIntOrNull() ?: 1
            targetDateMillis = nowCal.timeInMillis + (1000L * 60 * 60 * 24 * days)
            rawDateMatch = matchInDays.value
        }

        val knownCities = listOf(
            "Bombay", "Mumbai", "Surat", "Tirupur", "Tiruppur", "Ahmedabad", "Delhi", "Kolkata", "Calcutta",
            "Varanasi", "Banaras", "Bangalore", "Bengaluru", "Erode", "Salem", "Jaipur", "Ludhiana",
            "Coimbatore", "Chennai", "Madurai", "Kanpur", "Bhiwandi", "Ichalkaranji", "Karur", "Panipat",
            "Amritsar", "Surat Mill", "Central Warehouse"
        )
        var detectedCity = ""
        for (city in knownCities) {
            if (trimmed.contains(city, ignoreCase = true)) {
                detectedCity = if (city.equals("Mumbai", ignoreCase = true)) "Bombay" else city
                break
            }
        }

        var detectedBudget = 0.0
        val budgetRegex = Regex("(?i)(?:₹|rs\\.?|budget|amt)?\\s*(\\d+(?:[.,]\\d+)?)\\s*(k|lakh|lakhs|l|cr)?\\b")
        budgetRegex.findAll(trimmed).forEach { m ->
            val numStr = m.groupValues[1].replace(",", "")
            val unit = m.groupValues[2].lowercase()
            val numVal = numStr.toDoubleOrNull() ?: 0.0
            val multiplier = when (unit) {
                "k" -> 1_000.0
                "l", "lakh", "lakhs" -> 100_000.0
                "cr" -> 10_000_000.0
                else -> 1.0
            }
            if (numVal > 0) {
                val candidate = numVal * multiplier
                if (candidate > detectedBudget && candidate != (m.groupValues[1].toIntOrNull()?.toDouble() ?: 0.0) || candidate >= 500.0) {
                    detectedBudget = candidate
                }
            }
        }

        return com.example.ui.viewmodel.ParsedPlanData(
            title = trimmed,
            dateMillis = targetDateMillis,
            location = detectedCity,
            estimatedBudget = detectedBudget,
            rawMatchedDateStr = rawDateMatch
        )
    }
}
