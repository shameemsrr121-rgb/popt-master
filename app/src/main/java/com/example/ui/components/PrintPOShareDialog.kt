package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.data.entity.CompanyProfileEntity
import com.example.data.entity.PurchaseOrderEntity
import com.example.data.entity.PurchaseOrderItemEntity
import com.example.data.entity.SupplierEntity
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

@Composable
fun PrintPOShareDialog(
    po: PurchaseOrderEntity,
    poItems: List<PurchaseOrderItemEntity>,
    categoryTaxRules: List<com.example.data.entity.CategoryTaxEntity> = emptyList(),
    supplierName: String,
    supplier: SupplierEntity? = null,
    companyProfile: CompanyProfileEntity? = null,
    currencyFormat: NumberFormat,
    dateFormat: DateFormat,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isGeneratingPdf by remember { mutableStateOf(false) }
    var pendingShareTargetPackage by remember { mutableStateOf<String?>(null) }
    var showVerificationPreview by remember { mutableStateOf(false) }

    val activeCompany = companyProfile ?: CompanyProfileEntity()

    // Helper to generate PDF file on device
    fun generatePdfFile(): File? {
        return try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val titlePaint = Paint().apply {
                color = android.graphics.Color.parseColor("#1E293B")
                textSize = 20f
                isFakeBoldText = true
            }
            val subtitlePaint = Paint().apply {
                color = android.graphics.Color.parseColor("#64748B")
                textSize = 10f
            }
            val textPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#0F172A")
                textSize = 10f
            }
            val boldPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#0F172A")
                textSize = 10f
                isFakeBoldText = true
            }
            val linePaint = Paint().apply {
                color = android.graphics.Color.parseColor("#CBD5E1")
                strokeWidth = 1f
            }

            // Top Header Banner
            val headerBg = RectF(0f, 0f, 595f, 75f)
            canvas.drawRect(headerBg, Paint().apply { color = android.graphics.Color.parseColor("#1E293B") })

            val whitePaint = Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = 16f
                isFakeBoldText = true
            }
            canvas.drawText("POPT • ${activeCompany.companyName}", 24f, 32f, whitePaint)

            val whiteSub = Paint().apply {
                color = android.graphics.Color.parseColor("#CBD5E1")
                textSize = 9f
            }
            canvas.drawText("GSTIN: ${activeCompany.gstNumber} | ${activeCompany.phone}", 24f, 50f, whiteSub)
            canvas.drawText("PURCHASE ORDER INVOICE", 420f, 40f, Paint().apply {
                color = android.graphics.Color.parseColor("#38BDF8")
                textSize = 11f
                isFakeBoldText = true
            })

            var y = 95f

            // 2-Column Details Box: Left = Issued By (Company), Right = Supplier Details
            // Left Column (Company Master)
            canvas.drawText("ISSUED BY (BUYER):", 24f, y, boldPaint)
            canvas.drawText("SUPPLIER DETAILS (SELLER):", 310f, y, boldPaint)
            y += 14f

            canvas.drawText(activeCompany.companyName, 24f, y, boldPaint)
            canvas.drawText(supplier?.name ?: supplierName, 310f, y, boldPaint)
            y += 14f

            if (activeCompany.gstNumber.isNotBlank()) {
                canvas.drawText("GSTIN: ${activeCompany.gstNumber}", 24f, y, boldPaint)
            }
            if (!supplier?.gstNumber.isNullOrBlank()) {
                canvas.drawText("GSTIN: ${supplier?.gstNumber}", 310f, y, boldPaint)
            } else {
                canvas.drawText("GSTIN: N/A", 310f, y, textPaint)
            }
            y += 14f

            if (supplier?.contactPerson?.isNotBlank() == true) {
                canvas.drawText("Contact: ${supplier.contactPerson}", 310f, y, textPaint)
            }
            canvas.drawText("Email: ${activeCompany.email}", 24f, y, textPaint)
            y += 14f

            canvas.drawText("Address: ${activeCompany.address.take(35)}", 24f, y, subtitlePaint)
            if (supplier?.address?.isNotBlank() == true) {
                canvas.drawText("Address: ${supplier.address.take(35)}", 310f, y, subtitlePaint)
            }
            y += 18f

            canvas.drawLine(24f, y, 571f, y, linePaint)
            y += 16f

            // PO Metadata Bar
            canvas.drawText("PO Number: ${po.poNumber}", 24f, y, boldPaint)
            canvas.drawText("Order Date: ${dateFormat.format(Date(po.orderDateMillis))}", 200f, y, textPaint)
            canvas.drawText("Expected: ${dateFormat.format(Date(po.expectedDeliveryDateMillis))}", 380f, y, textPaint)
            y += 18f

            canvas.drawLine(24f, y, 571f, y, linePaint)
            y += 18f

            // Table Header
            canvas.drawText("#", 24f, y, boldPaint)
            canvas.drawText("Item / SKU", 50f, y, boldPaint)
            canvas.drawText("Product Image", 240f, y, boldPaint)
            canvas.drawText("Qty", 350f, y, boldPaint)
            canvas.drawText("Unit Price", 400f, y, boldPaint)
            canvas.drawText("Total", 490f, y, boldPaint)
            y += 10f
            canvas.drawLine(24f, y, 571f, y, linePaint)
            y += 20f

            // Table Rows
            poItems.forEachIndexed { idx, item ->
                canvas.drawText("${idx + 1}", 24f, y + 14f, textPaint)

                // Item Name & SKU
                val displayName = if (item.itemName.length > 22) item.itemName.take(20) + ".." else item.itemName
                canvas.drawText(displayName, 50f, y + 10f, boldPaint)
                val gstRateStr = if (item.taxPercent % 1.0 == 0.0) "${item.taxPercent.toInt()}%" else "${item.taxPercent}%"
                canvas.drawText("SKU: ${item.itemSku} | GST: $gstRateStr", 50f, y + 24f, subtitlePaint)

                // Product Image / Thumbnail
                val photoUri = item.photoUri ?: po.photoUri
                val bitmap = loadItemBitmap(context, photoUri)
                val imgRect = RectF(240f, y - 4f, 285f, y + 36f)
                if (bitmap != null) {
                    try {
                        canvas.drawBitmap(bitmap, null, imgRect, null)
                    } catch (e: Exception) {
                        canvas.drawRect(imgRect, linePaint)
                        canvas.drawText("No Image", 242f, y + 20f, subtitlePaint)
                    } finally {
                        try { bitmap.recycle() } catch (_: Exception) {}
                    }
                } else {
                    canvas.drawRect(imgRect, linePaint)
                    canvas.drawText("No Image", 242f, y + 20f, subtitlePaint)
                }

                // Numbers (Unit Price & Total Price)
                canvas.drawText("${item.quantityOrdered}", 350f, y + 18f, textPaint)
                canvas.drawText(currencyFormat.format(item.unitPrice), 400f, y + 18f, textPaint)
                canvas.drawText(currencyFormat.format(item.totalPrice), 490f, y + 18f, boldPaint)

                y += 48f
            }

            y += 10f
            canvas.drawLine(24f, y, 571f, y, linePaint)
            y += 16f

            // Totals Section with Split Tax Breakdown
            canvas.drawText("Total Taxable Approx Cost Amount:", 260f, y, textPaint)
            canvas.drawText(currencyFormat.format(po.subtotal), 480f, y, textPaint)
            y += 14f

            val taxGroups = poItems.groupBy { it.taxPercent }.toSortedMap()
            if (taxGroups.isNotEmpty()) {
                taxGroups.forEach { (rate, items) ->
                    val taxable = items.sumOf { it.totalPrice }
                    val tax = items.sumOf { it.taxAmount.takeIf { t -> t > 0.0 } ?: (it.totalPrice * (rate / 100.0)) }
                    val halfRate = rate / 2.0
                    val halfTax = tax / 2.0
                    val rateStr = if (rate % 1.0 == 0.0) rate.toInt().toString() else rate.toString()

                    canvas.drawText("Taxable Amount (GST $rateStr%):", 260f, y, boldPaint)
                    canvas.drawText(currencyFormat.format(taxable), 480f, y, boldPaint)
                    y += 12f

                    canvas.drawText("  ↳ Tax Amount (GST $rateStr%):", 260f, y, textPaint)
                    canvas.drawText(currencyFormat.format(tax), 480f, y, textPaint)
                    y += 12f

                    canvas.drawText("     CGST ($halfRate%): ${currencyFormat.format(halfTax)} | SGST ($halfRate%): ${currencyFormat.format(halfTax)}", 260f, y, subtitlePaint)
                    y += 14f
                }
            } else if (po.taxAmount > 0) {
                val cgst = po.taxAmount / 2.0
                val sgst = po.taxAmount / 2.0
                canvas.drawText("Total GST Input Tax:", 260f, y, textPaint)
                canvas.drawText(currencyFormat.format(po.taxAmount), 480f, y, textPaint)
                y += 14f
                canvas.drawText("  CGST: ${currencyFormat.format(cgst)} | SGST: ${currencyFormat.format(sgst)}", 260f, y, subtitlePaint)
                y += 14f
            }

            y += 4f
            val totalBg = RectF(260f, y - 10f, 571f, y + 16f)
            canvas.drawRect(totalBg, Paint().apply { color = android.graphics.Color.parseColor("#F1F5F9") })
            canvas.drawText("Total PO Amount (Grand Total):", 270f, y + 4f, boldPaint)
            canvas.drawText(currencyFormat.format(po.totalAmount), 480f, y + 4f, boldPaint)
            y += 32f

            if (po.notes.isNotBlank()) {
                canvas.drawText("Notes: ${po.notes}", 24f, y, subtitlePaint)
                y += 20f
            }

            canvas.drawText("Authorized Signature: _______________________", 24f, y, subtitlePaint)

            pdfDocument.finishPage(page)

            val pdfFile = File(context.cacheDir, "PO_${po.poNumber}.pdf")
            val fos = FileOutputStream(pdfFile)
            pdfDocument.writeTo(fos)
            fos.close()
            pdfDocument.close()

            pdfFile
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    // Share PDF file using FileProvider and Android Intent system
    fun sharePdf(targetPackage: String? = null) {
        val pdfFile = generatePdfFile()
        if (pdfFile == null || !pdfFile.exists()) {
            Toast.makeText(context, "Failed to generate PDF document", Toast.LENGTH_SHORT).show()
            showVerificationPreview = true
            return
        }

        try {
            val authority = "${context.packageName}.provider"
            val pdfUri: Uri = FileProvider.getUriForFile(context, authority, pdfFile)

            val messageText = buildString {
                appendLine("Dear $supplierName,")
                appendLine()
                appendLine("Please find attached the official Purchase Order document PDF #${po.poNumber}.")
                appendLine()
                appendLine("📦 Order Items:")
                poItems.forEachIndexed { idx, item ->
                    appendLine("${idx + 1}. ${item.itemName} (SKU: ${item.itemSku}) x${item.quantityOrdered} @ ${currencyFormat.format(item.unitPrice)}")
                }
                appendLine()
                appendLine("💰 Total Amount: ${currencyFormat.format(po.totalAmount)}")
                appendLine("📅 Expected Delivery: ${dateFormat.format(Date(po.expectedDeliveryDateMillis))}")
                if (po.notes.isNotBlank()) {
                    appendLine("📝 Notes: ${po.notes}")
                }
                appendLine()
                appendLine("Sent via PO Master System")
            }

            fun createBaseIntent(): Intent {
                return Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, pdfUri)
                    putExtra(Intent.EXTRA_SUBJECT, "Purchase Order PDF #${po.poNumber}")
                    putExtra(Intent.EXTRA_TEXT, messageText)
                    clipData = android.content.ClipData.newRawUri("PO PDF", pdfUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }

            if (targetPackage != null) {
                var launched = false
                val packagesToTry = listOf(targetPackage, "com.whatsapp.w4b")
                for (pkg in packagesToTry) {
                    try {
                        val whatsappIntent = createBaseIntent().apply { setPackage(pkg) }
                        context.startActivity(whatsappIntent)
                        launched = true
                        break
                    } catch (e: Throwable) {
                        // Package not installed or error
                    }
                }

                if (!launched) {
                    try {
                        val chooserIntent = Intent.createChooser(createBaseIntent(), "Share Purchase Order PDF").apply {
                            clipData = android.content.ClipData.newRawUri("PO PDF", pdfUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        Toast.makeText(context, "WhatsApp not installed. Opening system share menu...", Toast.LENGTH_SHORT).show()
                        context.startActivity(chooserIntent)
                    } catch (t: Throwable) {
                        Toast.makeText(context, "Sharing unavailable. Opening in-app PO preview.", Toast.LENGTH_SHORT).show()
                        showVerificationPreview = true
                    }
                }
            } else {
                try {
                    val chooserIntent = Intent.createChooser(createBaseIntent(), "Share Purchase Order PDF").apply {
                        clipData = android.content.ClipData.newRawUri("PO PDF", pdfUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooserIntent)
                } catch (t: Throwable) {
                    Toast.makeText(context, "Sharing unavailable. Opening in-app PO preview.", Toast.LENGTH_SHORT).show()
                    showVerificationPreview = true
                }
            }
        } catch (e: Throwable) {
            Toast.makeText(context, "Error preparing PDF share: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            showVerificationPreview = true
        }
    }

    // Print or View PDF file - Safe execution with in-app preview
    fun viewOrPrintPdf() {
        showVerificationPreview = true
        try {
            val pdfFile = generatePdfFile()
            if (pdfFile != null && pdfFile.exists()) {
                val authority = "${context.packageName}.provider"
                val pdfUri: Uri = FileProvider.getUriForFile(context, authority, pdfFile)

                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(pdfUri, "application/pdf")
                    clipData = android.content.ClipData.newRawUri("PO PDF", pdfUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(viewIntent, "Open / Print PO PDF").apply {
                    clipData = android.content.ClipData.newRawUri("PO PDF", pdfUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    context.startActivity(chooser)
                } catch (t: Throwable) {
                    Toast.makeText(context, "No external PDF viewer app found. Showing in-app PO preview.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            Toast.makeText(context, "Opening In-App PDF Preview Mode", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f)
                .testTag("print_po_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = "PDF Purchase Order",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(26.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "PDF Purchase Order",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "PO #${po.poNumber} • $supplierName",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(14.dp))

                // Printable PDF Preview Canvas Box
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // PDF Document Top Header Preview
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "POPT • PURCHASE ORDER",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimary
                                        )
                                        Text(
                                            text = "PO Number: ${po.poNumber}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                                    ) {
                                        Text(
                                            text = po.status,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Order Summary Header Box
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Buyer Company:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        Text(activeCompany.companyName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                    if (activeCompany.gstNumber.isNotBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Company GSTIN:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                            Text(activeCompany.gstNumber, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Supplier:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        Text(supplier?.name ?: supplierName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                    if (!supplier?.gstNumber.isNullOrBlank()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Supplier GSTIN:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                            Text(supplier!!.gstNumber, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.secondary)
                                        }
                                    }
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Order Date:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        Text(dateFormat.format(Date(po.orderDateMillis)), style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Expected Delivery:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        Text(dateFormat.format(Date(po.expectedDeliveryDateMillis)), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }

                        // Line Items Heading
                        item {
                            Text(
                                text = "Ordered Items & Item Pictures (${poItems.size}):",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Items list with pictures
                        itemsIndexed(poItems) { idx, item ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Item Picture Thumbnail
                                    val photoModel = item.photoUri ?: po.photoUri
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(54.dp)
                                    ) {
                                        if (!photoModel.isNullOrBlank()) {
                                            AsyncImage(
                                                model = photoModel,
                                                contentDescription = item.itemName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Icon(
                                                    Icons.Default.Inventory2,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.itemName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val mainCats = listOfNotNull(
                                            item.c1Brand.takeIf { it.isNotBlank() }?.let { "Brand: $it" },
                                            item.c2Size.takeIf { it.isNotBlank() }?.let { "Size: $it" },
                                            item.c3Design.takeIf { it.isNotBlank() }?.let { "Design: $it" },
                                            item.c4Colour.takeIf { it.isNotBlank() }?.let { "Colour: $it" }
                                        ).joinToString(" | ")
                                        if (mainCats.isNotBlank()) {
                                            Text(mainCats, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        }
                                        Text(
                                            text = "SKU: ${item.itemSku}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        val lineTaxPercent = item.taxPercent
                                        Text(
                                            text = "${item.quantityOrdered} units × ${currencyFormat.format(item.unitPrice)} (GST ${if (lineTaxPercent % 1.0 == 0.0) lineTaxPercent.toInt().toString() else lineTaxPercent.toString()}%)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Text(
                                        text = currencyFormat.format(item.totalPrice),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // Totals Summary Box with Split Tax Breakdown
                        item {
                            val taxGroups = remember(poItems) {
                                poItems.groupBy { it.taxPercent }.toSortedMap()
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Total Taxable Cost Amount:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                        Text(currencyFormat.format(po.subtotal), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                    }

                                    // Split Tax Slabs
                                    taxGroups.forEach { (rate, items) ->
                                        val taxable = items.sumOf { it.totalPrice }
                                        val tax = items.sumOf { it.taxAmount.takeIf { t -> t > 0.0 } ?: (it.totalPrice * (rate / 100.0)) }
                                        val halfRate = rate / 2.0
                                        val halfTax = tax / 2.0
                                        val rateStr = if (rate % 1.0 == 0.0) rate.toInt().toString() else rate.toString()

                                        Surface(
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(6.dp),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("Taxable Amount (GST $rateStr%):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                    Text(currencyFormat.format(taxable), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                }
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("  ↳ Tax Amount (GST $rateStr%):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                                    Text(currencyFormat.format(tax), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                }
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                                    Text("     • CGST ($halfRate%): ${currencyFormat.format(halfTax)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text("• SGST ($halfRate%): ${currencyFormat.format(halfTax)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }

                                    if (po.taxAmount > 0) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Total GST Tax Amount:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                            Text(currencyFormat.format(po.taxAmount), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                                        }
                                    }

                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Total PO Amount (Grand Total):", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            currencyFormat.format(po.totalAmount),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons for Sharing PDF & Printing
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // WhatsApp Direct Share PDF
                        Button(
                            onClick = {
                                pendingShareTargetPackage = "com.whatsapp"
                                showVerificationPreview = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("whatsapp_pdf_share_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF25D366),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "WhatsApp PDF",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        // General System Share PDF (All Apps)
                        Button(
                            onClick = {
                                pendingShareTargetPackage = null
                                showVerificationPreview = true
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("general_pdf_share_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.IosShare, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Share PDF",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewOrPrintPdf() },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View / Print PDF")
                        }

                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(0.6f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }

    if (showVerificationPreview) {
        PdfPreShareVerificationDialog(
            po = po,
            poItems = poItems,
            supplierName = supplierName,
            currencyFormat = currencyFormat,
            dateFormat = dateFormat,
            targetPackage = pendingShareTargetPackage,
            onConfirmShare = {
                showVerificationPreview = false
                sharePdf(pendingShareTargetPackage)
            },
            onDismiss = {
                showVerificationPreview = false
            }
        )
    }
}

@Composable
private fun PdfPreShareVerificationDialog(
    po: PurchaseOrderEntity,
    poItems: List<PurchaseOrderItemEntity>,
    supplierName: String,
    currencyFormat: NumberFormat,
    dateFormat: DateFormat,
    targetPackage: String?,
    onConfirmShare: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(8.dp)
                                    .size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Verify PO & Images",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Pre-share PDF Inspection",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Please verify line items, totals, and attached product photos before sending to $supplierName.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // PO Quick Summary Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("PO Number:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(po.poNumber, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Supplier:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(supplierName, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Expected Delivery:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(dateFormat.format(Date(po.expectedDeliveryDateMillis)), style = MaterialTheme.typography.labelSmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Grand Total:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            Text(currencyFormat.format(po.totalAmount), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Product Photos & SKUs (${poItems.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(poItems) { idx, item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val photoModel = item.photoUri ?: po.photoUri
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    if (!photoModel.isNullOrBlank()) {
                                        AsyncImage(
                                            model = photoModel,
                                            contentDescription = item.itemName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                            Icon(
                                                Icons.Default.Inventory2,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.outline,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.itemName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        val cats = listOfNotNull(
                                            item.c1Brand.takeIf { it.isNotBlank() }?.let { "Brand: $it" },
                                            item.c2Size.takeIf { it.isNotBlank() }?.let { "Size: $it" },
                                            item.c3Design.takeIf { it.isNotBlank() }?.let { "Design: $it" },
                                            item.c4Colour.takeIf { it.isNotBlank() }?.let { "Colour: $it" }
                                        ).joinToString(" | ")
                                        if (cats.isNotBlank()) {
                                            Text(cats, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        }
                                        Text("SKU: ${item.itemSku} • Qty: ${item.quantityOrdered}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }

                                Text(currencyFormat.format(item.totalPrice), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Back")
                    }

                    Button(
                        onClick = onConfirmShare,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = if (targetPackage == "com.whatsapp") ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)) else ButtonDefaults.buttonColors()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (targetPackage == "com.whatsapp") "Send WhatsApp" else "Confirm & Share")
                    }
                }
            }
        }
    }
}

private fun loadItemBitmap(context: Context, photoUri: String?): Bitmap? {
    if (photoUri.isNullOrBlank()) return null
    return try {
        val uri = Uri.parse(photoUri)
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = 2
        }
        if (uri.scheme == "content" || uri.scheme == "file") {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } else {
            BitmapFactory.decodeFile(photoUri, opts)
        }
    } catch (e: Exception) {
        null
    }
}
