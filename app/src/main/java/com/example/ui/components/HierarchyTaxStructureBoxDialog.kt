package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.CategoryTaxEntity
import com.example.data.entity.StoreHierarchyEntity
import com.example.service.TaxMasterService
import com.example.ui.viewmodel.POMasterViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HierarchyTaxStructureBoxDialog(
    viewModel: POMasterViewModel,
    onDismiss: () -> Unit
) {
    val storeHierarchy by viewModel.storeHierarchyState.collectAsState()
    val categoryTaxRules by viewModel.categoryTaxRulesState.collectAsState()
    val inventoryList by viewModel.inventoryState.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Creation Box, 1: Tax Rules

    // State for Division, Section, Department
    var division by remember { mutableStateOf("Readymade") }
    var divisionDropdownExpanded by remember { mutableStateOf(false) }

    var section by remember { mutableStateOf("") }
    var sectionDropdownExpanded by remember { mutableStateOf(false) }

    var department by remember { mutableStateOf("") }
    var departmentDropdownExpanded by remember { mutableStateOf(false) }

    // State for Category & Tax Structure
    var categoryName by remember { mutableStateOf("Readymade") }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var hsnCode by remember { mutableStateOf("") }
    var selectedTaxRate by remember { mutableStateOf(5.0) }
    var customTaxRateStr by remember { mutableStateOf("5.0") }
    var isCustomRate by remember { mutableStateOf(false) }
    var minCostSlabStr by remember { mutableStateOf("0") }
    var maxCostSlabStr by remember { mutableStateOf("9999999") }
    var hasSlabCondition by remember { mutableStateOf(false) } // e.g. < 2500 vs >= 2500
    var ruleDescription by remember { mutableStateOf("Standard Category Tax Structure") }

    // Test Cost Simulator
    var testCostStr by remember { mutableStateOf("1500") }

    var successMessage by remember { mutableStateOf<String?>(null) }

    // Extract unique divisions
    val existingDivisions = remember(storeHierarchy, inventoryList) {
        (storeHierarchy.map { it.division.trim() } + inventoryList.map { it.division.trim() } + listOf("Readymade", "Textile"))
            .filter { it.isNotBlank() }.distinct().sorted()
    }

    // Extract unique sections for current division
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

    // Extract unique departments for current section and division
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

    // Available categories
    val availableCategories = remember(categoryTaxRules, inventoryList) {
        val taxCats = categoryTaxRules.map { it.categoryName.trim() }.filter { it.isNotBlank() }
        val invCats = inventoryList.map { it.category.trim() }.filter { it.isNotBlank() }
        val defaults = listOf("Readymade", "Textile", "Sarees", "Fabrics", "Accessories")
        (taxCats + invCats + defaults).distinct().sorted()
    }

    // Live Tax Determination Simulation
    val simCost = testCostStr.toDoubleOrNull() ?: 1500.0
    val activeEffectiveTaxRate = if (isCustomRate) {
        customTaxRateStr.toDoubleOrNull() ?: selectedTaxRate
    } else {
        selectedTaxRate
    }

    val liveEvaluatedRate = remember(categoryName, division, department, section, simCost, categoryTaxRules, activeEffectiveTaxRate) {
        TaxMasterService.getTaxRate(
            itemName = "",
            categoryName = categoryName,
            costAmount = simCost,
            categoryTaxRules = categoryTaxRules,
            division = division,
            department = department,
            section = section
        )
    }

    val liveTaxExplanation = remember(categoryName, division, department, section, simCost, categoryTaxRules) {
        TaxMasterService.getTaxExplanation(
            division = division,
            categoryName = categoryName,
            department = department,
            costAmount = simCost,
            categoryTaxRules = categoryTaxRules
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AccountTree,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Hierarchy & Tax Structure",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Store Classification & GST Tax Determination",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Navigation Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.clip(RoundedCornerShape(10.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Creation Box", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(Icons.Default.AddBox, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Tax Rules (${categoryTaxRules.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(Icons.Default.ReceiptLong, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (selectedTab == 0) {
                    // Visible Aligned Geometric Stepper Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        FlowStepPill(
                            step = "1",
                            title = "Division",
                            isActive = division.isNotBlank(),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier
                                .size(12.dp)
                                .padding(horizontal = 1.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        FlowStepPill(
                            step = "2",
                            title = "Section",
                            isActive = section.isNotBlank(),
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier
                                .size(12.dp)
                                .padding(horizontal = 1.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        FlowStepPill(
                            step = "3",
                            title = "Dept",
                            isActive = department.isNotBlank(),
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier
                                .size(12.dp)
                                .padding(horizontal = 1.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        FlowStepPill(
                            step = "4",
                            title = "GST Tax",
                            isActive = categoryName.isNotBlank(),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1.05f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (successMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = successMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // Tab Contents
                when (selectedTab) {
                    0 -> {
                        // Builder Box
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            // --- BOX 1: DIVISION ---
                            item {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                StepNumberCircle(step = "1", color = MaterialTheme.colorScheme.primary)
                                                Text(
                                                    text = "Division",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Level 1",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "Top-level store merchandise classification",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                OutlinedTextField(
                                                    value = division,
                                                    onValueChange = { division = it },
                                                    label = { Text("Division Name *") },
                                                    placeholder = { Text("e.g. Readymade, Textile...") },
                                                    singleLine = true,
                                                    trailingIcon = {
                                                        IconButton(onClick = { divisionDropdownExpanded = !divisionDropdownExpanded }) {
                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Division")
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )

                                                DropdownMenu(
                                                    expanded = divisionDropdownExpanded,
                                                    onDismissRequest = { divisionDropdownExpanded = false }
                                                ) {
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

                                            FilledTonalButton(
                                                onClick = {
                                                    if (division.isNotBlank()) {
                                                        viewModel.addHierarchyNode(division.trim(), "", "")
                                                        successMessage = "Division '$division' saved to database!"
                                                    }
                                                },
                                                enabled = division.isNotBlank(),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .padding(top = 8.dp)
                                                    .testTag("box_add_division_button")
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Add", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Connector between Box 1 and Box 2
                            item {
                                HierarchyStepConnector(label = "Cascades to Section")
                            }

                            // --- BOX 2: SECTION ---
                            item {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                StepNumberCircle(step = "2", color = MaterialTheme.colorScheme.secondary)
                                                Text(
                                                    text = "Section",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                            }
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Level 2",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = if (division.isNotBlank()) "Section under Division '$division'" else "Requires parent Division",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                OutlinedTextField(
                                                    value = section,
                                                    onValueChange = { section = it },
                                                    label = { Text("Section Name *") },
                                                    placeholder = { Text("e.g. Men's Wear, Sarees...") },
                                                    singleLine = true,
                                                    trailingIcon = {
                                                        IconButton(onClick = { sectionDropdownExpanded = !sectionDropdownExpanded }) {
                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Section")
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )

                                                DropdownMenu(
                                                    expanded = sectionDropdownExpanded,
                                                    onDismissRequest = { sectionDropdownExpanded = false }
                                                ) {
                                                    if (existingSectionsForDivision.isEmpty()) {
                                                        DropdownMenuItem(
                                                            text = { Text("No sections created under '$division' yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) },
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

                                            FilledTonalButton(
                                                onClick = {
                                                    if (division.isNotBlank() && section.isNotBlank()) {
                                                        viewModel.addHierarchyNode(division.trim(), section.trim(), "")
                                                        successMessage = "Section '$section' created under Division '$division'!"
                                                    }
                                                },
                                                enabled = division.isNotBlank() && section.isNotBlank(),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .padding(top = 8.dp)
                                                    .testTag("box_add_section_button")
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Add", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Connector between Box 2 and Box 3
                            item {
                                HierarchyStepConnector(label = "Cascades to Department")
                            }

                            // --- BOX 3: DEPARTMENT ---
                            item {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                StepNumberCircle(step = "3", color = MaterialTheme.colorScheme.tertiary)
                                                Text(
                                                    text = "Department",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                            Surface(
                                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Level 3",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.tertiary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = if (section.isNotBlank()) "Department under Section '$section'" else "Requires parent Section",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp)
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                OutlinedTextField(
                                                    value = department,
                                                    onValueChange = { department = it },
                                                    label = { Text("Department Name *") },
                                                    placeholder = { Text("e.g. Formal Shirts, Silk Sarees...") },
                                                    singleLine = true,
                                                    trailingIcon = {
                                                        IconButton(onClick = { departmentDropdownExpanded = !departmentDropdownExpanded }) {
                                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Department")
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )

                                                DropdownMenu(
                                                    expanded = departmentDropdownExpanded,
                                                    onDismissRequest = { departmentDropdownExpanded = false }
                                                ) {
                                                    if (existingDepartmentsForSection.isEmpty()) {
                                                        DropdownMenuItem(
                                                            text = { Text("No departments under '$section' yet", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) },
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

                                            FilledTonalButton(
                                                onClick = {
                                                    if (division.isNotBlank() && department.isNotBlank()) {
                                                        viewModel.addHierarchyNode(division.trim(), section.trim(), department.trim())
                                                        successMessage = "Department '$department' created under Section '$section'!"
                                                    }
                                                },
                                                enabled = division.isNotBlank() && department.isNotBlank(),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier
                                                    .height(56.dp)
                                                    .padding(top = 8.dp)
                                                    .testTag("box_add_department_button")
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Add", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // Connector between Box 3 and Box 4
                            item {
                                HierarchyStepConnector(label = "Determines Tax Structure")
                            }

                            // --- BOX 4: CATEGORY & TAX STRUCTURE DETERMINATION ---
                            item {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                StepNumberCircle(step = "4", color = MaterialTheme.colorScheme.primary)
                                                Text(
                                                    text = "Category & Tax Structure",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = "Level 4",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "Binds merchandise hierarchy to statutory and custom GST tax structure",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                                        )

                                        // Category Name Input
                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedTextField(
                                                value = categoryName,
                                                onValueChange = { categoryName = it },
                                                label = { Text("Category Name *") },
                                                placeholder = { Text("e.g. Readymade, Textile, Silk, Linen...") },
                                                singleLine = true,
                                                trailingIcon = {
                                                    IconButton(onClick = { categoryDropdownExpanded = !categoryDropdownExpanded }) {
                                                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Category")
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            DropdownMenu(
                                                expanded = categoryDropdownExpanded,
                                                onDismissRequest = { categoryDropdownExpanded = false }
                                            ) {
                                                availableCategories.forEach { catOpt ->
                                                    DropdownMenuItem(
                                                        text = { Text(catOpt, fontWeight = if (catOpt.equals(categoryName, ignoreCase = true)) FontWeight.Bold else FontWeight.Normal) },
                                                        onClick = {
                                                            categoryName = catOpt
                                                            categoryDropdownExpanded = false
                                                            // Auto-adjust default rates based on category
                                                            if (catOpt.equals("Textile", ignoreCase = true)) {
                                                                selectedTaxRate = 5.0
                                                                isCustomRate = false
                                                            } else if (catOpt.equals("Readymade", ignoreCase = true)) {
                                                                selectedTaxRate = 18.0
                                                                isCustomRate = false
                                                            }
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Tax Rate Presets
                                        Text(
                                            text = "GST Tax Rate Presets",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState())
                                        ) {
                                            listOf(
                                                5.0 to "5% Flat (Textile)",
                                                18.0 to "18% Readymade",
                                                28.0 to "28% Luxury",
                                                0.0 to "0% Nil"
                                            ).forEach { (rate, label) ->
                                                val isSelected = !isCustomRate && selectedTaxRate == rate
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        selectedTaxRate = rate
                                                        isCustomRate = false
                                                    },
                                                    label = { Text(label, fontSize = 12.sp) }
                                                )
                                            }
                                            FilterChip(
                                                selected = isCustomRate,
                                                onClick = { isCustomRate = true },
                                                label = { Text("Custom %", fontSize = 12.sp) }
                                            )
                                        }

                                        if (isCustomRate) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedTextField(
                                                value = customTaxRateStr,
                                                onValueChange = { customTaxRateStr = it },
                                                label = { Text("Custom Tax Rate %") },
                                                placeholder = { Text("e.g. 5.0, 12.5") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Cost Slab Condition Checkbox
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Checkbox(
                                                checked = hasSlabCondition,
                                                onCheckedChange = { hasSlabCondition = it }
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Enable Purchase Cost Slab Condition (Min / Max Cost ₹)",
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }

                                        if (hasSlabCondition) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                OutlinedTextField(
                                                    value = minCostSlabStr,
                                                    onValueChange = { minCostSlabStr = it },
                                                    label = { Text("Min Cost (₹)") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedTextField(
                                                    value = maxCostSlabStr,
                                                    onValueChange = { maxCostSlabStr = it },
                                                    label = { Text("Max Cost (₹)") },
                                                    singleLine = true,
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Rule Description
                                        OutlinedTextField(
                                            value = ruleDescription,
                                            onValueChange = { ruleDescription = it },
                                            label = { Text("Tax Structure Remarks / Rule Name") },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // LIVE DETERMINED TAX STRUCTURE PREVIEW (GEOMETRICALLY ALIGNED)
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(12.dp),
                                            tonalElevation = 2.dp,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(14.dp)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        Surface(
                                                            shape = CircleShape,
                                                            color = MaterialTheme.colorScheme.primaryContainer,
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Icon(
                                                                    Icons.Default.Calculate,
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = "Live Tax Structure",
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "$activeEffectiveTaxRate% GST",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(10.dp))

                                                // Proportionally Aligned Geometric Metric Cards
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    TaxMetricCard(
                                                        label = "CGST",
                                                        value = "${activeEffectiveTaxRate / 2.0}%",
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    TaxMetricCard(
                                                        label = "SGST",
                                                        value = "${activeEffectiveTaxRate / 2.0}%",
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    TaxMetricCard(
                                                        label = "Total GST",
                                                        value = "$activeEffectiveTaxRate%",
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                                    shape = RoundedCornerShape(6.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Text(
                                                        text = "Rule: $liveTaxExplanation",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(10.dp))

                                                // Test Cost Simulation Proportional Layout
                                                val calculatedTaxAmt = simCost * (liveEvaluatedRate / 100.0)
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    OutlinedTextField(
                                                        value = testCostStr,
                                                        onValueChange = { testCostStr = it },
                                                        label = { Text("Simulate Cost (₹)") },
                                                        singleLine = true,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        Column(
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally
                                                        ) {
                                                            Text("Landed Cost", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                                            Text(
                                                                text = "₹${"%.2f".format(simCost + calculatedTaxAmt)}",
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.primary,
                                                                fontSize = 14.sp
                                                            )
                                                            Text("+ ₹${"%.2f".format(calculatedTaxAmt)} tax ($liveEvaluatedRate%)", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        // SAVE HIERARCHY & TAX STRUCTURE BUTTON
                                        Button(
                                            onClick = {
                                                val rate = if (isCustomRate) customTaxRateStr.toDoubleOrNull() ?: selectedTaxRate else selectedTaxRate
                                                val minCost = if (hasSlabCondition) minCostSlabStr.toDoubleOrNull() ?: 0.0 else 0.0
                                                val maxCost = if (hasSlabCondition) maxCostSlabStr.toDoubleOrNull() ?: 9999999.0 else 9999999.0

                                                viewModel.saveHierarchyWithTaxStructure(
                                                    division = division,
                                                    section = section,
                                                    department = department,
                                                    categoryName = categoryName,
                                                    hsnCode = hsnCode,
                                                    taxRatePercent = rate,
                                                    minSlabAmount = minCost,
                                                    maxSlabAmount = maxCost,
                                                    description = ruleDescription.ifBlank { "$categoryName ($rate% GST)" },
                                                    ruleName = "$categoryName Standard Tax Structure"
                                                )

                                                successMessage = "✓ Hierarchy & Tax Structure saved! ($division ➔ $section ➔ $department | $categoryName: $rate% GST)"
                                            },
                                            enabled = division.isNotBlank(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("save_hierarchy_tax_button"),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Save Hierarchy & Category Tax Structure", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // Category Tax Rules Tab
                        if (categoryTaxRules.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No custom category tax rules configured.", color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(categoryTaxRules, key = { it.id }) { rule ->
                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.padding(14.dp)
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = rule.categoryName,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }

                                                    Surface(
                                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "${rule.taxRatePercent}% GST",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.height(4.dp))

                                                Text(
                                                    text = rule.ruleName.ifBlank { rule.description },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.SemiBold
                                                )

                                                if (rule.minSlabAmount > 0.0 || rule.maxSlabAmount < 9000000.0) {
                                                    Text(
                                                        text = "Slab: ₹${"%.0f".format(rule.minSlabAmount)} to ₹${"%.0f".format(rule.maxSlabAmount)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }

                                            IconButton(onClick = { viewModel.deleteCategoryTaxRule(rule) }) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete Rule",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Bottom Close Button
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun StepNumberCircle(
    step: String,
    color: Color
) {
    Surface(
        shape = CircleShape,
        color = color,
        modifier = Modifier.size(24.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = step,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun FlowStepPill(
    step: String,
    title: String,
    isActive: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, if (isActive) color else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = if (isActive) color else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = step,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) color else MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HierarchyStepConnector(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun TaxMetricCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
        }
    }
}
