import React, { useState } from 'react';
import { 
  BarChart3, 
  Download, 
  Printer, 
  IndianRupee, 
  PieChart, 
  FileSpreadsheet,
  TrendingUp,
  Building
} from 'lucide-react';
import { PurchaseOrder, Supplier } from '../types';

interface ReportsViewProps {
  orders: PurchaseOrder[];
  suppliers: Supplier[];
}

export const ReportsView: React.FC<ReportsViewProps> = ({ orders, suppliers }) => {
  const [selectedPeriod, setSelectedPeriod] = useState('ALL');

  // Calculations
  const validOrders = orders.filter(o => o.status !== 'CANCELLED');
  const totalSpend = validOrders.reduce((sum, o) => sum + Number(o.total_amount || 0), 0);
  const totalTax = validOrders.reduce((sum, o) => sum + Number(o.tax_amount || 0), 0);
  const totalTaxable = validOrders.reduce((sum, o) => sum + Number(o.subtotal || 0), 0);

  // Group by Supplier
  const spendBySupplier: { [name: string]: { count: number; total: number } } = {};
  validOrders.forEach(o => {
    const sName = o.supplier_name || 'Unknown Supplier';
    if (!spendBySupplier[sName]) {
      spendBySupplier[sName] = { count: 0, total: 0 };
    }
    spendBySupplier[sName].count += 1;
    spendBySupplier[sName].total += Number(o.total_amount || 0);
  });

  const supplierRows = Object.entries(spendBySupplier).sort((a, b) => b[1].total - a[1].total);

  // Export to CSV function
  const handleExportCSV = () => {
    const headers = ['PO Number', 'Supplier', 'Order Date', 'Expected Delivery', 'Status', 'Taxable Amount', 'Tax Amount', 'Total Amount', 'Location'];
    const rows = orders.map(o => [
      o.po_number,
      `"${o.supplier_name.replace(/"/g, '""')}"`,
      new Date(Number(o.order_date_millis)).toLocaleDateString('en-IN'),
      new Date(Number(o.expected_delivery_date_millis)).toLocaleDateString('en-IN'),
      o.status,
      o.subtotal,
      o.tax_amount,
      o.total_amount,
      `"${o.location}"`
    ]);

    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map(e => e.join(','))].join('\n');
    const encodedUri = encodeURI(csvContent);
    const link = document.createElement('a');
    link.setAttribute('href', encodedUri);
    link.setAttribute('download', `PO_Procurement_Report_${Date.now()}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl shadow-sm border border-slate-200/80">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Spend & Procurement Reports</h1>
          <p className="text-sm text-slate-500 mt-1">
            Analyze purchasing spend, vendor allocation, and tax liability breakdowns
          </p>
        </div>
        <div className="flex items-center gap-3">
          <button
            onClick={handleExportCSV}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-700 text-white font-semibold text-sm shadow-sm transition-all hover:scale-[1.02]"
          >
            <Download className="w-4 h-4" />
            Export Excel / CSV
          </button>
          <button
            onClick={() => window.print()}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-white font-semibold text-sm shadow-sm transition-all"
          >
            <Printer className="w-4 h-4" />
            Print Report
          </button>
        </div>
      </div>

      {/* KPI Stats */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm">
          <span className="text-xs font-bold uppercase text-slate-400">Total Purchase Spend</span>
          <div className="text-2xl font-extrabold text-slate-900 mt-2">
            ₹{totalSpend.toLocaleString('en-IN', { maximumFractionDigits: 0 })}
          </div>
          <p className="text-xs text-slate-500 mt-1">Across {validOrders.length} confirmed orders</p>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm">
          <span className="text-xs font-bold uppercase text-slate-400">Taxable Purchase Value</span>
          <div className="text-2xl font-extrabold text-slate-900 mt-2">
            ₹{totalTaxable.toLocaleString('en-IN', { maximumFractionDigits: 0 })}
          </div>
          <p className="text-xs text-slate-500 mt-1">Base cost before taxes</p>
        </div>

        <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm">
          <span className="text-xs font-bold uppercase text-slate-400">Input GST Tax Paid</span>
          <div className="text-2xl font-extrabold text-indigo-600 mt-2">
            ₹{totalTax.toLocaleString('en-IN', { maximumFractionDigits: 0 })}
          </div>
          <p className="text-xs text-slate-500 mt-1">Eligible Input Tax Credit (ITC)</p>
        </div>
      </div>

      {/* Vendor Spend Breakdown Table */}
      <div className="bg-white rounded-2xl shadow-sm border border-slate-200/80 p-5">
        <h2 className="text-lg font-bold text-slate-900 mb-4 flex items-center gap-2">
          <Building className="w-5 h-5 text-indigo-600" />
          Spend Distribution by Supplier
        </h2>

        {supplierRows.length === 0 ? (
          <p className="text-xs text-slate-400 py-6 text-center">No spend data recorded</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 border-b border-slate-200 text-xs uppercase font-bold text-slate-500">
                <tr>
                  <th className="py-3 px-4">Supplier Name</th>
                  <th className="py-3 px-4 text-center">Orders Placed</th>
                  <th className="py-3 px-4 text-right">Total Purchasing Value</th>
                  <th className="py-3 px-4 text-right">% of Total Spend</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {supplierRows.map(([name, data]) => {
                  const percent = totalSpend > 0 ? ((data.total / totalSpend) * 100).toFixed(1) : '0';
                  return (
                    <tr key={name} className="hover:bg-slate-50/50">
                      <td className="py-3 px-4 font-bold text-slate-800">{name}</td>
                      <td className="py-3 px-4 text-center font-semibold text-slate-600">{data.count}</td>
                      <td className="py-3 px-4 text-right font-extrabold text-slate-900">
                        ₹{data.total.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
                      </td>
                      <td className="py-3 px-4 text-right">
                        <span className="inline-block px-2 py-0.5 rounded-full text-xs font-bold bg-indigo-50 text-indigo-700">
                          {percent}%
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};
