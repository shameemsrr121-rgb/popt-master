package com.example.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val contactPerson: String,
    val phone: String,
    val email: String,
    val address: String,
    val paymentTerms: String, // e.g., "Net 30", "Net 60", "COD", "Advance"
    val rating: Float,        // 1.0 to 5.0
    val category: String,     // e.g., "Readymade", "Textile"
    val defaultMarginPercent: Double = 30.0, // Default supplier margin setting
    val isActive: Boolean = true,
    val gstNumber: String = "" // Supplier GSTIN Number
)

@Entity(tableName = "company_profile")
data class CompanyProfileEntity(
    @PrimaryKey val id: Long = 1,
    val companyName: String = "Garden Ayisha Textiles & Readymades",
    val address: String = "Purasawalkam | T.Nagar | Avadi",
    val phone: String = "+91 98400 12345",
    val email: String = "info@gardenayisha.com",
    val gstNumber: String = "", // Optional GSTIN
    val website: String = "www.gardenayisha.com",
    val logoUri: String? = null,
    val bankName: String = "",
    val accountNumber: String = "",
    val ifscCode: String = "",
    val termsAndConditions: String = ""
)

@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sku: String,
    val name: String,
    val category: String,
    val unit: String,                 // "Pcs", "Kg", "Liters", "Boxes", "Sheets"
    val unitCost: Double,            // Purchasing Cost
    val standardSellingPrice: Double, // Selling Price
    val currentStock: Int,
    val minReorderLevel: Int,
    val defaultSupplierId: Long? = null,
    // Retail Clothing Store Hierarchy: Division --> Section --> Department
    val division: String = "Readymade",
    val section: String = "Main Store",
    val department: String = "General",
    // Up to 6 Category Boxes for Clothing Store Attributes
    val category1: String = "", // e.g. Brand / Designer
    val category2: String = "", // e.g. Season / Collection
    val category3: String = "", // e.g. Fabric / Material
    val category4: String = "", // e.g. Fit / Style
    val category5: String = "", // e.g. Size Matrix
    val category6: String = ""  // e.g. Color / Pattern
)

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val address: String = "",
    val type: String = "Warehouse", // "Warehouse", "Retail Outlet", "Distribution Hub"
    val isActive: Boolean = true
)

@Entity(tableName = "purchase_orders")
data class PurchaseOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val poNumber: String,
    val supplierId: Long,
    val supplierName: String,
    val orderDateMillis: Long,
    val expectedDeliveryDateMillis: Long,
    val paymentTerms: String,
    val status: String, // "DRAFT", "ISSUED", "PARTIALLY_RECEIVED", "RECEIVED", "CANCELLED"
    val subtotal: Double,
    val taxAmount: Double,
    val totalAmount: Double,
    val notes: String = "",
    val photoUri: String? = null, // Reference image captured for PO
    val location: String = "Central Warehouse" // Target store location(s)
)

@Entity(tableName = "po_items")
data class PurchaseOrderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val poId: Long,
    val inventoryItemId: Long,
    val itemSku: String,
    val itemName: String,
    val quantityOrdered: Int,
    val quantityReceived: Int = 0,
    val unitPrice: Double,
    val totalPrice: Double,
    val taxPercent: Double = 5.0, // e.g., 5.0, 12.0, 18.0, 28.0, 0.0
    val taxableAmount: Double = 0.0, // unitPrice * quantityOrdered
    val taxAmount: Double = 0.0, // taxableAmount * (taxPercent / 100.0)
    val photoUri: String? = null, // Reference image captured for product line item
    val location: String = "Central Warehouse", // Specific location for line item
    val division: String = "",
    val section: String = "",
    val department: String = "",
    val c1Brand: String = "",
    val c2Size: String = "",
    val c3Design: String = "",
    val c4Colour: String = ""
)

@Entity(tableName = "po_tax_breakdowns")
data class POTaxBreakdownEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val poId: Long,
    val gstPercent: Double,              // e.g., 5.0, 12.0, 18.0, 28.0, 0.0
    val taxableAmount: Double,           // Sum of taxable amount for this GST slab
    val cgstPercent: Double,             // gstPercent / 2.0
    val cgstAmount: Double,              // taxAmount / 2.0
    val sgstPercent: Double,             // gstPercent / 2.0
    val sgstAmount: Double,              // taxAmount / 2.0
    val totalTaxAmount: Double           // cgstAmount + sgstAmount
)

data class POTaxBreakdownSummary(
    val gstPercent: Double,
    val taxableAmount: Double,
    val taxAmount: Double,
    val itemCount: Int
)

@Entity(tableName = "margin_rules")
data class MarginRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val targetName: String, // Category or SKU or "Default"
    val targetMarginPercent: Double,  // e.g., 25.0 for 25%
    val minMarginPercent: Double,     // e.g., 15.0
    val taxPercent: Double,           // e.g., 18.0
    val overheadPercent: Double,      // e.g., 5.0
    val defaultMarkupPercent: Double  // e.g., 30.0
)

@Entity(tableName = "users")
data class UserAccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val passwordHash: String, // Plain string password for simplicity
    val fullName: String = "",
    val role: String = "User", // e.g., "Administrator", "Procurement Lead", "Store Keeper"
    val isAdmin: Boolean = false,
    val canDeletePO: Boolean = true, // Right to delete POs
    
    // Module access rights
    val showProcurement: Boolean = true,
    val showInventory: Boolean = true,
    val showSupplier: Boolean = true,
    val showReport: Boolean = true,
    val showMargin: Boolean = true,
    val showGoogleSheets: Boolean = true,
    val showAdminPanel: Boolean = false
)

@Entity(tableName = "store_hierarchy")
data class StoreHierarchyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val division: String,
    val section: String = "",
    val department: String = ""
)

@Entity(tableName = "category_tax_rules")
data class CategoryTaxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryName: String,
    val hsnCode: String = "",
    val taxRatePercent: Double = 5.0,
    val minSlabAmount: Double = 0.0,
    val maxSlabAmount: Double = 9999999.0,
    val description: String = "",
    val ruleName: String = ""
)

@Entity(tableName = "purchase_plan_notes")
data class PurchasePlanNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,                           // e.g. "Bombay Trip - Silk Sourcing" or "Aug 27 Bombay"
    val plannedDateMillis: Long,                 // Planned Start Date / From Date
    val toDateMillis: Long = 0L,                 // Planned End Date / To Date (0L or <= plannedDateMillis if single day)
    val location: String = "",                   // e.g. "Bombay", "Surat", "Tirupur", "Local"
    val notes: String = "",                      // Detailed memo, specifications, quantity notes
    val estimatedBudget: Double = 0.0,           // Estimated budget in ₹
    val categoryOrSupplier: String = "",         // Targeted category or vendor name
    val priority: String = "MEDIUM",             // "HIGH", "MEDIUM", "LOW"
    val status: String = "PLANNED",              // "PLANNED", "IN_PROGRESS", "COMPLETED", "CONVERTED_TO_PO"
    val reminderDaysBefore: Int = 1,             // Remind N days before
    val createdAtMillis: Long = System.currentTimeMillis()
)


