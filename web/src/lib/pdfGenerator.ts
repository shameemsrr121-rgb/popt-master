import { jsPDF } from 'jspdf';
import autoTable from 'jspdf-autotable';
import { PurchaseOrder, Supplier, CompanyProfile } from '../types';

/**
 * Loads an image from URL or data URI and converts to base64 for jsPDF embedding
 */
export async function loadImageAsBase64(url?: string | null): Promise<string | null> {
  if (!url || !url.trim()) return null;
  const cleanUrl = url.trim();
  if (cleanUrl.startsWith('data:image/')) return cleanUrl;

  try {
    const res = await fetch(cleanUrl, { mode: 'cors' });
    if (!res.ok) return null;
    const blob = await res.blob();
    return new Promise((resolve) => {
      const reader = new FileReader();
      reader.onloadend = () => {
        resolve(typeof reader.result === 'string' ? reader.result : null);
      };
      reader.onerror = () => resolve(null);
      reader.readAsDataURL(blob);
    });
  } catch {
    return null;
  }
}

export function generatePurchaseOrderPDF(
  po: PurchaseOrder,
  supplier?: Supplier,
  company?: CompanyProfile | null,
  itemImages?: (string | null)[]
): jsPDF {
  const doc = new jsPDF({
    orientation: 'portrait',
    unit: 'mm',
    format: 'a4'
  });

  const pageWidth = doc.internal.pageSize.getWidth();
  const pageHeight = doc.internal.pageSize.getHeight();
  const margin = 14;

  // Primary Theme Colors (Deep Indigo & Slate)
  const primaryColor: [number, number, number] = [30, 41, 59]; // slate-800
  const accentColor: [number, number, number] = [79, 70, 229]; // indigo-600
  const lightBg: [number, number, number] = [248, 250, 252]; // slate-50
  const borderColor: [number, number, number] = [226, 232, 240]; // slate-200

  // Top Accent Bar
  doc.setFillColor(...accentColor);
  doc.rect(0, 0, pageWidth, 4, 'F');

  // --- HEADER SECTION ---
  let currentY = 16;

  // Left Header: Company Information
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(16);
  doc.setTextColor(...primaryColor);
  const companyName = company?.company_name || 'PO MASTER TEXTILES';
  doc.text(companyName, margin, currentY);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8.5);
  doc.setTextColor(100, 116, 139); // slate-500
  currentY += 5;

  if (company?.address) {
    const addressLines = doc.splitTextToSize(company.address, 95);
    doc.text(addressLines, margin, currentY);
    currentY += addressLines.length * 3.8;
  }

  const phoneEmail = [
    company?.phone ? `Ph: ${company.phone}` : '',
    company?.email ? `Email: ${company.email}` : ''
  ].filter(Boolean).join('  |  ');

  if (phoneEmail) {
    doc.text(phoneEmail, margin, currentY);
    currentY += 3.8;
  }

  if (company?.gst_number) {
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(...accentColor);
    doc.text(`GSTIN: ${company.gst_number}`, margin, currentY);
    doc.setFont('helvetica', 'normal');
    currentY += 4;
  }

  // Right Header: Document Badge & Details
  const rightColX = pageWidth - margin;

  doc.setFont('helvetica', 'bold');
  doc.setFontSize(20);
  doc.setTextColor(...accentColor);
  doc.text('PURCHASE ORDER', rightColX, 16, { align: 'right' });

  doc.setFontSize(11);
  doc.setTextColor(...primaryColor);
  doc.text(po.po_number || 'PO-DRAFT', rightColX, 22, { align: 'right' });

  // PO Meta Details Table/Lines
  doc.setFontSize(8.5);
  doc.setFont('helvetica', 'normal');
  doc.setTextColor(100, 116, 139);

  const orderDate = po.order_date_millis
    ? new Date(Number(po.order_date_millis)).toLocaleDateString('en-IN', {
        day: '2-digit',
        month: 'short',
        year: 'numeric'
      })
    : 'N/A';

  const expDate = po.expected_delivery_date_millis
    ? new Date(Number(po.expected_delivery_date_millis)).toLocaleDateString('en-IN', {
        day: '2-digit',
        month: 'short',
        year: 'numeric'
      })
    : 'N/A';

  doc.text(`Date: ${orderDate}`, rightColX, 28, { align: 'right' });
  doc.text(`Exp. Delivery: ${expDate}`, rightColX, 32.5, { align: 'right' });
  
  // Status Pill
  const status = (po.status || 'DRAFT').toUpperCase();
  const statusWidth = 24;
  const statusX = rightColX - statusWidth;
  doc.setFillColor(...lightBg);
  doc.setDrawColor(...borderColor);
  doc.roundedRect(statusX, 35, statusWidth, 5.5, 1, 1, 'FD');
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(7.5);
  doc.setTextColor(71, 85, 105);
  doc.text(status, statusX + statusWidth / 2, 38.8, { align: 'center' });

  // Divider line
  currentY = Math.max(currentY + 2, 44);
  doc.setDrawColor(...borderColor);
  doc.setLineWidth(0.4);
  doc.line(margin, currentY, pageWidth - margin, currentY);

  // --- VENDOR & DELIVERY BOXES ---
  currentY += 4;
  const boxHeight = 34;
  const boxWidth = (pageWidth - margin * 2 - 6) / 2;

  // Box 1: Vendor Details
  doc.setFillColor(...lightBg);
  doc.setDrawColor(...borderColor);
  doc.roundedRect(margin, currentY, boxWidth, boxHeight, 2, 2, 'FD');

  doc.setFont('helvetica', 'bold');
  doc.setFontSize(8);
  doc.setTextColor(...accentColor);
  doc.text('VENDOR / SUPPLIER DETAILS', margin + 4, currentY + 5.5);

  doc.setFontSize(9.5);
  doc.setTextColor(...primaryColor);
  const supplierName = supplier?.name || po.supplier_name || 'Vendor';
  doc.text(supplierName, margin + 4, currentY + 10.5);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8);
  doc.setTextColor(100, 116, 139);

  let vendorY = currentY + 15;
  if (supplier?.contact_person) {
    doc.text(`Attn: ${supplier.contact_person}`, margin + 4, vendorY);
    vendorY += 3.8;
  }
  const supContact = [supplier?.phone, supplier?.email].filter(Boolean).join(' | ');
  if (supContact) {
    doc.text(supContact, margin + 4, vendorY);
    vendorY += 3.8;
  }
  if (supplier?.gst_number) {
    doc.setFont('helvetica', 'bold');
    doc.setTextColor(71, 85, 105);
    doc.text(`GSTIN: ${supplier.gst_number}`, margin + 4, vendorY);
    doc.setFont('helvetica', 'normal');
  }

  // Box 2: Delivery & Shipping Details
  const shipBoxX = margin + boxWidth + 6;
  doc.setFillColor(...lightBg);
  doc.setDrawColor(...borderColor);
  doc.roundedRect(shipBoxX, currentY, boxWidth, boxHeight, 2, 2, 'FD');

  doc.setFont('helvetica', 'bold');
  doc.setFontSize(8);
  doc.setTextColor(...accentColor);
  doc.text('DELIVERY & BILLING DESTINATION', shipBoxX + 4, currentY + 5.5);

  doc.setFontSize(9.5);
  doc.setTextColor(...primaryColor);
  doc.text(po.location || 'Central Warehouse', shipBoxX + 4, currentY + 10.5);

  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8);
  doc.setTextColor(100, 116, 139);

  let shipY = currentY + 15;
  if (po.created_by) {
    doc.text(`Created By: ${po.created_by}`, shipBoxX + 4, shipY);
    shipY += 3.8;
  }
  doc.text(`Destination: Commercial Godown / Store Depot`, shipBoxX + 4, shipY);

  currentY += boxHeight + 6;

  // --- ITEMS TABLE ---
  const tableItems = po.items || [];
  const hasAnyImages = itemImages && itemImages.some(img => !!img);

  const tableData = tableItems.map((item, index) => {
    // Format description with attributes
    const parts = [item.item_name || 'Item'];
    const attrs = [
      item.c1_brand ? `Brand: ${item.c1_brand}` : '',
      item.c2_size ? `Size: ${item.c2_size}` : '',
      item.c3_design ? `Design: ${item.c3_design}` : '',
      item.c4_colour ? `Color: ${item.c4_colour}` : '',
      item.division ? `Div: ${item.division}` : '',
      item.department ? `Dept: ${item.department}` : ''
    ].filter(Boolean).join(', ');

    if (attrs) {
      parts.push(`(${attrs})`);
    }

    const qty = Number(item.quantity_ordered) || 0;
    const unitPrice = Number(item.unit_price) || 0;
    const taxRate = Number(item.tax_percent) || 0;
    const total = Number(item.total_price) || (qty * unitPrice * (1 + taxRate / 100));

    if (hasAnyImages) {
      return [
        String(index + 1),
        '', // Image rendered in didDrawCell
        parts.join('\n'),
        `${qty} pcs`,
        `Rs. ${unitPrice.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
        `${taxRate}%`,
        `Rs. ${total.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
      ];
    }

    return [
      String(index + 1),
      parts.join('\n'),
      `${qty} pcs`,
      `Rs. ${unitPrice.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      `${taxRate}%`,
      `Rs. ${total.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`
    ];
  });

  const tableHeaders = hasAnyImages
    ? [['#', 'Dress Photo', 'Item Description', 'Qty', 'Unit Price', 'GST', 'Total Amount']]
    : [['#', 'Item Description', 'Qty', 'Unit Price', 'GST', 'Total Amount']];

  const columnStylesConfig: any = hasAnyImages
    ? {
        0: { halign: 'center', cellWidth: 8 },
        1: { halign: 'center', cellWidth: 20, minCellHeight: 18 },
        2: { halign: 'left' },
        3: { halign: 'center', cellWidth: 16 },
        4: { halign: 'right', cellWidth: 26 },
        5: { halign: 'center', cellWidth: 14 },
        6: { halign: 'right', cellWidth: 30, fontStyle: 'bold' }
      }
    : {
        0: { halign: 'center', cellWidth: 10 },
        1: { halign: 'left' },
        2: { halign: 'center', cellWidth: 20 },
        3: { halign: 'right', cellWidth: 28 },
        4: { halign: 'center', cellWidth: 16 },
        5: { halign: 'right', cellWidth: 32, fontStyle: 'bold' }
      };

  autoTable(doc, {
    startY: currentY,
    head: tableHeaders,
    body: tableData.length > 0 ? tableData : [['-', 'No items added to this PO', '-', '-', '-', '-']],
    theme: 'grid',
    headStyles: {
      fillColor: primaryColor,
      textColor: [255, 255, 255],
      fontStyle: 'bold',
      fontSize: 8.5,
      halign: 'left'
    },
    styles: {
      font: 'helvetica',
      fontSize: 8,
      cellPadding: 3,
      textColor: [51, 65, 85], // slate-700
      lineColor: [226, 232, 240], // slate-200
      lineWidth: 0.2,
      minCellHeight: hasAnyImages ? 16 : 0
    },
    columnStyles: columnStylesConfig,
    alternateRowStyles: {
      fillColor: [248, 250, 252] // light slate
    },
    margin: { left: margin, right: margin },
    didDrawCell: (data) => {
      if (hasAnyImages && data.column.index === 1 && data.cell.section === 'body') {
        const itemImg = itemImages ? itemImages[data.row.index] : null;
        if (itemImg) {
          try {
            const padding = 1.5;
            const maxW = data.cell.width - padding * 2;
            const maxH = data.cell.height - padding * 2;
            const imgSize = Math.min(maxW, maxH, 15);
            const posX = data.cell.x + (data.cell.width - imgSize) / 2;
            const posY = data.cell.y + (data.cell.height - imgSize) / 2;
            doc.addImage(itemImg, 'JPEG', posX, posY, imgSize, imgSize);
          } catch {
            // If failed to add image, cell remains clean text space
          }
        }
      }
    }
  });

  // Calculate table finish Y
  const finalY = (doc as any).lastAutoTable?.finalY || currentY + 40;

  // Check if we need space for summary and footer or new page
  let summaryY = finalY + 6;
  if (summaryY + 55 > pageHeight - margin) {
    doc.addPage();
    summaryY = margin + 10;
  }

  // --- SUMMARY & TOTALS SECTION ---
  const summaryBoxWidth = 75;
  const summaryX = pageWidth - margin - summaryBoxWidth;

  // Left Section on the same line: Notes & Bank info
  const notesWidth = summaryX - margin - 6;

  if (po.notes || company?.terms_and_conditions) {
    doc.setFont('helvetica', 'bold');
    doc.setFontSize(8);
    doc.setTextColor(...primaryColor);
    doc.text('TERMS & INSTRUCTIONS:', margin, summaryY + 3);

    doc.setFont('helvetica', 'normal');
    doc.setFontSize(7.5);
    doc.setTextColor(100, 116, 139);

    let noteText = po.notes ? `Note: ${po.notes}\n` : '';
    if (company?.terms_and_conditions) {
      noteText += company.terms_and_conditions;
    }

    const noteLines = doc.splitTextToSize(noteText.trim(), notesWidth);
    doc.text(noteLines, margin, summaryY + 7);
  }

  // Right Box: Financial Summary
  doc.setFillColor(...lightBg);
  doc.setDrawColor(...borderColor);
  doc.roundedRect(summaryX, summaryY, summaryBoxWidth, 38, 2, 2, 'FD');

  const subtotal = Number(po.subtotal || 0);
  const taxAmount = Number(po.tax_amount || 0);
  const grandTotal = Number(po.total_amount || 0);
  const cgst = taxAmount / 2;
  const sgst = taxAmount / 2;

  let rowY = summaryY + 6;
  doc.setFontSize(8);
  doc.setFont('helvetica', 'normal');
  doc.setTextColor(100, 116, 139);

  // Subtotal
  doc.text('Taxable Subtotal:', summaryX + 4, rowY);
  doc.text(`Rs. ${subtotal.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`, summaryX + summaryBoxWidth - 4, rowY, { align: 'right' });

  // CGST
  rowY += 5;
  doc.text('CGST (Central):', summaryX + 4, rowY);
  doc.text(`Rs. ${cgst.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`, summaryX + summaryBoxWidth - 4, rowY, { align: 'right' });

  // SGST
  rowY += 5;
  doc.text('SGST (State):', summaryX + 4, rowY);
  doc.text(`Rs. ${sgst.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`, summaryX + summaryBoxWidth - 4, rowY, { align: 'right' });

  // Total Tax
  rowY += 5;
  doc.text('Total GST Tax:', summaryX + 4, rowY);
  doc.text(`Rs. ${taxAmount.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`, summaryX + summaryBoxWidth - 4, rowY, { align: 'right' });

  // Divider
  rowY += 3;
  doc.setDrawColor(...borderColor);
  doc.line(summaryX + 4, rowY, summaryX + summaryBoxWidth - 4, rowY);

  // Grand Total Banner
  rowY += 6;
  doc.setFont('helvetica', 'bold');
  doc.setFontSize(9.5);
  doc.setTextColor(...accentColor);
  doc.text('Grand Total:', summaryX + 4, rowY);
  doc.text(`Rs. ${grandTotal.toLocaleString('en-IN', { minimumFractionDigits: 2 })}`, summaryX + summaryBoxWidth - 4, rowY, { align: 'right' });

  // --- SIGNATORY & FOOTER SECTION ---
  const footerY = Math.max(summaryY + 45, pageHeight - 32);

  // Bank Details (if present)
  if (company?.bank_name && company?.account_number) {
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(7.5);
    doc.setTextColor(148, 163, 184); // slate-400
    doc.text(`Bank: ${company.bank_name}  |  A/C: ${company.account_number}  |  IFSC: ${company.ifsc_code || 'N/A'}`, margin, footerY + 12);
  }

  // Authorized Signatory
  const signX = pageWidth - margin - 50;
  doc.setFont('helvetica', 'normal');
  doc.setFontSize(8);
  doc.setTextColor(71, 85, 105);
  doc.text(`For ${company?.company_name || 'PO Master'}`, signX, footerY + 4);
  
  doc.setDrawColor(203, 213, 225);
  doc.setLineWidth(0.3);
  doc.line(signX, footerY + 16, signX + 50, footerY + 16);

  doc.setFontSize(7.5);
  doc.setTextColor(148, 163, 184);
  doc.text('Authorized Signatory', signX + 10, footerY + 20);

  // Page Numbers
  const totalPages = doc.getNumberOfPages();
  for (let i = 1; i <= totalPages; i++) {
    doc.setPage(i);
    doc.setFont('helvetica', 'normal');
    doc.setFontSize(7);
    doc.setTextColor(148, 163, 184);
    doc.text(
      `Generated via POPT Purchase Order Master | Page ${i} of ${totalPages}`,
      pageWidth / 2,
      pageHeight - 6,
      { align: 'center' }
    );
  }

  return doc;
}

export async function downloadPurchaseOrderPDF(
  po: PurchaseOrder,
  supplier?: Supplier,
  company?: CompanyProfile | null
): Promise<void> {
  const items = po.items || [];
  const imagePromises = items.map(item => loadImageAsBase64(item.photo_uri || po.photo_uri));
  const resolvedImages = await Promise.all(imagePromises);

  const doc = generatePurchaseOrderPDF(po, supplier, company, resolvedImages);
  const filename = `${po.po_number || 'purchase_order'}.pdf`;
  doc.save(filename);
}

export async function getPurchaseOrderPDFBlob(
  po: PurchaseOrder,
  supplier?: Supplier,
  company?: CompanyProfile | null
): Promise<Blob> {
  const items = po.items || [];
  const imagePromises = items.map(item => loadImageAsBase64(item.photo_uri || po.photo_uri));
  const resolvedImages = await Promise.all(imagePromises);

  const doc = generatePurchaseOrderPDF(po, supplier, company, resolvedImages);
  return doc.output('blob');
}
