import React, { useState } from 'react';
import { 
  Truck, 
  CheckCircle2, 
  PackageCheck, 
  Clock, 
  AlertCircle, 
  Search, 
  X, 
  ArrowRight,
  Package,
  Image as ImageIcon,
  ZoomIn,
  Calendar,
  Building,
  Layers,
  Filter
} from 'lucide-react';
import { PurchaseOrder, PurchaseOrderItem, User } from '../types';
import { supabase } from '../lib/supabase';

interface DeliveriesViewProps {
  orders: PurchaseOrder[];
  currentUser: User | null;
  onRefresh: () => void;
}

export const DeliveriesView: React.FC<DeliveriesViewProps> = ({
  orders,
  currentUser,
  onRefresh
}) => {
  const [selectedOrder, setSelectedOrder] = useState<PurchaseOrder | null>(null);
  const [receivingItems, setReceivingItems] = useState<PurchaseOrderItem[]>([]);
  const [isReceivingModalOpen, setIsReceivingModalOpen] = useState(false);
  const [isSaving, setIsSaving] = useState(false);
  const [viewingDressImage, setViewingDressImage] = useState<{ url: string; title: string } | null>(null);

  // Filters & Slicers
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedSupplier, setSelectedSupplier] = useState<string>('ALL');
  const [selectedDepartment, setSelectedDepartment] = useState<string>('ALL');
  const [selectedCreatedDate, setSelectedCreatedDate] = useState<string>('');

  // Extract unique suppliers and departments across orders & line items
  const supplierOptions = Array.from(new Set(orders.map(o => o.supplier_name).filter(Boolean))).sort();
  
  const departmentOptions = Array.from(new Set(
    orders.flatMap(o => (o.items || []).map(i => i.department).concat([o.department || ''])).filter(Boolean)
  )).sort();

  // Filter only orders that need receiving or are received, matching slicers
  const deliveryOrders = orders.filter(o => {
    const isDeliveryStatus = o.status === 'ISSUED' || o.status === 'PARTIALLY_RECEIVED' || o.status === 'RECEIVED';
    if (!isDeliveryStatus) return false;

    // Search query: PO#, supplier, destination, notes
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const matchPO = o.po_number.toLowerCase().includes(q);
      const matchSupp = o.supplier_name.toLowerCase().includes(q);
      const matchDest = (o.location || '').toLowerCase().includes(q);
      const matchNotes = (o.notes || '').toLowerCase().includes(q);
      const matchItems = (o.items || []).some(i => i.item_name.toLowerCase().includes(q) || (i.department || '').toLowerCase().includes(q));
      if (!matchPO && !matchSupp && !matchDest && !matchNotes && !matchItems) return false;
    }

    // Supplier slicer
    if (selectedSupplier !== 'ALL' && o.supplier_name !== selectedSupplier) {
      return false;
    }

    // Department slicer: matches order department or any line item department
    if (selectedDepartment !== 'ALL') {
      const dept = selectedDepartment.toLowerCase();
      const matchesOrderDept = (o.department || '').toLowerCase() === dept;
      const matchesItemDept = (o.items || []).some(i => (i.department || '').toLowerCase() === dept);
      if (!matchesOrderDept && !matchesItemDept) return false;
    }

    // Created Date filter (YYYY-MM-DD from order_date_millis or created_at)
    if (selectedCreatedDate) {
      const orderDateIso = new Date(Number(o.order_date_millis)).toISOString().split('T')[0];
      const createdDateIso = o.created_at ? new Date(o.created_at).toISOString().split('T')[0] : '';
      if (orderDateIso !== selectedCreatedDate && createdDateIso !== selectedCreatedDate) {
        return false;
      }
    }

    return true;
  });

  const clearFilters = () => {
    setSearchQuery('');
    setSelectedSupplier('ALL');
    setSelectedDepartment('ALL');
    setSelectedCreatedDate('');
  };

  const hasActiveFilters = searchQuery || selectedSupplier !== 'ALL' || selectedDepartment !== 'ALL' || selectedCreatedDate;

  const openReceivingModal = async (order: PurchaseOrder) => {
    setSelectedOrder(order);
    // Fetch fresh items for this PO
    const { data: items } = await supabase
      .from('po_items')
      .select('*')
      .eq('po_id', order.id);

    setReceivingItems(items || []);
    setIsReceivingModalOpen(true);
  };

  const updateItemReceivedQty = (index: number, qty: number) => {
    const updated = [...receivingItems];
    updated[index] = { ...updated[index], quantity_received: Math.max(0, qty) };
    setReceivingItems(updated);
  };

  const handleSaveReceiving = async () => {
    if (!selectedOrder) return;
    setIsSaving(true);
    try {
      // 1. Update received quantity for each item
      for (const item of receivingItems) {
        if (item.id) {
          await supabase
            .from('po_items')
            .update({ quantity_received: item.quantity_received })
            .eq('id', item.id);
        }
      }

      // 2. Determine new PO status
      const allReceived = receivingItems.every(i => i.quantity_received >= i.quantity_ordered);
      const someReceived = receivingItems.some(i => i.quantity_received > 0);

      const newStatus = allReceived ? 'RECEIVED' : (someReceived ? 'PARTIALLY_RECEIVED' : 'ISSUED');

      await supabase
        .from('purchase_orders')
        .update({ status: newStatus })
        .eq('id', selectedOrder.id);

      setIsReceivingModalOpen(false);
      setSelectedOrder(null);
      onRefresh();
    } catch (err: any) {
      alert(`Error updating received quantities: ${err.message}`);
    } finally {
      setIsSaving(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl shadow-sm border border-slate-200/80">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Delivery & Goods Receiving Desk</h1>
          <p className="text-sm text-slate-500 mt-1">
            Receive incoming shipments, verify item quantities, and update warehouse stock
          </p>
        </div>
      </div>

      {/* Deliveries Filters & Slicers Bar */}
      <div className="bg-white p-4 rounded-2xl shadow-sm border border-slate-200/80 space-y-3">
        <div className="flex items-center justify-between pb-2 border-b border-slate-100 flex-wrap gap-2">
          <div className="flex items-center gap-2 text-xs font-bold text-slate-700">
            <Filter className="w-4 h-4 text-indigo-600" />
            <span>Search &amp; Slicers</span>
            <span className="text-[11px] font-normal text-slate-400">({deliveryOrders.length} orders found)</span>
          </div>
          {hasActiveFilters && (
            <button
              onClick={clearFilters}
              className="text-xs font-semibold text-rose-600 hover:text-rose-700 hover:underline flex items-center gap-1"
            >
              <X className="w-3.5 h-3.5" /> Reset Slicers
            </button>
          )}
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-3">
          {/* Slicer 1: Search Query */}
          <div>
            <label className="block text-[11px] font-bold text-slate-600 mb-1">Search Keywords</label>
            <div className="relative">
              <Search className="w-3.5 h-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
              <input
                type="text"
                placeholder="PO#, items, notes..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full pl-8 pr-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
          </div>

          {/* Slicer 2: PO Created Date Slicer */}
          <div>
            <label className="block text-[11px] font-bold text-slate-600 mb-1 flex items-center gap-1">
              <Calendar className="w-3 h-3 text-indigo-600" /> PO Created Date
            </label>
            <input
              type="date"
              value={selectedCreatedDate}
              onChange={(e) => setSelectedCreatedDate(e.target.value)}
              className="w-full px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500 text-slate-800"
            />
          </div>

          {/* Slicer 3: Supplier Slicer */}
          <div>
            <label className="block text-[11px] font-bold text-slate-600 mb-1 flex items-center gap-1">
              <Building className="w-3 h-3 text-indigo-600" /> Supplier Slicer
            </label>
            <select
              value={selectedSupplier}
              onChange={(e) => setSelectedSupplier(e.target.value)}
              className="w-full px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500 text-slate-800"
            >
              <option value="ALL">All Suppliers ({supplierOptions.length})</option>
              {supplierOptions.map(s => (
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
              value={selectedDepartment}
              onChange={(e) => setSelectedDepartment(e.target.value)}
              className="w-full px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium focus:outline-none focus:ring-2 focus:ring-indigo-500 text-slate-800"
            >
              <option value="ALL">All Departments ({departmentOptions.length})</option>
              {departmentOptions.map(d => (
                <option key={d} value={d}>{d}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {/* Deliveries Table */}
      <div className="bg-white rounded-2xl shadow-sm border border-slate-200/80 overflow-hidden">
        {deliveryOrders.length === 0 ? (
          <div className="py-16 text-center text-slate-400">
            <Truck className="w-12 h-12 mx-auto text-slate-300 mb-3" />
            <p className="text-base font-semibold text-slate-700">No matching deliveries found</p>
            <p className="text-xs text-slate-400 mt-1">
              {hasActiveFilters ? 'Try adjusting your slicers or date filters' : 'Issue a purchase order first to begin delivery tracking'}
            </p>
            {hasActiveFilters && (
              <button
                onClick={clearFilters}
                className="mt-3 px-3.5 py-1.5 rounded-xl bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-semibold transition-colors"
              >
                Clear all filters
              </button>
            )}
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 border-b border-slate-200 text-xs uppercase font-bold text-slate-500">
                <tr>
                  <th className="py-3.5 px-4">PO Number</th>
                  <th className="py-3.5 px-4">Created Date</th>
                  <th className="py-3.5 px-4">Supplier</th>
                  <th className="py-3.5 px-4">Departments</th>
                  <th className="py-3.5 px-4">Expected Date</th>
                  <th className="py-3.5 px-4">Destination</th>
                  <th className="py-3.5 px-4">Delivery Status</th>
                  <th className="py-3.5 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {deliveryOrders.map(order => {
                  const createdDate = order.order_date_millis 
                    ? new Date(Number(order.order_date_millis)).toLocaleDateString('en-IN')
                    : (order.created_at ? new Date(order.created_at).toLocaleDateString('en-IN') : '-');
                  const expectedDate = new Date(Number(order.expected_delivery_date_millis));
                  const isOverdue = expectedDate.getTime() < Date.now() && order.status !== 'RECEIVED';

                  const orderDepts = Array.from(new Set(
                    (order.items || []).map(i => i.department).concat([order.department || '']).filter(Boolean)
                  ));

                  return (
                    <tr key={order.id} className="hover:bg-slate-50/70 transition-colors">
                      <td className="py-3.5 px-4 font-bold text-indigo-600">
                        {order.po_number}
                      </td>
                      <td className="py-3.5 px-4 text-xs font-semibold text-slate-700">
                        {createdDate}
                      </td>
                      <td className="py-3.5 px-4 font-medium text-slate-800">
                        {order.supplier_name}
                      </td>
                      <td className="py-3.5 px-4 text-xs">
                        <div className="flex items-center gap-1 flex-wrap">
                          {orderDepts.length > 0 ? (
                            orderDepts.map(d => (
                              <span key={d} className="px-2 py-0.5 rounded-md bg-slate-100 text-slate-700 font-medium text-[11px] border border-slate-200">
                                {d}
                              </span>
                            ))
                          ) : (
                            <span className="text-slate-400 italic text-[11px]">General</span>
                          )}
                        </div>
                      </td>
                      <td className="py-3.5 px-4 text-xs">
                        <span className={isOverdue ? 'text-rose-600 font-bold' : 'text-slate-600'}>
                          {expectedDate.toLocaleDateString('en-IN')}
                          {isOverdue && ' (Overdue)'}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-slate-600 text-xs font-medium">
                        {order.location}
                      </td>
                      <td className="py-3.5 px-4">
                        <span className={`px-2.5 py-1 rounded-full text-xs font-semibold ${
                          order.status === 'RECEIVED'
                            ? 'bg-emerald-100 text-emerald-800'
                            : order.status === 'PARTIALLY_RECEIVED'
                            ? 'bg-blue-100 text-blue-800'
                            : 'bg-amber-100 text-amber-800'
                        }`}>
                          {order.status.replace('_', ' ')}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-right">
                        <button
                          onClick={() => openReceivingModal(order)}
                          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-indigo-50 hover:bg-indigo-100 text-indigo-700 text-xs font-bold transition-colors"
                        >
                          <PackageCheck className="w-4 h-4" /> Receive Items
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* RECEIVE GOODS MODAL */}
      {isReceivingModalOpen && selectedOrder && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm overflow-y-auto p-2 sm:p-4">
          <div className="min-h-full flex items-center justify-center py-6 sm:py-8">
            <div className="bg-white rounded-3xl max-w-2xl w-full p-4 sm:p-6 shadow-2xl border border-slate-100 my-auto">
              <div className="flex items-center justify-between pb-4 border-b border-slate-100">
              <div>
                <h2 className="text-xl font-bold text-slate-900">Record Received Stock</h2>
                <p className="text-xs text-slate-500">
                  {selectedOrder.po_number} • {selectedOrder.supplier_name}
                </p>
              </div>
              <button
                onClick={() => setIsReceivingModalOpen(false)}
                className="p-2 text-slate-400 hover:text-slate-600 rounded-xl"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="mt-4 space-y-4">
              <p className="text-xs text-slate-600 bg-slate-50 p-3 rounded-xl border">
                Enter the quantity physically received in the warehouse. If all items match the ordered quantity, the PO will be marked as <b>RECEIVED</b>.
              </p>

              <div className="overflow-x-auto border border-slate-200 rounded-2xl">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50 font-bold text-slate-700 border-b border-slate-200">
                    <tr>
                      <th className="p-3 w-16 text-center">Dress Photo</th>
                      <th className="p-3">Item Description</th>
                      <th className="p-3 text-center">Ordered</th>
                      <th className="p-3 text-center">Received</th>
                      <th className="p-3 text-center">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {receivingItems.map((item, idx) => {
                      const isComplete = item.quantity_received >= item.quantity_ordered;
                      const dressPhoto = item.photo_uri || selectedOrder?.photo_uri;
                      return (
                        <tr key={idx} className="hover:bg-slate-50/50 align-middle">
                          <td className="p-2.5 text-center">
                            {dressPhoto ? (
                              <div className="relative group w-12 h-12 mx-auto rounded-lg overflow-hidden border border-slate-200 shadow-2xs bg-slate-50">
                                <img
                                  src={dressPhoto}
                                  alt={item.item_name}
                                  onClick={() => setViewingDressImage({ url: dressPhoto, title: item.item_name })}
                                  className="w-full h-full object-cover cursor-pointer hover:scale-105 transition-transform"
                                />
                                <button
                                  type="button"
                                  onClick={() => setViewingDressImage({ url: dressPhoto, title: item.item_name })}
                                  className="absolute inset-0 bg-slate-900/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center text-white"
                                  title="Enlarge dress photo"
                                >
                                  <ZoomIn className="w-3.5 h-3.5" />
                                </button>
                              </div>
                            ) : (
                              <div className="w-12 h-12 mx-auto rounded-lg border border-slate-200 bg-slate-50 flex items-center justify-center text-slate-300">
                                <ImageIcon className="w-4 h-4" />
                              </div>
                            )}
                          </td>
                          <td className="p-3">
                            <span className="font-bold text-slate-900 block">{item.item_name}</span>
                          </td>
                          <td className="p-3 text-center font-bold text-slate-700">
                            {item.quantity_ordered}
                          </td>
                          <td className="p-3 text-center">
                            <input
                              type="number"
                              min="0"
                              max={item.quantity_ordered * 2}
                              value={item.quantity_received}
                              onChange={(e) => updateItemReceivedQty(idx, Number(e.target.value))}
                              className="w-20 px-2 py-1 text-center font-bold bg-white border border-slate-300 rounded-lg text-xs focus:ring-2 focus:ring-indigo-500"
                            />
                          </td>
                          <td className="p-3 text-center">
                            {isComplete ? (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-800">
                                Complete
                              </span>
                            ) : (
                              <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-100 text-amber-800">
                                Pending {item.quantity_ordered - item.quantity_received}
                              </span>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              {/* Action Buttons */}
              <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsReceivingModalOpen(false)}
                  className="px-4 py-2 rounded-xl text-sm font-semibold text-slate-600 hover:bg-slate-100"
                >
                  Cancel
                </button>
                <button
                  type="button"
                  onClick={handleSaveReceiving}
                  disabled={isSaving}
                  className="px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-sm shadow-md shadow-indigo-600/30 transition-all disabled:opacity-50"
                >
                  {isSaving ? 'Updating Stock...' : 'Save Stock Receipt'}
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
      )}

      {/* Full-size Image Viewer Modal */}
      {viewingDressImage && (
        <div 
          className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in"
          onClick={() => setViewingDressImage(null)}
        >
          <div 
            className="relative max-w-3xl max-h-[90vh] bg-white rounded-3xl overflow-hidden shadow-2xl border border-slate-700/50 flex flex-col"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="flex items-center justify-between px-5 py-3.5 border-b border-slate-100 bg-slate-50/80">
              <div className="flex items-center gap-2">
                <ImageIcon className="w-4 h-4 text-indigo-600" />
                <span className="text-sm font-bold text-slate-800">{viewingDressImage.title}</span>
              </div>
              <button
                onClick={() => setViewingDressImage(null)}
                className="p-1.5 text-slate-400 hover:text-slate-700 rounded-xl hover:bg-slate-200/60 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="p-4 flex items-center justify-center bg-slate-900/5 overflow-auto max-h-[calc(90vh-65px)]">
              <img
                src={viewingDressImage.url}
                alt={viewingDressImage.title}
                className="max-w-full max-h-[75vh] rounded-xl object-contain shadow-md"
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
