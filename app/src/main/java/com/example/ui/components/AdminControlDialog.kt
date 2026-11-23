package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.entity.CompanyProfileEntity
import com.example.data.entity.InventoryEntity
import com.example.data.entity.SupplierEntity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.ui.viewmodel.POMasterViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminControlDialog(
    viewModel: POMasterViewModel,
    onDismiss: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val safeDismiss = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    val locations by viewModel.locationsState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Rights, 1 = Location Master, 2 = Company Master

    // Helper currency formatter for INR
    val inrFormatter = remember {
        val f = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"))
        f.minimumFractionDigits = 2
        f.maximumFractionDigits = 2
        f
    }

    Dialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .pointerInput(Unit) {
                    detectTapGestures {
                        focusManager.clearFocus()
                    }
                }
                .testTag("admin_control_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                Icons.Default.AdminPanelSettings,
                                contentDescription = "Admin Control",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Unified Admin Panel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "User Rights • Location Master • Company Master",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    IconButton(onClick = safeDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close Admin Dialog")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Admin Tab Row
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 8.dp,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { focusManager.clearFocus(); selectedTab = 0 },
                        text = { Text("1. User Rights", fontWeight = FontWeight.Bold, softWrap = false) },
                        icon = { Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { focusManager.clearFocus(); selectedTab = 1 },
                        text = { Text("2. Location Master", fontWeight = FontWeight.Bold, softWrap = false) },
                        icon = { Icon(Icons.Default.Place, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { focusManager.clearFocus(); selectedTab = 2 },
                        text = { Text("3. Company Master", fontWeight = FontWeight.Bold, softWrap = false) },
                        icon = { Icon(Icons.Default.Domain, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { focusManager.clearFocus(); selectedTab = 3 },
                        text = { Text("4. Tax Master (Category Manager)", fontWeight = FontWeight.Bold, softWrap = false) },
                        icon = { Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Content switching
                when (selectedTab) {
                    0 -> AdminUserRightsSection(viewModel = viewModel)
                    1 -> AdminLocationMasterSection(
                        locations = locations,
                        onAddLocation = { code, name, address, type ->
                            viewModel.saveLocation(code = code, name = name, address = address, type = type)
                        },
                        onDeleteLocation = { loc ->
                            viewModel.deleteLocation(loc)
                        }
                    )
                    2 -> AdminCompanyMasterSection(viewModel = viewModel)
                    3 -> AdminTaxMasterSection(viewModel = viewModel)
                }
            }
        }
    }
}

// Sub-Section 1: Admin Supplier Control
@Composable
private fun AdminSupplierControlSection(
    suppliers: List<SupplierEntity>,
    onAddSupplier: (name: String, code: String, contact: String, phone: String, email: String, address: String, margin: Double, rating: Double) -> Unit,
    onDeleteSupplier: (SupplierEntity) -> Unit
) {
    var code by remember { mutableStateOf("SUP-${(100..999).random()}") }
    var name by remember { mutableStateOf("") }
    var contactPerson by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var marginStr by remember { mutableStateOf("15.0") }
    var ratingStr by remember { mutableStateOf("4.5") }

    var showSuccessBanner by remember { mutableStateOf(false) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = "Register New Supplier (Admin)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (showSuccessBanner) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Supplier registered successfully in database!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Supplier Name *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = contactPerson,
                    onValueChange = { contactPerson = it },
                    label = { Text("Contact Person") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Office Address / Location") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        val margin = marginStr.toDoubleOrNull() ?: 15.0
                        val rating = ratingStr.toDoubleOrNull() ?: 4.5
                        onAddSupplier(name, code, contactPerson, phone, email, address, margin, rating)
                        name = ""
                        contactPerson = ""
                        phone = ""
                        email = ""
                        address = ""
                        code = "SUP-${(100..999).random()}"
                        showSuccessBanner = true
                    }
                },
                enabled = name.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_save_supplier_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create Supplier")
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Registered Suppliers (${suppliers.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        items(suppliers) { sup ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = sup.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(text = "Contact: ${sup.contactPerson.ifBlank { "N/A" }}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { onDeleteSupplier(sup) },
                            modifier = Modifier.size(28.dp).testTag("admin_delete_supplier_${sup.id}")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Supplier",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// Sub-Section 2: Admin Inventory Tree Control
@Composable
private fun AdminInventoryTreeControlSection(
    inventoryList: List<InventoryEntity>,
    suppliers: List<SupplierEntity>,
    inrFormatter: NumberFormat,
    onAddDepartmentNode: (div: String, sec: String, dept: String) -> Unit,
    onDeleteInventoryItem: (InventoryEntity) -> Unit
) {
    // Group inventory into Division -> Section -> Department -> Items
    val treeData = remember(inventoryList) {
        inventoryList.groupBy { it.division.ifBlank { "General Division" } }
            .mapValues { (_, divItems) ->
                divItems.groupBy { it.section.ifBlank { "General Section" } }
                    .mapValues { (_, secItems) ->
                        secItems.groupBy { it.department.ifBlank { "General Department" } }
                    }
            }
    }

    var showAddDeptDialog by remember { mutableStateOf(false) }
    var newDivision by remember { mutableStateOf("") }
    var newSection by remember { mutableStateOf("") }
    var newDeptName by remember { mutableStateOf("") }
    var showSuccessMsg by remember { mutableStateOf(false) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Store Hierarchy Structure (Division ➔ Section ➔ Department)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Create and manage custom store Divisions, Sections, and Departments.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newDivision,
                            onValueChange = { newDivision = it },
                            label = { Text("Division") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newSection,
                            onValueChange = { newSection = it },
                            label = { Text("Section") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newDeptName,
                            onValueChange = { newDeptName = it },
                            label = { Text("New Department Name *") },
                            singleLine = true,
                            modifier = Modifier.weight(1.5f)
                        )
                        Button(
                            onClick = {
                                if (newDeptName.isNotBlank()) {
                                    onAddDepartmentNode(newDivision, newSection, newDeptName)
                                    newDeptName = ""
                                    showSuccessMsg = true
                                }
                            },
                            enabled = newDeptName.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.CenterVertically)
                                .testTag("admin_add_dept_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Create Dept")
                        }
                    }

                    if (showSuccessMsg) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "✓ Department structure added to hierarchy!",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Existing Store Tree Hierarchy",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        if (treeData.isEmpty()) {
            item {
                Text("No inventory structure found in tree.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        } else {
            treeData.forEach { (divisionName, sectionsMap) ->
                item {
                    var divExpanded by remember { mutableStateOf(true) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { divExpanded = !divExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (divExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "DIVISION: $divisionName",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Icon(
                                    if (divExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null
                                )
                            }

                            if (divExpanded) {
                                Spacer(modifier = Modifier.height(6.dp))
                                sectionsMap.forEach { (sectionName, deptsMap) ->
                                    Column(
                                        modifier = Modifier
                                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
                                            .fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.SubdirectoryArrowRight,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.secondary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "SECTION: $sectionName",
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.secondary
                                            )
                                        }

                                        deptsMap.forEach { (deptName, itemsInDept) ->
                                            Column(
                                                modifier = Modifier
                                                    .padding(start = 24.dp, top = 4.dp, bottom = 4.dp)
                                                    .fillMaxWidth()
                                                    .background(
                                                        MaterialTheme.colorScheme.surface,
                                                        RoundedCornerShape(6.dp)
                                                    )
                                                    .padding(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Default.Label,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(14.dp),
                                                            tint = MaterialTheme.colorScheme.tertiary
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = "DEPT: $deptName",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer
                                                    ) {
                                                        Text(
                                                            text = "${itemsInDept.size} Items",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }

                                                itemsInDept.forEach { item ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(start = 8.dp, top = 4.dp, end = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "• ${item.name}",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        IconButton(
                                                            onClick = { onDeleteInventoryItem(item) },
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .testTag("admin_delete_item_${item.id}")
                                                        ) {
                                                            Icon(
                                                                Icons.Default.Delete,
                                                                contentDescription = "Delete Item",
                                                                tint = MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Sub-Section 3: Admin Tax Master
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminTaxMasterSection(viewModel: POMasterViewModel) {
    val categoryTaxRules by viewModel.categoryTaxRulesState.collectAsState()

    var categoryName by remember { mutableStateOf("") }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var ruleName by remember { mutableStateOf("") }
    var taxRateStr by remember { mutableStateOf("5.0") }
    var minCostStr by remember { mutableStateOf("0") }
    var maxCostStr by remember { mutableStateOf("9999999") }
    var description by remember { mutableStateOf("Category GST Tax Rule") }

    val existingCategories = remember(categoryTaxRules) {
        val rules = categoryTaxRules.map { it.categoryName.trim() }.filter { it.isNotBlank() }
        (rules + listOf("Readymade", "Textile")).distinct().sorted()
    }

    var showSuccessBanner by remember { mutableStateOf(false) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column {
                Text(
                    text = "Tax Master & Category Manager",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Create categories and configure tax slabs (cost ranges & GST rates) for tax calculation rules.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        if (showSuccessBanner) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "New Category Tax & Cost Rule saved to Database!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // Category Selection / Entry Field with ADD Button
        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
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
                                    if (categoryName.isNotBlank()) {
                                        val rate = taxRateStr.toDoubleOrNull() ?: 5.0
                                        val minCost = minCostStr.toDoubleOrNull() ?: 0.0
                                        val maxCost = maxCostStr.toDoubleOrNull() ?: 9999999.0
                                        viewModel.saveCategoryTaxRule(
                                            categoryName = categoryName.trim(),
                                            hsnCode = "",
                                            taxRatePercent = rate,
                                            minSlabAmount = minCost,
                                            maxSlabAmount = maxCost,
                                            description = description,
                                            ruleName = ruleName.ifBlank { "${categoryName.trim()} $rate% Rule" }
                                        )
                                        showSuccessBanner = true
                                    }
                                },
                                enabled = categoryName.isNotBlank(),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .height(32.dp)
                                    .padding(end = 4.dp)
                                    .testTag("admin_add_category_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("ADD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                DropdownMenu(
                    expanded = categoryDropdownExpanded,
                    onDismissRequest = { categoryDropdownExpanded = false }
                ) {
                    existingCategories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat, fontWeight = if (cat.equals(categoryName, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                categoryName = cat
                                categoryDropdownExpanded = false
                                if (ruleName.isBlank() || ruleName.contains("%")) {
                                    ruleName = "$cat GST Rule"
                                }
                            }
                        )
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Quick Tax Slab Selection:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(
                        5.0 to "5% Flat",
                        18.0 to "18% Ready",
                        28.0 to "28% Lux",
                        0.0 to "0% Nil"
                    ).forEach { (rate, label) ->
                        FilterChip(
                            selected = taxRateStr == rate.toString() || (rate % 1.0 == 0.0 && taxRateStr == rate.toInt().toString()),
                            onClick = {
                                taxRateStr = if (rate % 1.0 == 0.0) rate.toInt().toString() else rate.toString()
                                if (ruleName.isBlank() || ruleName.contains("%") || ruleName.contains("Slab")) {
                                    ruleName = "${categoryName.ifBlank { "Category" }} ${rate.toInt()}% Slab"
                                }
                            },
                            label = { Text(label, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ruleName,
                    onValueChange = { ruleName = it },
                    label = { Text("Rule Title *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1.5f)
                )
                OutlinedTextField(
                    value = taxRateStr,
                    onValueChange = { taxRateStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Tax Rate (%) *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Cost Wise Slab Rule Inputs
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Cost Wise Tax Slab (Item Unit Cost Range in ₹):",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minCostStr,
                        onValueChange = { minCostStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Min Cost (₹)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxCostStr,
                        onValueChange = { maxCostStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Max Cost (₹)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description Note") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                onClick = {
                    if (categoryName.isNotBlank()) {
                        val rate = taxRateStr.toDoubleOrNull() ?: 5.0
                        val minCost = minCostStr.toDoubleOrNull() ?: 0.0
                        val maxCost = maxCostStr.toDoubleOrNull() ?: 9999999.0
                        viewModel.saveCategoryTaxRule(
                            categoryName = categoryName,
                            hsnCode = "",
                            taxRatePercent = rate,
                            minSlabAmount = minCost,
                            maxSlabAmount = maxCost,
                            description = description,
                            ruleName = ruleName.ifBlank { "$categoryName $rate% Rule" }
                        )
                        showSuccessBanner = true
                    }
                },
                enabled = categoryName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("admin_save_tax_rule_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ADD CATEGORY TAX RULE")
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text("Database Configured Category Tax Rules:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }

        items(categoryTaxRules, key = { it.id }) { rule ->
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                color = if (rule.categoryName.equals("Textile", ignoreCase = true)) Color(0xFF22C55E) else MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = rule.categoryName,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(rule.ruleName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            text = "Cost Range: ₹${rule.minSlabAmount.toInt()} - ₹${if (rule.maxSlabAmount > 999999) "Above" else rule.maxSlabAmount.toInt()} • GST Rate: ${rule.taxRatePercent}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    IconButton(onClick = { viewModel.deleteCategoryTaxRule(rule) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Rule", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// Sub-Section 4: Admin User Rights Control
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdminUserRightsSection(
    viewModel: POMasterViewModel
) {
    val usersList by viewModel.usersState.collectAsState()

    var editingUserId by remember { mutableLongStateOf(0L) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isAdmin by remember { mutableStateOf(false) }
    var canDeletePO by remember { mutableStateOf(true) }

    // Panel Rights Toggles
    var showProcurement by remember { mutableStateOf(true) }
    var showInventory by remember { mutableStateOf(true) }
    var showSupplier by remember { mutableStateOf(true) }
    var showReport by remember { mutableStateOf(true) }
    var showMargin by remember { mutableStateOf(true) }
    var showGoogleSheets by remember { mutableStateOf(true) }

    var showSuccessBanner by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun clearForm() {
        editingUserId = 0L
        username = ""
        password = ""
        isAdmin = false
        canDeletePO = true
        showProcurement = true
        showInventory = true
        showSupplier = true
        showReport = true
        showMargin = true
        showGoogleSheets = true
        validationError = null
    }

    fun startEditing(user: com.example.data.entity.UserAccountEntity) {
        editingUserId = user.id
        username = user.username
        password = user.passwordHash
        isAdmin = user.isAdmin
        canDeletePO = user.canDeletePO
        showProcurement = user.showProcurement
        showInventory = user.showInventory
        showSupplier = user.showSupplier
        showReport = user.showReport
        showMargin = user.showMargin
        showGoogleSheets = user.showGoogleSheets
        validationError = null
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column {
                Text(
                    text = "User Creation & Access Rights Control",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Create system accounts with custom passwords and specify which panels each user is allowed to access (Supplier, Inventory, Google Sheets, etc.). Only Admin has full control.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showSuccessBanner) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (editingUserId == 0L) "New user account created successfully!" else "User rights and account updated successfully!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        if (validationError != null) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = validationError ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        // Form Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (editingUserId == 0L) "➕ Create New System User" else "✏️ Edit User Rights & Password",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it; validationError = null },
                            label = { Text("Username *") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; validationError = null },
                            label = { Text("Password *") },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle password"
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Switch(
                            checked = isAdmin,
                            onCheckedChange = {
                                isAdmin = it
                                if (it) {
                                    showProcurement = true
                                    showInventory = true
                                    showSupplier = true
                                    showReport = true
                                    showMargin = true
                                    showGoogleSheets = true
                                    canDeletePO = true
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("System Administrator Privileges", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("Admin has complete access to all panels, tax master, and user creation.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = canDeletePO,
                            onCheckedChange = { canDeletePO = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text("PO Delete Rights", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Text("Allow this user to delete purchase orders.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }

                    Text("Allowed Module & Panel Access Rights:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        FilterChip(
                            selected = showProcurement,
                            onClick = { if (!isAdmin) showProcurement = !showProcurement },
                            label = { Text("Procurement Panel") },
                            leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = showInventory,
                            onClick = { if (!isAdmin) showInventory = !showInventory },
                            label = { Text("Inventory Panel") },
                            leadingIcon = { Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = showSupplier,
                            onClick = { if (!isAdmin) showSupplier = !showSupplier },
                            label = { Text("Supplier Panel") },
                            leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = showReport,
                            onClick = { if (!isAdmin) showReport = !showReport },
                            label = { Text("Reports Panel") },
                            leadingIcon = { Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                        FilterChip(
                            selected = showGoogleSheets,
                            onClick = { if (!isAdmin) showGoogleSheets = !showGoogleSheets },
                            label = { Text("Cloud Database Panel") },
                            leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (editingUserId != 0L) {
                            OutlinedButton(
                                onClick = { clearForm() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel Edit")
                            }
                        }

                        Button(
                            onClick = {
                                if (username.isBlank() || password.isBlank()) {
                                    validationError = "Please fill in Username and Password."
                                    return@Button
                                }

                                val newUser = com.example.data.entity.UserAccountEntity(
                                    id = editingUserId,
                                    username = username.trim(),
                                    passwordHash = password.trim(),
                                    fullName = username.trim(),
                                    role = if (isAdmin) "Administrator" else "User",
                                    isAdmin = isAdmin,
                                    canDeletePO = if (isAdmin) true else canDeletePO,
                                    showProcurement = if (isAdmin) true else showProcurement,
                                    showInventory = if (isAdmin) true else showInventory,
                                    showSupplier = if (isAdmin) true else showSupplier,
                                    showReport = if (isAdmin) true else showReport,
                                    showMargin = if (isAdmin) true else showMargin,
                                    showGoogleSheets = if (isAdmin) true else showGoogleSheets,
                                    showAdminPanel = isAdmin
                                )

                                viewModel.saveUser(newUser)
                                showSuccessBanner = true
                                clearForm()
                            },
                            modifier = Modifier.weight(1.5f)
                        ) {
                            Icon(if (editingUserId == 0L) Icons.Default.PersonAdd else Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (editingUserId == 0L) "Save User" else "Update User Rights")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Registered System Users (${usersList.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        items(usersList) { userItem ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (userItem.isAdmin) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = CircleShape,
                                color = if (userItem.isAdmin) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (userItem.isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Column {
                                Text(text = userItem.fullName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "@${userItem.username} • Role: ${userItem.role} ${if (userItem.isAdmin) "• [FULL ADMIN]" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Row {
                            IconButton(onClick = { startEditing(userItem) }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit User Rights", tint = MaterialTheme.colorScheme.primary)
                            }
                            if (userItem.username != "admin") {
                                IconButton(onClick = { viewModel.deleteUser(userItem) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete User", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Allowed Panels:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (userItem.showProcurement) SuggestionChip(onClick = {}, label = { Text("Procurement", style = MaterialTheme.typography.labelSmall) })
                        if (userItem.showInventory) SuggestionChip(onClick = {}, label = { Text("Inventory", style = MaterialTheme.typography.labelSmall) })
                        if (userItem.showSupplier) SuggestionChip(onClick = {}, label = { Text("Suppliers", style = MaterialTheme.typography.labelSmall) })
                        if (userItem.showReport) SuggestionChip(onClick = {}, label = { Text("Reports", style = MaterialTheme.typography.labelSmall) })
                        if (userItem.showGoogleSheets) SuggestionChip(onClick = {}, label = { Text("Cloud DB", style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
        }
    }
}

private data class TaxRuleItem(
    val name: String,
    val hsn: String,
    val rate: Double,
    val description: String
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminLocationMasterSection(
    locations: List<com.example.data.entity.LocationEntity>,
    onAddLocation: (code: String, name: String, address: String, type: String) -> Unit,
    onDeleteLocation: (com.example.data.entity.LocationEntity) -> Unit
) {
    var code by remember { mutableStateOf("LOC-${(100..999).random()}") }
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Warehouse") }
    val typeOptions = listOf("Warehouse", "Retail Outlet", "Distribution Hub", "Rack / Shelf")

    var searchQuery by remember { mutableStateOf("") }

    val filteredLocations = remember(locations, searchQuery) {
        locations.filter {
            searchQuery.isBlank() ||
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.code.contains(searchQuery, ignoreCase = true) ||
                    it.address.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "Location Master Management",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Register stores, warehouses, and distribution locations for PO routing & inventory placement.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Register New Location", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Location Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Address / Details") },
                        singleLine = true,
                        modifier = Modifier.weight(1.5f)
                    )

                    var typeExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = !typeExpanded },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = type,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Type") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            singleLine = true
                        )
                        ExposedDropdownMenu(
                            expanded = typeExpanded,
                            onDismissRequest = { typeExpanded = false }
                        ) {
                            typeOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        type = option
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            onAddLocation(code, name, address, type)
                            code = "LOC-${(100..999).random()}"
                            name = ""
                            address = ""
                        }
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().testTag("add_location_button")
                ) {
                    Icon(Icons.Default.AddLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save Store Location")
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search location name...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(filteredLocations, key = { it.id }) { loc ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = loc.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Text(text = "Type: ${loc.type}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            if (loc.address.isNotBlank()) {
                                Text(text = loc.address, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { onDeleteLocation(loc) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Location", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

// Sub-Section 6: Admin Company Master
@Composable
private fun AdminCompanyMasterSection(
    viewModel: POMasterViewModel
) {
    val companyProfile by viewModel.companyProfileState.collectAsState()

    var companyName by remember(companyProfile) { mutableStateOf(companyProfile.companyName) }
    var address by remember(companyProfile) { mutableStateOf(companyProfile.address) }
    var phone by remember(companyProfile) { mutableStateOf(companyProfile.phone) }
    var email by remember(companyProfile) { mutableStateOf(companyProfile.email) }
    var gstNumber by remember(companyProfile) { mutableStateOf(companyProfile.gstNumber) }
    var website by remember(companyProfile) { mutableStateOf(companyProfile.website) }

    var saveStatusMsg by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Domain, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Company Master Settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Configure company profile name, optional GSTIN & contact details for PO issuing.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = companyName,
                onValueChange = { companyName = it },
                label = { Text("Company Name *") },
                leadingIcon = { Icon(Icons.Default.Business, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = gstNumber,
                onValueChange = { gstNumber = it },
                label = { Text("Company GST Number (GSTIN) (Optional)") },
                placeholder = { Text("e.g. 27AAACA123411Z5") },
                leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Registered Address") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    placeholder = { Text("e.g. 0422-2345678") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    placeholder = { Text("e.g. info@gardenayisha.com") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = website,
                onValueChange = { website = it },
                label = { Text("Website") },
                placeholder = { Text("e.g. https://gardenayisha.com") },
                leadingIcon = { Icon(Icons.Default.Language, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth()
            )
        }

        saveStatusMsg?.let { msg ->
            Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = {
                if (companyName.isNotBlank()) {
                    viewModel.saveCompanyProfile(
                        CompanyProfileEntity(
                            id = 1,
                            companyName = companyName,
                            address = address,
                            phone = phone,
                            email = email,
                            gstNumber = gstNumber,
                            website = website
                        )
                    )
                    saveStatusMsg = "Company Master details updated successfully!"
                }
            },
            enabled = companyName.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Company Master Profile")
        }
    }
}
