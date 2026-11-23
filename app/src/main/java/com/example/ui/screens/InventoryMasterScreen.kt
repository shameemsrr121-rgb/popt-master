package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.example.data.entity.CategoryTaxEntity
import com.example.data.entity.InventoryEntity
import com.example.service.TaxMasterService
import com.example.ui.components.HierarchyTaxStructureBoxDialog
import com.example.ui.viewmodel.POMasterViewModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryMasterScreen(
    viewModel: POMasterViewModel
) {
    val inventoryList by viewModel.inventoryState.collectAsState()
    val suppliers by viewModel.suppliersState.collectAsState()
    val storeHierarchy by viewModel.storeHierarchyState.collectAsState()
    val categoryTaxRules by viewModel.categoryTaxRulesState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    var showAddEditDialog by remember { mutableStateOf(false) }
    var showHierarchyTaxBoxDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<InventoryEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<InventoryEntity?>(null) }

    val context = LocalContext.current
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }

    val supplierMap = remember(suppliers) { suppliers.associateBy { it.id } }

    val categoryFilterOptions = remember(inventoryList, categoryTaxRules) {
        (listOf("All") +
                categoryTaxRules.map { it.categoryName.trim() } +
                inventoryList.map { it.category.trim() })
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith { a, b ->
                if (a == "All") -1 else if (b == "All") 1 else a.compareTo(b)
            }
    }

    val filteredList = remember(inventoryList, searchQuery, selectedCategoryFilter) {
        inventoryList.filter { item ->
            val matchesCategory = selectedCategoryFilter == "All" ||
                    item.category.equals(selectedCategoryFilter, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    item.name.contains(searchQuery, ignoreCase = true) ||
                    item.sku.contains(searchQuery, ignoreCase = true) ||
                    item.department.contains(searchQuery, ignoreCase = true) ||
                    item.category.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    itemToEdit = null
                    showAddEditDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = "Add Inventory Item") },
                text = { Text("Add Item") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_inventory_fab")
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inventory_search_input"),
                placeholder = { Text("Search item name, category, SKU...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Category Filter Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categoryFilterOptions) { cat ->
                    FilterChip(
                        selected = selectedCategoryFilter == cat,
                        onClick = { selectedCategoryFilter = cat },
                        label = { Text(cat) },
                        modifier = Modifier.testTag("inventory_filter_$cat")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dedicated Box: Hierarchy & Category Tax Structure Creation Bar
            Surface(
                onClick = { showHierarchyTaxBoxDialog = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                tonalElevation = 2.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_hierarchy_tax_box_bar")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountTree,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Hierarchy & Tax Structure Box",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Create Division ➔ Section ➔ Dept with Category to determine tax",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = { showHierarchyTaxBoxDialog = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open Box", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Inventory List
            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No inventory items found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        InventoryCard(
                            item = item,
                            supplierName = supplierMap[item.defaultSupplierId]?.name ?: "Unassigned",
                            currencyFormat = currencyFormat,
                            categoryTaxRules = categoryTaxRules,
                            onEdit = {
                                itemToEdit = item
                                showAddEditDialog = true
                            },
                            onDelete = {
                                itemToDelete = item
                            }
                        )
                    }
                }
            }
        }
    }

    // Dedicated Hierarchy & Tax Structure Box Dialog
    if (showHierarchyTaxBoxDialog) {
        HierarchyTaxStructureBoxDialog(
            viewModel = viewModel,
            onDismiss = { showHierarchyTaxBoxDialog = false }
        )
    }

    // Add / Edit Item Dialog
    if (showAddEditDialog) {
        InventoryAddEditDialog(
            item = itemToEdit,
            suppliers = suppliers,
            storeHierarchy = storeHierarchy,
            inventoryList = inventoryList,
            categoryTaxRules = categoryTaxRules,
            onAddHierarchyNode = { div, sec, dept ->
                viewModel.addHierarchyNode(div, sec, dept)
            },
            onAddCategoryRule = { categoryName ->
                viewModel.saveCategoryTaxRule(
                    categoryName = categoryName,
                    ruleName = "$categoryName Standard Rule"
                )
            },
            onSaveCategoryTaxRule = { categoryName, rate, hsn ->
                viewModel.saveCategoryTaxRule(
                    categoryName = categoryName,
                    hsnCode = hsn,
                    taxRatePercent = rate,
                    ruleName = "$categoryName Standard Tax Rule"
                )
            },
            onDismiss = { showAddEditDialog = false },
            onSave = { sku, name, cat, unit, cost, price, stock, reorder, suppId, div, sec, dept, c1, c2, c3, c4, c5, c6 ->
                viewModel.saveInventoryItem(
                    id = itemToEdit?.id ?: 0L,
                    sku = sku,
                    name = name,
                    category = cat,
                    unit = unit,
                    unitCost = cost,
                    sellingPrice = price,
                    currentStock = stock,
                    minReorderLevel = reorder,
                    defaultSupplierId = suppId,
                    division = div,
                    section = sec,
                    department = dept,
                    category1 = c1,
                    category2 = c2,
                    category3 = c3,
                    category4 = c4,
                    category5 = c5,
                    category6 = c6
                )
                showAddEditDialog = false
            }
        )
    }

    // Delete Confirmation Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Inventory Item") },
            text = { Text("Are you sure you want to delete '${item.name}'? This will remove the item from local records.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteInventoryItem(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun InventoryCard(
    item: InventoryEntity,
    supplierName: String,
    currencyFormat: NumberFormat,
    categoryTaxRules: List<CategoryTaxEntity> = emptyList(),
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val determinedTaxRate = remember(item, categoryTaxRules) {
        TaxMasterService.getTaxRate(
            itemName = item.name,
            categoryName = item.category,
            costAmount = item.unitCost,
            categoryTaxRules = categoryTaxRules,
            division = item.division,
            department = item.department,
            section = item.section
        )
    }

    val taxExplanation = remember(item, categoryTaxRules) {
        TaxMasterService.getTaxExplanation(
            division = item.division,
            categoryName = item.category,
            department = item.department,
            costAmount = item.unitCost,
            categoryTaxRules = categoryTaxRules
        )
    }

    val taxOnUnitCost = item.unitCost * (determinedTaxRate / 100.0)
    val landedCost = item.unitCost + taxOnUnitCost

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inventory_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = item.category.ifBlank { "General" },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }

                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text(
                            text = "${item.division.ifBlank { "Main Store" }} ➔ ${item.section.ifBlank { "General" }} ➔ Dept: ${item.department.ifBlank { item.category }}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Item", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Item", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(6.dp))

            // Quick Details Row
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Supplier: $supplierName",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "SKU: ${item.sku}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Tax Structure & Cost Display Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "Tax Structure: $determinedTaxRate% GST (${taxExplanation.take(25)})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Cost: ${currencyFormat.format(item.unitCost)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Landed: ${currencyFormat.format(landedCost)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryAddEditDialog(
    item: InventoryEntity?,
    suppliers: List<com.example.data.entity.SupplierEntity>,
    storeHierarchy: List<com.example.data.entity.StoreHierarchyEntity>,
    inventoryList: List<InventoryEntity>,
    categoryTaxRules: List<com.example.data.entity.CategoryTaxEntity> = emptyList(),
    onAddHierarchyNode: (div: String, sec: String, dept: String) -> Unit,
    onAddCategoryRule: (categoryName: String) -> Unit = {},
    onSaveCategoryTaxRule: ((categoryName: String, rate: Double, hsn: String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (
        sku: String,
        name: String,
        category: String,
        unit: String,
        unitCost: Double,
        sellingPrice: Double,
        currentStock: Int,
        minReorderLevel: Int,
        defaultSupplierId: Long?,
        division: String,
        section: String,
        department: String,
        category1: String,
        category2: String,
        category3: String,
        category4: String,
        category5: String,
        category6: String
    ) -> Unit
) {
    val availableCategories = remember(categoryTaxRules, inventoryList) {
        val taxCats = categoryTaxRules.map { it.categoryName.trim() }.filter { it.isNotBlank() }
        val invCats = inventoryList.map { it.category.trim() }.filter { it.isNotBlank() }
        val defaults = listOf("Readymade", "Textile")
        (taxCats + invCats + defaults).distinct().sorted()
    }
    var sku by remember { mutableStateOf(item?.sku ?: "ITEM-${(100..999).random()}") }
    var category by remember { mutableStateOf(item?.category ?: availableCategories.firstOrNull() ?: "") }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var unit by remember { mutableStateOf(item?.unit ?: "Pcs") }
    var unitCostStr by remember { mutableStateOf(item?.unitCost?.toString() ?: "0.0") }
    var sellingPriceStr by remember { mutableStateOf(item?.standardSellingPrice?.toString() ?: "0.0") }
    var selectedSupplierId by remember { mutableStateOf(item?.defaultSupplierId ?: suppliers.firstOrNull()?.id) }

    // Hierarchy: 1. Division, 2. Section (below Division), 3. Department (Type only)
    var division by remember { mutableStateOf(item?.division ?: "") }
    var divisionDropdownExpanded by remember { mutableStateOf(false) }

    var section by remember { mutableStateOf(item?.section ?: "") }
    var sectionDropdownExpanded by remember { mutableStateOf(false) }

    var department by remember { mutableStateOf(item?.department ?: (item?.category ?: "")) }
    var departmentDropdownExpanded by remember { mutableStateOf(false) }

    var statusNotice by remember { mutableStateOf<String?>(null) }

    // Existing divisions in DB
    val existingDivisions = remember(storeHierarchy, inventoryList) {
        (storeHierarchy.map { it.division.trim() } + inventoryList.map { it.division.trim() })
            .filter { it.isNotBlank() }.distinct().sorted()
    }

    // Existing sections for selected division in DB
    val existingSectionsForDivision = remember(storeHierarchy, inventoryList, division) {
        if (division.isBlank()) emptyList()
        else {
            val fromHierarchy = storeHierarchy
                .filter { it.division.equals(division, ignoreCase = true) && it.section.isNotBlank() }
                .map { it.section.trim() }
            val fromInventory = inventoryList
                .filter { it.division.equals(division, ignoreCase = true) && it.section.isNotBlank() }
                .map { it.section.trim() }
            (fromHierarchy + fromInventory).filter { it.isNotBlank() }.distinct().sorted()
        }
    }

    // Existing departments for selected section in DB
    val existingDepartmentsForSection = remember(storeHierarchy, inventoryList, division, section) {
        val fromHierarchy = storeHierarchy
            .filter {
                (division.isBlank() || it.division.equals(division, ignoreCase = true)) &&
                        (section.isBlank() || it.section.equals(section, ignoreCase = true)) &&
                        it.department.isNotBlank()
            }
            .map { it.department.trim() }
        val fromInventory = inventoryList
            .filter {
                (division.isBlank() || it.division.equals(division, ignoreCase = true)) &&
                        (section.isBlank() || it.section.equals(section, ignoreCase = true)) &&
                        it.department.isNotBlank()
            }
            .map { it.department.trim() }
        (fromHierarchy + fromInventory).filter { it.isNotBlank() }.distinct().sorted()
    }

    val derivedName by remember {
        derivedStateOf {
            val hierarchy = listOf(division, section, department).filter { it.isNotBlank() }.joinToString(" • ")
            if (hierarchy.isNotBlank()) hierarchy else if (category.isNotBlank()) category else "New Item"
        }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val safeDismiss = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        title = { Text(if (item == null) "Add Inventory Item" else "Edit Item Details") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .pointerInput(Unit) {
                        detectTapGestures {
                            focusManager.clearFocus()
                        }
                    }
            ) {
                if (statusNotice != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = statusNotice ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                // --- 1. DIVISION ---
                item {
                    Text(
                        text = "1. Division",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = division,
                            onValueChange = { division = it },
                            label = { Text("Division Name *") },
                            placeholder = { Text("Type or select Division...") },
                            singleLine = true,
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { divisionDropdownExpanded = !divisionDropdownExpanded },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Division")
                                    }
                                    Button(
                                        onClick = {
                                            if (division.isNotBlank()) {
                                                onAddHierarchyNode(division, "", "")
                                                statusNotice = "Division '$division' saved to Database!"
                                            }
                                        },
                                        enabled = division.isNotBlank(),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .padding(end = 4.dp)
                                            .testTag("add_division_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("ADD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inventory_division_input")
                        )

                        DropdownMenu(
                            expanded = divisionDropdownExpanded,
                            onDismissRequest = { divisionDropdownExpanded = false }
                        ) {
                            if (existingDivisions.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No Divisions created yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) },
                                    onClick = { divisionDropdownExpanded = false }
                                )
                            } else {
                                existingDivisions.forEach { divOpt ->
                                    DropdownMenuItem(
                                        text = { Text(divOpt, fontWeight = if (divOpt.equals(division, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            division = divOpt
                                            divisionDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 2. SECTION (BELOW DIVISION) ---
                item {
                    Text(
                        text = "2. Section (Belongs to Division selected above)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = section,
                            onValueChange = { section = it },
                            label = { Text("Section Name *") },
                            placeholder = {
                                Text(if (division.isBlank()) "Select or type Division above first *" else "Type or select Section for $division...")
                            },
                            singleLine = true,
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { sectionDropdownExpanded = !sectionDropdownExpanded },
                                        enabled = division.isNotBlank(),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Section")
                                    }
                                    Button(
                                        onClick = {
                                            if (division.isBlank()) {
                                                statusNotice = "Please enter or select a Division first!"
                                            } else if (section.isNotBlank()) {
                                                onAddHierarchyNode(division, section, "")
                                                statusNotice = "Section '$section' created under Division '$division'!"
                                            }
                                        },
                                        enabled = division.isNotBlank() && section.isNotBlank(),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .padding(end = 4.dp)
                                            .testTag("add_section_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("ADD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inventory_section_input")
                        )

                        DropdownMenu(
                            expanded = sectionDropdownExpanded,
                            onDismissRequest = { sectionDropdownExpanded = false }
                        ) {
                            if (existingSectionsForDivision.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(if (division.isBlank()) "Select Division first" else "No sections created under '$division' yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) },
                                    onClick = { sectionDropdownExpanded = false }
                                )
                            } else {
                                existingSectionsForDivision.forEach { secOpt ->
                                    DropdownMenuItem(
                                        text = { Text(secOpt, fontWeight = if (secOpt.equals(section, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            section = secOpt
                                            sectionDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 3. DEPARTMENT (UNDER SECTION) ---
                item {
                    Text(
                        text = "3. Department (Belongs to Section selected above)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = department,
                            onValueChange = { department = it },
                            label = { Text("Department Name *") },
                            placeholder = { Text("Type or select Department (e.g. Formal Shirts, Kurtis)...") },
                            singleLine = true,
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { departmentDropdownExpanded = !departmentDropdownExpanded },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Department")
                                    }
                                    Button(
                                        onClick = {
                                            if (division.isBlank()) {
                                                statusNotice = "Please enter Division first!"
                                            } else if (department.isNotBlank()) {
                                                onAddHierarchyNode(division, section, department)
                                                statusNotice = "Department '$department' created under Section '$section'!"
                                            }
                                        },
                                        enabled = division.isNotBlank() && department.isNotBlank(),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .padding(end = 4.dp)
                                            .testTag("add_department_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("ADD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inventory_department_input")
                        )

                        DropdownMenu(
                            expanded = departmentDropdownExpanded,
                            onDismissRequest = { departmentDropdownExpanded = false }
                        ) {
                            if (existingDepartmentsForSection.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No departments created yet under '$section'", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) },
                                    onClick = { departmentDropdownExpanded = false }
                                )
                            } else {
                                existingDepartmentsForSection.forEach { deptOpt ->
                                    DropdownMenuItem(
                                        text = { Text(deptOpt, fontWeight = if (deptOpt.equals(department, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal) },
                                        onClick = {
                                            department = deptOpt
                                            departmentDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // --- 4. CATEGORY & TAX STRUCTURE DETERMINATION BOX ---
                item {
                    Text(
                        text = "4. Category & Tax Structure Determination",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = { category = it },
                            label = { Text("Category Name *") },
                            placeholder = { Text("Type or select Category...") },
                            singleLine = true,
                            trailingIcon = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { categoryDropdownExpanded = !categoryDropdownExpanded },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Category")
                                    }
                                    Button(
                                        onClick = {
                                            if (category.isNotBlank()) {
                                                onAddCategoryRule(category)
                                                statusNotice = "Category '$category' added with standard tax rule!"
                                            }
                                        },
                                        enabled = category.isNotBlank(),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .height(32.dp)
                                            .padding(end = 4.dp)
                                            .testTag("add_category_button")
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text("ADD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inventory_category_input")
                        )

                        DropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false }
                        ) {
                            availableCategories.forEach { catOpt ->
                                DropdownMenuItem(
                                    text = { Text(catOpt, fontWeight = if (catOpt.equals(category, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = {
                                        category = catOpt
                                        categoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // --- 5. LIVE DETERMINED TAX STRUCTURE PANEL ---
                item {
                    val currentCost = unitCostStr.toDoubleOrNull() ?: 0.0
                    val determinedTaxRate = TaxMasterService.getTaxRate(
                        itemName = derivedName,
                        categoryName = category,
                        costAmount = currentCost,
                        categoryTaxRules = categoryTaxRules,
                        division = division,
                        department = department,
                        section = section
                    )
                    val determinedExplanation = TaxMasterService.getTaxExplanation(
                        division = division,
                        categoryName = category,
                        department = department,
                        costAmount = currentCost,
                        categoryTaxRules = categoryTaxRules
                    )
                    val itemTaxAmt = currentCost * (determinedTaxRate / 100.0)
                    val effectiveLandedCost = currentCost + itemTaxAmt

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        tonalElevation = 1.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Determined Tax Structure",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "$determinedTaxRate% GST",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "CGST: ${determinedTaxRate / 2.0}% | SGST: ${determinedTaxRate / 2.0}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )

                            Text(
                                text = "Rule: $determinedExplanation",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )

                            if (currentCost > 0.0) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "At Cost ₹${"%.2f".format(currentCost)} ➔ GST: ₹${"%.2f".format(itemTaxAmt)} | Landed: ₹${"%.2f".format(effectiveLandedCost)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Quick Tax Structure Assignment for this Category
                            Text(
                                text = "Set Category Tax Rate Preset:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                listOf(
                                    5.0 to "5% Flat",
                                    18.0 to "18% Ready",
                                    28.0 to "28% Lux"
                                ).forEach { (rate, label) ->
                                    AssistChip(
                                        onClick = {
                                            if (category.isNotBlank() && onSaveCategoryTaxRule != null) {
                                                onSaveCategoryTaxRule(category, rate, "")
                                                statusNotice = "Tax Structure set to $rate% GST for '$category'!"
                                            }
                                        },
                                        label = { Text(label, fontSize = 11.sp) },
                                        leadingIcon = {
                                            if (determinedTaxRate == rate) {
                                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    val cost = unitCostStr.toDoubleOrNull() ?: 0.0
                    val price = sellingPriceStr.toDoubleOrNull() ?: 0.0
                    val stock = 100
                    val reorder = 0
                    val finalDept = if (department.isNotBlank()) department else if (category.isNotBlank()) category else "Department"
                    onSave(
                        sku, derivedName, category, unit, cost, price, stock, reorder, selectedSupplierId,
                        division, section, finalDept,
                        "", "", "", "", "", ""
                    )
                }
            ) {
                Text("Save Item")
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) {
                Text("Cancel")
            }
        }
    )
}

