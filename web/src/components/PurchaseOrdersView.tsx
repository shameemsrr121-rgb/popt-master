import React, { useState, useRef } from 'react';
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
  ZoomIn
} from 'lucide-react';
import { PurchaseOrder, PurchaseOrderItem, Supplier, Location, InventoryItem, CompanyProfile, User, TaxRule } from '../types';
import { supabase, uploadPoAttachment } from '../lib/supabase';
import { calculateTaxRate, getTaxFlowBadge } from '../lib/taxMaster';
import { CameraCaptureDialog } from './CameraCaptureDialog';

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

  // Form states for Create PO
  const [supplierId, setSupplierId] = useState<number>(suppliers[0]?.id || 0);
  const [deliveryDate, setDeliveryDate] = useState(
    new Date(Date.now() + 7 * 86400000).toISOString().split('T')[0]
  );
  const [locationName, setLocationName] = useState('Central Warehouse');
  const [notes, setNotes] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewPhotoUrl, setPreviewPhotoUrl] = useState<string | null>(null);
  const [isCameraOpen, setIsCameraOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const fileUploadInputRef = useRef<HTMLInputElement | null>(null);

  // Line item camera & photo upload state
  const [activeItemCameraIndex, setActiveItemCameraIndex] = useState<number | null>(null);
  const [uploadingItemIndex, setUploadingItemIndex] = useState<number | null>(null);
  const itemFileInputRef = useRef<HTMLInputElement | null>(null);
  const [targetUploadIndex, setTargetUploadIndex] = useState<number | null>(null);

  // Full-size image preview viewer modal
  const [viewingImageModalUrl, setViewingImageModalUrl] = useState<{ url: string; title: string } | null>(null);

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
    setLineItems([
      ...lineItems,
      {
        item_sku: `ITEM-${String(lineItems.length + 1).padStart(3, '0')}`,
        item_name: '',
        quantity_ordered: 1,
        quantity_received: 0,
        unit_price: 0,
        tax_percent: 5.0,
        taxable_amount: 0,
        tax_amount: 0,
        total_price: 0,
        location: locationName
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

  const handleCaptureItemPhoto = async (index: number, file: File) => {
    setUploadingItemIndex(index);
    try {
      const uploadedUrl = await uploadPoAttachment(file, `dress_${index + 1}`);
      if (uploadedUrl) {
        updateLineItem(index, { photo_uri: uploadedUrl });
      } else {
        alert('Failed to upload image. Please check your network connection and try again.');
      }
    } catch (err: any) {
      console.error('Error uploading line item photo:', err);
      alert('Error uploading item photo.');
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
      await handleCaptureItemPhoto(targetUploadIndex, file);
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
    if (lineItems.some(item => !item.item_name || item.quantity_ordered <= 0)) {
      alert('Please fill out all line item details with valid quantities');
      return;
    }

    setIsSubmitting(true);
    try {
      let uploadedPhotoUrl: string | null = null;
      if (selectedFile) {
        uploadedPhotoUrl = await uploadPoAttachment(selectedFile, 'po');
      }

      const selectedSupplier = suppliers.find(s => s.id === Number(supplierId));
      const supplierName = selectedSupplier?.name || 'Selected Vendor';

      const poNumber = `PO-${Date.now().toString().slice(-6)}`;
      const orderDateMillis = Date.now();
      const expectedDeliveryMillis = new Date(deliveryDate).getTime();

      const subtotal = calculateSubtotal();
      const totalTax = calculateTotalTax();
      const grandTotal = calculateGrandTotal();

      // 1. Insert Purchase Order
      const { data: poData, error: poError } = await supabase
        .from('purchase_orders')
        .insert({
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
          photo_uri: uploadedPhotoUrl,
          location: locationName,
          created_by: currentUser?.full_name || currentUser?.username || 'Web Portal'
        })
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
        location: item.location || locationName,
        division: item.division || 'Readymade',
        section: item.section || 'Main Store',
        department: item.department || 'General',
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
      setSelectedFile(null);
      setPreviewPhotoUrl(null);
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

  // Filtered orders
  const filteredOrders = orders.filter(po => {
    const matchesSearch = po.po_number.toLowerCase().includes(searchQuery.toLowerCase()) ||
      po.supplier_name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (po.notes && po.notes.toLowerCase().includes(searchQuery.toLowerCase()));
    
    const matchesStatus = statusFilter === 'ALL' || po.status === statusFilter;
    return matchesSearch && matchesStatus;
  });

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

      {/* Filter Tabs & Search Bar */}
      <div className="flex flex-col md:flex-row items-center justify-between gap-4">
        {/* Status Pills */}
        <div className="flex items-center gap-1.5 overflow-x-auto w-full md:w-auto pb-1">
          {['ALL', 'ISSUED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'DRAFT', 'CANCELLED'].map(status => (
            <button
              key={status}
              onClick={() => setStatusFilter(status)}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition-all ${
                statusFilter === status
                  ? 'bg-slate-900 text-white shadow-sm'
                  : 'bg-white text-slate-600 hover:bg-slate-100 border border-slate-200/80'
              }`}
            >
              {status === 'ALL' ? 'All Orders' : status.replace('_', ' ')}
            </button>
          ))}
        </div>

        {/* Search Input */}
        <div className="relative w-full md:w-72">
          <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400" />
          <input
            type="text"
            placeholder="Search PO#, supplier, notes..."
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="w-full pl-9 pr-4 py-2 bg-white border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 shadow-sm"
          />
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
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-3xl max-w-4xl w-full p-6 shadow-2xl border border-slate-100 my-8">
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

            <form onSubmit={handleCreatePO} className="mt-5 space-y-6">
              {/* Top metadata fields */}
              <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-4">
                {/* Supplier */}
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Supplier / Vendor *</label>
                  <select
                    value={supplierId}
                    onChange={(e) => setSupplierId(Number(e.target.value))}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    required
                  >
                    <option value="">Select Supplier</option>
                    {suppliers.map(s => (
                      <option key={s.id} value={s.id}>{s.name} ({s.code})</option>
                    ))}
                  </select>
                </div>

                {/* Delivery Date */}
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Expected Delivery *</label>
                  <input
                    type="date"
                    value={deliveryDate}
                    onChange={(e) => setDeliveryDate(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    required
                  />
                </div>

                {/* Target Location */}
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Receiving Warehouse</label>
                  <select
                    value={locationName}
                    onChange={(e) => setLocationName(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  >
                    {locations.map(loc => (
                      <option key={loc.id} value={loc.name}>{loc.name}</option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Line Items Editor */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h3 className="text-sm font-bold text-slate-800">Order Items</h3>
                  <button
                    type="button"
                    onClick={addLineItem}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-indigo-50 text-indigo-600 hover:bg-indigo-100 text-xs font-semibold"
                  >
                    <Plus className="w-3.5 h-3.5" /> Add Item
                  </button>
                </div>

                <div className="overflow-x-auto border border-slate-200 rounded-2xl">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-slate-50 border-b border-slate-200 font-bold text-slate-600">
                      <tr>
                        <th className="p-3 w-24">Dress Photo</th>
                        <th className="p-3 min-w-[220px]">Item Description &amp; Tax Flow *</th>
                        <th className="p-3 w-20">Qty *</th>
                        <th className="p-3 w-28">Unit Price (₹)</th>
                        <th className="p-3 w-24">GST %</th>
                        <th className="p-3 text-right">Taxable</th>
                        <th className="p-3 text-right">Total (₹)</th>
                        <th className="p-3 w-10"></th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-slate-100">
                      {lineItems.map((item, idx) => {
                        const taxBadgeInfo = getTaxFlowBadge({
                          itemName: item.item_name,
                          category: item.division,
                          division: item.division,
                          department: item.department,
                          section: item.section,
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
                                <div className="flex flex-col gap-1 w-20">
                                  <button
                                    type="button"
                                    onClick={() => setActiveItemCameraIndex(idx)}
                                    title="Snap live camera photo of this dress"
                                    className="inline-flex items-center justify-center gap-1 px-2 py-1.5 rounded-lg bg-indigo-50 hover:bg-indigo-100 text-indigo-700 text-[11px] font-bold border border-indigo-200/80 transition-colors shadow-2xs"
                                  >
                                    <Camera className="w-3 h-3 shrink-0" />
                                    <span>Snap</span>
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() => handleTriggerItemFileInput(idx)}
                                    title="Upload picture from gallery / file"
                                    className="inline-flex items-center justify-center gap-1 px-1.5 py-1 rounded-md bg-slate-50 hover:bg-slate-100 text-slate-600 text-[10px] font-medium border border-slate-200 transition-colors"
                                  >
                                    <Upload className="w-2.5 h-2.5 shrink-0" />
                                    <span>Upload</span>
                                  </button>
                                </div>
                              )}
                            </td>
                            <td className="p-2 space-y-1">
                              <input
                                type="text"
                                placeholder="e.g. Silk Saree, Cotton Shirt, Linen..."
                                value={item.item_name}
                                onChange={(e) => updateLineItem(idx, { item_name: e.target.value })}
                                className="w-full px-2 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-medium focus:ring-1 focus:ring-indigo-500"
                                required
                              />

                              {/* Inventory Quick Match if available */}
                              {inventory.length > 0 && !item.inventory_item_id && item.item_name.length >= 2 && (
                                <div className="flex flex-wrap gap-1 pt-0.5">
                                  {inventory
                                    .filter(inv => inv.name.toLowerCase().includes(item.item_name.toLowerCase()))
                                    .slice(0, 3)
                                    .map(inv => (
                                      <button
                                        type="button"
                                        key={inv.id}
                                        onClick={() => handleSelectInventoryItem(idx, inv)}
                                        className="text-[10px] px-1.5 py-0.5 rounded bg-indigo-50 text-indigo-700 hover:bg-indigo-100 border border-indigo-200 transition-colors"
                                      >
                                        Use: {inv.name} (₹{inv.unit_cost})
                                      </button>
                                    ))}
                                </div>
                              )}

                              {/* Statutory Tax Flow Badge */}
                              <div className="flex items-center gap-1.5 flex-wrap pt-0.5">
                                {isOverridden ? (
                                  <span className="inline-flex items-center gap-1 px-1.5 py-0.5 rounded text-[10px] font-semibold bg-amber-50 text-amber-700 border border-amber-200">
                                    ⚙️ Manual {item.tax_percent}% GST (Overridden)
                                  </span>
                                ) : (
                                  <span
                                    className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-bold border shadow-xs transition-colors ${
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
                            <td className="p-2">
                              <input
                                type="number"
                                min="1"
                                value={item.quantity_ordered}
                                onChange={(e) => updateLineItem(idx, { quantity_ordered: Number(e.target.value) })}
                                className="w-full px-2 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-semibold"
                                required
                              />
                            </td>
                            <td className="p-2">
                              <input
                                type="number"
                                min="0"
                                step="0.01"
                                value={item.unit_price}
                                onChange={(e) => updateLineItem(idx, { unit_price: Number(e.target.value) })}
                                className="w-full px-2 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-semibold"
                                required
                              />
                            </td>
                            <td className="p-2">
                              <div className="space-y-1">
                                <select
                                  value={item.tax_percent}
                                  onChange={(e) => updateLineItem(idx, { tax_percent: Number(e.target.value) }, true)}
                                  className={`w-full px-1.5 py-1.5 bg-white border rounded-lg text-xs font-bold ${
                                    isOverridden ? 'border-amber-400 text-amber-900 bg-amber-50/30' : 'border-slate-200 text-slate-800'
                                  }`}
                                >
                                  <option value="0">0% GST</option>
                                  <option value="5">5% GST</option>
                                  <option value="12">12% GST</option>
                                  <option value="18">18% GST</option>
                                  <option value="28">28% GST</option>
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
                                    Reset to Auto
                                  </button>
                                )}
                              </div>
                            </td>
                            <td className="p-2 text-right font-medium text-slate-600 pt-3">
                              ₹{item.taxable_amount.toFixed(2)}
                            </td>
                            <td className="p-2 text-right font-bold text-slate-900 pt-3">
                              ₹{item.total_price.toFixed(2)}
                            </td>
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
              </div>

              {/* Bottom: Notes & Attachment & Total Summary */}
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

                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-2">
                      Attach Reference Photo / Proforma (Cloud Storage)
                    </label>

                    {previewPhotoUrl ? (
                      <div className="relative border-2 border-dashed border-indigo-200 bg-indigo-50/30 rounded-2xl p-3 flex items-center gap-4">
                        <div className="relative w-20 h-20 rounded-xl overflow-hidden bg-white border border-slate-200 shadow-sm flex-shrink-0">
                          {selectedFile?.type.includes('pdf') ? (
                            <div className="w-full h-full flex flex-col items-center justify-center text-rose-500 bg-rose-50">
                              <FileText className="w-8 h-8" />
                              <span className="text-[10px] font-bold mt-1">PDF</span>
                            </div>
                          ) : (
                            <img
                              src={previewPhotoUrl}
                              alt="Attachment preview"
                              className="w-full h-full object-cover"
                            />
                          )}
                        </div>

                        <div className="flex-1 min-w-0">
                          <p className="text-xs font-bold text-slate-800 truncate">
                            {selectedFile?.name || 'Attached Photo'}
                          </p>
                          <p className="text-[11px] text-slate-500 mt-0.5">
                            {selectedFile ? `${(selectedFile.size / 1024).toFixed(1)} KB` : 'Ready to upload'}
                          </p>
                          <div className="flex items-center gap-2 mt-2">
                            <button
                              type="button"
                              onClick={() => setIsCameraOpen(true)}
                              className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-white border border-slate-200 text-[11px] font-semibold text-slate-700 hover:bg-slate-50 shadow-2xs transition-colors"
                            >
                              <Camera className="w-3 h-3 text-indigo-600" /> Retake Photo
                            </button>
                            <button
                              type="button"
                              onClick={() => {
                                setSelectedFile(null);
                                setPreviewPhotoUrl(null);
                              }}
                              className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-rose-50 border border-rose-100 text-[11px] font-semibold text-rose-600 hover:bg-rose-100 transition-colors"
                            >
                              <Trash2 className="w-3 h-3" /> Remove
                            </button>
                          </div>
                        </div>
                      </div>
                    ) : (
                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                        <button
                          type="button"
                          onClick={() => setIsCameraOpen(true)}
                          className="flex items-center justify-center gap-2 px-4 py-3 rounded-xl border border-indigo-200 bg-indigo-50/60 hover:bg-indigo-100/70 text-indigo-700 font-bold text-xs shadow-2xs transition-all active:scale-[0.99]"
                        >
                          <Camera className="w-4 h-4 text-indigo-600" />
                          <span>Snap Camera Photo</span>
                        </button>

                        <button
                          type="button"
                          onClick={() => fileUploadInputRef.current?.click()}
                          className="flex items-center justify-center gap-2 px-4 py-3 rounded-xl border border-slate-200 bg-slate-50 hover:bg-slate-100 text-slate-700 font-semibold text-xs shadow-2xs transition-all active:scale-[0.99]"
                        >
                          <Upload className="w-4 h-4 text-slate-500" />
                          <span>Choose from Files / Gallery</span>
                        </button>
                      </div>
                    )}

                    <input
                      ref={fileUploadInputRef}
                      type="file"
                      accept="image/*,.pdf"
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (file) {
                          setSelectedFile(file);
                          setPreviewPhotoUrl(URL.createObjectURL(file));
                        }
                      }}
                      className="hidden"
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
              <div className="flex items-center justify-end gap-3 pt-4 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsCreateOpen(false)}
                  className="px-4 py-2 rounded-xl text-sm font-semibold text-slate-600 hover:bg-slate-100"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-6 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-sm shadow-md shadow-indigo-600/30 transition-all disabled:opacity-50"
                >
                  {isSubmitting ? 'Issuing Order...' : 'Issue Purchase Order'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* PO DETAIL & PRINT MODAL */}
      {selectedPOForView && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-3xl max-w-3xl w-full p-8 shadow-2xl border border-slate-100 my-8">
            <div className="flex items-center justify-between pb-4 border-b border-slate-100 no-print">
              <span className="text-sm font-bold text-slate-400">PURCHASE ORDER INVOICE</span>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => window.print()}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white text-xs font-semibold shadow-sm"
                >
                  <Printer className="w-3.5 h-3.5" /> Print / Save PDF
                </button>
                <button
                  onClick={() => setSelectedPOForView(null)}
                  className="p-1.5 text-slate-400 hover:text-slate-600 rounded-lg"
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
                </div>
                <div>
                  <div className="font-bold text-slate-400 uppercase tracking-wider mb-1">Deliver To:</div>
                  <div className="text-sm font-bold text-slate-900">{selectedPOForView.location}</div>
                  <div className="text-slate-600 mt-0.5">Created By: {selectedPOForView.created_by}</div>
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
      )}

      {/* Live Camera Viewfinder Dialog for General PO */}
      <CameraCaptureDialog
        isOpen={isCameraOpen}
        onClose={() => setIsCameraOpen(false)}
        title="Snap PO Reference / Sample Photo"
        onPhotoCaptured={(file, previewUrl) => {
          setSelectedFile(file);
          setPreviewPhotoUrl(previewUrl);
        }}
      />

      {/* Live Camera Viewfinder Dialog for Individual Dress / Line Item */}
      {activeItemCameraIndex !== null && (
        <CameraCaptureDialog
          isOpen={true}
          onClose={() => setActiveItemCameraIndex(null)}
          title={`Snap Dress Photo: ${lineItems[activeItemCameraIndex]?.item_name || `Line Item #${activeItemCameraIndex + 1}`}`}
          onPhotoCaptured={async (file) => {
            const idx = activeItemCameraIndex;
            setActiveItemCameraIndex(null);
            if (idx !== null) {
              await handleCaptureItemPhoto(idx, file);
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
    </div>
  );
};
