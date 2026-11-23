package com.example.ui.components

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.entity.PurchaseOrderEntity
import com.example.data.entity.PurchasePlanNoteEntity
import com.example.ui.viewmodel.POMasterViewModel
import com.example.ui.viewmodel.ParsedPlanData
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class CalendarFilter {
    ALL,
    OVERDUE_DELIVERIES,
    DUE_TODAY,
    UPCOMING_7_DAYS,
    PURCHASE_PLANS_ONLY,
    PO_DELIVERIES_ONLY,
    COMPLETED,
    CUSTOM_DATE_RANGE
}

data class CalendarDay(
    val dayOfMonth: Int,
    val dateMillis: Long,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val posOnDate: List<PurchaseOrderEntity>,
    val plansOnDate: List<PurchasePlanNoteEntity>
)

sealed class ScheduleItem {
    data class PODeliveryItem(val po: PurchaseOrderEntity) : ScheduleItem()
    data class PlanNoteItem(val plan: PurchasePlanNoteEntity) : ScheduleItem()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasePlanningCalendarDialog(
    viewModel: POMasterViewModel,
    onDismiss: () -> Unit,
    onViewPODetails: ((PurchaseOrderEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val purchaseOrders by viewModel.purchaseOrdersState.collectAsState()
    val planNotes by viewModel.purchasePlanNotesState.collectAsState()
    val suppliers by viewModel.suppliersState.collectAsState()
    val companyProfile by viewModel.companyProfileState.collectAsState()
    val categoryTaxRules by viewModel.categoryTaxRulesState.collectAsState()
    val selectedPOItems by viewModel.selectedPOItems.collectAsState()
    val supplierMap = remember(suppliers) { suppliers.associateBy { it.id } }

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.forLanguageTag("en-IN")) }
    val dayDateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("en-IN")) }

    // Quick Natural Language Plan Input State
    var quickPlanInputText by remember { mutableStateOf("") }
    var parsedLivePlan by remember { mutableStateOf<ParsedPlanData?>(null) }
    var showDetailedPlanNoteDialog by remember { mutableStateOf(false) }
    var planNoteToEdit by remember { mutableStateOf<PurchasePlanNoteEntity?>(null) }

    // Calendar navigation state (Year and Month)
    var currentCalendar by remember {
        mutableStateOf(Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        })
    }

    // Selected date millis (null means show all for month)
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var selectedFilter by remember { mutableStateOf(CalendarFilter.ALL) }
    var poToPrintShare by remember { mutableStateOf<PurchaseOrderEntity?>(null) }

    // Custom Date Range Filter state (From Date - To Date)
    var filterFromDateMillis by remember { mutableStateOf<Long?>(null) }
    var filterToDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDateRangeFilterDialog by remember { mutableStateOf(false) }

    // Today range calculations
    val todayCal = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val todayStartMillis = remember { todayCal.timeInMillis }
    val todayEndMillis = remember { todayStartMillis + TimeUnit.DAYS.toMillis(1) - 1 }

    // Live NLP detection when user types in Quick Plan bar
    LaunchedEffect(quickPlanInputText) {
        if (quickPlanInputText.trim().length >= 3) {
            parsedLivePlan = viewModel.parseQuickPlanText(quickPlanInputText)
        } else {
            parsedLivePlan = null
        }
    }

    // Urgent / Important PO metrics
    val overduePOs = remember(purchaseOrders, todayStartMillis) {
        purchaseOrders.filter { po ->
            po.expectedDeliveryDateMillis < todayStartMillis &&
                    (po.status == "ISSUED" || po.status == "PARTIALLY_RECEIVED" || po.status == "DRAFT")
        }.sortedBy { it.expectedDeliveryDateMillis }
    }

    val dueTodayPOs = remember(purchaseOrders, todayStartMillis, todayEndMillis) {
        purchaseOrders.filter { po ->
            po.expectedDeliveryDateMillis in todayStartMillis..todayEndMillis &&
                    (po.status == "ISSUED" || po.status == "PARTIALLY_RECEIVED" || po.status == "DRAFT")
        }
    }

    // Upcoming Plans & Reminders
    val upcomingPlans = remember(planNotes, todayStartMillis) {
        planNotes.filter {
            it.status != "COMPLETED" && it.status != "CONVERTED_TO_PO" &&
                    it.plannedDateMillis >= todayStartMillis
        }.sortedBy { it.plannedDateMillis }
    }

    val dueTodayPlans = remember(planNotes, todayStartMillis, todayEndMillis) {
        planNotes.filter {
            it.plannedDateMillis in todayStartMillis..todayEndMillis &&
                    it.status != "COMPLETED"
        }
    }

    // Month Days Grid Calculation
    val daysInMonthList = remember(currentCalendar, purchaseOrders, planNotes) {
        val cal = currentCalendar.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // Sunday = 1
        val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val days = mutableListOf<CalendarDay>()

        // Leading slots from previous month
        val leadingEmptySlots = firstDayOfWeek - 1
        for (i in 0 until leadingEmptySlots) {
            val prevCal = cal.clone() as Calendar
            prevCal.add(Calendar.DAY_OF_MONTH, -(leadingEmptySlots - i))
            val pMillis = prevCal.timeInMillis
            val nextPMillis = pMillis + TimeUnit.DAYS.toMillis(1) - 1
            days.add(
                CalendarDay(
                    dayOfMonth = prevCal.get(Calendar.DAY_OF_MONTH),
                    dateMillis = pMillis,
                    isCurrentMonth = false,
                    isToday = isSameDay(pMillis, todayStartMillis),
                    posOnDate = purchaseOrders.filter { it.expectedDeliveryDateMillis in pMillis..nextPMillis },
                    plansOnDate = planNotes.filter { plan ->
                        val planEnd = if (plan.toDateMillis >= plan.plannedDateMillis) (plan.toDateMillis + TimeUnit.DAYS.toMillis(1) - 1) else (plan.plannedDateMillis + TimeUnit.DAYS.toMillis(1) - 1)
                        plan.plannedDateMillis <= nextPMillis && planEnd >= pMillis
                    }
                )
            )
        }

        // Days of current month
        for (day in 1..maxDays) {
            cal.set(Calendar.DAY_OF_MONTH, day)
            val dMillis = cal.timeInMillis
            val nextDMillis = dMillis + TimeUnit.DAYS.toMillis(1) - 1

            val posOnDay = purchaseOrders.filter { po ->
                po.expectedDeliveryDateMillis in dMillis..nextDMillis
            }
            val plansOnDay = planNotes.filter { plan ->
                val planEnd = if (plan.toDateMillis >= plan.plannedDateMillis) (plan.toDateMillis + TimeUnit.DAYS.toMillis(1) - 1) else (plan.plannedDateMillis + TimeUnit.DAYS.toMillis(1) - 1)
                plan.plannedDateMillis <= nextDMillis && planEnd >= dMillis
            }

            days.add(
                CalendarDay(
                    dayOfMonth = day,
                    dateMillis = dMillis,
                    isCurrentMonth = true,
                    isToday = isSameDay(dMillis, todayStartMillis),
                    posOnDate = posOnDay,
                    plansOnDate = plansOnDay
                )
            )
        }

        // Trailing slots
        val trailingSlots = (7 - (days.size % 7)) % 7
        for (i in 1..trailingSlots) {
            cal.set(Calendar.DAY_OF_MONTH, maxDays)
            cal.add(Calendar.DAY_OF_MONTH, i)
            val tMillis = cal.timeInMillis
            val nextTMillis = tMillis + TimeUnit.DAYS.toMillis(1) - 1
            days.add(
                CalendarDay(
                    dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                    dateMillis = tMillis,
                    isCurrentMonth = false,
                    isToday = false,
                    posOnDate = purchaseOrders.filter { it.expectedDeliveryDateMillis in tMillis..nextTMillis },
                    plansOnDate = planNotes.filter { plan ->
                        val planEnd = if (plan.toDateMillis >= plan.plannedDateMillis) (plan.toDateMillis + TimeUnit.DAYS.toMillis(1) - 1) else (plan.plannedDateMillis + TimeUnit.DAYS.toMillis(1) - 1)
                        plan.plannedDateMillis <= nextTMillis && planEnd >= tMillis
                    }
                )
            )
        }

        days
    }

    // Unified List of Schedule Items for Display
    val displayedScheduleItems = remember(
        purchaseOrders,
        planNotes,
        selectedDateMillis,
        selectedFilter,
        filterFromDateMillis,
        filterToDateMillis,
        currentCalendar
    ) {
        val itemsList = mutableListOf<ScheduleItem>()

        if (selectedDateMillis != null) {
            val start = selectedDateMillis!!
            val end = start + TimeUnit.DAYS.toMillis(1) - 1
            if (selectedFilter != CalendarFilter.PURCHASE_PLANS_ONLY) {
                purchaseOrders.filter { it.expectedDeliveryDateMillis in start..end }
                    .forEach { itemsList.add(ScheduleItem.PODeliveryItem(it)) }
            }
            if (selectedFilter != CalendarFilter.PO_DELIVERIES_ONLY) {
                planNotes.filter { plan ->
                    val planEnd = if (plan.toDateMillis >= plan.plannedDateMillis) (plan.toDateMillis + TimeUnit.DAYS.toMillis(1) - 1) else (plan.plannedDateMillis + TimeUnit.DAYS.toMillis(1) - 1)
                    plan.plannedDateMillis <= end && planEnd >= start
                }.forEach { itemsList.add(ScheduleItem.PlanNoteItem(it)) }
            }
        } else {
            when (selectedFilter) {
                CalendarFilter.CUSTOM_DATE_RANGE -> {
                    val fStart = filterFromDateMillis ?: todayStartMillis
                    val fEnd = (filterToDateMillis ?: fStart) + TimeUnit.DAYS.toMillis(1) - 1
                    purchaseOrders.filter { it.expectedDeliveryDateMillis in fStart..fEnd }
                        .forEach { itemsList.add(ScheduleItem.PODeliveryItem(it)) }
                    planNotes.filter { plan ->
                        val planEnd = if (plan.toDateMillis >= plan.plannedDateMillis) (plan.toDateMillis + TimeUnit.DAYS.toMillis(1) - 1) else (plan.plannedDateMillis + TimeUnit.DAYS.toMillis(1) - 1)
                        plan.plannedDateMillis <= fEnd && planEnd >= fStart
                    }.forEach { itemsList.add(ScheduleItem.PlanNoteItem(it)) }
                }
                CalendarFilter.OVERDUE_DELIVERIES -> {
                    overduePOs.forEach { itemsList.add(ScheduleItem.PODeliveryItem(it)) }
                }
                CalendarFilter.DUE_TODAY -> {
                    dueTodayPOs.forEach { itemsList.add(ScheduleItem.PODeliveryItem(it)) }
                    dueTodayPlans.forEach { itemsList.add(ScheduleItem.PlanNoteItem(it)) }
                }
                CalendarFilter.UPCOMING_7_DAYS -> {
                    val next7End = todayStartMillis + TimeUnit.DAYS.toMillis(7)
                    purchaseOrders.filter { it.expectedDeliveryDateMillis in (todayEndMillis + 1)..next7End }
                        .forEach { itemsList.add(ScheduleItem.PODeliveryItem(it)) }
                    planNotes.filter { plan ->
                        val planEnd = if (plan.toDateMillis >= plan.plannedDateMillis) (plan.toDateMillis + TimeUnit.DAYS.toMillis(1) - 1) else (plan.plannedDateMillis + TimeUnit.DAYS.toMillis(1) - 1)
                        plan.plannedDateMillis <= next7End && planEnd >= todayStartMillis
                    }.forEach { itemsList.add(ScheduleItem.PlanNoteItem(it)) }
                }
                CalendarFilter.PURCHASE_PLANS_ONLY -> {
                    planNotes.sortedBy { it.plannedDateMillis }
                        .forEach { itemsList.add(ScheduleItem.PlanNoteItem(it)) }
                }
                CalendarFilter.PO_DELIVERIES_ONLY -> {
                    purchaseOrders.sortedBy { it.expectedDeliveryDateMillis }
                        .forEach { itemsList.add(ScheduleItem.PODeliveryItem(it)) }
                }
                CalendarFilter.COMPLETED -> {
                    purchaseOrders.filter { it.status == "RECEIVED" }
                        .forEach { itemsList.add(ScheduleItem.PODeliveryItem(it)) }
                    planNotes.filter { it.status == "COMPLETED" || it.status == "CONVERTED_TO_PO" }
                        .forEach { itemsList.add(ScheduleItem.PlanNoteItem(it)) }
                }
                CalendarFilter.ALL -> {
                    purchaseOrders.forEach { itemsList.add(ScheduleItem.PODeliveryItem(it)) }
                    planNotes.forEach { itemsList.add(ScheduleItem.PlanNoteItem(it)) }
                }
            }
        }

        // Sort items chronologically by date
        itemsList.sortedBy { item ->
            when (item) {
                is ScheduleItem.PODeliveryItem -> item.po.expectedDeliveryDateMillis
                is ScheduleItem.PlanNoteItem -> item.plan.plannedDateMillis
            }
        }
    }

    // Document Print / Share Dialog
    poToPrintShare?.let { po ->
        val supplierObj = supplierMap[po.supplierId]
        PrintPOShareDialog(
            po = po,
            poItems = selectedPOItems,
            categoryTaxRules = categoryTaxRules,
            supplierName = supplierObj?.name ?: po.supplierName,
            supplier = supplierObj,
            companyProfile = companyProfile,
            currencyFormat = currencyFormat,
            dateFormat = dayDateFormat,
            onDismiss = { poToPrintShare = null }
        )
    }

    // Detailed Add / Edit Plan Note Dialog
    if (showDetailedPlanNoteDialog) {
        AddEditPurchasePlanNoteDialog(
            initialPlan = planNoteToEdit,
            initialDateMillis = selectedDateMillis ?: parsedLivePlan?.dateMillis ?: System.currentTimeMillis(),
            initialToDateMillis = parsedLivePlan?.toDateMillis ?: 0L,
            initialTitle = parsedLivePlan?.title ?: "",
            initialLocation = parsedLivePlan?.location ?: "",
            initialBudget = parsedLivePlan?.estimatedBudget ?: 0.0,
            currencyFormat = currencyFormat,
            onDismiss = {
                showDetailedPlanNoteDialog = false
                planNoteToEdit = null
            },
            onSave = { id, title, dateMillis, toDateMillis, location, notes, budget, category, priority, status, reminderDays ->
                viewModel.savePurchasePlanNote(
                    id = id,
                    title = title,
                    plannedDateMillis = dateMillis,
                    toDateMillis = toDateMillis,
                    location = location,
                    notes = notes,
                    estimatedBudget = budget,
                    categoryOrSupplier = category,
                    priority = priority,
                    status = status,
                    reminderDaysBefore = reminderDays
                )
                showDetailedPlanNoteDialog = false
                planNoteToEdit = null
                quickPlanInputText = ""
                parsedLivePlan = null
                Toast.makeText(context, "Purchase Plan '$title' Saved!", Toast.LENGTH_SHORT).show()
            },
            onDelete = { plan ->
                viewModel.deletePurchasePlanNote(plan)
                showDetailedPlanNoteDialog = false
                planNoteToEdit = null
                Toast.makeText(context, "Purchase Plan Removed", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Custom Date Range Filter Dialog
    if (showDateRangeFilterDialog) {
        DateRangeFilterDialog(
            initialFromDate = filterFromDateMillis ?: todayStartMillis,
            initialToDate = filterToDateMillis ?: (todayStartMillis + TimeUnit.DAYS.toMillis(7)),
            dayDateFormat = dayDateFormat,
            onDismiss = { showDateRangeFilterDialog = false },
            onApply = { fromMillis, toMillis ->
                filterFromDateMillis = fromMillis
                filterToDateMillis = toMillis
                selectedDateMillis = null
                selectedFilter = CalendarFilter.CUSTOM_DATE_RANGE
                showDateRangeFilterDialog = false
            },
            onClear = {
                filterFromDateMillis = null
                filterToDateMillis = null
                selectedFilter = CalendarFilter.ALL
                showDateRangeFilterDialog = false
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp, bottom = 12.dp, start = 8.dp, end = 8.dp)
                .testTag("purchase_planning_calendar_dialog"),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // --- Top Header Bar ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "OPT Purchase Planner & Tracker",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "Order, Planner, Tracker • Trips & PO Deliveries",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("calendar_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- Smart Quick NLP Note Adder Bar (e.g. 'Aug 27 Bombay') ---
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = quickPlanInputText,
                                onValueChange = { quickPlanInputText = it },
                                placeholder = {
                                    Text(
                                        "Quick Note: e.g. 'Aug 27 Bombay silk purchase 50k'",
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.EditNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                trailingIcon = {
                                    if (quickPlanInputText.isNotEmpty()) {
                                        IconButton(onClick = {
                                            quickPlanInputText = ""
                                            parsedLivePlan = null
                                        }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_plan_note_input"),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (quickPlanInputText.isNotBlank()) {
                                            val parsed = parsedLivePlan ?: viewModel.parseQuickPlanText(quickPlanInputText)
                                            viewModel.savePurchasePlanNote(
                                                title = parsed.title,
                                                plannedDateMillis = parsed.dateMillis,
                                                toDateMillis = parsed.toDateMillis,
                                                location = parsed.location,
                                                estimatedBudget = parsed.estimatedBudget
                                            )
                                            Toast.makeText(context, "Plan '${parsed.title}' Added!", Toast.LENGTH_SHORT).show()
                                            quickPlanInputText = ""
                                            parsedLivePlan = null
                                            focusManager.clearFocus()
                                            keyboardController?.hide()
                                        }
                                    }
                                )
                            )

                            Button(
                                onClick = {
                                    if (quickPlanInputText.isNotBlank()) {
                                        val parsed = parsedLivePlan ?: viewModel.parseQuickPlanText(quickPlanInputText)
                                        viewModel.savePurchasePlanNote(
                                            title = parsed.title,
                                            plannedDateMillis = parsed.dateMillis,
                                            toDateMillis = parsed.toDateMillis,
                                            location = parsed.location,
                                            estimatedBudget = parsed.estimatedBudget
                                        )
                                        val dateLabel = if (parsed.toDateMillis > parsed.dateMillis) {
                                            "${dayDateFormat.format(Date(parsed.dateMillis))} to ${dayDateFormat.format(Date(parsed.toDateMillis))}"
                                        } else {
                                            dayDateFormat.format(Date(parsed.dateMillis))
                                        }
                                        Toast.makeText(context, "Plan Added for $dateLabel", Toast.LENGTH_SHORT).show()
                                        quickPlanInputText = ""
                                        parsedLivePlan = null
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    } else {
                                        showDetailedPlanNoteDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("add_quick_plan_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (quickPlanInputText.isBlank()) "Plan" else "Save", fontWeight = FontWeight.Bold)
                            }
                        }

                        // Live AI/NLP Parsed Suggestion Banner
                        if (parsedLivePlan != null && quickPlanInputText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("Detected:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(3.dp))
                                        val dateText = if (parsedLivePlan!!.toDateMillis > parsedLivePlan!!.dateMillis) {
                                            "${dayDateFormat.format(Date(parsedLivePlan!!.dateMillis))} ➔ ${dayDateFormat.format(Date(parsedLivePlan!!.toDateMillis))}"
                                        } else {
                                            dayDateFormat.format(Date(parsedLivePlan!!.dateMillis))
                                        }
                                        Text(
                                            dateText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (parsedLivePlan!!.location.isNotBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFFEF3C7)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFB45309))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                parsedLivePlan!!.location,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFB45309)
                                            )
                                        }
                                    }
                                }

                                if (parsedLivePlan!!.estimatedBudget > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFFDCFCE7)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Payments, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF15803D))
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                currencyFormat.format(parsedLivePlan!!.estimatedBudget),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF15803D)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                TextButton(
                                    onClick = { showDetailedPlanNoteDialog = true },
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text("More Options ➔", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- Summary Reminder Chips / Metrics ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalendarMetricCard(
                        title = "Overdue POs",
                        count = overduePOs.size,
                        containerColor = if (overduePOs.isNotEmpty()) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = if (overduePOs.isNotEmpty()) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant,
                        icon = Icons.Default.Warning,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedDateMillis = null
                            selectedFilter = CalendarFilter.OVERDUE_DELIVERIES
                        }
                    )

                    CalendarMetricCard(
                        title = "Plans & Trips",
                        count = upcomingPlans.size,
                        containerColor = Color(0xFFF3E8FF),
                        contentColor = Color(0xFF7E22CE),
                        icon = Icons.Default.FlightTakeoff,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedDateMillis = null
                            selectedFilter = CalendarFilter.PURCHASE_PLANS_ONLY
                        }
                    )

                    CalendarMetricCard(
                        title = "Due Today",
                        count = dueTodayPOs.size + dueTodayPlans.size,
                        containerColor = if (dueTodayPOs.isNotEmpty() || dueTodayPlans.isNotEmpty()) Color(0xFFFEF3C7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        contentColor = if (dueTodayPOs.isNotEmpty() || dueTodayPlans.isNotEmpty()) Color(0xFFD97706) else MaterialTheme.colorScheme.onSurfaceVariant,
                        icon = Icons.Default.Today,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            selectedDateMillis = todayStartMillis
                            selectedFilter = CalendarFilter.DUE_TODAY
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // --- Calendar Month Navigation & Grid ---
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val newCal = currentCalendar.clone() as Calendar
                                    newCal.add(Calendar.MONTH, -1)
                                    currentCalendar = newCal
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month")
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = monthYearFormat.format(currentCalendar.time),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    modifier = Modifier.clickable {
                                        currentCalendar = Calendar.getInstance().apply {
                                            set(Calendar.HOUR_OF_DAY, 0)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        selectedDateMillis = todayStartMillis
                                    }
                                ) {
                                    Text(
                                        text = "Today",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val newCal = currentCalendar.clone() as Calendar
                                    newCal.add(Calendar.MONTH, 1)
                                    currentCalendar = newCal
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month")
                            }
                        }

                        // Weekday headers
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { dayName ->
                                Text(
                                    text = dayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (dayName == "Sun") Color(0xFFDC2626) else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.width(34.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Days Grid with Hybrid PO & Plan indicators
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(7),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            items(daysInMonthList) { calDay ->
                                val isSelected = selectedDateMillis?.let { isSameDay(it, calDay.dateMillis) } ?: false
                                val hasOverdue = calDay.posOnDate.any { it.status == "ISSUED" && it.expectedDeliveryDateMillis < todayStartMillis }
                                val hasPendingPO = calDay.posOnDate.any { it.status == "ISSUED" || it.status == "PARTIALLY_RECEIVED" }
                                val hasReceivedPO = calDay.posOnDate.any { it.status == "RECEIVED" }
                                val hasPlans = calDay.plansOnDate.isNotEmpty()

                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1.15f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when {
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                calDay.isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                calDay.isCurrentMonth -> Color.Transparent
                                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                                            }
                                        )
                                        .clickable {
                                            selectedDateMillis = if (isSelected) null else calDay.dateMillis
                                            selectedFilter = CalendarFilter.ALL
                                        }
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "${calDay.dayOfMonth}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (calDay.isToday || isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                            color = when {
                                                isSelected -> Color.White
                                                calDay.isToday -> MaterialTheme.colorScheme.primary
                                                calDay.isCurrentMonth -> MaterialTheme.colorScheme.onSurface
                                                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                            }
                                        )

                                        // Indicator Dots for POs and Plans on this day
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 1.dp)
                                        ) {
                                            // Plan Note indicator (Purple)
                                            if (hasPlans) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) Color.White else Color(0xFF9333EA))
                                                )
                                            }

                                            // PO Overdue indicator (Red)
                                            if (hasOverdue) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) Color.White else Color(0xFFDC2626))
                                                )
                                            } else if (hasPendingPO) {
                                                // PO Pending indicator (Blue)
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) Color.White else Color(0xFF0284C7))
                                                )
                                            } else if (hasReceivedPO && !hasPlans) {
                                                // PO Received indicator (Green)
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isSelected) Color.White else Color(0xFF16A34A))
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

                // --- Filter Chips Row ---
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedDateMillis == null && selectedFilter == CalendarFilter.ALL,
                            onClick = {
                                selectedDateMillis = null
                                filterFromDateMillis = null
                                filterToDateMillis = null
                                selectedFilter = CalendarFilter.ALL
                            },
                            label = { Text("All (${purchaseOrders.size + planNotes.size})") }
                        )
                    }

                    // Custom Date Range (From - To) Filter Chip
                    item {
                        val isRangeSelected = selectedFilter == CalendarFilter.CUSTOM_DATE_RANGE && filterFromDateMillis != null && filterToDateMillis != null
                        FilterChip(
                            selected = isRangeSelected,
                            onClick = {
                                showDateRangeFilterDialog = true
                            },
                            label = {
                                if (isRangeSelected) {
                                    Text("📅 ${dayDateFormat.format(Date(filterFromDateMillis!!))} – ${dayDateFormat.format(Date(filterToDateMillis!!))}")
                                } else {
                                    Text("📅 Date Range (From - To)")
                                }
                            },
                            trailingIcon = if (isRangeSelected) {
                                {
                                    IconButton(
                                        onClick = {
                                            filterFromDateMillis = null
                                            filterToDateMillis = null
                                            selectedFilter = CalendarFilter.ALL
                                        },
                                        modifier = Modifier.size(16.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear Range", modifier = Modifier.size(12.dp))
                                    }
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFEF3C7),
                                selectedLabelColor = Color(0xFFB45309)
                            )
                        )
                    }

                    item {
                        FilterChip(
                            selected = selectedFilter == CalendarFilter.PURCHASE_PLANS_ONLY,
                            onClick = {
                                selectedDateMillis = null
                                selectedFilter = CalendarFilter.PURCHASE_PLANS_ONLY
                            },
                            label = { Text("✈️ Plans & Trips (${planNotes.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF3E8FF),
                                selectedLabelColor = Color(0xFF7E22CE)
                            )
                        )
                    }

                    item {
                        FilterChip(
                            selected = selectedFilter == CalendarFilter.OVERDUE_DELIVERIES,
                            onClick = {
                                selectedDateMillis = null
                                selectedFilter = CalendarFilter.OVERDUE_DELIVERIES
                            },
                            label = { Text("🚨 Overdue POs (${overduePOs.size})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFEE2E2),
                                selectedLabelColor = Color(0xFFDC2626)
                            )
                        )
                    }

                    item {
                        FilterChip(
                            selected = selectedFilter == CalendarFilter.DUE_TODAY,
                            onClick = {
                                selectedDateMillis = todayStartMillis
                                selectedFilter = CalendarFilter.DUE_TODAY
                            },
                            label = { Text("📅 Due Today (${dueTodayPOs.size + dueTodayPlans.size})") }
                        )
                    }

                    item {
                        FilterChip(
                            selected = selectedFilter == CalendarFilter.PO_DELIVERIES_ONLY,
                            onClick = {
                                selectedDateMillis = null
                                selectedFilter = CalendarFilter.PO_DELIVERIES_ONLY
                            },
                            label = { Text("📦 PO Deliveries (${purchaseOrders.size})") }
                        )
                    }

                    item {
                        FilterChip(
                            selected = selectedFilter == CalendarFilter.COMPLETED,
                            onClick = {
                                selectedDateMillis = null
                                selectedFilter = CalendarFilter.COMPLETED
                            },
                            label = { Text("✅ Completed") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // --- Section Header & Quick Add PO Action ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        val headerTitle = when {
                            selectedDateMillis != null -> "Schedule for ${dayDateFormat.format(Date(selectedDateMillis!!))}"
                            selectedFilter == CalendarFilter.CUSTOM_DATE_RANGE && filterFromDateMillis != null && filterToDateMillis != null -> "📅 Range: ${dayDateFormat.format(Date(filterFromDateMillis!!))} to ${dayDateFormat.format(Date(filterToDateMillis!!))}"
                            selectedFilter == CalendarFilter.PURCHASE_PLANS_ONLY -> "✈️ Planned Purchases & Sourcing Trips"
                            selectedFilter == CalendarFilter.OVERDUE_DELIVERIES -> "🚨 Overdue Delivery Commitments"
                            selectedFilter == CalendarFilter.DUE_TODAY -> "📅 Due Today (POs & Plans)"
                            selectedFilter == CalendarFilter.PO_DELIVERIES_ONLY -> "📦 All Purchase Orders Tracker"
                            else -> "Schedule & Delivery Timeline"
                        }
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${displayedScheduleItems.size} Scheduled Item(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledTonalButton(
                            onClick = {
                                planNoteToEdit = null
                                showDetailedPlanNoteDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("new_plan_note_btn")
                        ) {
                            Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("New Note", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // --- Schedule Items List (Cards for both PO Deliveries & Purchase Plan Notes) ---
                if (displayedScheduleItems.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.EventAvailable,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No purchases or trips scheduled for this selection",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Type a quick note (e.g. 'Aug 27 Bombay silk purchase') or click 'New Note' to plan sourcing trips & orders.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(displayedScheduleItems, key = { item ->
                            when (item) {
                                is ScheduleItem.PODeliveryItem -> "po_${item.po.id}"
                                is ScheduleItem.PlanNoteItem -> "plan_${item.plan.id}"
                            }
                        }) { item ->
                            when (item) {
                                is ScheduleItem.PlanNoteItem -> {
                                    val plan = item.plan
                                    val daysDiff = TimeUnit.MILLISECONDS.toDays(plan.plannedDateMillis - todayStartMillis)
                                    val isPast = plan.plannedDateMillis < todayStartMillis
                                    val isToday = isSameDay(plan.plannedDateMillis, todayStartMillis)

                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = when (plan.status) {
                                                "COMPLETED", "CONVERTED_TO_PO" -> Color(0xFFF0FDF4)
                                                else -> Color(0xFFFAF5FF)
                                            }
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            when (plan.status) {
                                                "COMPLETED", "CONVERTED_TO_PO" -> Color(0xFF86EFAC)
                                                else -> Color(0xFFD8B4FE)
                                            }
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                planNoteToEdit = plan
                                                showDetailedPlanNoteDialog = true
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = Color(0xFF9333EA),
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Icon(
                                                                if (plan.location.isNotBlank()) Icons.Default.FlightTakeoff else Icons.Default.EditNote,
                                                                contentDescription = null,
                                                                tint = Color.White,
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                        }
                                                    }

                                                    Text(
                                                        text = plan.title,
                                                        style = MaterialTheme.typography.titleSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF581C87),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                // Status / Countdown Pill
                                                Surface(
                                                    color = when {
                                                        plan.status == "CONVERTED_TO_PO" -> Color(0xFF16A34A)
                                                        plan.status == "COMPLETED" -> Color(0xFF16A34A)
                                                        isToday -> Color(0xFFD97706)
                                                        isPast -> Color(0xFF9CA3AF)
                                                        else -> Color(0xFF7E22CE)
                                                    },
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = when {
                                                            plan.status == "CONVERTED_TO_PO" -> "CONVERTED TO PO"
                                                            plan.status == "COMPLETED" -> "COMPLETED"
                                                            isToday -> "TODAY"
                                                            daysDiff > 0 -> "In $daysDiff days"
                                                            else -> "Past Plan"
                                                        },
                                                        color = Color.White,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            // Date & Location & Budget Line
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                val hasDateRange = plan.toDateMillis > plan.plannedDateMillis
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                    Icon(
                                                        if (hasDateRange) Icons.Default.DateRange else Icons.Default.Event,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = Color(0xFF7E22CE)
                                                    )
                                                    if (hasDateRange) {
                                                        val spanDays = TimeUnit.MILLISECONDS.toDays(plan.toDateMillis - plan.plannedDateMillis) + 1
                                                        Text(
                                                            "${dayDateFormat.format(Date(plan.plannedDateMillis))} ➔ ${dayDateFormat.format(Date(plan.toDateMillis))}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF581C87)
                                                        )
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = Color(0xFFF3E8FF)
                                                        ) {
                                                            Text(
                                                                "$spanDays Days",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF7E22CE),
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    } else {
                                                        Text(
                                                            dayDateFormat.format(Date(plan.plannedDateMillis)),
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF581C87)
                                                        )
                                                    }
                                                }

                                                if (plan.location.isNotBlank()) {
                                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFFB45309))
                                                        Text(
                                                            plan.location,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFF92400E)
                                                        )
                                                    }
                                                }

                                                if (plan.estimatedBudget > 0) {
                                                    Spacer(modifier = Modifier.weight(1f))
                                                    Text(
                                                        "Budget: ${currencyFormat.format(plan.estimatedBudget)}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF15803D)
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

                                            // Action Buttons: Convert to PO, WhatsApp Share, Complete, Test Notification
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Test / Trigger Notification Now
                                                FilledTonalButton(
                                                    onClick = {
                                                        viewModel.triggerPlanNotificationNow(plan)
                                                        Toast.makeText(context, "🔔 Notification sent for '${plan.title}'!", Toast.LENGTH_SHORT).show()
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.padding(end = 6.dp)
                                                ) {
                                                    Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp), tint = Color(0xFF7E22CE))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("Notify", style = MaterialTheme.typography.labelSmall)
                                                }

                                                // WhatsApp Share Trip Notes
                                                OutlinedButton(
                                                    onClick = {
                                                        val dateShareLine = if (plan.toDateMillis > plan.plannedDateMillis) {
                                                            "Date: From ${dayDateFormat.format(Date(plan.plannedDateMillis))} to ${dayDateFormat.format(Date(plan.toDateMillis))} (${TimeUnit.MILLISECONDS.toDays(plan.toDateMillis - plan.plannedDateMillis) + 1} Days)\n"
                                                        } else {
                                                            "Date: ${dayDateFormat.format(Date(plan.plannedDateMillis))}\n"
                                                        }
                                                        val shareText = "📍 Purchase Plan / Sourcing Memo\n" +
                                                                "Title: ${plan.title}\n" +
                                                                dateShareLine +
                                                                (if (plan.location.isNotBlank()) "Location: ${plan.location}\n" else "") +
                                                                (if (plan.estimatedBudget > 0) "Budget: ${currencyFormat.format(plan.estimatedBudget)}\n" else "") +
                                                                (if (plan.notes.isNotBlank()) "Notes: ${plan.notes}\n" else "") +
                                                                "- ${companyProfile.companyName.ifBlank { "PO Master ERP" }} (Planner)"
                                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                                            type = "text/plain"
                                                            putExtra(Intent.EXTRA_TEXT, shareText)
                                                        }
                                                        try {
                                                            context.startActivity(Intent.createChooser(intent, "Share Purchase Plan"))
                                                        } catch (e: Exception) {
                                                            Toast.makeText(context, "Could not share", Toast.LENGTH_SHORT).show()
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("Share", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                    }
                                }

                                is ScheduleItem.PODeliveryItem -> {
                                    val po = item.po
                                    val supplier = supplierMap[po.supplierId]
                                    val isOverdue = po.expectedDeliveryDateMillis < todayStartMillis && (po.status == "ISSUED" || po.status == "PARTIALLY_RECEIVED")
                                    val isDueToday = po.expectedDeliveryDateMillis in todayStartMillis..todayEndMillis && (po.status == "ISSUED" || po.status == "PARTIALLY_RECEIVED")
                                    val daysDifference = TimeUnit.MILLISECONDS.toDays(po.expectedDeliveryDateMillis - todayStartMillis)

                                    Card(
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = when {
                                                isOverdue -> Color(0xFFFEF2F2)
                                                isDueToday -> Color(0xFFFFFBEB)
                                                po.status == "RECEIVED" -> Color(0xFFF0FDF4)
                                                else -> MaterialTheme.colorScheme.surface
                                            }
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            when {
                                                isOverdue -> Color(0xFFF87171)
                                                isDueToday -> Color(0xFFFBBF24)
                                                po.status == "RECEIVED" -> Color(0xFF86EFAC)
                                                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = po.poNumber,
                                                            style = MaterialTheme.typography.titleSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.primary
                                                        )
                                                        when {
                                                            isOverdue -> {
                                                                Surface(color = Color(0xFFDC2626), shape = RoundedCornerShape(4.dp)) {
                                                                    Text(
                                                                        text = "OVERDUE (${-daysDifference}d)",
                                                                        color = Color.White,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.ExtraBold,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                            isDueToday -> {
                                                                Surface(color = Color(0xFFD97706), shape = RoundedCornerShape(4.dp)) {
                                                                    Text(
                                                                        text = "DUE TODAY",
                                                                        color = Color.White,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.ExtraBold,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                            po.status == "RECEIVED" -> {
                                                                Surface(color = Color(0xFF16A34A), shape = RoundedCornerShape(4.dp)) {
                                                                    Text(
                                                                        text = "RECEIVED",
                                                                        color = Color.White,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                            else -> {
                                                                Surface(color = Color(0xFF0284C7), shape = RoundedCornerShape(4.dp)) {
                                                                    Text(
                                                                        text = if (daysDifference > 0) "In $daysDifference days" else "Pending",
                                                                        color = Color.White,
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        fontWeight = FontWeight.Bold,
                                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }

                                                    Text(
                                                        text = po.supplierName,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }

                                                Text(
                                                    text = currencyFormat.format(po.totalAmount),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.LocalShipping,
                                                        contentDescription = null,
                                                        tint = if (isOverdue) Color(0xFFDC2626) else MaterialTheme.colorScheme.secondary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = "Delivery: ${dayDateFormat.format(Date(po.expectedDeliveryDateMillis))}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isOverdue) Color(0xFFDC2626) else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                Text(
                                                    text = "📍 ${po.location}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Action buttons: Remind Vendor, Mark Received, View / Print
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (supplier != null && supplier.phone.isNotBlank() && po.status != "RECEIVED") {
                                                    OutlinedButton(
                                                        onClick = {
                                                            val cleanPhone = supplier.phone.replace(Regex("[^0-9+]"), "")
                                                            val message = "Hello ${supplier.name}, reminder regarding PO #${po.poNumber} (Amount: ${currencyFormat.format(po.totalAmount)}) expected on ${dayDateFormat.format(Date(po.expectedDeliveryDateMillis))}. Please confirm dispatch. - ${companyProfile.companyName.ifBlank { "PO Master ERP" }}"
                                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                                data = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}")
                                                            }
                                                            try {
                                                                context.startActivity(intent)
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "WhatsApp not available", Toast.LENGTH_SHORT).show()
                                                            }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                        modifier = Modifier.padding(end = 6.dp)
                                                    ) {
                                                        Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("Remind", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }

                                                if (po.status == "ISSUED" || po.status == "PARTIALLY_RECEIVED") {
                                                    FilledTonalButton(
                                                        onClick = {
                                                            viewModel.updatePOStatus(po, "RECEIVED")
                                                            Toast.makeText(context, "PO #${po.poNumber} Marked as RECEIVED!", Toast.LENGTH_SHORT).show()
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                        modifier = Modifier.padding(end = 6.dp)
                                                    ) {
                                                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("Receive", style = MaterialTheme.typography.labelSmall)
                                                    }
                                                }

                                                Button(
                                                    onClick = {
                                                        viewModel.loadPOItems(po.id)
                                                        poToPrintShare = po
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("View PO", style = MaterialTheme.typography.labelSmall)
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

/**
 * Add / Edit Purchase Plan & Trip Note Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditPurchasePlanNoteDialog(
    initialPlan: PurchasePlanNoteEntity? = null,
    initialDateMillis: Long = System.currentTimeMillis(),
    initialToDateMillis: Long = 0L,
    initialTitle: String = "",
    initialLocation: String = "",
    initialBudget: Double = 0.0,
    currencyFormat: NumberFormat,
    onDismiss: () -> Unit,
    onSave: (
        id: Long,
        title: String,
        dateMillis: Long,
        toDateMillis: Long,
        location: String,
        notes: String,
        budget: Double,
        category: String,
        priority: String,
        status: String,
        reminderDays: Int
    ) -> Unit,
    onDelete: ((PurchasePlanNoteEntity) -> Unit)? = null
) {
    val context = LocalContext.current
    val dayDateFormat = remember { SimpleDateFormat("dd MMMM yyyy", Locale.forLanguageTag("en-IN")) }

    var title by remember { mutableStateOf(initialPlan?.title ?: initialTitle) }
    var fromDateMillis by remember { mutableStateOf(initialPlan?.plannedDateMillis ?: initialDateMillis) }
    var toDateMillis by remember {
        mutableStateOf(
            if (initialPlan != null && initialPlan.toDateMillis > 0L) {
                initialPlan.toDateMillis
            } else if (initialToDateMillis > 0L) {
                initialToDateMillis
            } else {
                fromDateMillis
            }
        )
    }
    var location by remember { mutableStateOf(initialPlan?.location ?: initialLocation) }
    var notes by remember { mutableStateOf(initialPlan?.notes ?: "") }
    var budgetStr by remember { mutableStateOf(if ((initialPlan?.estimatedBudget ?: initialBudget) > 0) (initialPlan?.estimatedBudget ?: initialBudget).toInt().toString() else "") }
    var categoryOrSupplier by remember { mutableStateOf(initialPlan?.categoryOrSupplier ?: "") }
    var priority by remember { mutableStateOf(initialPlan?.priority ?: "MEDIUM") }
    var status by remember { mutableStateOf(initialPlan?.status ?: "PLANNED") }
    var reminderDays by remember { mutableIntStateOf(initialPlan?.reminderDaysBefore ?: 1) }

    val quickCities = listOf("Bombay", "Surat", "Tirupur", "Ahmedabad", "Delhi", "Kolkata", "Local Mill")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            LazyColumn(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (initialPlan != null) "Edit Purchase Plan Note" else "Plan Sourcing Trip & Purchase",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title / Purpose *") },
                        placeholder = { Text("e.g. Bombay Silk Market Trip, Surat Mill Visit") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) }
                    )
                }

                // Date Range (From & To Date) Pickers
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Planning Date / Trip Period",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // From Date Picker
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val cal = Calendar.getInstance().apply { timeInMillis = fromDateMillis }
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                val newCal = Calendar.getInstance().apply {
                                                    set(Calendar.YEAR, year)
                                                    set(Calendar.MONTH, month)
                                                    set(Calendar.DAY_OF_MONTH, day)
                                                    set(Calendar.HOUR_OF_DAY, 0)
                                                    set(Calendar.MINUTE, 0)
                                                    set(Calendar.SECOND, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                }
                                                val selectedFrom = newCal.timeInMillis
                                                fromDateMillis = selectedFrom
                                                if (toDateMillis < selectedFrom) {
                                                    toDateMillis = selectedFrom
                                                }
                                            },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("From Date (Start)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        dayDateFormat.format(Date(fromDateMillis)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // To Date Picker
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val cal = Calendar.getInstance().apply { timeInMillis = if (toDateMillis >= fromDateMillis) toDateMillis else fromDateMillis }
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                val newCal = Calendar.getInstance().apply {
                                                    set(Calendar.YEAR, year)
                                                    set(Calendar.MONTH, month)
                                                    set(Calendar.DAY_OF_MONTH, day)
                                                    set(Calendar.HOUR_OF_DAY, 0)
                                                    set(Calendar.MINUTE, 0)
                                                    set(Calendar.SECOND, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                }
                                                val selectedTo = newCal.timeInMillis
                                                toDateMillis = if (selectedTo < fromDateMillis) fromDateMillis else selectedTo
                                            },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("To Date (End)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        dayDateFormat.format(Date(toDateMillis)),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Quick Duration Shortcuts
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            item {
                                FilterChip(
                                    selected = toDateMillis <= fromDateMillis,
                                    onClick = { toDateMillis = fromDateMillis },
                                    label = { Text("1 Day", fontSize = 11.sp) }
                                )
                            }
                            item {
                                val target = fromDateMillis + TimeUnit.DAYS.toMillis(1)
                                FilterChip(
                                    selected = isSameDay(toDateMillis, target),
                                    onClick = { toDateMillis = target },
                                    label = { Text("2 Days", fontSize = 11.sp) }
                                )
                            }
                            item {
                                val target = fromDateMillis + TimeUnit.DAYS.toMillis(2)
                                FilterChip(
                                    selected = isSameDay(toDateMillis, target),
                                    onClick = { toDateMillis = target },
                                    label = { Text("3 Days", fontSize = 11.sp) }
                                )
                            }
                            item {
                                val target = fromDateMillis + TimeUnit.DAYS.toMillis(4)
                                FilterChip(
                                    selected = isSameDay(toDateMillis, target),
                                    onClick = { toDateMillis = target },
                                    label = { Text("5 Days", fontSize = 11.sp) }
                                )
                            }
                            item {
                                val target = fromDateMillis + TimeUnit.DAYS.toMillis(6)
                                FilterChip(
                                    selected = isSameDay(toDateMillis, target),
                                    onClick = { toDateMillis = target },
                                    label = { Text("1 Week", fontSize = 11.sp) }
                                )
                            }
                        }

                        // Trip Summary Pill
                        if (toDateMillis > fromDateMillis) {
                            val durationDays = TimeUnit.MILLISECONDS.toDays(toDateMillis - fromDateMillis) + 1
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF3E8FF),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.FlightTakeoff, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF7E22CE))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "✈️ $durationDays Days Sourcing Trip (${dayDateFormat.format(Date(fromDateMillis))} → ${dayDateFormat.format(Date(toDateMillis))})",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF7E22CE)
                                    )
                                }
                            }
                        }
                    }
                }

                // Location / City & Quick City Chips
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("Destination / Market Location") },
                            placeholder = { Text("e.g. Bombay, Surat, Tirupur, Ahmedabad") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(quickCities) { city ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (location.equals(city, ignoreCase = true)) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.clickable { location = city }
                                ) {
                                    Text(
                                        text = city,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (location.equals(city, ignoreCase = true)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Estimated Budget (₹) & Category
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = budgetStr,
                            onValueChange = { budgetStr = it },
                            label = { Text("Est. Budget (₹)") },
                            placeholder = { Text("e.g. 50000") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )

                        OutlinedTextField(
                            value = categoryOrSupplier,
                            onValueChange = { categoryOrSupplier = it },
                            label = { Text("Target Vendor / Category") },
                            placeholder = { Text("e.g. Silk, Banarasi Mill") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }

                // Notes / Purchase Requirements Memo
                item {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Detailed Items & Sourcing Requirements") },
                        placeholder = { Text("e.g. Buy 500m Banarasi Silk, 200m Cotton Voile, check rate below ₹180/m") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp),
                        maxLines = 4
                    )
                }

                // Status & Priority Selectors
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Priority", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("LOW", "MEDIUM", "HIGH").forEach { p ->
                                    FilterChip(
                                        selected = priority == p,
                                        onClick = { priority = p },
                                        label = { Text(p, fontSize = 10.sp) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Reminder Notification Timing & Alert Config
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔔 Alert Reminder Schedule", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = when (reminderDays) {
                                    0 -> "On Day of Event"
                                    1 -> "1 Day Before (Default)"
                                    else -> "$reminderDays Days Before"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val reminderOptions = listOf(
                                0 to "Event Day",
                                1 to "1 Day Before",
                                2 to "2 Days Before",
                                3 to "3 Days Before",
                                7 to "1 Week Before"
                            )
                            items(reminderOptions) { (days, label) ->
                                FilterChip(
                                    selected = reminderDays == days,
                                    onClick = { reminderDays = days },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                // Actions: Save / Delete
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (initialPlan != null && onDelete != null) {
                            TextButton(
                                onClick = { onDelete(initialPlan) },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delete")
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = {
                                if (title.isBlank()) {
                                    Toast.makeText(context, "Please enter plan title", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val budget = budgetStr.replace(",", "").toDoubleOrNull() ?: 0.0
                                val savedToDate = if (toDateMillis > fromDateMillis) toDateMillis else 0L
                                onSave(
                                    initialPlan?.id ?: 0L,
                                    title,
                                    fromDateMillis,
                                    savedToDate,
                                    location,
                                    notes,
                                    budget,
                                    categoryOrSupplier,
                                    priority,
                                    status,
                                    reminderDays
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("save_purchase_plan_btn")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Plan Note", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Filter by Date Range Dialog (From - To)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeFilterDialog(
    initialFromDate: Long,
    initialToDate: Long,
    dayDateFormat: SimpleDateFormat,
    onDismiss: () -> Unit,
    onApply: (fromMillis: Long, toMillis: Long) -> Unit,
    onClear: () -> Unit
) {
    val context = LocalContext.current
    var fromMillis by remember { mutableStateOf(initialFromDate) }
    var toMillis by remember { mutableStateOf(if (initialToDate >= initialFromDate) initialToDate else initialFromDate) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = "Filter by Date Range",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Text(
                    text = "Select custom date range to view PO deliveries and planned sourcing trips:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // From & To Date Pickers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = fromMillis }
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        val newCal = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, year)
                                            set(Calendar.MONTH, month)
                                            set(Calendar.DAY_OF_MONTH, day)
                                            set(Calendar.HOUR_OF_DAY, 0)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        fromMillis = newCal.timeInMillis
                                        if (toMillis < fromMillis) {
                                            toMillis = fromMillis
                                        }
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("From Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(dayDateFormat.format(Date(fromMillis)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val cal = Calendar.getInstance().apply { timeInMillis = toMillis }
                                DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        val newCal = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, year)
                                            set(Calendar.MONTH, month)
                                            set(Calendar.DAY_OF_MONTH, day)
                                            set(Calendar.HOUR_OF_DAY, 0)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        val selectedTo = newCal.timeInMillis
                                        toMillis = if (selectedTo < fromMillis) fromMillis else selectedTo
                                    },
                                    cal.get(Calendar.YEAR),
                                    cal.get(Calendar.MONTH),
                                    cal.get(Calendar.DAY_OF_MONTH)
                                ).show()
                            }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("To Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(dayDateFormat.format(Date(toMillis)), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Quick Range Presets
                Text("Quick Presets:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        SuggestionChip(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.DAY_OF_WEEK, getActualMinimum(Calendar.DAY_OF_WEEK))
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                }
                                fromMillis = cal.timeInMillis
                                cal.add(Calendar.DAY_OF_WEEK, 6)
                                toMillis = cal.timeInMillis
                            },
                            label = { Text("This Week", fontSize = 11.sp) }
                        )
                    }
                    item {
                        SuggestionChip(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.DAY_OF_MONTH, 1)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                }
                                fromMillis = cal.timeInMillis
                                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                                toMillis = cal.timeInMillis
                            },
                            label = { Text("This Month", fontSize = 11.sp) }
                        )
                    }
                    item {
                        SuggestionChip(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                }
                                fromMillis = cal.timeInMillis
                                toMillis = fromMillis + TimeUnit.DAYS.toMillis(7)
                            },
                            label = { Text("Next 7 Days", fontSize = 11.sp) }
                        )
                    }
                    item {
                        SuggestionChip(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                }
                                fromMillis = cal.timeInMillis
                                toMillis = fromMillis + TimeUnit.DAYS.toMillis(14)
                            },
                            label = { Text("Next 14 Days", fontSize = 11.sp) }
                        )
                    }
                    item {
                        SuggestionChip(
                            onClick = {
                                val cal = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                }
                                fromMillis = cal.timeInMillis
                                toMillis = fromMillis + TimeUnit.DAYS.toMillis(30)
                            },
                            label = { Text("Next 30 Days", fontSize = 11.sp) }
                        )
                    }
                }

                // Actions: Clear & Apply
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onClear) {
                        Icon(Icons.Default.ClearAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Filter")
                    }

                    Button(
                        onClick = {
                            onApply(fromMillis, toMillis)
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Apply Filter", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarMetricCard(
    title: String,
    count: Int,
    containerColor: Color,
    contentColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.85f),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = contentColor
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

fun isSameDay(millis1: Long, millis2: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { timeInMillis = millis1 }
    val cal2 = Calendar.getInstance().apply { timeInMillis = millis2 }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
