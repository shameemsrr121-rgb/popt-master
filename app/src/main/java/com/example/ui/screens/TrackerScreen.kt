package com.example.ui.screens

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.PurchaseOrderEntity
import com.example.data.entity.PurchaseOrderItemEntity
import com.example.ui.components.PrintPOShareDialog
import com.example.ui.theme.rememberWindowSizeInfo
import com.example.ui.viewmodel.POMasterViewModel
import java.text.DateFormat
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.TimeUnit

enum class POTrackingStage(val title: String, val stepNumber: Int) {
    DRAFT("Draft", 1),
    SUBMITTED("Submitted", 2),
    APPROVED("Approved", 3),
    ORDERED("In Transit", 4),
    COMPLETED("Completed", 5)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    viewModel: POMasterViewModel,
    onNavigateToCreatePO: () -> Unit = {},
    onViewPODetail: (PurchaseOrderEntity) -> Unit = {}
) {
    val windowInfo = rememberWindowSizeInfo()
    val purchaseOrders by viewModel.purchaseOrdersState.collectAsState()
    val suppliers by viewModel.suppliersState.collectAsState()
    val companyProfile by viewModel.companyProfileState.collectAsState()
    val categoryTaxRules by viewModel.categoryTaxRulesState.collectAsState()
    val selectedPOItems by viewModel.selectedPOItems.collectAsState()

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedStatusFilter by remember { mutableStateOf("ALL") }
    var poToPrintShare by remember { mutableStateOf<PurchaseOrderEntity?>(null) }

    LaunchedEffect(poToPrintShare) {
        poToPrintShare?.let { po ->
            viewModel.loadPOItems(po.id)
        }
    }

    val todayStartMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    // Filtered POs
    val filteredPOs = remember(purchaseOrders, searchQuery, selectedStatusFilter) {
        purchaseOrders.filter { po ->
            val matchesFilter = when (selectedStatusFilter) {
                "ALL" -> true
                "DRAFT" -> po.status == "DRAFT"
                "ISSUED", "SUBMITTED" -> po.status == "ISSUED"
                "PARTIAL", "PARTIALLY_RECEIVED" -> po.status == "PARTIALLY_RECEIVED"
                "RECEIVED", "COMPLETED" -> po.status == "RECEIVED"
                "OVERDUE" -> (po.status == "ISSUED" || po.status == "PARTIALLY_RECEIVED") && po.expectedDeliveryDateMillis < todayStartMillis
                else -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    po.poNumber.contains(searchQuery, ignoreCase = true) ||
                    po.supplierName.contains(searchQuery, ignoreCase = true) ||
                    po.location.contains(searchQuery, ignoreCase = true)
            matchesFilter && matchesSearch
        }.sortedByDescending { it.orderDateMillis }
    }

    if (poToPrintShare != null) {
        val targetPO = poToPrintShare!!
        val poSupplier = suppliers.find { it.id == targetPO.supplierId }
        PrintPOShareDialog(
            po = targetPO,
            poItems = selectedPOItems,
            categoryTaxRules = categoryTaxRules,
            supplierName = targetPO.supplierName,
            supplier = poSupplier,
            companyProfile = companyProfile,
            currencyFormat = currencyFormat,
            dateFormat = dateFormat,
            onDismiss = { poToPrintShare = null }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .padding(horizontal = windowInfo.defaultPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
        ) {
            // 1. Tracker Header & Pipeline Overview
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
                            Column {
                                Text(
                                    text = "PO Lifecycle Tracker",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Live status pipeline from Draft to Fulfillment",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF475569))
                            ) {
                                Text(
                                    text = "${purchaseOrders.size} Total Orders",
                                    color = Color(0xFF38BDF8),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Visual Pipeline Stepper
                        if (windowInfo.isSmallPhone) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TrackerPipelineStep(
                                        title = "Draft",
                                        count = purchaseOrders.count { it.status == "DRAFT" },
                                        color = Color(0xFF94A3B8),
                                        isActive = selectedStatusFilter == "DRAFT",
                                        modifier = Modifier.weight(1f),
                                        onClick = { selectedStatusFilter = if (selectedStatusFilter == "DRAFT") "ALL" else "DRAFT" }
                                    )
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
                                    TrackerPipelineStep(
                                        title = "Issued",
                                        count = purchaseOrders.count { it.status == "ISSUED" },
                                        color = Color(0xFF38BDF8),
                                        isActive = selectedStatusFilter == "ISSUED" || selectedStatusFilter == "SUBMITTED",
                                        modifier = Modifier.weight(1f),
                                        onClick = { selectedStatusFilter = if (selectedStatusFilter == "ISSUED" || selectedStatusFilter == "SUBMITTED") "ALL" else "ISSUED" }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TrackerPipelineStep(
                                        title = "Partial",
                                        count = purchaseOrders.count { it.status == "PARTIALLY_RECEIVED" },
                                        color = Color(0xFFF59E0B),
                                        isActive = selectedStatusFilter == "PARTIAL",
                                        modifier = Modifier.weight(1f),
                                        onClick = { selectedStatusFilter = if (selectedStatusFilter == "PARTIAL") "ALL" else "PARTIAL" }
                                    )
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
                                    TrackerPipelineStep(
                                        title = "Received",
                                        count = purchaseOrders.count { it.status == "RECEIVED" },
                                        color = Color(0xFF10B981),
                                        isActive = selectedStatusFilter == "RECEIVED" || selectedStatusFilter == "COMPLETED",
                                        modifier = Modifier.weight(1f),
                                        onClick = { selectedStatusFilter = if (selectedStatusFilter == "RECEIVED" || selectedStatusFilter == "COMPLETED") "ALL" else "RECEIVED" }
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TrackerPipelineStep(
                                    title = "Draft",
                                    count = purchaseOrders.count { it.status == "DRAFT" },
                                    color = Color(0xFF94A3B8),
                                    isActive = selectedStatusFilter == "DRAFT",
                                    onClick = { selectedStatusFilter = if (selectedStatusFilter == "DRAFT") "ALL" else "DRAFT" }
                                )
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
                                TrackerPipelineStep(
                                    title = "Issued",
                                    count = purchaseOrders.count { it.status == "ISSUED" },
                                    color = Color(0xFF38BDF8),
                                    isActive = selectedStatusFilter == "ISSUED" || selectedStatusFilter == "SUBMITTED",
                                    onClick = { selectedStatusFilter = if (selectedStatusFilter == "ISSUED" || selectedStatusFilter == "SUBMITTED") "ALL" else "ISSUED" }
                                )
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
                                TrackerPipelineStep(
                                    title = "Partial",
                                    count = purchaseOrders.count { it.status == "PARTIALLY_RECEIVED" },
                                    color = Color(0xFFF59E0B),
                                    isActive = selectedStatusFilter == "PARTIAL",
                                    onClick = { selectedStatusFilter = if (selectedStatusFilter == "PARTIAL") "ALL" else "PARTIAL" }
                                )
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF475569), modifier = Modifier.size(14.dp))
                                TrackerPipelineStep(
                                    title = "Received",
                                    count = purchaseOrders.count { it.status == "RECEIVED" },
                                    color = Color(0xFF10B981),
                                    isActive = selectedStatusFilter == "RECEIVED" || selectedStatusFilter == "COMPLETED",
                                    onClick = { selectedStatusFilter = if (selectedStatusFilter == "RECEIVED" || selectedStatusFilter == "COMPLETED") "ALL" else "RECEIVED" }
                                )
                            }
                        }
                    }
                }
            }

            // 2. Search & Filter Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("tracker_search_input"),
                        placeholder = { Text("Search by PO #, Supplier, Location...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear search")
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
            }

            // 3. Quick Status Filter Pills
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val filterPills = listOf(
                        Triple("ALL", "All Orders", purchaseOrders.size),
                        Triple("SUBMITTED", "Issued", purchaseOrders.count { it.status == "ISSUED" }),
                        Triple("PARTIAL", "Partial", purchaseOrders.count { it.status == "PARTIALLY_RECEIVED" }),
                        Triple("COMPLETED", "Received", purchaseOrders.count { it.status == "RECEIVED" }),
                        Triple("DRAFT", "Draft", purchaseOrders.count { it.status == "DRAFT" }),
                        Triple("OVERDUE", "Overdue", purchaseOrders.count { (it.status == "ISSUED" || it.status == "PARTIALLY_RECEIVED") && it.expectedDeliveryDateMillis < todayStartMillis })
                    )

                    items(filterPills) { (key, label, count) ->
                        FilterChip(
                            selected = selectedStatusFilter == key,
                            onClick = { selectedStatusFilter = key },
                            label = { Text("$label ($count)", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // 4. Filtered PO List
            if (filteredPOs.isEmpty()) {
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
                                Icons.Default.FilterListOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotBlank()) "No orders found matching '$searchQuery'" else "No orders in this status category",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try changing your search query or status filter",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(filteredPOs, key = { it.id }) { po ->
                    POTrackingCard(
                        po = po,
                        currencyFormat = currencyFormat,
                        dateFormat = dateFormat,
                        todayStartMillis = todayStartMillis,
                        onPrintShare = { poToPrintShare = po },
                        onUpdateStatus = { nextStatus -> viewModel.updatePOStatus(po, nextStatus) },
                        onClick = {
                            viewModel.setSelectedPO(po)
                            onViewPODetail(po)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackerPipelineStep(
    title: String,
    count: Int,
    color: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) color.copy(alpha = 0.25f) else Color(0xFF1E293B),
        border = BorderStroke(1.dp, if (isActive) color else Color(0xFF334155)),
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag("tracker_step_$title")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                color = if (isActive) Color.White else Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun POTrackingCard(
    po: PurchaseOrderEntity,
    currencyFormat: NumberFormat,
    dateFormat: DateFormat,
    todayStartMillis: Long,
    onPrintShare: () -> Unit,
    onUpdateStatus: (String) -> Unit,
    onClick: () -> Unit
) {
    val (statusBg, statusFg, stageProgress) = when (po.status) {
        "DRAFT" -> Triple(Color(0xFFF1F5F9), Color(0xFF475569), 0.2f)
        "ISSUED" -> Triple(Color(0xFFE0F2FE), Color(0xFF0369A1), 0.5f)
        "PARTIALLY_RECEIVED" -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), 0.75f)
        "RECEIVED" -> Triple(Color(0xFFD1FAE5), Color(0xFF047857), 1.0f)
        "CANCELLED" -> Triple(Color(0xFFFEE2E2), Color(0xFFB91C1C), 0.0f)
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, 0.5f)
    }

    val isOverdue = (po.status == "ISSUED" || po.status == "PARTIALLY_RECEIVED") && po.expectedDeliveryDateMillis < todayStartMillis
    val daysUntilDelivery = TimeUnit.MILLISECONDS.toDays(po.expectedDeliveryDateMillis - todayStartMillis)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("tracker_po_card_${po.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (isOverdue) Color(0xFFF87171) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: PO # + Status Badge + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = po.poNumber,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = statusBg
                    ) {
                        Text(
                            text = po.status.replace("_", " "),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusFg,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = currencyFormat.format(po.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Supplier & Location
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = po.supplierName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "📍 ${po.location.ifBlank { "Central WH" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Timeline & Stage Progress Bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Ordered: ${dateFormat.format(Date(po.orderDateMillis))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    val deliveryStatusText = when {
                        po.status == "RECEIVED" -> "Fulfilled"
                        isOverdue -> "⚠️ Overdue by ${-daysUntilDelivery}d"
                        daysUntilDelivery == 0L -> "🚨 Due Today"
                        else -> "Due in ${daysUntilDelivery}d (${dateFormat.format(Date(po.expectedDeliveryDateMillis))})"
                    }
                    Text(
                        text = deliveryStatusText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isOverdue || daysUntilDelivery == 0L) FontWeight.Bold else FontWeight.Normal,
                        color = if (isOverdue) Color(0xFFDC2626) else if (daysUntilDelivery == 0L) Color(0xFFD97706) else MaterialTheme.colorScheme.outline
                    )
                }

                LinearProgressIndicator(
                    progress = { stageProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = statusFg,
                    trackColor = statusBg
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Row: Fast Status Transitions & PDF Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (po.status == "DRAFT") {
                        FilledTonalButton(
                            onClick = { onUpdateStatus("ISSUED") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Submit PO", style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (po.status == "ISSUED") {
                        FilledTonalButton(
                            onClick = { onUpdateStatus("PARTIALLY_RECEIVED") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.LocalShipping, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("In Transit / Partial", style = MaterialTheme.typography.labelSmall)
                        }
                    } else if (po.status == "PARTIALLY_RECEIVED") {
                        FilledTonalButton(
                            onClick = { onUpdateStatus("RECEIVED") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Complete PO", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                IconButton(
                    onClick = onPrintShare,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share PO",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
