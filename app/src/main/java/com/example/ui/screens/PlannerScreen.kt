package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import com.example.data.entity.PurchasePlanNoteEntity
import com.example.data.entity.SupplierEntity
import com.example.ui.components.PurchasePlanningCalendarDialog
import com.example.ui.theme.rememberWindowSizeInfo
import com.example.ui.viewmodel.POMasterViewModel
import java.text.DateFormat
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerScreen(
    viewModel: POMasterViewModel,
    onConvertToPO: (PurchasePlanNoteEntity) -> Unit = {}
) {
    val windowInfo = rememberWindowSizeInfo()
    val planNotes by viewModel.purchasePlanNotesState.collectAsState()
    val suppliers by viewModel.suppliersState.collectAsState()
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    var showCreatePlanDialog by remember { mutableStateOf(false) }
    var showCalendarViewDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("UPCOMING") }
    var searchQuery by remember { mutableStateOf("") }
    var planToEdit by remember { mutableStateOf<PurchasePlanNoteEntity?>(null) }

    val todayStartMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val monthEndMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
        }.timeInMillis
    }

    val filteredPlans = remember(planNotes, selectedFilter, searchQuery) {
        planNotes.filter { plan ->
            val matchesFilter = when (selectedFilter) {
                "UPCOMING" -> plan.status != "COMPLETED" && plan.status != "CONVERTED_TO_PO" && plan.plannedDateMillis >= todayStartMillis
                "THIS_MONTH" -> plan.plannedDateMillis in todayStartMillis..monthEndMillis
                "ALL" -> true
                "CONVERTED" -> plan.status == "CONVERTED_TO_PO"
                "COMPLETED" -> plan.status == "COMPLETED"
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    plan.title.contains(searchQuery, ignoreCase = true) ||
                    plan.categoryOrSupplier.contains(searchQuery, ignoreCase = true) ||
                    plan.location.contains(searchQuery, ignoreCase = true) ||
                    plan.notes.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }.sortedBy { it.plannedDateMillis }
    }

    val totalPlannedBudget = remember(filteredPlans) { filteredPlans.sumOf { it.estimatedBudget } }

    if (showCreatePlanDialog || planToEdit != null) {
        CreateOrEditPlanDialog(
            viewModel = viewModel,
            suppliers = suppliers,
            initialPlan = planToEdit,
            onDismiss = {
                showCreatePlanDialog = false
                planToEdit = null
            }
        )
    }

    if (showCalendarViewDialog) {
        PurchasePlanningCalendarDialog(
            viewModel = viewModel,
            onDismiss = { showCalendarViewDialog = false }
        )
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    planToEdit = null
                    showCreatePlanDialog = true
                },
                icon = { Icon(Icons.Default.Add, contentDescription = "Plan Order") },
                text = { Text("Plan Future Order") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("plan_order_fab")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .padding(horizontal = windowInfo.defaultPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
            ) {
                // 1. Planner Executive Banner
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f, fill = false)) {
                                    Text(
                                        text = "Purchase Order Planner",
                                        style = if (windowInfo.isSmallPhone) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "Plan future supplier orders, budget forecasts & convert seamlessly",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF94A3B8),
                                        maxLines = if (windowInfo.isSmallPhone) 1 else 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                FilledTonalButton(
                                    onClick = { showCalendarViewDialog = true },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color(0xFF1E293B),
                                        contentColor = Color(0xFF38BDF8)
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = if (windowInfo.isSmallPhone) 8.dp else 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Calendar", style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Planned Orders", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8), maxLines = 1)
                                        Text(
                                            text = "${filteredPlans.size} Orders",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, Color(0xFF334155))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("Forecasted Budget", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8), maxLines = 1)
                                        Text(
                                            text = currencyFormat.format(totalPlannedBudget),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF34D399),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Search Box
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("planner_search_input"),
                        placeholder = { Text("Search planned orders, suppliers, items...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }

                // 3. Filter Chips
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val filters = listOf(
                            Pair("UPCOMING", "Upcoming Plans"),
                            Pair("THIS_MONTH", "This Month"),
                            Pair("ALL", "All Plans (${planNotes.size})"),
                            Pair("CONVERTED", "Converted to PO"),
                            Pair("COMPLETED", "Completed")
                        )

                        items(filters) { (key, label) ->
                            FilterChip(
                                selected = selectedFilter == key,
                                onClick = { selectedFilter = key },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }

                // 4. Plans List
                if (filteredPlans.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.EventNote,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No planned orders match '$searchQuery'" else "No planned orders found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap 'Plan Future Order' below to schedule upcoming supplier orders",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(filteredPlans, key = { it.id }) { plan ->
                        PlannedOrderCard(
                            plan = plan,
                            currencyFormat = currencyFormat,
                            dateFormat = dateFormat,
                            todayStartMillis = todayStartMillis,
                            onEdit = { planToEdit = plan },
                            onDelete = { viewModel.deletePurchasePlanNote(plan) },
                            onConvertToPO = {
                                viewModel.updatePurchasePlanStatus(plan, "CONVERTED_TO_PO")
                                onConvertToPO(plan)
                            },
                            onToggleStatus = { nextStatus ->
                                viewModel.updatePurchasePlanStatus(plan, nextStatus)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannedOrderCard(
    plan: PurchasePlanNoteEntity,
    currencyFormat: NumberFormat,
    dateFormat: DateFormat,
    todayStartMillis: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onConvertToPO: () -> Unit,
    onToggleStatus: (String) -> Unit
) {
    val isDue = plan.plannedDateMillis <= todayStartMillis
    val daysUntil = TimeUnit.MILLISECONDS.toDays(plan.plannedDateMillis - todayStartMillis)
    val isConverted = plan.status == "CONVERTED_TO_PO"

    val (badgeBg, badgeFg) = when (plan.status) {
        "CONVERTED_TO_PO" -> Pair(Color(0xFFD1FAE5), Color(0xFF047857))
        "COMPLETED" -> Pair(Color(0xFFE2E8F0), Color(0xFF475569))
        "IN_REVIEW" -> Pair(Color(0xFFFEF3C7), Color(0xFFB45309))
        else -> Pair(Color(0xFFE0F2FE), Color(0xFF0369A1))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("plan_order_card_${plan.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isDue && !isConverted && plan.status != "COMPLETED") Color(0xFFF59E0B) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = plan.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = plan.status.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (plan.estimatedBudget > 0) {
                    Text(
                        text = currencyFormat.format(plan.estimatedBudget),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Supplier & Location info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (plan.categoryOrSupplier.isNotBlank()) "🏢 ${plan.categoryOrSupplier}" else "🏢 General Sourcing",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (plan.location.isNotBlank()) {
                    Text(
                        text = "📍 ${plan.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (plan.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = plan.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Date & Countdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Planned: ${dateFormat.format(Date(plan.plannedDateMillis))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                val timingText = when {
                    isConverted -> "✅ Converted"
                    plan.status == "COMPLETED" -> "Done"
                    daysUntil < 0 -> "⚠️ Past Date (${-daysUntil}d ago)"
                    daysUntil == 0L -> "🚨 Due Today"
                    else -> "In ${daysUntil} days"
                }

                Text(
                    text = timingText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (daysUntil == 0L) Color(0xFFD97706) else MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Row: Convert to PO / Edit / Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isConverted && plan.status != "COMPLETED") {
                    Button(
                        onClick = onConvertToPO,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Convert to PO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateOrEditPlanDialog(
    viewModel: POMasterViewModel,
    suppliers: List<SupplierEntity>,
    initialPlan: PurchasePlanNoteEntity? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initialPlan?.title ?: "") }
    var categoryOrSupplier by remember { mutableStateOf(initialPlan?.categoryOrSupplier ?: "") }
    var location by remember { mutableStateOf(initialPlan?.location ?: "") }
    var budgetStr by remember { mutableStateOf(if (initialPlan != null && initialPlan.estimatedBudget > 0) initialPlan.estimatedBudget.toInt().toString() else "") }
    var notes by remember { mutableStateOf(initialPlan?.notes ?: "") }
    var plannedDateMillis by remember { mutableStateOf(initialPlan?.plannedDateMillis ?: (System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7))) }
    var priority by remember { mutableStateOf(initialPlan?.priority ?: "MEDIUM") }
    var status by remember { mutableStateOf(initialPlan?.status ?: "PLANNED") }
    var supplierDropdownExpanded by remember { mutableStateOf(false) }

    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 520.dp)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = if (initialPlan == null) "Plan Future Order" else "Edit Purchase Plan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Order Plan Title *") },
                    placeholder = { Text("e.g., Q3 Raw Material Sourcing") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Supplier Selector
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = categoryOrSupplier,
                        onValueChange = { categoryOrSupplier = it },
                        label = { Text("Supplier / Sourcing Category") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        trailingIcon = {
                            IconButton(onClick = { supplierDropdownExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = supplierDropdownExpanded,
                        onDismissRequest = { supplierDropdownExpanded = false }
                    ) {
                        suppliers.forEach { sup ->
                            DropdownMenuItem(
                                text = { Text("${sup.name} (${sup.category})") },
                                onClick = {
                                    categoryOrSupplier = sup.name
                                    supplierDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Date Picker Trigger
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val cal = Calendar.getInstance().apply { timeInMillis = plannedDateMillis }
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val newCal = Calendar.getInstance().apply {
                                        set(year, month, dayOfMonth, 9, 0, 0)
                                    }
                                    plannedDateMillis = newCal.timeInMillis
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Planned Order Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(dateFormat.format(Date(plannedDateMillis)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = budgetStr,
                        onValueChange = { budgetStr = it.filter { char -> char.isDigit() || char == '.' } },
                        label = { Text("Est. Budget (₹)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Location") },
                        placeholder = { Text("Central WH") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Planned Items / Scope Description") },
                    placeholder = { Text("List target SKUs, expected quantities, specifications...") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    minLines = 2,
                    maxLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (title.isNotBlank()) {
                                viewModel.savePurchasePlanNote(
                                    id = initialPlan?.id ?: 0L,
                                    title = title,
                                    plannedDateMillis = plannedDateMillis,
                                    location = location,
                                    notes = notes,
                                    estimatedBudget = budgetStr.toDoubleOrNull() ?: 0.0,
                                    categoryOrSupplier = categoryOrSupplier,
                                    priority = priority,
                                    status = status
                                )
                                onDismiss()
                            }
                        },
                        enabled = title.isNotBlank(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (initialPlan == null) "Save Plan" else "Update Plan")
                    }
                }
            }
        }
    }
}
