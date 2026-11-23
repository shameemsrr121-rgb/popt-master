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
  Package
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

  // Filter only orders that need receiving or are received
  const deliveryOrders = orders.filter(o => 
    o.status === 'ISSUED' || o.status === 'PARTIALLY_RECEIVED' || o.status === 'RECEIVED'
  );

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

      {/* Deliveries Table */}
      <div className="bg-white rounded-2xl shadow-sm border border-slate-200/80 overflow-hidden">
        {deliveryOrders.length === 0 ? (
          <div className="py-16 text-center text-slate-400">
            <Truck className="w-12 h-12 mx-auto text-slate-300 mb-3" />
            <p className="text-base font-semibold text-slate-700">No active deliveries pending</p>
            <p className="text-xs text-slate-400 mt-1">Issue a purchase order first to begin delivery tracking</p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 border-b border-slate-200 text-xs uppercase font-bold text-slate-500">
                <tr>
                  <th className="py-3.5 px-4">PO Number</th>
                  <th className="py-3.5 px-4">Supplier</th>
                  <th className="py-3.5 px-4">Expected Date</th>
                  <th className="py-3.5 px-4">Destination</th>
                  <th className="py-3.5 px-4">Delivery Status</th>
                  <th className="py-3.5 px-4 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {deliveryOrders.map(order => {
                  const expectedDate = new Date(Number(order.expected_delivery_date_millis));
                  const isOverdue = expectedDate.getTime() < Date.now() && order.status !== 'RECEIVED';

                  return (
                    <tr key={order.id} className="hover:bg-slate-50/70 transition-colors">
                      <td className="py-3.5 px-4 font-bold text-indigo-600">
                        {order.po_number}
                      </td>
                      <td className="py-3.5 px-4 font-medium text-slate-800">
                        {order.supplier_name}
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
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-3xl max-w-2xl w-full p-6 shadow-2xl border border-slate-100 my-8">
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
                      <th className="p-3">SKU & Item Name</th>
                      <th className="p-3 text-center">Ordered</th>
                      <th className="p-3 text-center">Received</th>
                      <th className="p-3 text-center">Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {receivingItems.map((item, idx) => {
                      const isComplete = item.quantity_received >= item.quantity_ordered;
                      return (
                        <tr key={idx} className="hover:bg-slate-50/50">
                          <td className="p-3">
                            <span className="font-bold text-slate-900 block">{item.item_name}</span>
                            <span className="text-[11px] text-slate-400">{item.item_sku}</span>
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
      )}
    </div>
  );
};
