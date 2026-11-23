package com.example.service

import android.util.Log
import com.example.data.entity.CategoryTaxEntity

object TaxMasterService {
    private const val TAG = "TaxMasterService"

    // Supported Core Categories
    const val CATEGORY_READYMADE = "Readymade"
    const val CATEGORY_TEXTILE = "Textile"

    val STANDARD_CATEGORIES = listOf(CATEGORY_READYMADE, CATEGORY_TEXTILE)

    // Recognized keywords for Textile, Handloom & Fabric items that default to 5% GST
    private val TEXTILE_KEYWORDS = listOf(
        "textile", "textiles", "fabric", "fabrics", "saree", "sarees", "sari", "saris",
        "cotton", "silk", "linen", "chiffon", "georgette", "khadi", "handloom",
        "yarn", "unstitched", "wool", "polyester", "weaving", "shirting", "suiting",
        "dupatta", "dhoti", "lungi", "towel", "bedsheet", "curtain", "shawl",
        "crepe", "rayon", "viscose", "denim fabric", "knit fabric", "weaving yarn"
    )

    /**
     * Retrieves the exact GST tax rate percentage for a given item, category/division, and unit cost amount.
     *
     * Evaluation Order:
     * 1. Custom Category Tax Rules (defined by Admin/User in Tax Master):
     *    Matches categoryName, department, division, section, or itemName against configured CategoryTaxEntity rules
     *    where the cost falls within the specified slab (minSlabAmount <= cost <= maxSlabAmount).
     * 2. Inbuilt Statutory Rules:
     *    a. Textile Category / Division / Department / Section / Keywords: ALWAYS 5% GST flat.
     *    b. Readymade Category / Division / Standard Apparel:
     *       - Unit Cost below ₹2,500 (cost < 2500.0): 5% GST
     *       - Unit Cost ₹2,500 and above (cost >= 2500.0): 18% GST
     */
    fun getTaxRate(
        itemName: String = "",
        categoryName: String = "",
        costAmount: Double,
        categoryTaxRules: List<CategoryTaxEntity> = emptyList(),
        division: String = "",
        department: String = "",
        section: String = ""
    ): Double {
        val trimmedCat = categoryName.trim()
        val trimmedName = itemName.trim()
        val trimmedDept = department.trim()
        val trimmedDiv = division.trim()
        val trimmedSec = section.trim()

        try {
            Log.i(
                TAG,
                "==== TAX CALCULATION: Item='$trimmedName', Cat='$trimmedCat', Div='$trimmedDiv', Dept='$trimmedDept', Sec='$trimmedSec', Cost=₹$costAmount ===="
            )
        } catch (_: Throwable) {
            // JVM Unit Tests without Android Log mock
        }

        // 1. Check custom user-defined Category Tax Rules from Database / Tax Master
        if (categoryTaxRules.isNotEmpty()) {
            val matchingRule = categoryTaxRules.firstOrNull { rule ->
                val ruleCat = rule.categoryName.trim()
                val matchesCategory = (trimmedCat.isNotBlank() && ruleCat.equals(trimmedCat, ignoreCase = true)) ||
                        (trimmedDept.isNotBlank() && ruleCat.equals(trimmedDept, ignoreCase = true)) ||
                        (trimmedName.isNotBlank() && ruleCat.equals(trimmedName, ignoreCase = true)) ||
                        (trimmedDiv.isNotBlank() && ruleCat.equals(trimmedDiv, ignoreCase = true)) ||
                        (trimmedSec.isNotBlank() && ruleCat.equals(trimmedSec, ignoreCase = true))
                val inSlab = costAmount >= rule.minSlabAmount && costAmount <= rule.maxSlabAmount
                matchesCategory && inSlab
            }

            if (matchingRule != null) {
                try {
                    Log.i(
                        TAG,
                        "-> Custom Tax Master Rule Matched: '${matchingRule.categoryName}' (${matchingRule.ruleName}) -> Applied Rate: ${matchingRule.taxRatePercent}%"
                    )
                } catch (_: Throwable) {}
                return matchingRule.taxRatePercent
            }
        }

        // 2. Check if Textile (ALWAYS 5% only)
        val isTextile = trimmedCat.equals(CATEGORY_TEXTILE, ignoreCase = true) ||
                trimmedDiv.equals(CATEGORY_TEXTILE, ignoreCase = true) ||
                trimmedSec.equals(CATEGORY_TEXTILE, ignoreCase = true) ||
                trimmedDept.equals(CATEGORY_TEXTILE, ignoreCase = true) ||
                isTextileKeyword(trimmedCat) ||
                isTextileKeyword(trimmedDiv) ||
                isTextileKeyword(trimmedDept) ||
                isTextileKeyword(trimmedSec) ||
                isTextileKeyword(trimmedName)

        if (isTextile) {
            try {
                Log.i(TAG, "-> TEXTILE category identified: Applying flat 5.0% GST for cost ₹$costAmount")
            } catch (_: Throwable) {}
            return 5.0
        }

        // 3. Readymade Category (or any standard apparel item):
        // Cost below 2500 -> 5%, 2500 and above -> 18%
        val rate = if (costAmount < 2500.0) 5.0 else 18.0
        try {
            Log.i(TAG, "-> READYMADE category rule: Cost=₹$costAmount (<₹2500 -> 5%, >=₹2500 -> 18%) -> Applied Rate: $rate%")
        } catch (_: Throwable) {}
        return rate
    }

    private fun isTextileKeyword(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        return TEXTILE_KEYWORDS.any { keyword ->
            lower.contains(keyword) || keyword.contains(lower)
        }
    }

    /**
     * Helper to get tax explanation note for UI
     */
    fun getTaxExplanation(
        division: String = "",
        categoryName: String = "",
        department: String = "",
        costAmount: Double = 0.0,
        categoryTaxRules: List<CategoryTaxEntity> = emptyList()
    ): String {
        val trimmedCat = categoryName.trim()
        val trimmedDept = department.trim()
        val trimmedDiv = division.trim()

        if (categoryTaxRules.isNotEmpty()) {
            val matchingRule = categoryTaxRules.firstOrNull { rule ->
                val ruleCat = rule.categoryName.trim()
                val matches = (trimmedCat.isNotBlank() && ruleCat.equals(trimmedCat, ignoreCase = true)) ||
                        (trimmedDept.isNotBlank() && ruleCat.equals(trimmedDept, ignoreCase = true)) ||
                        (trimmedDiv.isNotBlank() && ruleCat.equals(trimmedDiv, ignoreCase = true))
                val inSlab = costAmount >= rule.minSlabAmount && costAmount <= rule.maxSlabAmount
                matches && inSlab
            }
            if (matchingRule != null) {
                return "Tax Master Rule (${matchingRule.categoryName}): ${matchingRule.taxRatePercent}% GST"
            }
        }

        return if (trimmedCat.equals(CATEGORY_TEXTILE, ignoreCase = true) ||
            trimmedDiv.equals(CATEGORY_TEXTILE, ignoreCase = true) ||
            trimmedDept.equals(CATEGORY_TEXTILE, ignoreCase = true) ||
            isTextileKeyword(trimmedCat) ||
            isTextileKeyword(trimmedDiv) ||
            isTextileKeyword(trimmedDept)
        ) {
            "Textile: Flat 5% GST (All Cost Slabs)"
        } else {
            if (costAmount < 2500.0) "Readymade (< ₹2,500): 5% GST"
            else "Readymade (≥ ₹2,500): 18% GST"
        }
    }

    /**
     * Backward-compatible alias for existing callers
     */
    fun getTaxRateForCategory(
        categoryName: String,
        costAmount: Double,
        categoryTaxRules: List<CategoryTaxEntity> = emptyList()
    ): Double {
        return getTaxRate(
            itemName = "",
            categoryName = categoryName,
            costAmount = costAmount,
            categoryTaxRules = categoryTaxRules
        )
    }

    /**
     * Retrieves the specific matched CategoryTaxEntity rule if one applies
     */
    fun getMatchingTaxRule(
        categoryName: String = "",
        division: String = "",
        department: String = "",
        section: String = "",
        itemName: String = "",
        costAmount: Double = 0.0,
        categoryTaxRules: List<CategoryTaxEntity> = emptyList()
    ): CategoryTaxEntity? {
        val trimmedCat = categoryName.trim()
        val trimmedDept = department.trim()
        val trimmedDiv = division.trim()
        val trimmedSec = section.trim()
        val trimmedName = itemName.trim()

        if (categoryTaxRules.isEmpty()) return null
        return categoryTaxRules.firstOrNull { rule ->
            val ruleCat = rule.categoryName.trim()
            val matches = (trimmedCat.isNotBlank() && ruleCat.equals(trimmedCat, ignoreCase = true)) ||
                    (trimmedDept.isNotBlank() && ruleCat.equals(trimmedDept, ignoreCase = true)) ||
                    (trimmedDiv.isNotBlank() && ruleCat.equals(trimmedDiv, ignoreCase = true)) ||
                    (trimmedSec.isNotBlank() && ruleCat.equals(trimmedSec, ignoreCase = true)) ||
                    (trimmedName.isNotBlank() && ruleCat.equals(trimmedName, ignoreCase = true))
            val inSlab = costAmount >= rule.minSlabAmount && costAmount <= rule.maxSlabAmount
            matches && inSlab
        }
    }
}
