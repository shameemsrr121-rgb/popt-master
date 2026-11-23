package com.example

import com.example.data.entity.CategoryTaxEntity
import com.example.service.TaxMasterService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaxCalculationUnitTest {

    @Test
    fun testTextileCategoryAlwaysFivePercent_lowCost() {
        val rate = TaxMasterService.getTaxRate(
            itemName = "Cotton Fabric",
            categoryName = "Textile",
            costAmount = 450.0,
            division = "Textile"
        )
        assertEquals(5.0, rate, 0.001)
    }

    @Test
    fun testTextileCategoryAlwaysFivePercent_highCostAbove2500() {
        val rate = TaxMasterService.getTaxRate(
            itemName = "Pure Kanchipuram Silk Saree",
            categoryName = "Textile",
            costAmount = 12500.0,
            division = "Textile"
        )
        assertEquals(5.0, rate, 0.001)
    }

    @Test
    fun testDepartmentBitTaggedAsTextile_highCostAbove2500_isFivePercent() {
        // When Department is 'bit' and tagged with Category 'Textile'
        val rate = TaxMasterService.getTaxRate(
            itemName = "bit",
            categoryName = "Textile",
            costAmount = 3200.0,
            division = "mens",
            department = "bit",
            section = "shirts"
        )
        assertEquals(5.0, rate, 0.001)
    }

    @Test
    fun testCustomCategoryTaxMasterRule_overridesDefault() {
        val customRule = CategoryTaxEntity(
            id = 1,
            categoryName = "Textile",
            hsnCode = "5208",
            taxRatePercent = 5.0,
            minSlabAmount = 0.0,
            maxSlabAmount = 9999999.0,
            description = "All Textile rates 5% GST",
            ruleName = "Textile Standard 5% Rule"
        )
        val rate = TaxMasterService.getTaxRate(
            itemName = "bit",
            categoryName = "Textile",
            costAmount = 3200.0,
            categoryTaxRules = listOf(customRule),
            division = "mens",
            department = "bit",
            section = "shirts"
        )
        assertEquals(5.0, rate, 0.001)
    }

    @Test
    fun testCustomDepartmentSpecificTaxMasterRule() {
        val customRule = CategoryTaxEntity(
            id = 2,
            categoryName = "bit",
            hsnCode = "5208",
            taxRatePercent = 5.0,
            minSlabAmount = 0.0,
            maxSlabAmount = 9999999.0,
            description = "Bit department 5% GST",
            ruleName = "Bit Special Rule"
        )
        val rate = TaxMasterService.getTaxRate(
            itemName = "bit",
            categoryName = "mens",
            costAmount = 3200.0,
            categoryTaxRules = listOf(customRule),
            division = "mens",
            department = "bit",
            section = "shirts"
        )
        assertEquals(5.0, rate, 0.001)
    }

    @Test
    fun testTextileKeywordAlwaysFivePercent() {
        val rateSaree = TaxMasterService.getTaxRate(
            itemName = "Handloom Designer Saree",
            categoryName = "",
            costAmount = 8500.0
        )
        assertEquals(5.0, rateSaree, 0.001)

        val rateFabric = TaxMasterService.getTaxRate(
            itemName = "Linen Shirting Fabric",
            categoryName = "",
            costAmount = 3200.0
        )
        assertEquals(5.0, rateFabric, 0.001)
    }

    @Test
    fun testReadymadeCategoryBelow2500_isFivePercent() {
        val rate1 = TaxMasterService.getTaxRate(
            itemName = "Cotton Formal Shirt",
            categoryName = "Readymade",
            costAmount = 999.0,
            division = "Readymade"
        )
        assertEquals(5.0, rate1, 0.001)

        val rate2 = TaxMasterService.getTaxRate(
            itemName = "Denim Jeans",
            categoryName = "Readymade",
            costAmount = 2499.0,
            division = "Readymade"
        )
        assertEquals(5.0, rate2, 0.001)
    }

    @Test
    fun testReadymadeCategory2500AndAbove_isEighteenPercent() {
        val rateAt2500 = TaxMasterService.getTaxRate(
            itemName = "Premium Blazer Suit",
            categoryName = "Readymade",
            costAmount = 2500.0,
            division = "Readymade"
        )
        assertEquals(18.0, rateAt2500, 0.001)

        val rateAbove2500 = TaxMasterService.getTaxRate(
            itemName = "Designer Party Wear Gown",
            categoryName = "Readymade",
            costAmount = 6500.0,
            division = "Readymade"
        )
        assertEquals(18.0, rateAbove2500, 0.001)
    }

    @Test
    fun testDefaultGeneralItemTaxSlabs() {
        val rateBelow2500 = TaxMasterService.getTaxRate(
            itemName = "Item #1",
            categoryName = "",
            costAmount = 1500.0
        )
        assertEquals(5.0, rateBelow2500, 0.001)

        val rateAbove2500 = TaxMasterService.getTaxRate(
            itemName = "Item #2",
            categoryName = "",
            costAmount = 4500.0
        )
        assertEquals(18.0, rateAbove2500, 0.001)
    }
}
