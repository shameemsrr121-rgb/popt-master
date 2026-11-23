package com.example.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.PoptLogoBadge
import com.example.ui.theme.rememberWindowSizeInfo
import com.example.ui.viewmodel.POMasterViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun ReportMasterScreen(
    viewModel: POMasterViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val windowInfo = rememberWindowSizeInfo()
    val poList by viewModel.purchaseOrdersState.collectAsState()
    val inventoryList by viewModel.inventoryState.collectAsState()
    val suppliers by viewModel.suppliersState.collectAsState()

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")) }
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("en-IN")) }

    fun formatInr(amount: Double): String {
        val str = currencyFormat.format(amount)
        return if (str.startsWith("₹") || str.contains("₹")) str else "₹ $str"
    }

    // Date Range Selection State
    val calendar = remember { Calendar.getInstance() }
    
    // Default From Date: First day of current month or 30 days ago
    var fromDateMillis by remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_MONTH, -30)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        mutableLongStateOf(cal.timeInMillis)
    }

    // Default To Date: Today end of day
    var toDateMillis by remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        mutableLongStateOf(cal.timeInMillis)
    }

    // Filter PO list by selected date range
    val filteredPOs = remember(poList, fromDateMillis, toDateMillis) {
        poList.filter { po ->
            po.orderDateMillis in fromDateMillis..toDateMillis
        }
    }

    // Metrics calculations based on filtered POs
    val totalPOs = filteredPOs.size
    val totalSpend = filteredPOs.sumOf { it.totalAmount }
    val totalTaxSpend = filteredPOs.sumOf { it.taxAmount }
    val avgPOValue = if (totalPOs > 0) totalSpend / totalPOs else 0.0

    val draftPOs = filteredPOs.count { it.status == "DRAFT" }
    val issuedPOs = filteredPOs.count { it.status == "ISSUED" || it.status == "ORDERED" }
    val partialPOs = filteredPOs.count { it.status == "PARTIALLY_RECEIVED" }
    val completedPOs = filteredPOs.count { it.status == "COMPLETED" || it.status == "RECEIVED" }

    val totalInventoryValue = inventoryList.sumOf { it.unitCost * it.currentStock }
    val totalItems = inventoryList.size

    // Date Picker Dialog Helpers
    val showFromDatePicker = {
        val cal = Calendar.getInstance().apply { timeInMillis = fromDateMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 0, 0, 0)
                }
                fromDateMillis = selectedCal.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val showToDatePicker = {
        val cal = Calendar.getInstance().apply { timeInMillis = toDateMillis }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth, 23, 59, 59)
                }
                toDateMillis = selectedCal.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            modifier = modifier
                .fillMaxSize()
                .widthIn(max = 840.dp)
                .padding(horizontal = windowInfo.defaultPadding)
                .testTag("report_master_screen")
        ) {
            // Executive Header Banner (matching Dashboard style)
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Reports & Audit",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Procurement analytics, date-filtered audit & GST summary",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            PoptLogoBadge()
                        }
                    }
                }
            }

            // Date Range Selector Card (From Date & To Date)
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Date Range Filter",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = {
                                    val cal = Calendar.getInstance()
                                    cal.add(Calendar.YEAR, -5)
                                    fromDateMillis = cal.timeInMillis
                                    toDateMillis = System.currentTimeMillis()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Text("All Time", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // From Date Picker Field
                            OutlinedButton(
                                onClick = { showFromDatePicker() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = if (windowInfo.isSmallPhone) 6.dp else 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("report_from_date_button")
                            ) {
                                Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text("From Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 10.sp)
                                    Text(
                                        text = dateFormat.format(Date(fromDateMillis)),
                                        style = if (windowInfo.isSmallPhone) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }

                            // To Date Picker Field
                            OutlinedButton(
                                onClick = { showToDatePicker() },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = if (windowInfo.isSmallPhone) 6.dp else 12.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("report_to_date_button")
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text("To Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 10.sp)
                                    Text(
                                        text = dateFormat.format(Date(toDateMillis)),
                                        style = if (windowInfo.isSmallPhone) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Executive Financial Overview
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Executive Financial Overview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ReportMetricCard(
                            title = "Total PO Spend",
                            value = formatInr(totalSpend),
                            subtitle = "$totalPOs Orders in Range",
                            icon = Icons.Default.MonetizationOn,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            isSmallPhone = windowInfo.isSmallPhone,
                            modifier = Modifier.weight(1f)
                        )

                        ReportMetricCard(
                            title = "Inventory Value",
                            value = formatInr(totalInventoryValue),
                            subtitle = "$totalItems SKUs in Stock",
                            icon = Icons.Default.Inventory2,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            isSmallPhone = windowInfo.isSmallPhone,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ReportMetricCard(
                            title = "Avg Order Value",
                            value = formatInr(avgPOValue),
                            subtitle = "Per Purchase Order",
                            icon = Icons.Default.Analytics,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            isSmallPhone = windowInfo.isSmallPhone,
                            modifier = Modifier.weight(1f)
                        )

                        ReportMetricCard(
                            title = "Tax Incurred",
                            value = formatInr(totalTaxSpend),
                            subtitle = "Total GST in Range",
                            icon = Icons.Default.Receipt,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            isSmallPhone = windowInfo.isSmallPhone,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // PO Status Breakdown
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Purchase Orders Status Breakdown",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatusProgressItem("DRAFT", draftPOs, totalPOs, Color(0xFF475569))
                            StatusProgressItem("ISSUED", issuedPOs, totalPOs, Color(0xFF0369A1))
                            StatusProgressItem("PARTIAL", partialPOs, totalPOs, Color(0xFFB45309))
                            StatusProgressItem("RECEIVED", completedPOs, totalPOs, Color(0xFF047857))
                        }
                    }
                }
            }

            // Purchase Orders Table View for Selected Date Range
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Purchase Orders (${filteredPOs.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Filtered Range",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                    if (filteredPOs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No Purchase Orders found in selected date range.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        filteredPOs.forEach { po ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = po.poNumber,
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Surface(
                                                color = when (po.status) {
                                                    "COMPLETED", "RECEIVED" -> Color(0xFFDCFCE7)
                                                    "ISSUED", "ORDERED" -> Color(0xFFE0F2FE)
                                                    else -> Color(0xFFFEF3C7)
                                                },
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = po.status,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (po.status) {
                                                        "COMPLETED", "RECEIVED" -> Color(0xFF15803D)
                                                        "ISSUED", "ORDERED" -> Color(0xFF0369A1)
                                                        else -> Color(0xFFB45309)
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Supplier: ${po.supplierName} • Date: ${dateFormat.format(Date(po.orderDateMillis))}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = formatInr(po.totalAmount),
                                            fontWeight = FontWeight.Black,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            text = "Tax: ${formatInr(po.taxAmount)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }

        // Supplier Spend Analytics
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Supplier Performance & Procurement Ranking",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (suppliers.isEmpty()) {
                        Text(
                            text = "No supplier data available yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        suppliers.take(5).forEach { supplier ->
                            val supplierPOs = filteredPOs.filter { it.supplierId == supplier.id }
                            val supplierTotal = supplierPOs.sumOf { it.totalAmount }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = supplier.name,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "${supplierPOs.size} Orders in Range • ${supplier.paymentTerms}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                Text(
                                    text = formatInr(supplierTotal),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun ReportMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    isSmallPhone: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(if (isSmallPhone) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = if (isSmallPhone) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(if (isSmallPhone) 16.dp else 20.dp))
            }

            Text(
                text = value,
                style = if (isSmallPhone) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = if (isSmallPhone) 10.sp else 11.sp
            )
        }
    }
}

@Composable
private fun StatusProgressItem(
    label: String,
    count: Int,
    total: Int,
    color: Color
) {
    val percent = if (total > 0) (count.toFloat() / total) * 100 else 0f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            maxLines = 1
        )
        Text(
            text = String.format("%.0f%%", percent),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 10.sp
        )
    }
}
