package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.*
import com.example.ui.components.CameraPhotoSnapDialog
import com.example.ui.components.PrintPOShareDialog
import com.example.ui.components.PurchasePlanningCalendarDialog
import com.example.ui.theme.rememberWindowSizeInfo
import com.example.ui.viewmodel.POMasterViewModel
import coil.compose.AsyncImage
import java.text.DateFormat
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcurementMasterScreen(
    viewModel: POMasterViewModel,
    userSession: UserSession? = null
) {
    val windowInfo = rememberWindowSizeInfo()
    val purchaseOrders by viewModel.purchaseOrdersState.collectAsState()
    val suppliers by viewModel.suppliersState.collectAsState()
    val inventoryList by viewModel.inventoryState.collectAsState()
    val locations by viewModel.locationsState.collectAsState()
    val companyProfile by viewModel.companyProfileState.collectAsState()
    val storeHierarchy by viewModel.storeHierarchyState.collectAsState()
    val categoryTaxRules by viewModel.categoryTaxRulesState.collectAsState()
    val selectedPO by viewModel.selectedPO.collectAsState()
    val selectedPOItems by viewModel.selectedPOItems.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("All") }

    var showCreatePODialog by remember { mutableStateOf(false) }
    var showReceiveGoodsDialog by remember { mutableStateOf(false) }
    var showPlanningCalendarDialog by remember { mutableStateOf(false) }
    var poToPrintShare by remember { mutableStateOf<PurchaseOrderEntity?>(null) }
    var initialDeliveryDateFromCalendar by remember { mutableStateOf<Long?>(null) }

    val context = LocalContext.current
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    val statuses = listOf("All", "DRAFT", "ISSUED", "PARTIALLY_RECEIVED", "RECEIVED", "CANCELLED")

    val filteredPOs = remember(purchaseOrders, searchQuery, selectedStatusFilter) {
        purchaseOrders.filter { po ->
            val matchesStatus = selectedStatusFilter == "All" || po.status == selectedStatusFilter
            val matchesSearch = searchQuery.isBlank() ||
                    po.poNumber.contains(searchQuery, ignoreCase = true) ||
                    po.supplierName.contains(searchQuery, ignoreCase = true)
            matchesStatus && matchesSearch
        }
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { 
                    initialDeliveryDateFromCalendar = null
                    showCreatePODialog = true 
                },
                icon = { Icon(Icons.Default.Add, contentDescription = "Create PO") },
                text = { Text("Issue New PO") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("create_po_fab")
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 840.dp)
                    .padding(horizontal = windowInfo.defaultPadding)
            ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar & Calendar Planning Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("po_search_input"),
                    placeholder = { Text("Search PO number, supplier...") },
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

                FilledTonalIconButton(
                    onClick = { showPlanningCalendarDialog = true },
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("procurement_calendar_tool_btn")
                ) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = "Purchase Planning Calendar",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(statuses) { status ->
                    val chipLabel = when (status) {
                        "All" -> "All"
                        "DRAFT" -> "Draft"
                        "ISSUED" -> "Issued"
                        "PARTIALLY_RECEIVED" -> "Partial"
                        "RECEIVED" -> "Received"
                        "CANCELLED" -> "Cancelled"
                        else -> status.replace("_", " ")
                    }
                    FilterChip(
                        selected = selectedStatusFilter == status,
                        onClick = { selectedStatusFilter = status },
                        label = { Text(chipLabel) },
                        modifier = Modifier.testTag("po_filter_$status")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // PO List
            if (filteredPOs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No purchase orders found",
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
                    items(filteredPOs, key = { it.id }) { po ->
                        POCard(
                            po = po,
                            currencyFormat = currencyFormat,
                            dateFormat = dateFormat,
                            onClick = {
                                viewModel.setSelectedPO(po)
                            },
                            onReceiveClick = {
                                viewModel.setSelectedPO(po)
                                showReceiveGoodsDialog = true
                            },
                            onPrintShareClick = {
                                viewModel.setSelectedPO(po)
                                poToPrintShare = po
                            }
                        )
                    }
                }
            }
        }
    }
}

    // Selected PO Detail Modal Sheet
    selectedPO?.let { po ->
        val canDelete = userSession == null || userSession.isAdmin || userSession.canDeletePO
        PODetailBottomSheet(
            po = po,
            poItems = selectedPOItems,
            categoryTaxRules = categoryTaxRules,
            currencyFormat = currencyFormat,
            dateFormat = dateFormat,
            canDeletePO = canDelete,
            onDismiss = { viewModel.setSelectedPO(null) },
            onUpdateStatus = { newStatus -> viewModel.updatePOStatus(po, newStatus) },
            onReceiveGoods = { showReceiveGoodsDialog = true },
            onPrintShare = { poToPrintShare = po },
            onDeletePO = { viewModel.deletePO(po) }
        )
    }

    // Print PO & Share via WhatsApp Dialog
    poToPrintShare?.let { po ->
        val supplierObj = suppliers.find { it.id == po.supplierId }
        val supplierName = supplierObj?.name ?: po.supplierName
        PrintPOShareDialog(
            po = po,
            poItems = selectedPOItems,
            categoryTaxRules = categoryTaxRules,
            supplierName = supplierName,
            supplier = supplierObj,
            companyProfile = companyProfile,
            currencyFormat = currencyFormat,
            dateFormat = dateFormat,
            onDismiss = { poToPrintShare = null }
        )
    }

    // Purchase Planning & Delivery Reminder Calendar Dialog
    if (showPlanningCalendarDialog) {
        PurchasePlanningCalendarDialog(
            viewModel = viewModel,
            onDismiss = { showPlanningCalendarDialog = false }
        )
    }

    // Create New PO Dialog Flow
    if (showCreatePODialog) {
        CreatePODialog(
            suppliers = suppliers,
            inventoryList = inventoryList,
            storeHierarchy = storeHierarchy,
            locations = locations,
            categoryTaxRules = categoryTaxRules,
            currencyFormat = currencyFormat,
            targetDeliveryDateMillis = initialDeliveryDateFromCalendar,
            onDismiss = { 
                showCreatePODialog = false
                initialDeliveryDateFromCalendar = null
            },
            onCreatePO = { supplier, deliveryDate, notes, lineItemInputs, poLocation, photoUri, status ->
                viewModel.createPurchaseOrder(
                    supplier = supplier,
                    paymentTerms = "Standard",
                    expectedDeliveryDateMillis = deliveryDate,
                    notes = notes,
                    lineItemInputs = lineItemInputs,
                    poLocation = poLocation,
                    poPhotoUri = photoUri,
                    status = status
                )
                showCreatePODialog = false
                initialDeliveryDateFromCalendar = null
            }
        )
    }

    // Receive Goods Flow Dialog (updates PO received quantities and auto-increments stock!)
    if (showReceiveGoodsDialog && selectedPO != null) {
        ReceiveGoodsDialog(
            po = selectedPO!!,
            items = selectedPOItems,
            onDismiss = { showReceiveGoodsDialog = false },
            onConfirmReceipt = { updatedItems ->
                viewModel.receivePOItemsAndFulfill(selectedPO!!, updatedItems)
                showReceiveGoodsDialog = false
            }
        )
    }
}

@Composable
private fun POCard(
    po: PurchaseOrderEntity,
    currencyFormat: NumberFormat,
    dateFormat: DateFormat,
    onClick: () -> Unit,
    onReceiveClick: (() -> Unit)? = null,
    onPrintShareClick: (() -> Unit)? = null
) {
    val (statusBg, statusFg) = when (po.status) {
        "DRAFT" -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
        "ISSUED" -> Pair(Color(0xFFE3F2FD), Color(0xFF1565C0))
        "PARTIALLY_RECEIVED" -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100))
        "RECEIVED" -> Pair(Color(0xFFE8F5E9), Color(0xFF2E7D32))
        "CANCELLED" -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828))
        else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("po_card_${po.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = statusBg,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = null,
                                tint = statusFg
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = po.poNumber,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = po.supplierName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (po.location.isNotBlank()) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = "📍 ${po.location}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                val statusLabel = when (po.status) {
                    "DRAFT" -> "Draft"
                    "ISSUED" -> "Issued"
                    "PARTIALLY_RECEIVED" -> "Partial"
                    "RECEIVED" -> "Received"
                    "CANCELLED" -> "Cancelled"
                    else -> po.status.replace("_", " ")
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = statusBg
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Ordered: ${dateFormat.format(Date(po.orderDateMillis))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Expected: ${dateFormat.format(Date(po.expectedDeliveryDateMillis))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (po.photoUri != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = po.photoUri,
                                contentDescription = "Product Reference Image",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Total Amount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = currencyFormat.format(po.totalAmount),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (onPrintShareClick != null) {
                    OutlinedButton(
                        onClick = onPrintShareClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Print / WhatsApp", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (po.status != "RECEIVED" && po.status != "CANCELLED" && onReceiveClick != null) {
                    Button(
                        onClick = onReceiveClick,
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Receive Goods", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PODetailBottomSheet(
    po: PurchaseOrderEntity,
    poItems: List<PurchaseOrderItemEntity>,
    categoryTaxRules: List<com.example.data.entity.CategoryTaxEntity> = emptyList(),
    currencyFormat: NumberFormat,
    dateFormat: DateFormat,
    canDeletePO: Boolean = true,
    onDismiss: () -> Unit,
    onUpdateStatus: (String) -> Unit,
    onReceiveGoods: () -> Unit,
    onPrintShare: () -> Unit,
    onDeletePO: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(
                        text = po.poNumber,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Supplier: ${po.supplierName}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Timeline & Status row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Status: ${po.status.replace("_", " ")}",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Terms: ${po.paymentTerms}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (po.photoUri != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Product Reference Photo",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = po.photoUri,
                                contentDescription = "PO Reference Photo",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Order Line Items",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                items(poItems, key = { it.id }) { item ->
                    val lineTaxPercent = remember(item, categoryTaxRules) {
                        if (item.taxPercent > 0.0) {
                            item.taxPercent.toInt()
                        } else {
                            com.example.service.TaxMasterService.getTaxRate(
                                itemName = item.itemName,
                                categoryName = item.division.ifBlank { item.department },
                                costAmount = item.unitPrice,
                                categoryTaxRules = categoryTaxRules,
                                division = item.division,
                                department = item.department,
                                section = item.section
                            ).toInt()
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (!item.photoUri.isNullOrBlank()) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                    ) {
                                        AsyncImage(
                                            model = item.photoUri,
                                            contentDescription = "Item Photo",
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = item.itemName, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    val poCats = listOfNotNull(
                                        item.c1Brand.takeIf { it.isNotBlank() }?.let { "Brand: $it" },
                                        item.c2Size.takeIf { it.isNotBlank() }?.let { "Size: $it" },
                                        item.c3Design.takeIf { it.isNotBlank() }?.let { "Design: $it" },
                                        item.c4Colour.takeIf { it.isNotBlank() }?.let { "Colour: $it" }
                                    ).joinToString(" | ")
                                    if (poCats.isNotBlank()) {
                                        Text(text = poCats, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (item.location.isNotBlank()) {
                                            Text(text = "📍 ${item.location}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Text(
                                        text = "Ordered: ${item.quantityOrdered} | Received: ${item.quantityReceived} (GST $lineTaxPercent%)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (item.quantityReceived >= item.quantityOrdered) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(text = currencyFormat.format(item.unitPrice) + " ea", style = MaterialTheme.typography.bodySmall)
                                Text(text = currencyFormat.format(item.totalPrice), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // Dynamic Financial Summary with Split GST Breakdown
            val taxGroups = remember(poItems) {
                poItems.groupBy { it.taxPercent }.toSortedMap()
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Total Taxable Approx Cost Amount:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(currencyFormat.format(po.subtotal), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Multi-tax Split Breakdown
            if (taxGroups.isNotEmpty()) {
                taxGroups.forEach { (rate, items) ->
                    val taxable = items.sumOf { it.totalPrice }
                    val tax = items.sumOf { it.taxAmount.takeIf { t -> t > 0.0 } ?: (it.totalPrice * (rate / 100.0)) }
                    val halfRate = rate / 2.0
                    val halfTax = tax / 2.0

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Taxable Amount (GST ${if (rate % 1.0 == 0.0) rate.toInt().toString() else rate.toString()}%):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Text(currencyFormat.format(taxable), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("  ↳ Tax Amount (GST ${if (rate % 1.0 == 0.0) rate.toInt().toString() else rate.toString()}%):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                Text(currencyFormat.format(tax), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("     • CGST ($halfRate%): ${currencyFormat.format(halfTax)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("• SGST ($halfRate%): ${currencyFormat.format(halfTax)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            } else if (po.taxAmount > 0) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("GST Input Tax:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(currencyFormat.format(po.taxAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Total PO Amount (Grand Total):", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(currencyFormat.format(po.totalAmount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onPrintShare,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Print / WhatsApp")
                }

                if (po.status != "RECEIVED" && po.status != "CANCELLED") {
                    Button(
                        onClick = onReceiveGoods,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Receive")
                    }
                }

                if (po.status == "DRAFT") {
                    Button(
                        onClick = { onUpdateStatus("ISSUED") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Issue PO")
                    }
                }

                if (canDeletePO) {
                    OutlinedButton(
                        onClick = onDeletePO,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete PO", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReceiveGoodsDialog(
    po: PurchaseOrderEntity,
    items: List<PurchaseOrderItemEntity>,
    onDismiss: () -> Unit,
    onConfirmReceipt: (List<PurchaseOrderItemEntity>) -> Unit
) {
    // Local state for receiving quantities
    val receivedQtyMap = remember {
        mutableStateMapOf<Long, Int>().apply {
            items.forEach { item ->
                put(item.id, item.quantityOrdered - item.quantityReceived)
            }
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
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        title = { Text("Receive Goods for ${po.poNumber}") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Input newly received quantities. Inventory stock will automatically increase upon confirmation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 280.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        val currentPending = item.quantityOrdered - item.quantityReceived
                        var inputStr by remember { mutableStateOf(receivedQtyMap[item.id]?.toString() ?: "0") }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(text = item.itemName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "Ordered: ${item.quantityOrdered} | Already Received: ${item.quantityReceived} | Outstanding: $currentPending",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = inputStr,
                                    onValueChange = {
                                        inputStr = it.filter { c -> c.isDigit() }
                                        receivedQtyMap[item.id] = inputStr.toIntOrNull() ?: 0
                                    },
                                    label = { Text("Receiving Now") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    modifier = Modifier.fillMaxWidth()
                                )
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
                    val updatedItems = items.map { item ->
                        val newlyReceived = receivedQtyMap[item.id] ?: 0
                        item.copy(quantityReceived = item.quantityReceived + newlyReceived)
                    }
                    onConfirmReceipt(updatedItems)
                }
            ) {
                Text("Confirm & Update Inventory")
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePODialog(
    suppliers: List<SupplierEntity>,
    inventoryList: List<InventoryEntity>,
    storeHierarchy: List<StoreHierarchyEntity>,
    locations: List<LocationEntity>,
    categoryTaxRules: List<com.example.data.entity.CategoryTaxEntity> = emptyList(),
    currencyFormat: NumberFormat,
    targetDeliveryDateMillis: Long? = null,
    onDismiss: () -> Unit,
    onCreatePO: (
        supplier: SupplierEntity,
        deliveryDateMillis: Long,
        notes: String,
        lineItems: List<com.example.ui.viewmodel.POLineItemInput>,
        poLocation: String,
        photoUri: String?,
        status: String
    ) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    val safeDismiss = {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }
    var selectedSupplier by remember { mutableStateOf(suppliers.firstOrNull()) }
    var supplierSearchText by remember { mutableStateOf("") }
    var supplierDropdownExpanded by remember { mutableStateOf(false) }

    var catalogSearchQuery by remember { mutableStateOf("") }
    var catalogDropdownExpanded by remember { mutableStateOf(false) }
    val filteredCatalogItems = remember(inventoryList, catalogSearchQuery) {
        if (catalogSearchQuery.isBlank()) inventoryList.take(20)
        else inventoryList.filter {
            it.name.contains(catalogSearchQuery, ignoreCase = true) ||
            it.sku.contains(catalogSearchQuery, ignoreCase = true) ||
            it.department.contains(catalogSearchQuery, ignoreCase = true) ||
            it.category.contains(catalogSearchQuery, ignoreCase = true)
        }.take(20)
    }

    val filteredSuppliers = remember(suppliers, supplierSearchText) {
        if (supplierSearchText.isBlank()) suppliers
        else suppliers.filter {
            it.name.contains(supplierSearchText, ignoreCase = true) ||
            it.code.contains(supplierSearchText, ignoreCase = true) ||
            it.contactPerson.contains(supplierSearchText, ignoreCase = true)
        }
    }

    // Division options created in Database
    val divisionOptions = remember(inventoryList, storeHierarchy) {
        val fromHierarchy = storeHierarchy.map { it.division.trim() }
        val fromInventory = inventoryList.map { it.division.trim() }
        val combined = (fromHierarchy + fromInventory).filter { it.isNotBlank() }.distinct().sorted()
        if (combined.isEmpty()) listOf("Readymade", "Textile") else combined
    }
    var selectedDivision by remember { mutableStateOf("") }
    var divisionSearchText by remember { mutableStateOf("") }
    var divisionDropdownExpanded by remember { mutableStateOf(false) }

    val filteredDivisions = remember(divisionOptions, divisionSearchText) {
        if (divisionSearchText.isBlank()) divisionOptions
        else divisionOptions.filter { it.contains(divisionSearchText, ignoreCase = true) }
    }

    // Section options created in Database filtered by selected Division
    val sectionOptions = remember(inventoryList, storeHierarchy, selectedDivision) {
        val fromHierarchy = storeHierarchy
            .filter { selectedDivision.isBlank() || it.division.equals(selectedDivision, ignoreCase = true) }
            .map { it.section.trim() }
        val fromInventory = inventoryList
            .filter { selectedDivision.isBlank() || it.division.equals(selectedDivision, ignoreCase = true) }
            .map { it.section.trim() }
        val combined = (fromHierarchy + fromInventory).filter { it.isNotBlank() }.distinct().sorted()
        if (combined.isNotEmpty()) combined
        else if (selectedDivision.equals("Textile", ignoreCase = true)) listOf("Sarees", "Fabrics & Suitings", "Dhotis & Handlooms", "Dress Materials")
        else listOf("Men's Wear", "Women's Wear", "Kids Wear", "General Section")
    }
    var selectedSection by remember { mutableStateOf("") }
    var sectionSearchText by remember { mutableStateOf("") }
    var sectionDropdownExpanded by remember { mutableStateOf(false) }

    val filteredSections = remember(sectionOptions, sectionSearchText) {
        if (sectionSearchText.isBlank()) sectionOptions
        else sectionOptions.filter { it.contains(sectionSearchText, ignoreCase = true) }
    }

    // Department options created in Database filtered by selected Division & Section
    val departmentOptions = remember(inventoryList, storeHierarchy, selectedDivision, selectedSection) {
        val fromHierarchy = storeHierarchy
            .filter {
                (selectedDivision.isBlank() || it.division.equals(selectedDivision, ignoreCase = true)) &&
                (selectedSection.isBlank() || it.section.equals(selectedSection, ignoreCase = true))
            }
            .map { it.department.trim() }
        val fromInventory = inventoryList
            .filter {
                (selectedDivision.isBlank() || it.division.equals(selectedDivision, ignoreCase = true)) &&
                (selectedSection.isBlank() || it.section.equals(selectedSection, ignoreCase = true))
            }
            .map { it.department.trim() }
        val combined = (fromHierarchy + fromInventory).filter { it.isNotBlank() }.distinct().sorted()
        if (combined.isNotEmpty()) combined
        else if (selectedDivision.equals("Textile", ignoreCase = true)) listOf("Silk Sarees", "Cotton Sarees", "Shirting & Suiting", "Traditional Dhotis")
        else listOf("Shirts & Tops", "Trousers & Jeans", "Kurtis & Salwars", "Kids Frocks")
    }
    var selectedDepartment by remember { mutableStateOf("") }
    var departmentSearchText by remember { mutableStateOf("") }
    var departmentDropdownExpanded by remember { mutableStateOf(false) }

    val filteredDepartments = remember(departmentOptions, departmentSearchText) {
        if (departmentSearchText.isBlank()) departmentOptions
        else departmentOptions.filter { it.contains(departmentSearchText, ignoreCase = true) }
    }

    // Cost rate box state (empty initially)
    var costRateInputStr by remember { mutableStateOf("") }

    // Resolve Category from Inventory Master or Store Hierarchy for the selected Department
    val resolvedCategory = remember(selectedDepartment, selectedDivision, selectedSection, inventoryList) {
        val matchingItem = inventoryList.find { item ->
            (selectedDepartment.isNotBlank() && item.department.equals(selectedDepartment, ignoreCase = true)) ||
            (selectedDepartment.isNotBlank() && item.name.equals(selectedDepartment, ignoreCase = true)) ||
            (selectedDepartment.isNotBlank() && item.sku.equals(selectedDepartment, ignoreCase = true))
        }
        if (matchingItem != null && matchingItem.category.isNotBlank()) {
            matchingItem.category
        } else {
            selectedDivision.ifBlank { "General" }
        }
    }

    // Inbuilt GST Tax Rate automatically computed based on Statutory & Tax Master Rules:
    // - Custom Tax Master Rules: Prioritized
    // - Textile: Always 5% GST
    // - Readymade: Below ₹2,500 = 5% GST, ₹2,500 and above = 18% GST
    val activeItemTaxRate = remember(selectedDepartment, selectedDivision, selectedSection, costRateInputStr, categoryTaxRules, resolvedCategory) {
        val cost = costRateInputStr.toDoubleOrNull() ?: 0.0
        com.example.service.TaxMasterService.getTaxRate(
            itemName = selectedDepartment,
            categoryName = resolvedCategory,
            costAmount = cost,
            categoryTaxRules = categoryTaxRules,
            division = selectedDivision,
            department = selectedDepartment,
            section = selectedSection
        )
    }

    // Multi-Location selection via tick boxes (map of locationName -> quantity string)
    val locationNames = remember(locations) {
        if (locations.isEmpty()) listOf("Central Warehouse", "Main Store", "Outlet #1")
        else locations.map { it.name }
    }
    val selectedLocationQtys = remember { mutableStateMapOf<String, String>() }

    val initialDeliveryDays = remember(targetDeliveryDateMillis) {
        if (targetDeliveryDateMillis != null && targetDeliveryDateMillis > System.currentTimeMillis()) {
            val diff = ((targetDeliveryDateMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).coerceAtLeast(1)
            diff.toString()
        } else {
            "7"
        }
    }
    var deliveryDaysStr by remember(targetDeliveryDateMillis) { mutableStateOf(initialDeliveryDays) }
    var notes by remember { mutableStateOf("") }

    // Camera photo for item
    var showItemPhotoCameraDialog by remember { mutableStateOf(false) }
    var lineItemPhotoUri by remember { mutableStateOf<String?>(null) }

    // Multi line items list
    val selectedLineItems = remember { mutableStateListOf<com.example.ui.viewmodel.POLineItemInput>() }

    // Navigation Step state (False = Add Items Form Mode, True = Review Totals Summary Mode)
    var isReviewMode by remember { mutableStateOf(false) }

    val canAddItem by remember {
        derivedStateOf {
            selectedDivision.isNotBlank() &&
                    selectedSection.isNotBlank() &&
                    selectedDepartment.isNotBlank() &&
                    (costRateInputStr.toDoubleOrNull() ?: 0.0) > 0.0 &&
                    selectedLocationQtys.any { (it.value.toIntOrNull() ?: 0) > 0 }
        }
    }

    fun resetItemFormFields() {
        selectedDivision = ""
        divisionSearchText = ""
        selectedSection = ""
        sectionSearchText = ""
        selectedDepartment = ""
        departmentSearchText = ""
        costRateInputStr = ""
        selectedLocationQtys.clear()
        lineItemPhotoUri = null
        divisionDropdownExpanded = false
        sectionDropdownExpanded = false
        departmentDropdownExpanded = false
    }

    fun addSelectedItemsToPO() {
        if (!canAddItem) return
        val costRate = costRateInputStr.toDoubleOrNull() ?: 1500.0
        val lineItemName = selectedDepartment.ifBlank { "Department" }
        val finalTaxRate = activeItemTaxRate

        selectedLocationQtys.forEach { (locName, qtyStr) ->
            val qty = qtyStr.toIntOrNull() ?: 0
            if (qty > 0) {
                val dynamicItem = InventoryEntity(
                    id = System.currentTimeMillis() % 1000000L + (locName.hashCode().coerceAtLeast(0) % 1000L),
                    sku = selectedDepartment.ifBlank { "Department" },
                    name = lineItemName,
                    category = resolvedCategory.ifBlank { selectedDivision.ifBlank { "General" } },
                    unit = "Pcs",
                    unitCost = costRate,
                    standardSellingPrice = costRate * 1.3,
                    currentStock = 0,
                    minReorderLevel = 10,
                    defaultSupplierId = selectedSupplier?.id,
                    division = selectedDivision,
                    section = selectedSection,
                    department = selectedDepartment
                )

                selectedLineItems.add(
                    com.example.ui.viewmodel.POLineItemInput(
                        item = dynamicItem,
                        quantity = qty,
                        location = locName,
                        photoUri = lineItemPhotoUri,
                        taxPercent = finalTaxRate,
                        c1Brand = "",
                        c2Size = "",
                        c3Design = "",
                        c4Colour = ""
                    )
                )
            }
        }
        resetItemFormFields()
    }

    if (showItemPhotoCameraDialog) {
        CameraPhotoSnapDialog(
            title = "Snap Photo for Line Item",
            onDismiss = { showItemPhotoCameraDialog = false },
            onPhotoCaptured = { uri ->
                lineItemPhotoUri = uri
                showItemPhotoCameraDialog = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = safeDismiss,
        properties = androidx.compose.ui.window.DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (isReviewMode) "Review & Create Purchase Order" else "Issue New Purchase Order",
                    fontWeight = FontWeight.Bold
                )
                // Navigation Step Indicator Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !isReviewMode,
                        onClick = { isReviewMode = false },
                        label = { Text("1. Add Items (${selectedLineItems.size})", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    FilterChip(
                        selected = isReviewMode,
                        onClick = {
                            if (canAddItem) {
                                addSelectedItemsToPO()
                            }
                            if (selectedLineItems.isNotEmpty()) {
                                isReviewMode = true
                            }
                        },
                        enabled = selectedLineItems.isNotEmpty() || canAddItem,
                        label = { Text("2. Review Totals (${currencyFormat.format(selectedLineItems.sumOf { it.item.unitCost * it.quantity })})", style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                if (suppliers.isEmpty()) {
                    item {
                        Text(
                            text = "Please ensure at least one supplier is created first.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (!isReviewMode) {
                    // --- FORM ENTRY MODE ---

                    // 1. Supplier Box (Dropdown only from created suppliers)
                    item {
                        ExposedDropdownMenuBox(
                            expanded = supplierDropdownExpanded,
                            onExpandedChange = { supplierDropdownExpanded = !supplierDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedSupplier?.let { "${it.name} (${it.code})" } ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("1. Supplier *") },
                                placeholder = { Text("Choose from created suppliers...") },
                                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = supplierDropdownExpanded) },
                                singleLine = true,
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("po_supplier_dropdown")
                            )

                            ExposedDropdownMenu(
                                expanded = supplierDropdownExpanded,
                                onDismissRequest = { supplierDropdownExpanded = false },
                                modifier = Modifier.heightIn(max = 280.dp)
                            ) {
                                OutlinedTextField(
                                    value = supplierSearchText,
                                    onValueChange = { supplierSearchText = it },
                                    placeholder = { Text("Search supplier or code...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (supplierSearchText.isNotEmpty()) {
                                            IconButton(onClick = { supplierSearchText = "" }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("po_supplier_search_box")
                                )
                                HorizontalDivider()

                                if (filteredSuppliers.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(if (suppliers.isEmpty()) "No suppliers created yet" else "No matching suppliers found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) },
                                        onClick = {}
                                    )
                                } else {
                                    filteredSuppliers.forEach { sup ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(sup.name, fontWeight = if (sup.id == selectedSupplier?.id) FontWeight.Bold else FontWeight.Normal)
                                                    Text("Code: ${sup.code} • Margin: ${sup.defaultMarginPercent}%", style = MaterialTheme.typography.labelSmall)
                                                }
                                            },
                                            onClick = {
                                                selectedSupplier = sup
                                                supplierSearchText = ""
                                                supplierDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ⚡ Quick Inventory Item Picker (Optional Fast Fill)
                    if (inventoryList.isNotEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text(
                                            "⚡ Quick Fill from Inventory Catalog",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    ExposedDropdownMenuBox(
                                        expanded = catalogDropdownExpanded,
                                        onExpandedChange = { catalogDropdownExpanded = !catalogDropdownExpanded },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = catalogSearchQuery,
                                            onValueChange = {
                                                catalogSearchQuery = it
                                                catalogDropdownExpanded = true
                                            },
                                            placeholder = { Text("Search SKU, name, or dept...") },
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                                            trailingIcon = {
                                                if (catalogSearchQuery.isNotEmpty()) {
                                                    IconButton(onClick = { catalogSearchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                                    }
                                                } else {
                                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = catalogDropdownExpanded)
                                                }
                                            },
                                            singleLine = true,
                                            modifier = Modifier
                                                .menuAnchor()
                                                .fillMaxWidth()
                                                .testTag("po_quick_catalog_search")
                                        )

                                        ExposedDropdownMenu(
                                            expanded = catalogDropdownExpanded && filteredCatalogItems.isNotEmpty(),
                                            onDismissRequest = { catalogDropdownExpanded = false },
                                            modifier = Modifier.heightIn(max = 240.dp)
                                        ) {
                                            filteredCatalogItems.forEach { item ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text(item.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                                            Text("SKU: ${item.sku} • Div: ${item.division} • Dept: ${item.department} • ₹${item.unitCost}", style = MaterialTheme.typography.labelSmall)
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedDivision = item.division.ifBlank { "Readymade" }
                                                        selectedSection = item.section.ifBlank { "General Section" }
                                                        selectedDepartment = item.department.ifBlank { item.name }
                                                        if (item.unitCost > 0) {
                                                            costRateInputStr = if (item.unitCost % 1.0 == 0.0) item.unitCost.toInt().toString() else item.unitCost.toString()
                                                        }
                                                        if (item.defaultSupplierId != null) {
                                                            suppliers.find { it.id == item.defaultSupplierId }?.let { sup ->
                                                                selectedSupplier = sup
                                                            }
                                                        }
                                                        catalogSearchQuery = ""
                                                        catalogDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Division Box (Dropdown only from created hierarchy)
                    item {
                        ExposedDropdownMenuBox(
                            expanded = divisionDropdownExpanded,
                            onExpandedChange = { divisionDropdownExpanded = !divisionDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedDivision,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("2. Division *") },
                                placeholder = { Text("Choose from created Divisions...") },
                                leadingIcon = { Icon(Icons.Default.Category, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = divisionDropdownExpanded) },
                                singleLine = true,
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("po_division_dropdown")
                            )

                            ExposedDropdownMenu(
                                expanded = divisionDropdownExpanded,
                                onDismissRequest = { divisionDropdownExpanded = false },
                                modifier = Modifier.heightIn(max = 280.dp)
                            ) {
                                OutlinedTextField(
                                    value = divisionSearchText,
                                    onValueChange = { divisionSearchText = it },
                                    placeholder = { Text("Search division...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (divisionSearchText.isNotEmpty()) {
                                            IconButton(onClick = { divisionSearchText = "" }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("po_division_search_box")
                                )
                                HorizontalDivider()

                                if (filteredDivisions.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No divisions found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) },
                                        onClick = {}
                                    )
                                } else {
                                    filteredDivisions.forEach { div ->
                                        DropdownMenuItem(
                                            text = { Text(div, fontWeight = if (div == selectedDivision) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = {
                                                selectedDivision = div
                                                selectedSection = ""
                                                selectedDepartment = ""
                                                divisionSearchText = ""
                                                divisionDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. Section Box (Dropdown only from created hierarchy)
                    item {
                        ExposedDropdownMenuBox(
                            expanded = sectionDropdownExpanded,
                            onExpandedChange = { sectionDropdownExpanded = !sectionDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedSection,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("3. Section *") },
                                placeholder = { Text("Choose from created Sections...") },
                                leadingIcon = { Icon(Icons.Default.Dashboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sectionDropdownExpanded) },
                                singleLine = true,
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("po_section_dropdown")
                            )

                            ExposedDropdownMenu(
                                expanded = sectionDropdownExpanded,
                                onDismissRequest = { sectionDropdownExpanded = false },
                                modifier = Modifier.heightIn(max = 280.dp)
                            ) {
                                OutlinedTextField(
                                    value = sectionSearchText,
                                    onValueChange = { sectionSearchText = it },
                                    placeholder = { Text("Search section...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (sectionSearchText.isNotEmpty()) {
                                            IconButton(onClick = { sectionSearchText = "" }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("po_section_search_box")
                                )
                                HorizontalDivider()

                                if (filteredSections.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No sections found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) },
                                        onClick = {}
                                    )
                                } else {
                                    filteredSections.forEach { sec ->
                                        DropdownMenuItem(
                                            text = { Text(sec, fontWeight = if (sec == selectedSection) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = {
                                                selectedSection = sec
                                                selectedDepartment = ""
                                                sectionSearchText = ""
                                                sectionDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Department Box (Dropdown only from created hierarchy)
                    item {
                        ExposedDropdownMenuBox(
                            expanded = departmentDropdownExpanded,
                            onExpandedChange = { departmentDropdownExpanded = !departmentDropdownExpanded },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedDepartment,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("4. Department *") },
                                placeholder = { Text("Choose from created Departments...") },
                                leadingIcon = { Icon(Icons.Default.Inventory, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = departmentDropdownExpanded) },
                                singleLine = true,
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                                    .testTag("po_department_dropdown")
                            )

                            ExposedDropdownMenu(
                                expanded = departmentDropdownExpanded,
                                onDismissRequest = { departmentDropdownExpanded = false },
                                modifier = Modifier.heightIn(max = 280.dp)
                            ) {
                                OutlinedTextField(
                                    value = departmentSearchText,
                                    onValueChange = { departmentSearchText = it },
                                    placeholder = { Text("Search department...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        if (departmentSearchText.isNotEmpty()) {
                                            IconButton(onClick = { departmentSearchText = "" }, modifier = Modifier.size(24.dp)) {
                                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                        .testTag("po_department_search_box")
                                )
                                HorizontalDivider()

                                if (filteredDepartments.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No departments found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) },
                                        onClick = {}
                                    )
                                } else {
                                    filteredDepartments.forEach { dept ->
                                        DropdownMenuItem(
                                            text = { Text(dept, fontWeight = if (dept == selectedDepartment) FontWeight.Bold else FontWeight.Normal) },
                                            onClick = {
                                                selectedDepartment = dept
                                                departmentSearchText = ""
                                                departmentDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Approx Cost Rate Box & GST Tax Slab Selector
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                value = costRateInputStr,
                                onValueChange = { costRateInputStr = it.filter { ch -> ch.isDigit() || ch == '.' } },
                                label = { Text("5. Unit Approx Cost Rate (₹) *") },
                                placeholder = { Text("Enter Approx Cost Rate e.g. 500, 2600") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.clearFocus() },
                                    onDone = { focusManager.clearFocus() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("po_cost_rate_box")
                            )

                            // Inbuilt Automatic GST Rate Display (No manual selection needed)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Calculate,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = "Automatic GST (Inbuilt):",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Surface(
                                        color = if (activeItemTaxRate == 5.0) Color(0xFF22C55E) else MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            text = "${if (activeItemTaxRate % 1.0 == 0.0) activeItemTaxRate.toInt().toString() else activeItemTaxRate.toString()}% GST",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                val taxRuleExplanation = com.example.service.TaxMasterService.getTaxExplanation(
                                    division = selectedDivision,
                                    categoryName = resolvedCategory,
                                    department = selectedDepartment,
                                    costAmount = costRateInputStr.toDoubleOrNull() ?: 0.0,
                                    categoryTaxRules = categoryTaxRules
                                )
                                Text(
                                    text = "Inbuilt Rule: $taxRuleExplanation",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // 6. Multi-Location Selection via Ticks (Checkboxes) & Quantity Box per Location
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "6. Select Location(s) via Ticks (Multi-Location):",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            locationNames.forEach { locName ->
                                val isChecked = selectedLocationQtys.containsKey(locName)
                                val qtyVal = selectedLocationQtys[locName] ?: "10"
                                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    focusManager.clearFocus()
                                                    if (isChecked) selectedLocationQtys.remove(locName)
                                                    else selectedLocationQtys[locName] = "10"
                                                }
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    focusManager.clearFocus()
                                                    if (checked) selectedLocationQtys[locName] = "10"
                                                    else selectedLocationQtys.remove(locName)
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = locName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                        if (isChecked) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        val current = qtyVal.toIntOrNull() ?: 10
                                                        val next = (current - 1).coerceAtLeast(1)
                                                        selectedLocationQtys[locName] = next.toString()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                                }

                                                OutlinedTextField(
                                                    value = qtyVal,
                                                    onValueChange = { newVal ->
                                                        selectedLocationQtys[locName] = newVal.filter { it.isDigit() }
                                                    },
                                                    label = { Text("Qty") },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                                    keyboardActions = KeyboardActions(
                                                        onDone = { focusManager.clearFocus() }
                                                    ),
                                                    modifier = Modifier
                                                        .width(72.dp)
                                                        .testTag("po_qty_box_$locName")
                                                )

                                                IconButton(
                                                    onClick = {
                                                        val current = qtyVal.toIntOrNull() ?: 10
                                                        val next = current + 1
                                                        selectedLocationQtys[locName] = next.toString()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }

                                    // Quick Quantity Presets Chips if location is checked
                                    if (isChecked) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier.fillMaxWidth().padding(start = 36.dp, top = 2.dp)
                                        ) {
                                            listOf(5, 10, 25, 50, 100).forEach { preset ->
                                                SuggestionChip(
                                                    onClick = {
                                                        selectedLocationQtys[locName] = preset.toString()
                                                    },
                                                    label = { Text("$preset", style = MaterialTheme.typography.labelSmall) },
                                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                                        containerColor = if (qtyVal == preset.toString()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                                    ),
                                                    modifier = Modifier.height(26.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Add Item Pic Button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showItemPhotoCameraDialog = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("po_add_item_pic_button")
                                ) {
                                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (lineItemPhotoUri == null) "Add Item Pic" else "Pic Attached ✓",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                if (lineItemPhotoUri != null) {
                                    AsyncImage(
                                        model = lineItemPhotoUri,
                                        contentDescription = "Item Photo Preview",
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                    )
                                }
                            }
                        }
                    }

                    // Count indicator of added items & Reloaded Fresh Box Banner
                    if (selectedLineItems.isNotEmpty()) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "✓ Item Added! (${selectedLineItems.size} line items in PO)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Subtotal: ${currencyFormat.format(selectedLineItems.sumOf { it.item.unitCost * it.quantity })}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = "Window reloaded with fresh boxes (unprefilled). Fill details above to add another item.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // 7. Action Buttons: Add Item & Split and Issue PO
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            // "Add Item" option button: Adds item for all ticked locations and resets form to fresh empty state!
                            OutlinedButton(
                                onClick = {
                                    if (canAddItem) {
                                        addSelectedItemsToPO()
                                    }
                                },
                                enabled = canAddItem,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("po_add_item_option_button")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Item")
                            }

                            // "Split & Issue PO" button: Moves to Total Details & Total Review Stage
                            Button(
                                onClick = {
                                    if (canAddItem) {
                                        addSelectedItemsToPO()
                                    }
                                    if (selectedLineItems.isNotEmpty()) {
                                        isReviewMode = true
                                    }
                                },
                                enabled = selectedLineItems.isNotEmpty() || canAddItem,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("po_split_and_issue_button")
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Split & Issue PO")
                            }
                        }
                    }

                } else {
                    // --- REVIEW MODE (TOTAL ITEM DETAILS & TOTAL SUMMARY) ---

                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Supplier: ${selectedSupplier?.name ?: "Supplier"}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Delivery Expected in ${deliveryDaysStr} days • Locations: ${selectedLineItems.map { it.location }.distinct().joinToString(", ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Total Item Details (${selectedLineItems.size} items):",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    items(selectedLineItems.indices.toList()) { index ->
                        val line = selectedLineItems[index]
                        val lineRate = line.item.unitCost
                        val lineGstPercent = line.taxPercent
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (!line.photoUri.isNullOrBlank()) {
                                        AsyncImage(
                                            model = line.photoUri,
                                            contentDescription = "Item Pic",
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                                        )
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                                            }
                                        }
                                    }

                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(
                                            text = "${index + 1}. ${line.item.name}",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            text = "Department: ${line.item.department.ifBlank { "General" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Division: ${line.item.division.ifBlank { "General" }} | Section: ${line.item.section.ifBlank { "General" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "📍 Location: ${line.location} | Qty: ${line.quantity} | Unit Approx Cost: ${currencyFormat.format(lineRate)} (GST ${if (lineGstPercent % 1.0 == 0.0) lineGstPercent.toInt().toString() else lineGstPercent.toString()}%)",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = currencyFormat.format(lineRate * line.quantity),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    IconButton(
                                        onClick = {
                                            selectedLineItems.removeAt(index)
                                            if (selectedLineItems.isEmpty()) isReviewMode = false
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }

                    // Total Summary Card with Multi-Tax Split Breakdown
                    item {
                        val subtotal = remember(selectedLineItems.toList()) {
                            selectedLineItems.sumOf { it.item.unitCost * it.quantity }
                        }
                        val taxGroups = remember(selectedLineItems.toList()) {
                            selectedLineItems.groupBy { it.taxPercent }.toSortedMap()
                        }
                        val totalTax = remember(selectedLineItems.toList()) {
                            selectedLineItems.sumOf { line ->
                                (line.item.unitCost * line.quantity) * (line.taxPercent / 100.0)
                            }
                        }
                        val grandTotal = subtotal + totalTax

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Total PO Financial Breakdown:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Items Count:", style = MaterialTheme.typography.labelMedium)
                                    Text("${selectedLineItems.sumOf { it.quantity }} units (${selectedLineItems.size} line items)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Total Taxable Approx Cost Amount:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                    Text(currencyFormat.format(subtotal), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }

                                // Split Tax Slabs Display
                                taxGroups.forEach { (rate, lines) ->
                                    val slabTaxable = lines.sumOf { it.item.unitCost * it.quantity }
                                    val slabTax = slabTaxable * (rate / 100.0)
                                    val halfRate = rate / 2.0
                                    val halfTax = slabTax / 2.0

                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("Taxable Amount (GST ${if (rate % 1.0 == 0.0) rate.toInt().toString() else rate.toString()}%):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                                Text(currencyFormat.format(slabTaxable), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("  ↳ Tax Amount (GST ${if (rate % 1.0 == 0.0) rate.toInt().toString() else rate.toString()}%):", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                                Text(currencyFormat.format(slabTax), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            }
                                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                Text("     • CGST ($halfRate%): ${currencyFormat.format(halfTax)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("• SGST ($halfRate%): ${currencyFormat.format(halfTax)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }

                                if (totalTax > 0) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Total GST Tax Amount:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        Text(currencyFormat.format(totalTax), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Total PO Amount (Grand Total):", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Text(currencyFormat.format(grandTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("PO Notes / Remarks") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedButton(
                            onClick = { isReviewMode = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Edit / Add More Items")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isReviewMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (selectedSupplier != null && selectedLineItems.isNotEmpty()) {
                                val deliveryDays = deliveryDaysStr.toLongOrNull() ?: 7L
                                val expectedDelivery = System.currentTimeMillis() + (deliveryDays * 86400000L)
                                val primaryLocation = selectedLineItems.firstOrNull()?.location ?: "Central Warehouse"
                                onCreatePO(selectedSupplier!!, expectedDelivery, notes, selectedLineItems, primaryLocation, null, "DRAFT")
                            }
                        },
                        enabled = selectedSupplier != null && selectedLineItems.isNotEmpty(),
                        modifier = Modifier.testTag("create_po_save_draft_button")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Draft")
                    }

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (selectedSupplier != null && selectedLineItems.isNotEmpty()) {
                                val deliveryDays = deliveryDaysStr.toLongOrNull() ?: 7L
                                val expectedDelivery = System.currentTimeMillis() + (deliveryDays * 86400000L)
                                val primaryLocation = selectedLineItems.firstOrNull()?.location ?: "Central Warehouse"
                                onCreatePO(selectedSupplier!!, expectedDelivery, notes, selectedLineItems, primaryLocation, null, "ISSUED")
                            }
                        },
                        enabled = selectedSupplier != null && selectedLineItems.isNotEmpty(),
                        modifier = Modifier.testTag("create_po_confirm_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Issue PO")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = safeDismiss) {
                Text("Cancel")
            }
        }
    )
}
