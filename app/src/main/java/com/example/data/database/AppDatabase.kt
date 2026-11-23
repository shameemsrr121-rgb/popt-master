package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.*
import com.example.data.entity.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        SupplierEntity::class,
        InventoryEntity::class,
        PurchaseOrderEntity::class,
        PurchaseOrderItemEntity::class,
        POTaxBreakdownEntity::class,
        MarginRuleEntity::class,
        LocationEntity::class,
        UserAccountEntity::class,
        CompanyProfileEntity::class,
        StoreHierarchyEntity::class,
        CategoryTaxEntity::class,
        PurchasePlanNoteEntity::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun supplierDao(): SupplierDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun purchaseOrderDao(): PurchaseOrderDao
    abstract fun marginRuleDao(): MarginRuleDao
    abstract fun locationDao(): LocationDao
    abstract fun userDao(): UserDao
    abstract fun companyProfileDao(): CompanyProfileDao
    abstract fun storeHierarchyDao(): StoreHierarchyDao
    abstract fun categoryTaxDao(): CategoryTaxDao
    abstract fun purchasePlanNoteDao(): PurchasePlanNoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context, scope: CoroutineScope = CoroutineScope(Dispatchers.IO)): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "po_master_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .addCallback(AppDatabaseCallback(scope))
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateInitialData(database)
                }
            }
        }

        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    ensureAdminUser(database)
                    ensureCompanyProfile(database)
                    ensureCategoryTaxRules(database)
                }
            }
        }

        private suspend fun ensureCategoryTaxRules(db: AppDatabase) {
            val dao = db.categoryTaxDao()
            val rules = dao.getAllCategoryTaxRulesList()
            if (rules.none { it.categoryName.equals("Textile", ignoreCase = true) }) {
                dao.insertCategoryTaxRule(
                    CategoryTaxEntity(
                        categoryName = "Textile",
                        ruleName = "Textile Category Flat 5% GST Rule",
                        minSlabAmount = 0.0,
                        maxSlabAmount = 9999999.0,
                        taxRatePercent = 5.0,
                        description = "Textile Category Always 5% Tax"
                    )
                )
            }
            if (rules.none { it.categoryName.equals("Readymade", ignoreCase = true) }) {
                dao.insertCategoryTaxRule(
                    CategoryTaxEntity(
                        categoryName = "Readymade",
                        ruleName = "Readymade Below 2500 Slab (5%)",
                        minSlabAmount = 0.0,
                        maxSlabAmount = 2499.99,
                        taxRatePercent = 5.0,
                        description = "Readymade below ₹2,500 (5% GST)"
                    )
                )
                dao.insertCategoryTaxRule(
                    CategoryTaxEntity(
                        categoryName = "Readymade",
                        ruleName = "Readymade 2500 and Above Slab (18%)",
                        minSlabAmount = 2500.0,
                        maxSlabAmount = 9999999.0,
                        taxRatePercent = 18.0,
                        description = "Readymade ₹2,500 and above (18% GST)"
                    )
                )
            }

            // Ensure initial Store Hierarchy for Readymade & Textile
            val hierarchyDao = db.storeHierarchyDao()
            val existingHierarchy = hierarchyDao.getAllHierarchyList()
            if (existingHierarchy.isEmpty()) {
                val initialNodes = listOf(
                    StoreHierarchyEntity(division = "Readymade", section = "Men's Wear", department = "Shirts & Tops"),
                    StoreHierarchyEntity(division = "Readymade", section = "Men's Wear", department = "Trousers & Jeans"),
                    StoreHierarchyEntity(division = "Readymade", section = "Women's Wear", department = "Kurtis & Salwars"),
                    StoreHierarchyEntity(division = "Readymade", section = "Women's Wear", department = "Western Wear"),
                    StoreHierarchyEntity(division = "Readymade", section = "Kids Wear", department = "Boys Wear"),
                    StoreHierarchyEntity(division = "Readymade", section = "Kids Wear", department = "Girls Wear"),
                    StoreHierarchyEntity(division = "Textile", section = "Sarees", department = "Silk Sarees"),
                    StoreHierarchyEntity(division = "Textile", section = "Sarees", department = "Cotton & Handloom Sarees"),
                    StoreHierarchyEntity(division = "Textile", section = "Fabrics & Suitings", department = "Shirting & Suiting"),
                    StoreHierarchyEntity(division = "Textile", section = "Dhotis & Handlooms", department = "Traditional Dhotis & Lungis")
                )
                initialNodes.forEach { hierarchyDao.insertHierarchy(it) }
            }
        }

        private suspend fun ensureCompanyProfile(db: AppDatabase) {
            val dao = db.companyProfileDao()
            if (dao.getCompanyProfileSync() == null) {
                dao.saveCompanyProfile(CompanyProfileEntity())
            }
        }

        private suspend fun ensureAdminUser(db: AppDatabase) {
            val userDao = db.userDao()
            if (userDao.getUserByUsername("admin") == null) {
                userDao.insertUser(
                    UserAccountEntity(
                        username = "admin",
                        passwordHash = "admin123",
                        fullName = "Master Administrator",
                        role = "System Administrator",
                        isAdmin = true,
                        canDeletePO = true,
                        showProcurement = true,
                        showInventory = true,
                        showSupplier = true,
                        showReport = true,
                        showMargin = true,
                        showGoogleSheets = true,
                        showAdminPanel = true
                    )
                )
            }
        }

        private suspend fun populateInitialData(db: AppDatabase) {
            // 1. Initial Store Locations
            val locationDao = db.locationDao()
            locationDao.insertLocation(
                LocationEntity(code = "LOC-101", name = "Central Warehouse", address = "Main Logistics Hub", type = "Warehouse")
            )
            locationDao.insertLocation(
                LocationEntity(code = "LOC-102", name = "Main Store", address = "Main Commercial Store", type = "Retail Outlet")
            )
            locationDao.insertLocation(
                LocationEntity(code = "LOC-103", name = "Outlet Store #1", address = "City Center Mall", type = "Retail Outlet")
            )

            // 2. Initial User Accounts
            val userDao = db.userDao()
            userDao.insertUser(
                UserAccountEntity(
                    username = "admin",
                    passwordHash = "admin123",
                    fullName = "Master Administrator",
                    role = "System Administrator",
                    isAdmin = true,
                    showProcurement = true,
                    showInventory = true,
                    showSupplier = true,
                    showReport = true,
                    showMargin = true,
                    showGoogleSheets = true,
                    showAdminPanel = true
                )
            )
        }
    }
}
