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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.PurchaseOrderEntity
import com.example.data.entity.PurchasePlanNoteEntity
import com.example.ui.components.PoptLogoIcon
import com.example.ui.theme.rememberWindowSizeInfo
import com.example.ui.viewmodel.POMasterViewModel
import java.text.DateFormat
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: POMasterViewModel,
    onNavigateToMaster: (Int) -> Unit,
    onOpenCreatePO: () -> Unit = { onNavigateToMaster(1) },
    onOpenInventory: () -> Unit = { onNavigateToMaster(5) },
    onOpenAdminPanel: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenCloudSync: () -> Unit = {}
) {
    val windowInfo = rememberWindowSizeInfo()
    val purchaseOrders by viewModel.purchaseOrdersState.collectAsState()
    val planNotes by viewModel.purchasePlanNotesState.collectAsState()
    val companyProfile by viewModel.companyProfileState.collectAsState()
    val inventoryList by viewModel.inventoryState.collectAsState()

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    // Status Calculations
    val draftPOs = remember(purchaseOrders) { purchaseOrders.filter { it.status == "DRAFT" } }
    val issuedPOs = remember(purchaseOrders) { purchaseOrders.filter { it.status == "ISSUED" } }
    val partialPOs = remember(purchaseOrders) { purchaseOrders.filter { it.status == "PARTIALLY_RECEIVED" } }
    val receivedPOs = remember(purchaseOrders) { purchaseOrders.filter { it.status == "RECEIVED" } }

    val activePOs = remember(purchaseOrders) {
        purchaseOrders.filter { it.status == "ISSUED" || it.status == "PARTIALLY_RECEIVED" }
    }
    val activePOSpend = remember(activePOs) { activePOs.sumOf { it.totalAmount } }
    val totalPOSpend = remember(purchaseOrders) { purchaseOrders.sumOf { it.totalAmount } }

    val todayStartMillis = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val upcomingPlans = remember(planNotes, todayStartMillis) {
        planNotes.filter { it.status != "COMPLETED" && it.status != "CONVERTED_TO_PO" && it.plannedDateMillis >= todayStartMillis }
            .sortedBy { it.plannedDateMillis }
            .take(3)
    }

    val recentPOs = remember(purchaseOrders) {
        purchaseOrders.sortedByDescending { it.orderDateMillis }.take(5)
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp)
        ) {
            // 1. Clean PO Executive Header Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("po_executive_banner"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF0F172A),
                                        Color(0xFF1E293B),
                                        Color(0xFF134E4A)
                                    )
                                )
                            )
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = companyProfile.companyName.ifBlank { "PO Tracker & Planner" },
                                    style = if (windowInfo.isSmallPhone) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (companyProfile.gstNumber.isNotBlank()) "GSTIN: ${companyProfile.gstNumber}" else "Purchase Order Master",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            PoptLogoIcon(
                                size = if (windowInfo.isSmallPhone) 28.dp else 32.dp,
                                showText = true,
                                cornerRadius = 6.dp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // PO Financial Summary Cards
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Active PO Spend",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF94A3B8),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = currencyFormat.format(activePOSpend),
                                            style = if (windowInfo.isSmallPhone) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF38BDF8),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${activePOs.size} Open Orders",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E293B),
                                border = BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "Total PO Spend",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF94A3B8),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = currencyFormat.format(totalPOSpend),
                                            style = if (windowInfo.isSmallPhone) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF34D399),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${purchaseOrders.size} All-Time Orders",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Primary Action Buttons (Create PO & Manage Inventory)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onOpenCreatePO,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_create_po_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(
                                    vertical = 12.dp,
                                    horizontal = if (windowInfo.isSmallPhone) 6.dp else 12.dp
                                )
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(if (windowInfo.isSmallPhone) 4.dp else 6.dp))
                                Text(
                                    text = "Create PO",
                                    style = if (windowInfo.isSmallPhone) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = onOpenInventory,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("home_open_inventory_btn"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(
                                    vertical = 12.dp,
                                    horizontal = if (windowInfo.isSmallPhone) 6.dp else 12.dp
                                )
                            ) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(if (windowInfo.isSmallPhone) 4.dp else 6.dp))
                                Text(
                                    text = "Inventory",
                                    style = if (windowInfo.isSmallPhone) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 2. Purchase Orders Status Grid: Draft | Issued | Partial | Received
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Purchase Orders Status",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = { onNavigateToMaster(3) }, // Tracker tab
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("Track Live", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (windowInfo.poStatusColumns == 2) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    POStatusBox(
                                        title = "Draft",
                                        count = draftPOs.size,
                                        amount = draftPOs.sumOf { it.totalAmount },
                                        color = Color(0xFF475569),
                                        bgColor = Color(0xFFF1F5F9),
                                        currencyFormat = currencyFormat,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        onClick = { onNavigateToMaster(1) }
                                    )
                                    POStatusBox(
                                        title = "Issued",
                                        count = issuedPOs.size,
                                        amount = issuedPOs.sumOf { it.totalAmount },
                                        color = Color(0xFF0369A1),
                                        bgColor = Color(0xFFE0F2FE),
                                        currencyFormat = currencyFormat,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        onClick = { onNavigateToMaster(3) }
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(IntrinsicSize.Min),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    POStatusBox(
                                        title = "Partial",
                                        count = partialPOs.size,
                                        amount = partialPOs.sumOf { it.totalAmount },
                                        color = Color(0xFFB45309),
                                        bgColor = Color(0xFFFEF3C7),
                                        currencyFormat = currencyFormat,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        onClick = { onNavigateToMaster(3) }
                                    )
                                    POStatusBox(
                                        title = "Received",
                                        count = receivedPOs.size,
                                        amount = receivedPOs.sumOf { it.totalAmount },
                                        color = Color(0xFF047857),
                                        bgColor = Color(0xFFD1FAE5),
                                        currencyFormat = currencyFormat,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                        onClick = { onNavigateToMaster(3) }
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                POStatusBox(
                                    title = "Draft",
                                    count = draftPOs.size,
                                    amount = draftPOs.sumOf { it.totalAmount },
                                    color = Color(0xFF475569),
                                    bgColor = Color(0xFFF1F5F9),
                                    currencyFormat = currencyFormat,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    onClick = { onNavigateToMaster(1) } // Go to Orders
                                )
                                POStatusBox(
                                    title = "Issued",
                                    count = issuedPOs.size,
                                    amount = issuedPOs.sumOf { it.totalAmount },
                                    color = Color(0xFF0369A1),
                                    bgColor = Color(0xFFE0F2FE),
                                    currencyFormat = currencyFormat,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    onClick = { onNavigateToMaster(3) } // Tracker
                                )
                                POStatusBox(
                                    title = "Partial",
                                    count = partialPOs.size,
                                    amount = partialPOs.sumOf { it.totalAmount },
                                    color = Color(0xFFB45309),
                                    bgColor = Color(0xFFFEF3C7),
                                    currencyFormat = currencyFormat,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    onClick = { onNavigateToMaster(3) }
                                )
                                POStatusBox(
                                    title = "Received",
                                    count = receivedPOs.size,
                                    amount = receivedPOs.sumOf { it.totalAmount },
                                    color = Color(0xFF047857),
                                    bgColor = Color(0xFFD1FAE5),
                                    currencyFormat = currencyFormat,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    onClick = { onNavigateToMaster(3) }
                                )
                            }
                        }
                    }
                }
            }

            // Quick Inventory & Tax Hierarchy Master Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenInventory() }
                        .testTag("home_inventory_summary_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(46.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Inventory2,
                                        contentDescription = "Inventory Master",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Inventory & Tax Hierarchy",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${inventoryList.size} SKUs • Divisions • Sections • GST Rules",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Go to Inventory",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // 3. Upcoming Planned Orders Section
            if (upcomingPlans.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Text(
                                        text = "Upcoming Planned Orders",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                TextButton(
                                    onClick = { onNavigateToMaster(2) }, // Planner tab
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                ) {
                                    Text("Planner", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                upcomingPlans.forEach { plan ->
                                    val daysLeft = TimeUnit.MILLISECONDS.toDays(plan.plannedDateMillis - todayStartMillis)
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { onNavigateToMaster(2) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = plan.title,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = if (plan.categoryOrSupplier.isNotBlank()) "${plan.categoryOrSupplier} • Planned for ${dateFormat.format(Date(plan.plannedDateMillis))}" else "Planned for ${dateFormat.format(Date(plan.plannedDateMillis))}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = if (daysLeft == 0L) Color(0xFFFEF3C7) else Color(0xFFE0F2FE)
                                            ) {
                                                Text(
                                                    text = if (daysLeft == 0L) "Due Today" else "In ${daysLeft}d",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (daysLeft == 0L) Color(0xFFB45309) else Color(0xFF0369A1),
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

            // 4. Recent Purchase Orders List
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Purchase Orders",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(
                        onClick = { onNavigateToMaster(1) }, // Orders tab
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.testTag("home_view_all_pos_btn")
                    ) {
                        Text("View All POs", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }

            if (recentPOs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No purchase orders yet",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap 'Create PO' to generate your first purchase order",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(recentPOs, key = { it.id }) { po ->
                    val (statusBg, statusFg) = when (po.status) {
                        "DRAFT" -> Pair(Color(0xFFF1F5F9), Color(0xFF475569))
                        "ISSUED" -> Pair(Color(0xFFE0F2FE), Color(0xFF0369A1))
                        "PARTIALLY_RECEIVED" -> Pair(Color(0xFFFEF3C7), Color(0xFFB45309))
                        "RECEIVED" -> Pair(Color(0xFFD1FAE5), Color(0xFF047857))
                        "CANCELLED" -> Pair(Color(0xFFFEE2E2), Color(0xFFB91C1C))
                        else -> Pair(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setSelectedPO(po)
                                onNavigateToMaster(1) // Orders tab
                            }
                            .testTag("recent_po_item_${po.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = statusBg,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ReceiptLong,
                                            contentDescription = null,
                                            tint = statusFg,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = po.poNumber,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = statusBg
                                        ) {
                                            Text(
                                                text = po.status.replace("_", " "),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                color = statusFg,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${po.supplierName} • ${dateFormat.format(Date(po.orderDateMillis))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = currencyFormat.format(po.totalAmount),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = po.location.ifBlank { "Central WH" },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun POStatusBox(
    title: String,
    count: Int,
    amount: Double,
    color: Color,
    bgColor: Color,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f)),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val formattedShort = if (amount >= 100000) {
                "₹${"%.1f".format(amount / 100000)}L"
            } else if (amount >= 1000) {
                "₹${"%.1f".format(amount / 1000)}K"
            } else if (amount > 0.0) {
                "₹${amount.toInt()}"
            } else {
                "₹0"
            }
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = if (amount > 0.0) color.copy(alpha = 0.15f) else color.copy(alpha = 0.08f)
            ) {
                Text(
                    text = formattedShort,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
    }
}
