import React from 'react';
import { 
  FileText, 
  Calendar, 
  Truck, 
  IndianRupee, 
  PlusCircle, 
  ArrowUpRight, 
  Clock, 
  CheckCircle2, 
  AlertTriangle 
} from 'lucide-react';
import { PurchaseOrder, PurchasePlan } from '../types';

interface DashboardViewProps {
  orders: PurchaseOrder[];
  plans: PurchasePlan[];
  onOpenCreatePO: () => void;
  onOpenCreateTrip: () => void;
  onNavigateToTab: (tab: string) => void;
  onViewPO: (po: PurchaseOrder) => void;
}

export const DashboardView: React.FC<DashboardViewProps> = ({
  orders,
  plans,
  onOpenCreatePO,
  onOpenCreateTrip,
  onNavigateToTab,
  onViewPO
}) => {
  const totalSpend = orders
    .filter(o => o.status !== 'CANCELLED')
    .reduce((sum, o) => sum + Number(o.total_amount || 0), 0);

  const openOrders = orders.filter(o => o.status === 'ISSUED' || o.status === 'PARTIALLY_RECEIVED');
  const pendingDeliveries = orders.filter(o => o.status === 'ISSUED' || o.status === 'PARTIALLY_RECEIVED');
  const upcomingPlans = plans.filter(p => p.status === 'PLANNED' || p.status === 'IN_PROGRESS');

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'ISSUED':
        return <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-100 text-amber-800 border border-amber-200">ISSUED</span>;
      case 'PARTIALLY_RECEIVED':
        return <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-blue-100 text-blue-800 border border-blue-200">PARTIAL RECEIPT</span>;
      case 'RECEIVED':
        return <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-100 text-emerald-800 border border-emerald-200">RECEIVED</span>;
      case 'CANCELLED':
        return <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-600 border border-slate-200">CANCELLED</span>;
      default:
        return <span className="px-2.5 py-1 rounded-full text-xs font-semibold bg-slate-100 text-slate-800 border border-slate-200">DRAFT</span>;
    }
  };

  return (
    <div className="space-y-6">
      {/* Welcome & Quick Actions Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl shadow-sm border border-slate-200/80">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Executive Procurement Dashboard</h1>
          <p className="text-sm text-slate-500 mt-1">
            Real-time overview of purchase orders, sourcing trips, and warehouse deliveries
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <button
            onClick={onOpenCreatePO}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-sm shadow-sm shadow-indigo-600/30 transition-all hover:scale-[1.02]"
          >
            <PlusCircle className="w-4 h-4" />
            Create Purchase Order
          </button>
          <button
            onClick={onOpenCreateTrip}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-semibold text-sm shadow-sm transition-all hover:scale-[1.02]"
          >
            <Calendar className="w-4 h-4" />
            Schedule Sourcing Trip
          </button>
        </div>
      </div>

      {/* KPI Cards Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {/* Total Spend */}
        <div className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200/80 relative overflow-hidden">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Total PO Value</span>
            <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center">
              <IndianRupee className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-2xl font-extrabold text-slate-900">
              ₹{totalSpend.toLocaleString('en-IN', { maximumFractionDigits: 0 })}
            </div>
            <p className="text-xs text-slate-500 mt-1 flex items-center gap-1">
              <span className="text-emerald-600 font-semibold">{orders.length}</span> total orders created
            </p>
          </div>
        </div>

        {/* Open Orders */}
        <div 
          onClick={() => onNavigateToTab('orders')}
          className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200/80 relative overflow-hidden cursor-pointer hover:border-indigo-300 transition-all group"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Active Orders</span>
            <div className="w-10 h-10 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center">
              <FileText className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-2xl font-extrabold text-slate-900 group-hover:text-indigo-600 transition-colors">
              {openOrders.length}
            </div>
            <p className="text-xs text-slate-500 mt-1 flex items-center gap-1">
              Issued or awaiting full delivery
            </p>
          </div>
        </div>

        {/* Pending Deliveries */}
        <div 
          onClick={() => onNavigateToTab('deliveries')}
          className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200/80 relative overflow-hidden cursor-pointer hover:border-indigo-300 transition-all group"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Pending Deliveries</span>
            <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center">
              <Truck className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-2xl font-extrabold text-slate-900 group-hover:text-indigo-600 transition-colors">
              {pendingDeliveries.length}
            </div>
            <p className="text-xs text-slate-500 mt-1 flex items-center gap-1">
              Shipments to be received in warehouse
            </p>
          </div>
        </div>

        {/* Upcoming Sourcing Trips */}
        <div 
          onClick={() => onNavigateToTab('planner')}
          className="bg-white p-5 rounded-2xl shadow-sm border border-slate-200/80 relative overflow-hidden cursor-pointer hover:border-indigo-300 transition-all group"
        >
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold uppercase tracking-wider text-slate-500">Sourcing Trips</span>
            <div className="w-10 h-10 rounded-xl bg-violet-50 text-violet-600 flex items-center justify-center">
              <Calendar className="w-5 h-5" />
            </div>
          </div>
          <div className="mt-3">
            <div className="text-2xl font-extrabold text-slate-900 group-hover:text-indigo-600 transition-colors">
              {upcomingPlans.length}
            </div>
            <p className="text-xs text-slate-500 mt-1 flex items-center gap-1">
              Active sourcing schedules
            </p>
          </div>
        </div>
      </div>

      {/* Main Content Split: Recent Orders & Upcoming Trips */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column (2 Cols): Recent Purchase Orders */}
        <div className="lg:col-span-2 bg-white rounded-2xl shadow-sm border border-slate-200/80 p-5">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-lg font-bold text-slate-900">Recent Purchase Orders</h2>
              <p className="text-xs text-slate-500">Latest orders submitted via Web or Mobile App</p>
            </div>
            <button
              onClick={() => onNavigateToTab('orders')}
              className="inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 hover:text-indigo-700"
            >
              View all orders <ArrowUpRight className="w-3.5 h-3.5" />
            </button>
          </div>

          {orders.length === 0 ? (
            <div className="py-12 text-center text-slate-400">
              <FileText className="w-10 h-10 mx-auto text-slate-300 mb-2" />
              <p className="text-sm font-medium">No purchase orders found</p>
              <button
                onClick={onOpenCreatePO}
                className="mt-3 text-xs text-indigo-600 font-semibold hover:underline"
              >
                Create your first PO
              </button>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-slate-100 text-xs uppercase font-bold text-slate-400">
                    <th className="pb-3 font-semibold">PO Number</th>
                    <th className="pb-3 font-semibold">Supplier</th>
                    <th className="pb-3 font-semibold">Status</th>
                    <th className="pb-3 font-semibold text-right">Amount</th>
                    <th className="pb-3 font-semibold text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {orders.slice(0, 6).map((order) => (
                    <tr key={order.id} className="hover:bg-slate-50/80 transition-colors">
                      <td className="py-3 font-semibold text-indigo-600">
                        {order.po_number}
                      </td>
                      <td className="py-3 text-slate-700 font-medium">
                        {order.supplier_name}
                      </td>
                      <td className="py-3">
                        {getStatusBadge(order.status)}
                      </td>
                      <td className="py-3 text-right font-bold text-slate-900">
                        ₹{Number(order.total_amount || 0).toLocaleString('en-IN', { maximumFractionDigits: 2 })}
                      </td>
                      <td className="py-3 text-right">
                        <button
                          onClick={() => onViewPO(order)}
                          className="px-2.5 py-1 text-xs font-semibold text-slate-700 hover:text-indigo-600 hover:bg-indigo-50 rounded-lg transition-colors"
                        >
                          Details
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Right Column (1 Col): Sourcing Trip Planner Feed */}
        <div className="bg-white rounded-2xl shadow-sm border border-slate-200/80 p-5">
          <div className="flex items-center justify-between mb-4">
            <div>
              <h2 className="text-lg font-bold text-slate-900">Sourcing Trips</h2>
              <p className="text-xs text-slate-500">Upcoming buyer schedules</p>
            </div>
            <button
              onClick={() => onNavigateToTab('planner')}
              className="inline-flex items-center gap-1 text-xs font-semibold text-indigo-600 hover:text-indigo-700"
            >
              Calendar <ArrowUpRight className="w-3.5 h-3.5" />
            </button>
          </div>

          {upcomingPlans.length === 0 ? (
            <div className="py-10 text-center text-slate-400">
              <Calendar className="w-10 h-10 mx-auto text-slate-300 mb-2" />
              <p className="text-sm font-medium">No trips scheduled</p>
              <button
                onClick={onOpenCreateTrip}
                className="mt-3 text-xs text-indigo-600 font-semibold hover:underline"
              >
                Schedule Sourcing Trip
              </button>
            </div>
          ) : (
            <div className="space-y-3">
              {upcomingPlans.slice(0, 5).map((plan) => {
                const dateStr = new Date(Number(plan.planned_date_millis)).toLocaleDateString('en-IN', {
                  day: 'numeric',
                  month: 'short'
                });
                return (
                  <div 
                    key={plan.id}
                    className="p-3.5 rounded-xl border border-slate-100 bg-slate-50/60 hover:bg-white hover:border-indigo-200 transition-all"
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-semibold text-slate-900 text-sm truncate max-w-[170px]">
                        {plan.title}
                      </span>
                      <span className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                        plan.priority === 'HIGH' 
                          ? 'bg-rose-100 text-rose-700' 
                          : plan.priority === 'LOW' 
                          ? 'bg-slate-200 text-slate-700'
                          : 'bg-amber-100 text-amber-700'
                      }`}>
                        {plan.priority}
                      </span>
                    </div>

                    <div className="mt-2 flex items-center justify-between text-xs text-slate-500">
                      <span className="flex items-center gap-1">
                        <Clock className="w-3 h-3 text-slate-400" />
                        {dateStr}
                        {plan.location && ` • ${plan.location}`}
                      </span>
                      {plan.estimated_budget > 0 && (
                        <span className="font-semibold text-slate-700">
                          ₹{Number(plan.estimated_budget).toLocaleString('en-IN')}
                        </span>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
