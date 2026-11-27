import React, { useState, useRef, useEffect, useMemo } from 'react';
import { 
  Plus, 
  Search, 
  Printer, 
  Trash2, 
  Eye, 
  Upload, 
  FileText, 
  X, 
  Check, 
  AlertCircle,
  Calendar,
  Building,
  Image as ImageIcon,
  Camera,
  Loader2,
  ZoomIn,
  Download,
  Share2,
  MessageCircle,
  CheckSquare,
  Square,
  Sparkles,
  Layers,
  RefreshCw
} from 'lucide-react';
import { PurchaseOrder, PurchaseOrderItem, Supplier, Location, InventoryItem, CompanyProfile, User, TaxRule, StoreHierarchyItem } from '../types';
import { supabase, uploadPoAttachment, fileToOptimizedDataUrl } from '../lib/supabase';
import { calculateTaxRate, getTaxFlowBadge } from '../lib/taxMaster';
import { sharePurchaseOrderOnWhatsApp } from '../lib/whatsappShare';
import { getStoreHierarchy } from '../lib/storeHierarchy';
import { CameraCaptureDialog } from './CameraCaptureDialog';
import { SearchableSupplierSelect, SearchableDepartmentInput } from './SearchableDropdowns';

interface PurchaseOrdersViewProps {
  orders: PurchaseOrder[];
  suppliers: Supplier[];
  locations: Location[];
  inventory: InventoryItem[];
  taxRules?: TaxRule[];
  companyProfile: CompanyProfile | null;
  currentUser: User | null;
  onRefresh: () => void;
  selectedPOForView: PurchaseOrder | null;
  setSelectedPOForView: (po: PurchaseOrder | null) => void;
  isCreateOpen: boolean;
  setIsCreateOpen: (open: boolean) => void;
}

export const PurchaseOrdersView: React.FC<PurchaseOrdersViewProps> = ({
  orders,
  suppliers,
  locations,
  inventory,
  taxRules = [],
  companyProfile,
  currentUser,
  onRefresh,
  selectedPOForView,
  setSelectedPOForView,
  isCreateOpen,
  setIsCreateOpen
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [selectedSupplierFilter, setSelectedSupplierFilter] = useState('ALL');
  const [selectedDepartmentFilter, setSelectedDepartmentFilter] = useState('ALL');
  const [selectedDateFilter, setSelectedDateFilter] = useState('');

  // Form states for Create PO
  const [supplierId, setSupplierId] = useState<number>(suppliers[0]?.id || 0);
  const [deliveryDate, setDeliveryDate] = useState(
    new Date(Date.now() + 7 * 86400000).toISOString().split('T')[0]
  );
  
  // Store Hierarchy State & Department auto-lookup
  const [hierarchyList, setHierarchyList] = useState<StoreHierarchyItem[]>([]);
  const [selectedDepartment, setSelectedDepartment] = useState('');
  const [autoDivision, setAutoDivision] = useState('');
  const [autoSection, setAutoSection] = useState('');
  const [autoTaxType, setAutoTaxType] = useState<'Textile' | 'Readymade'>('Readymade');
  
  // Multi-branch selection
  const [selectedBranches, setSelectedBranches] = useState<string[]>(['Central Warehouse']);

  const [notes, setNotes] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isRefreshingSuppliers, setIsRefreshingSuppliers] = useState(false);
  const [showQuickAddSupplier, setShowQuickAddSupplier] = useState(false);
  const [quickSuppName, setQuickSuppName] = useState('');
  const [quickSuppPhone, setQuickSuppPhone] = useState('');
  const [quickSuppGst, setQuickSuppGst] = useState('');
  const [isSavingSupplier, setIsSavingSupplier] = useState(false);

  // Sync supplierId when suppliers load or change
  useEffect(() => {
    if (suppliers.length > 0 && (!supplierId || !suppliers.some(s => s.id === supplierId))) {
      setSupplierId(suppliers[0].id);
    }
  }, [suppliers, supplierId]);

  const handleQuickAddSupplier = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!quickSuppName.trim()) return;
    setIsSavingSupplier(true);
    try {
      const code = `SUP-${quickSuppName.trim().slice(0, 3).toUpperCase()}-${Math.floor(100 + Math.random() * 900)}`;
      const { data, error } = await supabase.from('suppliers').insert({
        name: quickSuppName.trim(),
        code: code,
        phone: quickSuppPhone.trim() || null,
        gst_number: quickSuppGst.trim() || null
      }).select().single();

      if (error) throw error;
      if (data) {
        setSupplierId(data.id);
      }
      setQuickSuppName('');
      setQuickSuppPhone('');
      setQuickSuppGst('');
      setShowQuickAddSupplier(false);
      onRefresh();
    } catch (err: any) {
      alert(`Error adding supplier: ${err.message}`);
    } finally {
      setIsSavingSupplier(false);
    }
  };

  const handleManualRefreshSuppliers = async () => {
    setIsRefreshingSuppliers(true);
    try {
      await onRefresh();
    } finally {
      setIsRefreshingSuppliers(false);
    }
  };

  // Load hierarchy list
  useEffect(() => {
    getStoreHierarchy().then(data => {
      if (Array.isArray(data)) {
        setHierarchyList(data);
      }
    });
  }, []);

  // Sync initial branches if locations change
  useEffect(() => {
    if (locations.length > 0 && selectedBranches.length === 0) {
      setSelectedBranches([locations[0].name]);
    }
  }, [locations]);

  // Handle department change & auto-populate division, section, tax type
  const handleDepartmentChange = (deptName: string) => {
    setSelectedDepartment(deptName);
    const trimmedDept = deptName.trim().toLowerCase();
    
    // Look up in hierarchy
    let match = hierarchyList.find(h => h.department.trim().toLowerCase() === trimmedDept);
    
    // Fallback: look up in inventory
    if (!match) {
      const invMatch = inventory.find(i => (i.department || '').trim().toLowerCase() === trimmedDept);
      if (invMatch) {
        match = {
          department: invMatch.department,
          division: invMatch.division || 'Readymade',
          section: invMatch.section || 'General',
          type: invMatch.type || (invMatch.category === 'Textile' ? 'Textile' : 'Readymade')
        };
      }
    }

    if (match) {
      setAutoDivision(match.division);
      setAutoSection(match.section);
      setAutoTaxType(match.type);

      // Update all line items with this department & auto-looked up hierarchy
      setLineItems(prevItems => 
        prevItems.map((item, idx) => {
          const calcResult = calculateTaxRate({
            itemName: item.item_name,
            category: match!.division,
            division: match!.division,
            department: match!.department,
            section: match!.section,
            type: match!.type,
            unitCost: Number(item.unit_price) || 0,
            customRules: taxRules
          });
          const taxRate = customTaxOverrides[idx] ? item.tax_percent : calcResult.rate;
          const taxable = Number(item.quantity_ordered || 0) * Number(item.unit_price || 0);
          const tax = taxable * (taxRate / 100);
          return {
            ...item,
            department: match!.department,
            division: match!.division,
            section: match!.section,
            tax_percent: taxRate,
            taxable_amount: taxable,
            tax_amount: tax,
            total_price: taxable + tax
          };
        })
      );
    } else {
      // If user typed a brand new department not in hierarchy yet
      if (deptName) {
        setAutoDivision('Readymade');
        setAutoSection('General');
        setAutoTaxType('Readymade');
      }
    }
  };

  const toggleBranchSelection = (branchName: string) => {
    let newBranches: string[];
    if (selectedBranches.includes(branchName)) {
      if (selectedBranches.length === 1) {
        alert('At least one branch / warehouse must be selected');
        return;
      }
      newBranches = selectedBranches.filter(b => b !== branchName);
    } else {
      newBranches = [...selectedBranches, branchName];
    }
    setSelectedBranches(newBranches);

    // Sync to line items that haven't been manually diverged
    const newLocStr = newBranches.join(', ');
    setLineItems(prev => prev.map(item => ({
      ...item,
      location: item.location ? item.location : newLocStr
    })));
  };

  const handleItemDepartmentChange = (index: number, deptName: string) => {
    const trimmedDept = deptName.trim().toLowerCase();
    const match = hierarchyList.find(h => h.department.trim().toLowerCase() === trimmedDept);

    const div = match ? match.division : (lineItems[index]?.division || autoDivision || 'Readymade');
    const sec = match ? match.section : (lineItems[index]?.section || autoSection || 'Main Store');
    const typ = match ? match.type : (autoTaxType || 'Readymade');

    updateLineItem(index, {
      department: deptName,
      division: div,
      section: sec
    });
  };

  const toggleItemBranch = (index: number, branchName: string) => {
    const item = lineItems[index];
    if (!item) return;
    const currentLocs = (item.location || '').split(', ').map(s => s.trim()).filter(Boolean);
    let nextLocs: string[];
    if (currentLocs.includes(branchName)) {
      if (currentLocs.length <= 1) {
        alert('Item must have at least one branch assigned');
        return;
      }
      nextLocs = currentLocs.filter(b => b !== branchName);
    } else {
      nextLocs = [...currentLocs, branchName];
    }
    updateLineItem(index, { location: nextLocs.join(', ') });
  };

  const selectAllBranches = () => {
    const allNames = locations.map(l => l.name);
    if (allNames.length > 0) {
      setSelectedBranches(allNames);
    }
  };

  // Line item camera & photo upload state
  const [activeItemCameraIndex, setActiveItemCameraIndex] = useState<number | null>(null);
  const [uploadingItemIndex, setUploadingItemIndex] = useState<number | null>(null);
  const itemFileInputRef = useRef<HTMLInputElement | null>(null);
  const [targetUploadIndex, setTargetUploadIndex] = useState<number | null>(null);

  // Full-size image preview viewer modal
  const [viewingImageModalUrl, setViewingImageModalUrl] = useState<{ url: string; title: string } | null>(null);

  // WhatsApp phone confirmation dialog state
  const [whatsAppModalPO, setWhatsAppModalPO] = useState<PurchaseOrder | null>(null);
  const [whatsAppPhoneInput, setWhatsAppPhoneInput] = useState('');
  const [isSharingWhatsApp, setIsSharingWhatsApp] = useState(false);

  const handleOpenWhatsAppShare = (po: PurchaseOrder) => {
    const supp = suppliers.find(s => s.id === po.supplier_id || s.name.toLowerCase() === po.supplier_name.toLowerCase());
    setWhatsAppPhoneInput(supp?.phone || '');
    setWhatsAppModalPO(po);
  };

  const handleConfirmWhatsAppShare = async () => {
    if (!whatsAppModalPO) return;
    setIsSharingWhatsApp(true);
    try {
      const supp = suppliers.find(s => s.id === whatsAppModalPO.supplier_id || s.name.toLowerCase() === whatsAppModalPO.supplier_name.toLowerCase());
      await sharePurchaseOrderOnWhatsApp(whatsAppModalPO, supp, companyProfile, whatsAppPhoneInput);
      setWhatsAppModalPO(null);
    } catch (err) {
      console.error('Error sharing on WhatsApp:', err);
    } finally {
      setIsSharingWhatsApp(false);
    }
  };

  const handleQuickDownloadPDF = async (po: PurchaseOrder) => {
    const supp = suppliers.find(s => s.id === po.supplier_id || s.name.toLowerCase() === po.supplier_name.toLowerCase());
    const { downloadPurchaseOrderPDF } = await import('../lib/pdfGenerator');
    await downloadPurchaseOrderPDF(po, supp, companyProfile);
  };

  // Line items for Create PO
  const [lineItems, setLineItems] = useState<Array<PurchaseOrderItem>>([
    {
      item_sku: 'ITEM-001',
      item_name: 'Cotton Fabric Roll',
      quantity_ordered: 10,
      quantity_received: 0,
      unit_price: 150.0,
      tax_percent: 5.0,
      taxable_amount: 1500.0,
      tax_amount: 75.0,
      total_price: 1575.0,
      location: 'Central Warehouse',
      division: 'Readymade',
      section: 'Main Store',
      department: 'General',
      c1_brand: '',
      c2_size: '',
      c3_design: '',
      c4_colour: ''
    }
  ]);

  const addLineItem = () => {
    const defaultDept = selectedDepartment || 'General';
    const defaultDiv = autoDivision || 'Readymade';
    const defaultSec = autoSection || 'Main Store';
    const defaultType = autoTaxType || 'Readymade';
    const defaultLoc = selectedBranches.join(', ') || 'Central Warehouse';

    const defaultTaxResult = calculateTaxRate({
      itemName: '',
      category: defaultDiv,
      division: defaultDiv,
      department: defaultDept,
      section: defaultSec,
      type: defaultType,
      unitCost: 0,
      customRules: taxRules
    });

    setLineItems([
      ...lineItems,
      {
        item_sku: `ITEM-${String(lineItems.length + 1).padStart(3, '0')}`,
        item_name: '',
        quantity_ordered: 1,
        quantity_received: 0,
        unit_price: 0,
        tax_percent: defaultTaxResult.rate,
        taxable_amount: 0,
        tax_amount: 0,
        total_price: 0,
        location: defaultLoc,
        department: defaultDept,
        division: defaultDiv,
        section: defaultSec,
        c1_brand: '',
        c2_size: '',
        c3_design: '',
        c4_colour: ''
      }
    ]);
  };

  const [customTaxOverrides, setCustomTaxOverrides] = useState<Record<number, boolean>>({});

  const updateLineItem = (index: number, updates: Partial<PurchaseOrderItem>, isManualTaxChange = false) => {
    const updated = [...lineItems];
    const current = { ...updated[index], ...updates };

    if (isManualTaxChange) {
      setCustomTaxOverrides((prev) => ({ ...prev, [index]: true }));
    } else if (!customTaxOverrides[index]) {
      // Automatic tax calculation on description or price change
      const calcResult = calculateTaxRate({
        itemName: current.item_name,
        category: current.division,
        division: current.division,
        department: current.department,
        section: current.section,
        unitCost: Number(current.unit_price) || 0,
        customRules: taxRules
      });
      current.tax_percent = calcResult.rate;
    }

    const qty = Number(current.quantity_ordered) || 0;
    const price = Number(current.unit_price) || 0;
    const taxRate = Number(current.tax_percent) || 0;

    const taxable = qty * price;
    const tax = taxable * (taxRate / 100);
    const total = taxable + tax;

    current.taxable_amount = taxable;
    current.tax_amount = tax;
    current.total_price = total;

    updated[index] = current;
    setLineItems(updated);
  };

  const handleSelectInventoryItem = (index: number, invItem: InventoryItem) => {
    const unitPrice = invItem.unit_cost || 0;
    const calcResult = calculateTaxRate({
      itemName: invItem.name,
      category: invItem.category || invItem.type || invItem.division,
      type: invItem.type || (invItem.category === 'Textile' ? 'Textile' : 'Readymade'),
      division: invItem.division,
      department: invItem.department,
      section: invItem.section,
      unitCost: unitPrice,
      customRules: taxRules
    });

    setCustomTaxOverrides((prev) => ({ ...prev, [index]: false }));

    updateLineItem(index, {
      inventory_item_id: invItem.id,
      item_sku: invItem.sku || `ITEM-${String(index + 1).padStart(3, '0')}`,
      item_name: invItem.name,
      unit_price: unitPrice,
      tax_percent: calcResult.rate,
      photo_uri: invItem.photo_uri || undefined,
      division: invItem.division || 'Readymade',
      section: invItem.section || '',
      department: invItem.department || '',
      c1_brand: invItem.category1 || '',
      c2_size: invItem.category5 || '',
      c3_design: invItem.category4 || '',
      c4_colour: invItem.category6 || ''
    });
  };

  const removeLineItem = (index: number) => {
    if (lineItems.length > 1) {
      setLineItems(lineItems.filter((_, i) => i !== index));
      setCustomTaxOverrides((prev) => {
        const next = { ...prev };
        delete next[index];
        return next;
      });
    }
  };

  const handleCaptureItemPhoto = async (index: number, file: File, previewUrl?: string) => {
    // Show immediate preview if available so the user sees their photo instantly
    if (previewUrl) {
      updateLineItem(index, { photo_uri: previewUrl });
    }
    setUploadingItemIndex(index);
    try {
      const uploadedUrl = await uploadPoAttachment(file, `dress_${index + 1}`);
      if (uploadedUrl) {
        updateLineItem(index, { photo_uri: uploadedUrl });
      } else {
        // Fallback to local optimized base64 if upload returned null
        const fallbackDataUrl = await fileToOptimizedDataUrl(file);
        updateLineItem(index, { photo_uri: fallbackDataUrl });
      }
    } catch (err: any) {
      console.error('Error uploading line item photo, applying fallback:', err);
      try {
        const fallbackDataUrl = await fileToOptimizedDataUrl(file);
        updateLineItem(index, { photo_uri: fallbackDataUrl });
      } catch (fallbackErr) {
        alert('Error processing item photo.');
      }
    } finally {
      setUploadingItemIndex(null);
    }
  };

  const handleTriggerItemFileInput = (index: number) => {
    setTargetUploadIndex(index);
    if (itemFileInputRef.current) {
      itemFileInputRef.current.value = '';
      itemFileInputRef.current.click();
    }
  };

  const handleItemFileSelected = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file && targetUploadIndex !== null) {
      const previewUrl = URL.createObjectURL(file);
      await handleCaptureItemPhoto(targetUploadIndex, file, previewUrl);
    }
    setTargetUploadIndex(null);
  };

  const calculateSubtotal = () => lineItems.reduce((sum, item) => sum + item.taxable_amount, 0);
  const calculateTotalTax = () => lineItems.reduce((sum, item) => sum + item.tax_amount, 0);
  const calculateGrandTotal = () => lineItems.reduce((sum, item) => sum + item.total_price, 0);

  const handleCreatePO = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!supplierId) {
      alert('Please select a supplier');
      return;
    }
    const invalidItemIndex = lineItems.findIndex(
      item => !item.item_name || !item.item_name.trim() || Number(item.quantity_ordered) <= 0
    );
    if (invalidItemIndex !== -1) {
      alert(`Item #${invalidItemIndex + 1} is missing an "Item Description". Please enter a name or description for each product line item.`);
      return;
    }

    setIsSubmitting(true);
    try {
      const selectedSupplier = suppliers.find(s => s.id === Number(supplierId));
      const supplierName = selectedSupplier?.name || 'Selected Vendor';

      // Generate PO Number with format PO-DDMMYY-001 (auto-incrementing per day)
      const now = new Date();
      const dd = String(now.getDate()).padStart(2, '0');
      const mm = String(now.getMonth() + 1).padStart(2, '0');
      const yy = String(now.getFullYear()).slice(-2);
      const prefix = `PO-${dd}${mm}${yy}-`;

      let nextSeq = 1;
      try {
        const { data: existingPOs } = await supabase
          .from('purchase_orders')
          .select('po_number')
          .like('po_number', `${prefix}%`);

        if (existingPOs && existingPOs.length > 0) {
          const numbers = existingPOs.map(p => {
            const parts = (p.po_number || '').split('-');
            const seqStr = parts[parts.length - 1];
            const parsed = parseInt(seqStr, 10);
            return isNaN(parsed) ? 0 : parsed;
          });
          nextSeq = Math.max(...numbers, 0) + 1;
        }
      } catch (err) {
        console.warn('Could not query existing PO numbers, defaulting to sequence 1', err);
      }

      const poNumber = `${prefix}${String(nextSeq).padStart(3, '0')}`;
      const orderDateMillis = Date.now();
      const expectedDeliveryMillis = new Date(deliveryDate).getTime();

      const subtotal = calculateSubtotal();
      const totalTax = calculateTotalTax();
      const grandTotal = calculateGrandTotal();

      // 1. Insert Purchase Order
      const finalBranches = selectedBranches.length > 0 ? selectedBranches.join(', ') : 'Central Warehouse';
      const poPayload: Record<string, any> = {
        po_number: poNumber,
        supplier_id: Number(supplierId),
        supplier_name: supplierName,
        order_date_millis: orderDateMillis,
        expected_delivery_date_millis: expectedDeliveryMillis,
        payment_terms: '',
        status: 'ISSUED',
        subtotal: subtotal,
        tax_amount: totalTax,
        total_amount: grandTotal,
        notes: notes,
        photo_uri: null,
        location: finalBranches,
        created_by: currentUser?.full_name || currentUser?.username || 'Web Portal'
      };

      let { data: poData, error: poError } = await supabase
        .from('purchase_orders')
        .insert(poPayload)
        .select()
        .single();

      if (poError || !poData) {
        throw new Error(poError?.message || 'Failed to insert purchase order');
      }

      // 2. Insert Line Items
      const itemsToInsert = lineItems.map((item, idx) => ({
        po_id: poData.id,
        inventory_item_id: item.inventory_item_id || null,
        item_sku: item.item_sku || `ITEM-${idx + 1}`,
        item_name: item.item_name,
        quantity_ordered: item.quantity_ordered,
        quantity_received: 0,
        unit_price: item.unit_price,
        tax_percent: item.tax_percent,
        taxable_amount: item.taxable_amount,
        tax_amount: item.tax_amount,
        total_price: item.total_price,
        photo_uri: item.photo_uri || null,
        location: item.location || finalBranches,
        division: item.division || autoDivision || 'Readymade',
        section: item.section || autoSection || 'Main Store',
        department: item.department || selectedDepartment || 'General',
        c1_brand: item.c1_brand || '',
        c2_size: item.c2_size || '',
        c3_design: item.c3_design || '',
        c4_colour: item.c4_colour || ''
      }));

      const { error: itemsError } = await supabase
        .from('po_items')
        .insert(itemsToInsert);

      if (itemsError) {
        console.error('Error inserting PO items:', itemsError);
      }

      // 3. Insert Tax Breakdowns
      const taxSlabs: { [rate: number]: { taxable: number; tax: number } } = {};
      lineItems.forEach(item => {
        const rate = item.tax_percent;
        if (!taxSlabs[rate]) {
          taxSlabs[rate] = { taxable: 0, tax: 0 };
        }
        taxSlabs[rate].taxable += item.taxable_amount;
        taxSlabs[rate].tax += item.tax_amount;
      });

      const taxRows = Object.entries(taxSlabs).map(([rateStr, val]) => {
        const rate = Number(rateStr);
        const halfRate = rate / 2;
        const halfTax = val.tax / 2;
        return {
          po_id: poData.id,
          gst_percent: rate,
          taxable_amount: val.taxable,
          cgst_percent: halfRate,
          cgst_amount: halfTax,
          sgst_percent: halfRate,
          sgst_amount: halfTax,
          total_tax_amount: val.tax
        };
      });

      if (taxRows.length > 0) {
        await supabase.from('po_tax_breakdowns').insert(taxRows);
      }

      setIsCreateOpen(false);
      onRefresh();
    } catch (err: any) {
      alert(`Error creating PO: ${err.message}`);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDeletePO = async (poId: number) => {
    if (!currentUser?.can_delete_po && !currentUser?.is_admin) {
      alert('You do not have permission to delete purchase orders.');
      return;
    }
    if (confirm('Are you sure you want to delete this purchase order?')) {
      await supabase.from('purchase_orders').delete().eq('id', poId);
      if (selectedPOForView?.id === poId) {
        setSelectedPOForView(null);
      }
      onRefresh();
    }
  };

  // Extract unique filter options for PO list
  const poSupplierOptions = Array.from(new Set(orders.map(o => o.supplier_name).filter(Boolean))).sort();
  const poDepartmentOptions = Array.from(new Set(
    orders.flatMap(o => (o.items || []).map(i => i.department).concat([o.department || ''])).filter(Boolean)
  )).sort();

  // Filtered orders with Slicers
  const filteredOrders = orders.filter(po => {
    const matchesSearch = po.po_number.toLowerCase().includes(searchQuery.toLowerCase()) ||
      po.supplier_name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (po.notes && po.notes.toLowerCase().includes(searchQuery.toLowerCase())) ||
      (po.items || []).some(i => i.item_name.toLowerCase().includes(searchQuery.toLowerCase()));
    
    const matchesStatus = statusFilter === 'ALL' || po.status === statusFilter;

    const matchesSupplier = selectedSupplierFilter === 'ALL' || po.supplier_name === selectedSupplierFilter;

    const matchesDept = selectedDepartmentFilter === 'ALL' || 
      (po.department || '').toLowerCase() === selectedDepartmentFilter.toLowerCase() ||
      (po.items || []).some(i => (i.department || '').toLowerCase() === selectedDepartmentFilter.toLowerCase());

    let matchesDate = true;
    if (selectedDateFilter) {
      const orderDateIso = new Date(Number(po.order_date_millis)).toISOString().split('T')[0];
      const createdDateIso = po.created_at ? new Date(po.created_at).toISOString().split('T')[0] : '';
      matchesDate = orderDateIso === selectedDateFilter || createdDateIso === selectedDateFilter;
    }

    return matchesSearch && matchesStatus && matchesSupplier && matchesDept && matchesDate;
  });

  const clearPOFilters = () => {
    setSearchQuery('');
    setStatusFilter('ALL');
    setSelectedSupplierFilter('ALL');
    setSelectedDepartmentFilter('ALL');
    setSelectedDateFilter('');
  };

  const hasActivePOFilters = searchQuery || statusFilter !== 'ALL' || selectedSupplierFilter !== 'ALL' || selectedDepartmentFilter !== 'ALL' || selectedDateFilter;

  return (
    <div className="space-y-6">
      {/* Header & Search */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl shadow-sm border border-slate-200/80">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Purchase Orders</h1>
          <p className="text-sm text-slate-500 mt-1">
            Manage, approve, print, and track all supplier purchase orders
          </p>
        </div>
        <button
          onClick={() => setIsCreateOpen(true)}
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-sm shadow-sm shadow-indigo-600/30 transition-all hover:scale-[1.02]"
        >
          <Plus className="w-4 h-4" />
          Create New Purchase Order
        </button>
      </div>

      {/* Filter Tabs, Slicers & Search Bar */}
      <div className="bg-white p-4 rounded-2xl shadow-sm border border-slate-200/80 space-y-3">
        {/* Status Pills & Reset */}
        <div className="flex flex-col md:flex-row items-start md:items-center justify-between gap-3 pb-2 border-b border-slate-100">
          <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar w-full md:w-auto pb-1">
            {['ALL', 'ISSUED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'DRAFT', 'CANCELLED'].map(status => (
              <button
                key={status}
                onClick={() => setStatusFilter(status)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition-all ${
                  statusFilter === status
                    ? 'bg-slate-900 text-white shadow-sm'
                    : 'bg-slate-50 text-slate-600 hover:bg-slate-100 border border-slate-200/80'
                }`}
              >
                {status === 'ALL' ? 'All Orders' : status.replace('_', ' ')}
              </button>
            ))}
          </div>

          {hasActivePOFilters && (
            <button
              onClick={clearPOFilters}
              className="text-xs font-semibold text-rose-600 hover:text-rose-700 hover:underline flex items-center gap-1 whitespace-nowrap self-end md:self-auto"
            >
              <X className="w-3.5 h-3.5" /> Reset Slicers
            </button>
          )}
        </div>

        {/* 4 Interactive Slicers */}
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3">
          {/* Slicer 1: Search Keywords */}
          <div>
            <label className="block text-[11px] font-bold text-slate-600 mb-1">Search Keywords</label>
            <div className="relative">
              <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="PO#, supplier, items..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-8 pr-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
          </div>

          {/* Slicer 2: Order Date */}
          <div>
            <label className="block text-[11px] font-bold text-slate-600 mb-1 flex items-center gap-1">
              <Calendar className="w-3 h-3 text-indigo-600" /> Order Date
            </label>
            <input
              type="date"
              value={selectedDateFilter}
              onChange={(e) => setSelectedDateFilter(e.target.value)}
              className="w-full px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500 text-slate-800"
            />
          </div>

          {/* Slicer 3: Supplier Slicer */}
          <div>
            <label className="block text-[11px] font-bold text-slate-600 mb-1 flex items-center gap-1">
              <Building className="w-3 h-3 text-indigo-600" /> Supplier Slicer
            </label>
            <select
              value={selectedSupplierFilter}
              onChange={(e) => setSelectedSupplierFilter(e.target.value)}
              className="w-full px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500 text-slate-800"
            >
              <option value="ALL">All Suppliers ({poSupplierOptions.length})</option>
              {poSupplierOptions.map(s => (
                <option key={s} value={s}>{s}</option>
              ))}
            </select>
          </div>

          {/* Slicer 4: Department Slicer */}
          <div>
            <label className="block text-[11px] font-bold text-slate-600 mb-1 flex items-center gap-1">
              <Layers className="w-3 h-3 text-indigo-600" /> Department Slicer
            </label>
            <select
              value={selectedDepartmentFilter}
              onChange={(e) => setSelectedDepartmentFilter(e.target.value)}
              className="w-full px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500 text-slate-800"
            >
              <option value="ALL">All Departments ({poDepartmentOptions.length})</option>
              {poDepartmentOptions.map(d => (
                <option key={d} value={d}>{d}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Orders Table */}
      <div className="bg-white rounded-2xl shadow-sm border border-slate-200/80 overflow-hidden">
        {filteredOrders.length === 0 ? (
          <div className="py-16 text-center text-slate-400">
            <FileText className="w-12 h-12 mx-auto text-slate-300 mb-3" />
            <p className="text-base font-semibold text-slate-700">No purchase orders found</p>
            <p className="text-xs text-slate-400 mt-1">Try clearing your filters or create a new order</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 border-b border-slate-200 text-xs uppercase font-bold text-slate-500">
                <tr>
                  <th className="py-3.5 px-4">PO Number</th>
                  <th className="py-3.5 px-4">Supplier</th>
                  <th className="py-3.5 px-4">Order Date</th>
                  <th className="py-3.5 px-4">Expected Delivery</th>
                  <th className="py-3.5 px-4">Status</th>
                  <th className="py-3.5 px-4 text-right">Tax</th>
                  <th className="py-3.5 px-4 text-right">Total Amount</th>
                  <th className="py-3.5 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {filteredOrders.map(order => {
                  const orderDate = new Date(Number(order.order_date_millis)).toLocaleDateString('en-IN');
                  const deliveryDate = new Date(Number(order.expected_delivery_date_millis)).toLocaleDateString('en-IN');

                  return (
                    <tr key={order.id} className="hover:bg-slate-50/70 transition-colors">
                      <td className="py-3.5 px-4 font-bold text-indigo-600">
                        {order.po_number}
                      </td>
                      <td className="py-3.5 px-4 font-medium text-slate-800">
                        {order.supplier_name}
                      </td>
                      <td className="py-3.5 px-4 text-slate-500 text-xs">
                        {orderDate}
                      </td>
                      <td className="py-3.5 px-4 text-slate-500 text-xs">
                        {deliveryDate}
                      </td>
                      <td className="py-3.5 px-4">
                        <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${
                          order.status === 'ISSUED'
                            ? 'bg-amber-100 text-amber-800 border border-amber-200'
                            : order.status === 'PARTIALLY_RECEIVED'
                            ? 'bg-blue-100 text-blue-800 border border-blue-200'
                            : order.status === 'RECEIVED'
                            ? 'bg-emerald-100 text-emerald-800 border border-emerald-200'
                            : 'bg-slate-100 text-slate-700'
                        }`}>
                          {order.status.replace('_', ' ')}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-right text-slate-500 text-xs">
                        ₹{Number(order.tax_amount || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })}
                      </td>
                      <td className="py-3.5 px-4 text-right font-extrabold text-slate-900">
                        ₹{Number(order.total_amount || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })}
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <div className="flex items-center justify-end gap-1.5">
                          <button
                            onClick={() => handleOpenWhatsAppShare(order)}
                            title="Send PO on WhatsApp"
                            className="p-1.5 text-emerald-600 hover:text-emerald-700 hover:bg-emerald-50 rounded-lg transition-colors"
                          >
                            <MessageCircle className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => handleQuickDownloadPDF(order)}
                            title="Download PDF"
                            className="p-1.5 text-blue-600 hover:text-blue-700 hover:bg-blue-50 rounded-lg transition-colors"
                          >
                            <Download className="w-4 h-4" />
                          </button>
                          <button
                            onClick={() => setSelectedPOForView(order)}
                            title="View / Print PO"
                            className="p-1.5 text-slate-600 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors"
                          >
                            <Eye className="w-4 h-4" />
                          </button>
                          {(currentUser?.is_admin || currentUser?.can_delete_po) && (
                            <button
                              onClick={() => handleDeletePO(order.id)}
                              title="Delete PO"
                              className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors"
                            >
                              <Trash2 className="w-4 h-4" />
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* CREATE PURCHASE ORDER MODAL */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm overflow-y-auto p-2 sm:p-4">
          <div className="min-h-full flex items-center justify-center py-6 sm:py-8">
            <div className="bg-white rounded-3xl max-w-4xl w-full p-4 sm:p-6 shadow-2xl border border-slate-100 my-auto">
              <div className="flex items-center justify-between pb-4 border-b border-slate-100">
              <div>
                <h2 className="text-xl font-bold text-slate-900">Create New Purchase Order</h2>
                <p className="text-xs text-slate-500">Draft PO and line items will instantly sync with Supabase and Mobile App</p>
              </div>
              <button
                onClick={() => setIsCreateOpen(false)}
                className="p-2 text-slate-400 hover:text-slate-600 rounded-xl"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form
              onSubmit={(e) => {
                e.preventDefault();
              }}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && (e.target as HTMLElement).tagName !== 'TEXTAREA') {
                  e.preventDefault();
                }
              }}
              className="mt-5 space-y-6"
            >
              {/* Order of Input: Step 1 (Supplier), Step 2 (Department with auto Division & Section), Step 3 (Multi-Branch Selection) */}
              <div className="bg-slate-50/70 p-4 rounded-2xl border border-slate-200/80 space-y-4">
                <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
                  {/* Step 1: Supplier / Vendor */}
                  <div>
                    <div className="flex items-center justify-between mb-1">
                      <label className="text-xs font-bold text-slate-900 flex items-center gap-1.5">
                        <span className="w-4 h-4 rounded-full bg-indigo-600 text-white inline-flex items-center justify-center text-[10px]">1</span>
                        Supplier / Vendor *
                      </label>
                      <button
                        type="button"
                        onClick={() => setShowQuickAddSupplier(prev => !prev)}
                        className="text-[11px] font-semibold text-indigo-600 hover:text-indigo-700 hover:underline flex items-center gap-0.5"
                      >
                        <Plus className="w-3 h-3" />
                        {showQuickAddSupplier ? 'Cancel' : 'New Vendor'}
                      </button>
                    </div>

                    {showQuickAddSupplier ? (
                      <div className="p-2.5 bg-indigo-50/70 border border-indigo-200 rounded-xl space-y-2">
                        <div className="flex items-center justify-between">
                          <span className="text-[11px] font-bold text-indigo-900">Quick Add Supplier</span>
                          <button
                            type="button"
                            onClick={() => setShowQuickAddSupplier(false)}
                            className="text-slate-400 hover:text-slate-600"
                          >
                            <X className="w-3.5 h-3.5" />
                          </button>
                        </div>
                        <input
                          type="text"
                          placeholder="Supplier Name *"
                          value={quickSuppName}
                          onChange={(e) => setQuickSuppName(e.target.value)}
                          className="w-full px-2.5 py-1.5 bg-white border border-slate-300 rounded-lg text-xs font-semibold text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                        />
                        <div className="grid grid-cols-2 gap-1.5">
                          <input
                            type="text"
                            placeholder="Phone (optional)"
                            value={quickSuppPhone}
                            onChange={(e) => setQuickSuppPhone(e.target.value)}
                            className="w-full px-2.5 py-1.5 bg-white border border-slate-300 rounded-lg text-xs font-semibold text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                          />
                          <input
                            type="text"
                            placeholder="GSTIN (optional)"
                            value={quickSuppGst}
                            onChange={(e) => setQuickSuppGst(e.target.value)}
                            className="w-full px-2.5 py-1.5 bg-white border border-slate-300 rounded-lg text-xs font-semibold text-slate-900 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                          />
                        </div>
                        <button
                          type="button"
                          onClick={handleQuickAddSupplier}
                          disabled={!quickSuppName.trim() || isSavingSupplier}
                          className="w-full py-1.5 bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 text-white rounded-lg text-xs font-bold transition-colors"
                        >
                          {isSavingSupplier ? 'Saving...' : 'Add & Select Supplier'}
                        </button>
                      </div>
                    ) : (
                      <>
                        <SearchableSupplierSelect
                          suppliers={suppliers}
                          value={supplierId}
                          onChange={(id) => setSupplierId(id)}
                          required
                        />

                        {suppliers.length === 0 && (
                          <div className="mt-1.5 flex items-center justify-between text-xs text-amber-700 bg-amber-50 p-2 rounded-lg border border-amber-200">
                            <span>No suppliers loaded.</span>
                            <div className="flex items-center gap-2">
                              <button
                                type="button"
                                onClick={handleManualRefreshSuppliers}
                                disabled={isRefreshingSuppliers}
                                className="font-bold text-amber-800 underline inline-flex items-center gap-1"
                              >
                                <RefreshCw className={`w-3 h-3 ${isRefreshingSuppliers ? 'animate-spin' : ''}`} />
                                Reload
                              </button>
                              <button
                                type="button"
                                onClick={() => setShowQuickAddSupplier(true)}
                                className="font-bold text-indigo-700 underline"
                              >
                                + Add One
                              </button>
                            </div>
                          </div>
                        )}
                      </>
                    )}
                  </div>

                  {/* Step 2: Department (Auto-inserts Division & Section!) */}
                  <div>
                    <label className="block text-xs font-bold text-slate-800 mb-1 flex items-center gap-1.5">
                      <span className="w-4 h-4 rounded-full bg-indigo-600 text-white inline-flex items-center justify-center text-[10px]">2</span>
                      Department * (Auto-Hierarchy)
                    </label>
                    <SearchableDepartmentInput
                      options={Array.from(
                        new Set(
                          hierarchyList
                            .map((h) => h.department)
                            .concat(inventory.map((i) => i.department).filter(Boolean))
                        )
                      )}
                      value={selectedDepartment}
                      onChange={(dept) => handleDepartmentChange(dept)}
                      placeholder="Type or select Department..."
                      required
                    />

                    {/* Auto-populated Hierarchy badge */}
                    <div className="mt-1.5 flex items-center gap-1.5 flex-wrap">
                      {autoDivision || autoSection ? (
                        <div className="inline-flex items-center gap-1 px-2 py-0.5 rounded-lg bg-emerald-50 text-emerald-800 border border-emerald-200 text-[11px] font-bold">
                          <Sparkles className="w-3 h-3 text-emerald-600" />
                          <span>Auto: {autoDivision || 'MENS'} &gt; {autoSection || 'FORMALS'} ({autoTaxType})</span>
                        </div>
                      ) : (
                        <span className="text-[11px] text-slate-400">
                          ⚡ Enter department &rarr; Division, Section &amp; Tax auto-fill!
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Expected Delivery Date */}
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">Expected Delivery *</label>
                    <input
                      type="date"
                      value={deliveryDate}
                      onChange={(e) => setDeliveryDate(e.target.value)}
                      className="w-full px-3 py-2 bg-white border border-slate-300 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 shadow-2xs"
                      required
                    />
                  </div>
                </div>

                {/* Step 3: Multi-Branch Selection */}
                <div className="pt-2 border-t border-slate-200/60">
                  <div className="flex items-center justify-between mb-2">
                    <label className="text-xs font-bold text-slate-800 flex items-center gap-1.5">
                      <span className="w-4 h-4 rounded-full bg-indigo-600 text-white inline-flex items-center justify-center text-[10px]">3</span>
                      Destination Branches (Multi-Branch Selection) *
                    </label>
                    <div className="flex items-center gap-2">
                      <button
                        type="button"
                        onClick={selectAllBranches}
                        className="text-[11px] text-indigo-600 font-semibold hover:underline"
                      >
                        Select All
                      </button>
                      <span className="text-slate-300">|</span>
                      <span className="text-[11px] text-slate-500 font-medium">
                        {selectedBranches.length} branch(es) chosen
                      </span>
                    </div>
                  </div>

                  {/* Multi-Branch Checkboxes / Selection Pills */}
                  <div className="flex flex-wrap gap-2">
                    {locations.map(loc => {
                      const isSelected = selectedBranches.includes(loc.name);
                      return (
                        <button
                          type="button"
                          key={loc.id}
                          onClick={() => toggleBranchSelection(loc.name)}
                          className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold border transition-all ${
                            isSelected
                              ? 'bg-indigo-600 text-white border-indigo-600 shadow-xs'
                              : 'bg-white text-slate-700 border-slate-300 hover:bg-slate-50'
                          }`}
                        >
                          {isSelected ? (
                            <CheckSquare className="w-3.5 h-3.5 text-white" />
                          ) : (
                            <Square className="w-3.5 h-3.5 text-slate-400" />
                          )}
                          <span>{loc.name}</span>
                        </button>
                      );
                    })}
                    {locations.length === 0 && (
                      <span className="text-xs text-slate-400">Defaulting to Central Warehouse</span>
                    )}
                  </div>
                </div>
              </div>

              {/* Line Items Editor */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <h3 className="text-sm font-bold text-slate-800 flex items-center gap-1.5">
                      <span className="w-4 h-4 rounded-full bg-indigo-600 text-white inline-flex items-center justify-center text-[10px]">4</span>
                      PO Product Line Items ({lineItems.length})
                    </h3>
                    <p className="text-[11px] text-slate-500">
                      Qty &amp; Amount are mandatory. Design &amp; Size are optional. Add as many products as needed.
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={addLineItem}
                      className="inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-bold shadow-xs transition-all active:scale-[0.98]"
                    >
                      <Plus className="w-4 h-4" /> Add Product to PO
                    </button>
                  </div>
                </div>

                <div className="overflow-x-auto border border-slate-200 rounded-2xl">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-slate-50 border-b border-slate-200 font-bold text-slate-600">
                      <tr>
                        <th className="p-3 w-20">Dress Photo</th>
                        <th className="p-3 min-w-[200px]">Item Description &amp; Dept</th>
                        <th className="p-3 w-28 min-w-[110px]">Design (Opt)</th>
                        <th className="p-3 w-28 min-w-[110px]">Size (Opt)</th>
                        <th className="p-3 w-20 min-w-[70px]">Qty *</th>
                        <th className="p-3 w-32 min-w-[110px]">Amount / Price (₹) *</th>
                        <th className="p-3 w-20 min-w-[70px]">GST %</th>
                        <th className="p-3 text-right min-w-[80px]">Taxable</th>
                        <th className="p-3 text-right min-w-[90px]">Total (₹)</th>
                        <th className="p-3 w-8"></th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {lineItems.map((item, idx) => {
                        const taxBadgeInfo = getTaxFlowBadge({
                          itemName: item.item_name,
                          category: item.division || autoDivision,
                          division: item.division || autoDivision,
                          department: item.department || selectedDepartment,
                          section: item.section || autoSection,
                          unitCost: Number(item.unit_price) || 0,
                          customRules: taxRules
                        });
                        const isOverridden = !!customTaxOverrides[idx];
                        const isThisItemUploading = uploadingItemIndex === idx;

                        return (
                          <tr key={idx} className="hover:bg-slate-50/50 align-top">
                            {/* Dress / Item Photo Column */}
                            <td className="p-2">
                              {isThisItemUploading ? (
                                <div className="w-14 h-14 rounded-xl border border-dashed border-indigo-300 bg-indigo-50/50 flex flex-col items-center justify-center text-indigo-600 gap-1">
                                  <Loader2 className="w-4 h-4 animate-spin" />
                                  <span className="text-[9px] font-semibold">Saving...</span>
                                </div>
                              ) : item.photo_uri ? (
                                <div className="relative group w-14 h-14 rounded-xl overflow-hidden border border-slate-200 shadow-xs bg-slate-50">
                                  <img
                                    src={item.photo_uri}
                                    alt={item.item_name || 'Dress'}
                                    className="w-full h-full object-cover cursor-pointer hover:scale-105 transition-transform"
                                    onClick={() => setViewingImageModalUrl({ url: item.photo_uri!, title: item.item_name || `Item ${idx + 1}` })}
                                  />
                                  {/* Quick Actions Overlay */}
                                  <div className="absolute inset-0 bg-slate-900/60 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center gap-1.5 p-1">
                                    <button
                                      type="button"
                                      title="View Fullsize"
                                      onClick={() => setViewingImageModalUrl({ url: item.photo_uri!, title: item.item_name || `Item ${idx + 1}` })}
                                      className="p-1 rounded-md bg-white/90 hover:bg-white text-slate-800 shadow-xs transition-colors"
                                    >
                                      <ZoomIn className="w-3 h-3" />
                                    </button>
                                    <button
                                      type="button"
                                      title="Retake / Change Photo"
                                      onClick={() => setActiveItemCameraIndex(idx)}
                                      className="p-1 rounded-md bg-white/90 hover:bg-white text-indigo-600 shadow-xs transition-colors"
                                    >
                                      <Camera className="w-3 h-3" />
                                    </button>
                                    <button
                                      type="button"
                                      title="Remove Photo"
                                      onClick={() => updateLineItem(idx, { photo_uri: null })}
                                      className="p-1 rounded-md bg-white/90 hover:bg-white text-rose-600 shadow-xs transition-colors"
                                    >
                                      <X className="w-3 h-3" />
                                    </button>
                                  </div>
                                </div>
                              ) : (
                                <div className="flex flex-col gap-1 w-16">
                                  <button
                                    type="button"
                                    onClick={() => setActiveItemCameraIndex(idx)}
                                    title="Snap live camera photo of this dress"
                                    className="inline-flex items-center justify-center gap-1 px-1.5 py-1.5 rounded-lg bg-indigo-50 hover:bg-indigo-100 text-indigo-700 text-[10px] font-bold border border-indigo-200/80 transition-colors shadow-2xs"
                                  >
                                    <Camera className="w-3 h-3 shrink-0" />
                                    <span>Snap</span>
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => handleTriggerItemFileInput(idx)}
                                    title="Upload picture from gallery / file"
                                    className="inline-flex items-center justify-center gap-1 px-1.5 py-1 rounded-md bg-slate-50 hover:bg-slate-100 text-slate-600 text-[9px] font-medium border border-slate-200 transition-colors"
                                  >
                                    <Upload className="w-2.5 h-2.5 shrink-0" />
                                    <span>Upload</span>
                                  </button>
                                </div>
                              )}
                            </td>

                            {/* Item Name, Department Selector & Branch Destination */}
                            <td className="p-2 space-y-2">
                              <input
                                type="text"
                                placeholder="e.g. Silk Saree, Cotton Shirt, Kurta..."
                                value={item.item_name}
                                onChange={(e) => updateLineItem(idx, { item_name: e.target.value })}
                                className="w-full px-2 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-semibold focus:ring-1 focus:ring-indigo-500"
                                required
                              />

                              {/* Per-Item Department Selection */}
                              <div className="flex items-center gap-1.5 flex-wrap">
                                <span className="text-[10px] font-bold text-slate-500">Dept:</span>
                                <SearchableDepartmentInput
                                  options={Array.from(new Set(hierarchyList.map(h => h.department).filter(Boolean)))}
                                  value={item.department ?? ''}
                                  onChange={(dept) => handleItemDepartmentChange(idx, dept)}
                                  placeholder="Select or type dept..."
                                  compact
                                />
                                {(item.division || autoDivision) && (
                                  <span className="text-[10px] text-slate-400">
                                    ({item.division || autoDivision} &gt; {item.section || autoSection || 'Main'})
                                  </span>
                                )}
                              </div>

                              {/* Per-Item Destination / Branch Slicer Pills */}
                              <div className="flex items-center gap-1 flex-wrap pt-0.5">
                                <span className="text-[10px] font-bold text-slate-500">Branch:</span>
                                {locations.map(loc => {
                                  const itemBranchList = (item.location || selectedBranches.join(', ')).split(', ').map(s => s.trim());
                                  const isChecked = itemBranchList.includes(loc.name);
                                  return (
                                    <button
                                      type="button"
                                      key={loc.id}
                                      onClick={() => toggleItemBranch(idx, loc.name)}
                                      className={`px-1.5 py-0.5 rounded text-[10px] font-semibold border transition-all flex items-center gap-1 ${
                                        isChecked
                                          ? 'bg-indigo-600 text-white border-indigo-700 shadow-2xs'
                                          : 'bg-slate-50 text-slate-500 border-slate-200 hover:bg-slate-100'
                                      }`}
                                    >
                                      <span>{isChecked ? '✓' : '+'}</span>
                                      <span>{loc.name}</span>
                                    </button>
                                  );
                                })}
                                {locations.length === 0 && (
                                  <span className="text-[10px] text-slate-400">Central Warehouse</span>
                                )}
                              </div>

                              {/* Statutory Tax Flow Badge */}
                              <div className="flex items-center gap-1.5 flex-wrap pt-0.5">
                                {isOverridden ? (
                                  <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[9px] font-semibold bg-amber-50 text-amber-700 border border-amber-200">
                                    ⚙️ Manual {item.tax_percent}% GST
                                  </span>
                                ) : (
                                  <span
                                    className={`inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[9px] font-bold border shadow-xs transition-colors ${
                                      taxBadgeInfo.isTextile
                                        ? 'bg-emerald-50 text-emerald-700 border-emerald-300'
                                        : taxBadgeInfo.isHighSlab
                                        ? 'bg-rose-50 text-rose-700 border-rose-300'
                                        : 'bg-blue-50 text-blue-700 border-blue-300'
                                    }`}
                                  >
                                    🏷️ {taxBadgeInfo.badge}
                                  </span>
                                )}
                              </div>
                            </td>

                            {/* Design (Optional / Not mandatory) */}
                            <td className="p-2">
                              <input
                                type="text"
                                placeholder="Floral, Plain, Checks (Opt)"
                                value={item.c3_design || ''}
                                onChange={(e) => updateLineItem(idx, { c3_design: e.target.value })}
                                className="w-full px-2 py-1.5 bg-white border border-slate-200 rounded-lg text-xs"
                              />
                            </td>

                            {/* Size (Optional / Not mandatory) */}
                            <td className="p-2">
                              <input
                                type="text"
                                placeholder="M, L, XL, Free (Opt)"
                                value={item.c2_size || ''}
                                onChange={(e) => updateLineItem(idx, { c2_size: e.target.value })}
                                className="w-full px-2 py-1.5 bg-white border border-slate-200 rounded-lg text-xs"
                              />
                            </td>

                            {/* Qty (Mandatory) */}
                            <td className="p-2">
                              <input
                                type="number"
                                min="1"
                                value={item.quantity_ordered}
                                onChange={(e) => updateLineItem(idx, { quantity_ordered: Number(e.target.value) })}
                                className="w-full px-2 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-bold"
                                required
                              />
                            </td>

                            {/* Unit Price / Amount (Mandatory) */}
                            <td className="p-2">
                              <input
                                type="number"
                                min="0"
                                step="0.01"
                                placeholder="Amount ₹"
                                value={item.unit_price}
                                onChange={(e) => updateLineItem(idx, { unit_price: Number(e.target.value) })}
                                className="w-full px-2 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-bold"
                                required
                              />
                            </td>

                            {/* GST Rate */}
                            <td className="p-2">
                              <div className="space-y-0.5">
                                <select
                                  value={item.tax_percent}
                                  onChange={(e) => updateLineItem(idx, { tax_percent: Number(e.target.value) }, true)}
                                  className={`w-full px-1 py-1.5 bg-white border rounded-lg text-[11px] font-bold ${
                                    isOverridden ? 'border-amber-400 text-amber-900 bg-amber-50/30' : 'border-slate-200 text-slate-800'
                                  }`}
                                >
                                  <option value="0">0%</option>
                                  <option value="5">5%</option>
                                  <option value="12">12%</option>
                                  <option value="18">18%</option>
                                  <option value="28">28%</option>
                                </select>
                                {isOverridden && (
                                  <button
                                    type="button"
                                    onClick={() => {
                                      setCustomTaxOverrides(prev => {
                                        const next = { ...prev };
                                        delete next[idx];
                                        return next;
                                      });
                                      updateLineItem(idx, {});
                                    }}
                                    className="text-[9px] text-indigo-600 hover:underline block"
                                  >
                                    Auto
                                  </button>
                                )}
                              </div>
                            </td>

                            {/* Taxable */}
                            <td className="p-2 text-right font-medium text-slate-600 pt-3">
                              ₹{item.taxable_amount.toFixed(2)}
                            </td>

                            {/* Total Amount */}
                            <td className="p-2 text-right font-bold text-slate-900 pt-3">
                              ₹{item.total_price.toFixed(2)}
                            </td>

                            {/* Delete line item */}
                            <td className="p-2 text-center pt-3">
                              {lineItems.length > 1 && (
                                <button
                                  type="button"
                                  onClick={() => removeLineItem(idx)}
                                  className="text-slate-400 hover:text-rose-500"
                                >
                                  <X className="w-4 h-4" />
                                </button>
                              )}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>

                {/* Prominent "+ Add Product to PO" button under the table */}
                <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
                  <button
                    type="button"
                    onClick={addLineItem}
                    className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-bold text-xs border border-indigo-200 transition-all hover:scale-[1.01]"
                  >
                    <Plus className="w-4 h-4" />
                    <span>+ Add Another Product to this PO</span>
                  </button>
                  <span className="text-xs text-slate-400 font-medium ml-auto sm:ml-0">
                    Total Products: {lineItems.length}
                  </span>
                </div>
              </div>

              {/* Bottom: Notes & Total Summary */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-2">
                <div className="space-y-4">
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">PO Notes / Special Instructions</label>
                    <textarea
                      rows={3}
                      value={notes}
                      onChange={(e) => setNotes(e.target.value)}
                      placeholder="Add terms, packaging instructions, transport details..."
                      className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    />
                  </div>
                </div>

                {/* Calculation Summary Box */}
                <div className="bg-slate-50 p-4 rounded-2xl border border-slate-200/80 space-y-2.5">
                  <div className="flex justify-between text-xs text-slate-600">
                    <span>Taxable Subtotal:</span>
                    <span className="font-semibold">₹{calculateSubtotal().toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                  </div>

                  {/* Slabs breakdown */}
                  {(() => {
                    const taxSlabs: { [rate: number]: { taxable: number; tax: number } } = {};
                    lineItems.forEach(item => {
                      const rate = item.tax_percent;
                      if (!taxSlabs[rate]) taxSlabs[rate] = { taxable: 0, tax: 0 };
                      taxSlabs[rate].taxable += item.taxable_amount;
                      taxSlabs[rate].tax += item.tax_amount;
                    });
                    const slabs = Object.entries(taxSlabs);

                    return (
                      <div className="py-2 px-2.5 bg-white/80 rounded-xl border border-slate-200/60 space-y-1.5">
                        <div className="text-[11px] font-bold text-slate-500 uppercase tracking-wide">
                          Tax Flow (CGST + SGST Breakdown)
                        </div>
                        {slabs.map(([rateStr, val]) => {
                          const rate = Number(rateStr);
                          const halfRate = rate / 2;
                          const halfTax = val.tax / 2;
                          return (
                            <div key={rateStr} className="text-[11px] text-slate-600 space-y-0.5">
                              <div className="flex justify-between font-medium">
                                <span>GST {rate}% (on ₹{val.taxable.toFixed(2)}):</span>
                                <span className="font-bold text-slate-800">₹{val.tax.toFixed(2)}</span>
                              </div>
                              <div className="flex justify-between text-[10px] text-slate-400 pl-2">
                                <span>• CGST ({halfRate}%): ₹{halfTax.toFixed(2)}</span>
                                <span>• SGST ({halfRate}%): ₹{halfTax.toFixed(2)}</span>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    );
                  })()}

                  <div className="flex justify-between text-xs text-slate-600">
                    <span>Total GST Amount:</span>
                    <span className="font-bold text-indigo-600">₹{calculateTotalTax().toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                  </div>
                  <div className="pt-2 border-t border-slate-200 flex justify-between text-base font-extrabold text-slate-900">
                    <span>Grand Total:</span>
                    <span>₹{calculateGrandTotal().toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                  </div>
                </div>
              </div>

              {/* Submit Buttons */}
              <div className="flex flex-col-reverse sm:flex-row items-stretch sm:items-center justify-between gap-3 pt-4 border-t border-slate-200">
                <button
                  type="button"
                  onClick={addLineItem}
                  className="inline-flex items-center justify-center gap-1.5 px-4 py-2.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 font-semibold text-xs border border-slate-300 transition-all w-full sm:w-auto"
                >
                  <Plus className="w-4 h-4 text-indigo-600" />
                  <span>+ Add Product to PO</span>
                </button>
                <div className="flex flex-col-reverse sm:flex-row items-stretch sm:items-center gap-2 sm:gap-3 w-full sm:w-auto">
                  <button
                    type="button"
                    onClick={() => setIsCreateOpen(false)}
                    className="px-4 py-2.5 rounded-xl text-sm font-semibold text-slate-600 hover:bg-slate-100 text-center w-full sm:w-auto"
                  >
                    Cancel
                  </button>
                  <button
                    type="button"
                    onClick={handleCreatePO}
                    disabled={isSubmitting}
                    className="inline-flex items-center justify-center gap-2 px-6 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-sm shadow-md shadow-indigo-600/30 transition-all disabled:opacity-50 hover:scale-[1.01] w-full sm:w-auto text-center"
                  >
                    {isSubmitting ? (
                      <>
                        <Loader2 className="w-4 h-4 animate-spin" /> Creating PO...
                      </>
                    ) : (
                      <>
                        <Check className="w-4 h-4" /> 🚀 Create Purchase Order
                      </>
                    )}
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      </div>
      )}

      {/* PO DETAIL & PRINT MODAL */}
      {selectedPOForView && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm overflow-y-auto p-2 sm:p-4">
          <div className="min-h-full flex items-center justify-center py-6 sm:py-8">
            <div className="bg-white rounded-3xl max-w-3xl w-full p-4 sm:p-8 shadow-2xl border border-slate-100 my-auto">
            <div className="flex items-center justify-between pb-4 border-b border-slate-100 no-print">
              <span className="text-sm font-bold text-slate-400">PURCHASE ORDER INVOICE</span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => handleOpenWhatsAppShare(selectedPOForView)}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold shadow-sm transition-colors"
                >
                  <MessageCircle className="w-3.5 h-3.5" /> Send on WhatsApp
                </button>
                <button
                  onClick={() => handleQuickDownloadPDF(selectedPOForView)}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold shadow-sm transition-colors"
                >
                  <Download className="w-3.5 h-3.5" /> Download PDF
                </button>
                <button
                  onClick={() => window.print()}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-900 text-white text-xs font-semibold shadow-sm transition-colors"
                >
                  <Printer className="w-3.5 h-3.5" /> Print
                </button>
                <button
                  onClick={() => setSelectedPOForView(null)}
                  className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg ml-1"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            </div>

            {/* Printable Content */}
            <div className="mt-6 space-y-6">
              {/* Header */}
              <div className="flex items-start justify-between border-b pb-6">
                <div>
                  <h1 className="text-2xl font-black text-slate-900">{companyProfile?.company_name || 'PO Master'}</h1>
                  <p className="text-xs text-slate-500 mt-1">{companyProfile?.address}</p>
                  <p className="text-xs text-slate-500">{companyProfile?.phone} • {companyProfile?.email}</p>
                  {companyProfile?.gst_number && (
                    <p className="text-xs font-bold text-indigo-600 mt-1">GSTIN: {companyProfile.gst_number}</p>
                  )}
                </div>
                <div className="text-right">
                  <div className="text-xl font-black text-indigo-600">{selectedPOForView.po_number}</div>
                  <div className="text-xs text-slate-500 mt-1">
                    Date: {new Date(Number(selectedPOForView.order_date_millis)).toLocaleDateString('en-IN')}
                  </div>
                  <div className="text-xs text-slate-500">
                    Expected: {new Date(Number(selectedPOForView.expected_delivery_date_millis)).toLocaleDateString('en-IN')}
                  </div>
                  <div className="mt-2">
                    <span className="px-2 py-0.5 rounded-md text-xs font-bold bg-slate-100 text-slate-800 border">
                      {selectedPOForView.status}
                    </span>
                  </div>
                </div>
              </div>

              {/* Vendor & Delivery Box */}
              <div className="grid grid-cols-2 gap-6 bg-slate-50 p-4 rounded-xl text-xs">
                <div>
                  <div className="font-bold text-slate-400 uppercase tracking-wider mb-1">Vendor / Supplier:</div>
                  <div className="text-sm font-bold text-slate-900">{selectedPOForView.supplier_name}</div>
                  {selectedPOForView.department && (
                    <div className="mt-1.5 inline-flex items-center gap-1 px-2 py-0.5 rounded bg-indigo-50 text-indigo-700 font-semibold text-[11px] border border-indigo-200">
                      📁 Dept: {selectedPOForView.department}
                      {selectedPOForView.division && ` (${selectedPOForView.division} > ${selectedPOForView.section || 'General'})`}
                    </div>
                  )}
                </div>
                <div>
                  <div className="font-bold text-slate-400 uppercase tracking-wider mb-1">Destination Branches / Deliver To:</div>
                  <div className="text-sm font-bold text-slate-900">{selectedPOForView.location}</div>
                  <div className="text-slate-600 mt-0.5">Created By: {selectedPOForView.created_by}</div>
                  {selectedPOForView.location.includes(',') && (
                    <div className="flex flex-wrap gap-1 mt-1.5">
                      {selectedPOForView.location.split(',').map((branch, bi) => (
                        <span key={bi} className="inline-block px-1.5 py-0.5 bg-white border border-slate-200 rounded text-[10px] font-semibold text-slate-700">
                          📍 {branch.trim()}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              {/* Photo Preview if uploaded */}
              {selectedPOForView.photo_uri && (
                <div className="p-3 border rounded-xl bg-slate-50/50">
                  <div className="text-xs font-bold text-slate-500 mb-2 flex items-center gap-1">
                    <ImageIcon className="w-3.5 h-3.5" /> Sample Attachment
                  </div>
                  <img
                    src={selectedPOForView.photo_uri}
                    alt="PO Attachment"
                    className="max-h-48 rounded-lg object-contain border"
                  />
                </div>
              )}

              {/* Items Table */}
              <div>
                <table className="w-full text-left text-xs border border-slate-200 rounded-xl overflow-hidden">
                  <thead className="bg-slate-100 font-bold text-slate-700">
                    <tr>
                      <th className="p-2.5 w-14 text-center">Photo</th>
                      <th className="p-2.5">Item Description</th>
                      <th className="p-2.5 text-center">Qty</th>
                      <th className="p-2.5 text-right">Unit Price</th>
                      <th className="p-2.5 text-center">GST %</th>
                      <th className="p-2.5 text-right">Taxable</th>
                      <th className="p-2.5 text-right">GST (₹)</th>
                      <th className="p-2.5 text-right">Total</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {selectedPOForView.items && selectedPOForView.items.length > 0 ? (
                      selectedPOForView.items.map((item, idx) => {
                        const taxRate = Number(item.tax_percent) || 0;
                        const halfRate = taxRate / 2;
                        const displayPhoto = item.photo_uri || (selectedPOForView.items?.length === 1 ? selectedPOForView.photo_uri : null);
                        return (
                          <tr key={idx} className="align-middle">
                            <td className="p-2 text-center">
                              {displayPhoto ? (
                                <img
                                  src={displayPhoto}
                                  alt={item.item_name}
                                  onClick={() => setViewingImageModalUrl({ url: displayPhoto, title: item.item_name })}
                                  className="w-11 h-11 object-cover rounded-lg border border-slate-200 shadow-2xs mx-auto cursor-pointer hover:opacity-90"
                                />
                              ) : (
                                <div className="w-11 h-11 rounded-lg border border-slate-200 bg-slate-50 flex items-center justify-center text-slate-300 mx-auto">
                                  <ImageIcon className="w-4 h-4" />
                                </div>
                              )}
                            </td>
                            <td className="p-2.5">
                              <span className="font-semibold text-slate-900">{item.item_name}</span>
                              <div className="flex items-center gap-1.5 flex-wrap mt-0.5">
                                {(item.c3_design || item.c2_size) && (
                                  <span className="inline-block text-[10px] text-slate-500 font-medium">
                                    {item.c3_design && `Design: ${item.c3_design}`}
                                    {item.c3_design && item.c2_size && ' • '}
                                    {item.c2_size && `Size: ${item.c2_size}`}
                                  </span>
                                )}
                                {(item.department || item.division) && (
                                  <span className="inline-block text-[10px] text-indigo-600 bg-indigo-50 px-1 py-0.2 rounded border border-indigo-100">
                                    {item.department} {item.division ? `(${item.division})` : ''}
                                  </span>
                                )}
                              </div>
                              <span className="inline-block mt-0.5 text-[10px] text-slate-400">
                                CGST {halfRate}% + SGST {halfRate}%
                              </span>
                            </td>
                            <td className="p-2.5 text-center font-bold">{item.quantity_ordered}</td>
                            <td className="p-2.5 text-right">₹{Number(item.unit_price).toFixed(2)}</td>
                            <td className="p-2.5 text-center font-semibold text-indigo-700">{taxRate}%</td>
                            <td className="p-2.5 text-right text-slate-600">₹{Number(item.taxable_amount || 0).toFixed(2)}</td>
                            <td className="p-2.5 text-right text-indigo-600">₹{Number(item.tax_amount || 0).toFixed(2)}</td>
                            <td className="p-2.5 text-right font-bold">₹{Number(item.total_price).toFixed(2)}</td>
                          </tr>
                        );
                      })
                    ) : (
                      <tr>
                        <td className="p-2 text-center text-slate-300">
                          <ImageIcon className="w-4 h-4 mx-auto" />
                        </td>
                        <td className="p-2.5 font-medium">Standard Purchase Order Items</td>
                        <td className="p-2.5 text-center font-bold">1</td>
                        <td className="p-2.5 text-right">₹{Number(selectedPOForView.subtotal).toFixed(2)}</td>
                        <td className="p-2.5 text-center font-semibold">5%</td>
                        <td className="p-2.5 text-right">₹{Number(selectedPOForView.subtotal).toFixed(2)}</td>
                        <td className="p-2.5 text-right text-indigo-600">₹{Number(selectedPOForView.tax_amount || 0).toFixed(2)}</td>
                        <td className="p-2.5 text-right font-bold">₹{Number(selectedPOForView.total_amount).toFixed(2)}</td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>

              {/* Total Summary with Tax Flow Breakdown */}
              <div className="flex justify-end pt-2">
                <div className="w-80 space-y-2 text-xs">
                  <div className="flex justify-between text-slate-600">
                    <span>Taxable Subtotal:</span>
                    <span>₹{Number(selectedPOForView.subtotal || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                  </div>

                  {/* Statutory Tax Flow Breakdown in Printable view */}
                  {(() => {
                    const items = selectedPOForView.items || [];
                    const taxSlabs: { [rate: number]: { taxable: number; tax: number } } = {};
                    if (items.length > 0) {
                      items.forEach(it => {
                        const r = Number(it.tax_percent) || 0;
                        if (!taxSlabs[r]) taxSlabs[r] = { taxable: 0, tax: 0 };
                        taxSlabs[r].taxable += Number(it.taxable_amount) || 0;
                        taxSlabs[r].tax += Number(it.tax_amount) || 0;
                      });
                    } else if (selectedPOForView.tax_amount) {
                      taxSlabs[5] = { taxable: Number(selectedPOForView.subtotal || 0), tax: Number(selectedPOForView.tax_amount) };
                    }
                    const slabs = Object.entries(taxSlabs);

                    return (
                      <div className="p-2.5 bg-slate-50 rounded-lg border border-slate-200/70 space-y-1.5 text-[11px]">
                        <div className="font-bold text-slate-700 uppercase tracking-wider text-[10px]">
                          Statutory Tax Breakdown:
                        </div>
                        {slabs.map(([rateStr, val]) => {
                          const rate = Number(rateStr);
                          const halfRate = rate / 2;
                          const halfTax = val.tax / 2;
                          return (
                            <div key={rateStr} className="space-y-0.5 text-slate-600">
                              <div className="flex justify-between font-medium">
                                <span>GST {rate}% (Taxable: ₹{val.taxable.toFixed(2)}):</span>
                                <span className="font-bold text-slate-900">₹{val.tax.toFixed(2)}</span>
                              </div>
                              <div className="flex justify-between text-[10px] text-slate-500 pl-2">
                                <span>• CGST ({halfRate}%): ₹{halfTax.toFixed(2)}</span>
                                <span>• SGST ({halfRate}%): ₹{halfTax.toFixed(2)}</span>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                    );
                  })()}

                  <div className="flex justify-between text-slate-700 font-semibold">
                    <span>Total GST Tax Amount:</span>
                    <span className="text-indigo-600">₹{Number(selectedPOForView.tax_amount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                  </div>
                  <div className="flex justify-between text-sm font-extrabold text-slate-900 border-t pt-2">
                    <span>Grand Total:</span>
                    <span>₹{Number(selectedPOForView.total_amount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
                  </div>
                </div>
              </div>

              {/* Notes */}
              {selectedPOForView.notes && (
                <div className="text-xs text-slate-600 bg-slate-50 p-3 rounded-lg border">
                  <span className="font-bold text-slate-700 block mb-1">Notes / Instructions:</span>
                  {selectedPOForView.notes}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
      )}

      {/* Live Camera Viewfinder Dialog for Individual Dress / Line Item */}
      {activeItemCameraIndex !== null && (
        <CameraCaptureDialog
          isOpen={true}
          onClose={() => setActiveItemCameraIndex(null)}
          title={`Snap Dress Photo: ${lineItems[activeItemCameraIndex]?.item_name || `Line Item #${activeItemCameraIndex + 1}`}`}
          onPhotoCaptured={async (file, previewUrl) => {
            const idx = activeItemCameraIndex;
            setActiveItemCameraIndex(null);
            if (idx !== null) {
              await handleCaptureItemPhoto(idx, file, previewUrl);
            }
          }}
        />
      )}

      {/* Hidden file input for Line Item file uploads */}
      <input
        ref={itemFileInputRef}
        type="file"
        accept="image/*"
        onChange={handleItemFileSelected}
        className="hidden"
      />

      {/* Full-size Image Viewer Modal */}
      {viewingImageModalUrl && (
        <div 
          className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in"
          onClick={() => setViewingImageModalUrl(null)}
        >
          <div 
            className="relative max-w-3xl max-h-[90vh] bg-white rounded-3xl overflow-hidden shadow-2xl border border-slate-700/50 flex flex-col"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-5 py-3.5 border-b border-slate-100 bg-slate-50/80">
              <div className="flex items-center gap-2">
                <ImageIcon className="w-4 h-4 text-indigo-600" />
                <span className="text-sm font-bold text-slate-800">{viewingImageModalUrl.title}</span>
              </div>
              <button
                onClick={() => setViewingImageModalUrl(null)}
                className="p-1.5 text-slate-400 hover:text-slate-700 rounded-xl hover:bg-slate-200/60 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-4 flex items-center justify-center bg-slate-900/5 overflow-auto max-h-[calc(90vh-65px)]">
              <img
                src={viewingImageModalUrl.url}
                alt={viewingImageModalUrl.title}
                className="max-w-full max-h-[75vh] rounded-xl object-contain shadow-md"
              />
            </div>
          </div>
        </div>
      )}

      {/* WhatsApp Share Confirmation Modal */}
      {whatsAppModalPO && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in">
          <div className="bg-white rounded-2xl max-w-md w-full p-6 shadow-2xl border border-slate-100 space-y-4">
            <div className="flex items-center justify-between border-b border-slate-100 pb-3">
              <div className="flex items-center gap-2 text-emerald-600 font-bold text-base">
                <MessageCircle className="w-5 h-5" />
                <span>Send PO on WhatsApp</span>
              </div>
              <button
                onClick={() => setWhatsAppModalPO(null)}
                className="p-1 text-slate-400 hover:text-slate-600 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3 text-sm text-slate-600">
              <p>
                Sending Purchase Order <strong className="text-slate-900">{whatsAppModalPO.po_number}</strong> to{' '}
                <strong className="text-slate-900">{whatsAppModalPO.supplier_name}</strong>.
              </p>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Supplier WhatsApp Number (optional for Web Share / device picker):
                </label>
                <div className="relative">
                  <input
                    type="tel"
                    value={whatsAppPhoneInput}
                    onChange={(e) => setWhatsAppPhoneInput(e.target.value)}
                    placeholder="e.g. 9876543210 or +91 9876543210"
                    className="w-full px-3 py-2 rounded-xl border border-slate-200 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500 font-mono"
                  />
                </div>
                <p className="text-[11px] text-slate-400 mt-1">
                  On mobile devices, this opens native WhatsApp with the PDF document attached. On desktop, it downloads the PDF and opens WhatsApp Web with order summary pre-filled.
                </p>
              </div>

              <div className="p-3 bg-emerald-50/60 rounded-xl border border-emerald-100 text-xs text-emerald-800 space-y-1">
                <div className="font-semibold">📄 Attached Order Summary:</div>
                <div className="text-slate-600 text-[11px]">
                  • Items: {whatsAppModalPO.items?.length || 1} item(s)<br />
                  • Total Amount: ₹{Number(whatsAppModalPO.total_amount || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })}<br />
                  • Delivery: {whatsAppModalPO.location}
                </div>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-100">
              <button
                type="button"
                onClick={() => setWhatsAppModalPO(null)}
                className="px-4 py-2 rounded-xl text-xs font-semibold text-slate-600 hover:bg-slate-100"
              >
                Cancel
              </button>
              <button
                type="button"
                disabled={isSharingWhatsApp}
                onClick={handleConfirmWhatsAppShare}
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-xs shadow-md shadow-emerald-600/25 transition-all disabled:opacity-50"
              >
                {isSharingWhatsApp ? (
                  <>
                    <Loader2 className="w-4 h-4 animate-spin" /> Preparing PDF...
                  </>
                ) : (
                  <>
                    <MessageCircle className="w-4 h-4" /> Send Now
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
