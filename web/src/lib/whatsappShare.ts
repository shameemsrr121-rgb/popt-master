import { PurchaseOrder, Supplier, CompanyProfile } from '../types';
import { getPurchaseOrderPDFBlob, downloadPurchaseOrderPDF } from './pdfGenerator';

/**
 * Format clean, human-readable WhatsApp message text with order details.
 */
export function formatPurchaseOrderWhatsAppMessage(
  po: PurchaseOrder,
  supplier?: Supplier,
  company?: CompanyProfile | null
): string {
  const companyTitle = company?.company_name || 'PO Master';
  const supplierName = supplier?.name || po.supplier_name || 'Valued Supplier';
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
    : '';

  const items = po.items || [];
  const itemsText = items.length > 0
    ? items.map((item, idx) => {
        const qty = Number(item.quantity_ordered) || 0;
        const price = Number(item.unit_price) || 0;
        const taxRate = Number(item.tax_percent) || 0;
        const total = Number(item.total_price) || (qty * price * (1 + taxRate / 100));
        
        let details = `${idx + 1}. *${item.item_name}* - ${qty} pcs @ ₹${price.toLocaleString('en-IN')}`;
        if (taxRate > 0) {
          details += ` (GST ${taxRate}%)`;
        }
        details += ` = ₹${total.toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
        return details;
      }).join('\n')
    : 'No items listed';

  const totalAmount = Number(po.total_amount || 0).toLocaleString('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2
  });

  const lines = [
    `*PURCHASE ORDER: ${po.po_number || 'PO-NEW'}*`,
    `*Supplier:* ${supplierName}`,
    `*Date:* ${orderDate}`,
    expDate ? `*Expected Delivery:* ${expDate}` : '',
    `*Delivery Location:* ${po.location || 'Central Warehouse'}`,
    '----------------------------------',
    '*Ordered Items:*',
    itemsText,
    '----------------------------------',
    `*Total Amount:* ₹${totalAmount}`,
    '----------------------------------',
    po.notes ? `*Notes:* ${po.notes}\n----------------------------------` : '',
    `Sent via ${companyTitle}`
  ].filter(Boolean);

  return lines.join('\n');
}

/**
 * Clean phone number to WhatsApp international format (e.g. 919876543210)
 */
export function sanitizeWhatsAppPhone(phone?: string): string {
  if (!phone) return '';
  // Remove all non-numeric characters
  const clean = phone.replace(/\D/g, '');
  if (!clean) return '';
  // If 10 digits (typical Indian mobile), prepend 91
  if (clean.length === 10) {
    return `91${clean}`;
  }
  return clean;
}

/**
 * Share Purchase Order via WhatsApp:
 * 1. Attempts Web Share API with attached PDF file on supported devices (Mobile Chrome/Safari/Edge).
 * 2. If file sharing is not supported or fails, triggers direct PDF download and opens wa.me link with pre-filled text.
 */
export async function sharePurchaseOrderOnWhatsApp(
  po: PurchaseOrder,
  supplier?: Supplier,
  company?: CompanyProfile | null,
  overridePhone?: string
): Promise<{ method: 'share_api' | 'wa_link'; success: boolean }> {
  const messageText = formatPurchaseOrderWhatsAppMessage(po, supplier, company);
  const targetPhone = sanitizeWhatsAppPhone(overridePhone || supplier?.phone || '');
  const pdfFileName = `${po.po_number || 'Purchase_Order'}.pdf`;

  // Check Web Share API with file support
  let canShareFile = false;
  let pdfFile: File | null = null;

  try {
    const pdfBlob = await getPurchaseOrderPDFBlob(po, supplier, company);
    pdfFile = new File([pdfBlob], pdfFileName, { type: 'application/pdf' });

    if (
      typeof navigator !== 'undefined' &&
      navigator.canShare &&
      navigator.canShare({ files: [pdfFile] })
    ) {
      canShareFile = true;
    }
  } catch (err) {
    console.warn('PDF File preparation for Web Share API encountered error:', err);
    canShareFile = false;
  }

  // Attempt Web Share API if supported
  if (canShareFile && pdfFile && navigator.share) {
    try {
      await navigator.share({
        title: `Purchase Order ${po.po_number}`,
        text: messageText,
        files: [pdfFile]
      });
      return { method: 'share_api', success: true };
    } catch (shareError: any) {
      // User cancelled share dialog or it failed
      if (shareError.name === 'AbortError') {
        return { method: 'share_api', success: false };
      }
      console.warn('Web Share failed, falling back to WhatsApp link + download:', shareError);
    }
  }

  // Fallback: Download PDF for the user to attach, and open wa.me link with formatted message
  try {
    await downloadPurchaseOrderPDF(po, supplier, company);
  } catch (downloadErr) {
    console.error('PDF download error:', downloadErr);
  }

  // Open WhatsApp Web or mobile app
  const encodedText = encodeURIComponent(messageText);
  let whatsappUrl = `https://wa.me/?text=${encodedText}`;
  if (targetPhone) {
    whatsappUrl = `https://wa.me/${targetPhone}?text=${encodedText}`;
  }

  // Open in new window/tab
  window.open(whatsappUrl, '_blank', 'noopener,noreferrer');
  return { method: 'wa_link', success: true };
}
