import React, { useState } from 'react';
import { 
  Package, 
  Building, 
  Plus, 
  Search, 
  AlertTriangle, 
  Check, 
  X,
  Star,
  MapPin,
  Phone,
  Mail
} from 'lucide-react';
import { InventoryItem, Supplier, User } from '../types';
import { supabase } from '../lib/supabase';

interface InventorySuppliersViewProps {
  inventory: InventoryItem[];
  suppliers: Supplier[];
  onRefresh: () => void;
  currentUser?: User | null;
}

export const InventorySuppliersView: React.FC<InventorySuppliersViewProps> = ({
  inventory,
  suppliers,
  onRefresh,
  currentUser
}) => {
  const isAdmin = currentUser?.is_admin === true;
  const [subTab, setSubTab] = useState<'inventory' | 'suppliers'>('inventory');
  const [searchQuery, setSearchQuery] = useState('');

  // Modals
  const [isAddProductOpen, setIsAddProductOpen] = useState(false);
  const [isAddSupplierOpen, setIsAddSupplierOpen] = useState(false);

  // Add Product Form
  const [sku, setSku] = useState('');
  const [name, setName] = useState('');
  const [division, setDivision] = useState('Readymade');
  const [section, setSection] = useState('Main Store');
  const [department, setDepartment] = useState('General');
  const [unitCost, setUnitCost] = useState('100');
  const [sellingPrice, setSellingPrice] = useState('150');
  const [currentStock, setCurrentStock] = useState('50');
  const [minReorder, setMinReorder] = useState('10');

  // Add Supplier Form
  const [suppCode, setSuppCode] = useState('');
  const [suppName, setSuppName] = useState('');
  const [contactPerson, setContactPerson] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [gstNumber, setGstNumber] = useState('');
  const [paymentTerms, setPaymentTerms] = useState('Net 30');

  const handleCreateProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!sku.trim() || !name.trim()) {
      alert('Please enter SKU and Item Name');
      return;
    }

    try {
      const { error } = await supabase.from('inventory').insert({
        sku: sku.trim().toUpperCase(),
        name: name.trim(),
        division,
        section,
        department,
        unit: 'Pcs',
        unit_cost: Number(unitCost) || 0,
        standard_selling_price: Number(sellingPrice) || 0,
        current_stock: Number(currentStock) || 0,
        min_reorder_level: Number(minReorder) || 10
      });

      if (error) throw error;
      setIsAddProductOpen(false);
      setSku('');
      setName('');
      onRefresh();
    } catch (err: any) {
      alert(`Error creating item: ${err.message}`);
    }
  };

  const handleCreateSupplier = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!suppName.trim()) {
      alert('Please enter Supplier Name');
      return;
    }

    try {
      const autoCode = suppCode.trim().toUpperCase() || `SUP-${Date.now().toString().slice(-4)}`;
      const { error } = await supabase.from('suppliers').insert({
        code: autoCode,
        name: suppName.trim(),
        contact_person: contactPerson.trim(),
        phone: phone.trim(),
        email: email.trim(),
        gst_number: gstNumber.trim(),
        payment_terms: paymentTerms,
        rating: 4.5,
        is_active: true
      });

      if (error) throw error;
      setIsAddSupplierOpen(false);
      setSuppCode('');
      setSuppName('');
      onRefresh();
    } catch (err: any) {
      alert(`Error creating supplier: ${err.message}`);
    }
  };

  const filteredInventory = inventory.filter(i => 
    i.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    i.sku.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const filteredSuppliers = suppliers.filter(s => 
    s.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    s.code.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="space-y-6">
      {/* Sub Tabs & Action */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl shadow-sm border border-slate-200/80">
        <div className="flex items-center gap-2">
          <button
            onClick={() => setSubTab('inventory')}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-bold transition-all ${
              subTab === 'inventory'
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            <Package className="w-4 h-4" /> Inventory Master
          </button>
          <button
            onClick={() => setSubTab('suppliers')}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-bold transition-all ${
              subTab === 'suppliers'
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            <Building className="w-4 h-4" /> Supplier Directory
          </button>
        </div>

        <div className="flex items-center gap-3">
          <div className="relative w-64">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
            <input
              type="text"
              placeholder={`Search ${subTab}...`}
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-9 pr-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          {!isAdmin && (
            <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-amber-50 border border-amber-200 text-amber-700 text-xs font-semibold">
              <Check className="w-3.5 h-3.5" /> View Only
            </span>
          )}

          {isAdmin && subTab === 'inventory' && (
            <button
              onClick={() => setIsAddProductOpen(true)}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs shadow-sm transition-all whitespace-nowrap"
            >
              <Plus className="w-4 h-4" /> Add Product
            </button>
          )}
          {isAdmin && subTab === 'suppliers' && (
            <button
              onClick={() => setIsAddSupplierOpen(true)}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs shadow-sm transition-all whitespace-nowrap"
            >
              <Plus className="w-4 h-4" /> Add Supplier
            </button>
          )}
        </div>
      </div>

      {/* Content for Inventory */}
      {subTab === 'inventory' && (
        <div className="bg-white rounded-2xl shadow-sm border border-slate-200/80 overflow-hidden">
          {filteredInventory.length === 0 ? (
            <div className="py-16 text-center text-slate-400">
              <Package className="w-12 h-12 mx-auto text-slate-300 mb-3" />
              <p className="text-base font-semibold text-slate-700">No inventory items found</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 border-b border-slate-200 text-xs uppercase font-bold text-slate-500">
                  <tr>
                    <th className="py-3 px-4">SKU</th>
                    <th className="py-3 px-4">Item Name</th>
                    <th className="py-3 px-4">Division / Dept</th>
                    <th className="py-3 px-4 text-right">Cost Price</th>
                    <th className="py-3 px-4 text-right">Selling Price</th>
                    <th className="py-3 px-4 text-center">Stock</th>
                    <th className="py-3 px-4 text-center">Reorder Lvl</th>
                    <th className="py-3 px-4 text-center">Stock Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredInventory.map(item => {
                    const isLowStock = item.current_stock <= item.min_reorder_level;
                    return (
                      <tr key={item.id} className="hover:bg-slate-50/50">
                        <td className="py-3 px-4 font-bold text-indigo-600 text-xs">{item.sku}</td>
                        <td className="py-3 px-4 font-semibold text-slate-900">{item.name}</td>
                        <td className="py-3 px-4 text-slate-500 text-xs">
                          {item.division} • {item.department}
                        </td>
                        <td className="py-3 px-4 text-right text-slate-600">₹{Number(item.unit_cost).toFixed(2)}</td>
                        <td className="py-3 px-4 text-right font-bold text-slate-900">₹{Number(item.standard_selling_price).toFixed(2)}</td>
                        <td className="py-3 px-4 text-center font-extrabold text-slate-800">{item.current_stock}</td>
                        <td className="py-3 px-4 text-center text-slate-500 text-xs">{item.min_reorder_level}</td>
                        <td className="py-3 px-4 text-center">
                          {isLowStock ? (
                            <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-100 text-rose-700">
                              Low Stock
                            </span>
                          ) : (
                            <span className="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-100 text-emerald-700">
                              Adequate
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* Content for Suppliers */}
      {subTab === 'suppliers' && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredSuppliers.map(s => (
            <div key={s.id} className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold px-2 py-0.5 rounded-md bg-slate-100 text-slate-700 border">
                  {s.code}
                </span>
                <span className="text-xs font-semibold text-emerald-600">
                  {s.payment_terms}
                </span>
              </div>

              <div>
                <h3 className="font-bold text-slate-900 text-base">{s.name}</h3>
                {s.contact_person && (
                  <p className="text-xs text-slate-500">Contact: {s.contact_person}</p>
                )}
              </div>

              <div className="space-y-1 text-xs text-slate-600 pt-1">
                {s.phone && (
                  <p className="flex items-center gap-1.5"><Phone className="w-3.5 h-3.5 text-slate-400" /> {s.phone}</p>
                )}
                {s.email && (
                  <p className="flex items-center gap-1.5"><Mail className="w-3.5 h-3.5 text-slate-400" /> {s.email}</p>
                )}
                {s.gst_number && (
                  <p className="font-semibold text-indigo-600 mt-1">GSTIN: {s.gst_number}</p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {/* ADD PRODUCT MODAL */}
      {isAddProductOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-md w-full p-6 shadow-2xl border border-slate-100">
            <div className="flex items-center justify-between pb-3 border-b">
              <h3 className="font-bold text-slate-900">Add Inventory Item</h3>
              <button onClick={() => setIsAddProductOpen(false)}><X className="w-5 h-5 text-slate-400" /></button>
            </div>
            <form onSubmit={handleCreateProduct} className="mt-4 space-y-3">
              <div>
                <label className="text-xs font-semibold text-slate-700">SKU Code *</label>
                <input
                  type="text"
                  placeholder="SKU-1001"
                  value={sku}
                  onChange={(e) => setSku(e.target.value)}
                  className="w-full px-3 py-1.5 border rounded-xl text-sm"
                  required
                />
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-700">Item Name *</label>
                <input
                  type="text"
                  placeholder="e.g. Georgette Saree / Chiffon Kurti"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full px-3 py-1.5 border rounded-xl text-sm"
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-xs font-semibold text-slate-700">Cost Price (₹)</label>
                  <input
                    type="number"
                    value={unitCost}
                    onChange={(e) => setUnitCost(e.target.value)}
                    className="w-full px-3 py-1.5 border rounded-xl text-sm"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-700">Selling Price (₹)</label>
                  <input
                    type="number"
                    value={sellingPrice}
                    onChange={(e) => setSellingPrice(e.target.value)}
                    className="w-full px-3 py-1.5 border rounded-xl text-sm"
                  />
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-xs font-semibold text-slate-700">Initial Stock</label>
                  <input
                    type="number"
                    value={currentStock}
                    onChange={(e) => setCurrentStock(e.target.value)}
                    className="w-full px-3 py-1.5 border rounded-xl text-sm"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-700">Min Reorder Level</label>
                  <input
                    type="number"
                    value={minReorder}
                    onChange={(e) => setMinReorder(e.target.value)}
                    className="w-full px-3 py-1.5 border rounded-xl text-sm"
                  />
                </div>
              </div>
              <div className="flex justify-end gap-2 pt-3 border-t">
                <button type="button" onClick={() => setIsAddProductOpen(false)} className="px-3 py-1.5 text-xs font-semibold text-slate-600">Cancel</button>
                <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-xl text-xs font-bold">Save Item</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ADD SUPPLIER MODAL */}
      {isAddSupplierOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-md w-full p-6 shadow-2xl border border-slate-100">
            <div className="flex items-center justify-between pb-3 border-b">
              <h3 className="font-bold text-slate-900">Add New Supplier</h3>
              <button onClick={() => setIsAddSupplierOpen(false)}><X className="w-5 h-5 text-slate-400" /></button>
            </div>
            <form onSubmit={handleCreateSupplier} className="mt-4 space-y-3">
              <div>
                <label className="text-xs font-semibold text-slate-700">Supplier Name *</label>
                <input
                  type="text"
                  placeholder="e.g. Surat Silk Weavers Ltd"
                  value={suppName}
                  onChange={(e) => setSuppName(e.target.value)}
                  className="w-full px-3 py-1.5 border rounded-xl text-sm"
                  required
                />
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-xs font-semibold text-slate-700">Code (Optional)</label>
                  <input
                    type="text"
                    placeholder="SUP-01"
                    value={suppCode}
                    onChange={(e) => setSuppCode(e.target.value)}
                    className="w-full px-3 py-1.5 border rounded-xl text-sm"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-700">Payment Terms</label>
                  <select
                    value={paymentTerms}
                    onChange={(e) => setPaymentTerms(e.target.value)}
                    className="w-full px-3 py-1.5 border rounded-xl text-sm"
                  >
                    <option value="Net 30">Net 30</option>
                    <option value="Net 60">Net 60</option>
                    <option value="COD">COD</option>
                    <option value="Advance">Advance</option>
                  </select>
                </div>
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="text-xs font-semibold text-slate-700">Phone</label>
                  <input
                    type="text"
                    placeholder="+91 98..."
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    className="w-full px-3 py-1.5 border rounded-xl text-sm"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-700">Contact Person</label>
                  <input
                    type="text"
                    placeholder="Manager"
                    value={contactPerson}
                    onChange={(e) => setContactPerson(e.target.value)}
                    className="w-full px-3 py-1.5 border rounded-xl text-sm"
                  />
                </div>
              </div>
              <div>
                <label className="text-xs font-semibold text-slate-700">GSTIN Number</label>
                <input
                  type="text"
                  placeholder="33AAAAA0000A1Z5"
                  value={gstNumber}
                  onChange={(e) => setGstNumber(e.target.value)}
                  className="w-full px-3 py-1.5 border rounded-xl text-sm"
                />
              </div>
              <div className="flex justify-end gap-2 pt-3 border-t">
                <button type="button" onClick={() => setIsAddSupplierOpen(false)} className="px-3 py-1.5 text-xs font-semibold text-slate-600">Cancel</button>
                <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-xl text-xs font-bold">Save Supplier</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
