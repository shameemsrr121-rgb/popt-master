import React, { useState, useEffect, useRef } from 'react';
import { 
  Package, 
  Building, 
  Plus, 
  Search, 
  Check, 
  X,
  Phone, 
  Mail,
  Network,
  FolderTree,
  Trash2,
  Tag,
  Layers,
  Sparkles,
  ChevronRight,
  Info
} from 'lucide-react';
import { InventoryItem, Supplier, User, StoreHierarchyItem } from '../types';
import { supabase, uploadPoAttachment, fileToOptimizedDataUrl } from '../lib/supabase';
import { 
  getStoreHierarchy, 
  saveStoreHierarchyItem, 
  deleteStoreHierarchyItem 
} from '../lib/storeHierarchy';

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
  const [subTab, setSubTab] = useState<'hierarchy' | 'suppliers'>('hierarchy');
  const [searchQuery, setSearchQuery] = useState('');

  // Store Hierarchy State
  const [hierarchyList, setHierarchyList] = useState<StoreHierarchyItem[]>([]);
  const [loadingHierarchy, setLoadingHierarchy] = useState(false);

  // Modals
  const [isAddProductOpen, setIsAddProductOpen] = useState(false);
  const [isAddSupplierOpen, setIsAddSupplierOpen] = useState(false);
  const [isAddHierarchyOpen, setIsAddHierarchyOpen] = useState(false);

  // Add Supplier Form
  const [suppCode, setSuppCode] = useState('');
  const [suppName, setSuppName] = useState('');
  const [contactPerson, setContactPerson] = useState('');
  const [phone, setPhone] = useState('');
  const [email, setEmail] = useState('');
  const [gstNumber, setGstNumber] = useState('');

  // Quick Add Store Hierarchy Modal Form
  const [newHDivision, setNewHDivision] = useState('');
  const [newHSection, setNewHSection] = useState('');
  const [newHDepartment, setNewHDepartment] = useState('');
  const [newHType, setNewHType] = useState<'Textile' | 'Readymade'>('Readymade');

  // Load Hierarchy on Mount
  const loadHierarchy = async () => {
    setLoadingHierarchy(true);
    try {
      const data = await getStoreHierarchy();
      setHierarchyList(data);
    } catch (err) {
      console.error('Failed to load store hierarchy', err);
    } finally {
      setLoadingHierarchy(false);
    }
  };

  useEffect(() => {
    loadHierarchy();
  }, []);

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
        payment_terms: '',
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

  const handleCreateHierarchyItem = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newHDivision.trim() || !newHSection.trim() || !newHDepartment.trim()) {
      alert('Please enter Division, Section, and Department');
      return;
    }

    try {
      await saveStoreHierarchyItem({
        division: newHDivision.trim(),
        section: newHSection.trim(),
        department: newHDepartment.trim(),
        type: newHType
      });
      await loadHierarchy();
      setIsAddHierarchyOpen(false);
      setNewHDivision('');
      setNewHSection('');
      setNewHDepartment('');
    } catch (err: any) {
      alert(`Error adding hierarchy level: ${err.message}`);
    }
  };

  const handleDeleteHierarchyItem = async (item: StoreHierarchyItem) => {
    if (!confirm(`Remove hierarchy: ${item.division} > ${item.section} > ${item.department}?`)) {
      return;
    }
    await deleteStoreHierarchyItem(item);
    await loadHierarchy();
  };

  const filteredSuppliers = suppliers.filter(s => 
    s.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    s.code.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const filteredHierarchy = hierarchyList.filter(h => 
    h.division.toLowerCase().includes(searchQuery.toLowerCase()) ||
    h.section.toLowerCase().includes(searchQuery.toLowerCase()) ||
    h.department.toLowerCase().includes(searchQuery.toLowerCase()) ||
    h.type.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
    <div className="space-y-6">
      {/* Sub Tabs & Action Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl shadow-sm border border-slate-200/80">
        <div className="flex items-center gap-2 overflow-x-auto pb-1 sm:pb-0">
          <button
            onClick={() => setSubTab('hierarchy')}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-bold transition-all whitespace-nowrap ${
              subTab === 'hierarchy'
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            <FolderTree className="w-4 h-4" /> Store Hierarchy (3-Level)
          </button>
          <button
            onClick={() => setSubTab('suppliers')}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-bold transition-all whitespace-nowrap ${
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
              placeholder={`Search ${subTab === 'hierarchy' ? 'hierarchy...' : subTab + '...'}`}
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

          {isAdmin && subTab === 'hierarchy' && (
            <button
              onClick={() => setIsAddHierarchyOpen(true)}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs shadow-sm transition-all whitespace-nowrap"
            >
              <Plus className="w-4 h-4" /> Add Hierarchy Level
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

      {/* SUBTAB 1: STORE HIERARCHY MANAGER */}
      {subTab === 'hierarchy' && (
        <div className="space-y-6">
          {/* Header Info Box */}
          <div className="bg-gradient-to-r from-indigo-50 via-sky-50 to-emerald-50 p-5 rounded-2xl border border-indigo-100 flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div className="flex items-start gap-3">
              <div className="p-2.5 bg-indigo-600 text-white rounded-xl shadow-sm mt-0.5">
                <Network className="w-5 h-5" />
              </div>
              <div>
                <h3 className="font-bold text-slate-900 text-base">Store Classification Architecture</h3>
                <p className="text-xs text-slate-600 mt-0.5">
                  Strict 3-Level Cascading Hierarchy: <strong className="text-indigo-900">Division</strong> &rarr; <strong className="text-indigo-900">Section</strong> &rarr; <strong className="text-indigo-900">Department</strong> + <strong className="text-indigo-900">Tax Type</strong>.
                </p>
                <div className="flex flex-wrap items-center gap-3 mt-2 text-xs">
                  <span className="inline-flex items-center gap-1 px-2.5 py-1 bg-white rounded-lg border border-slate-200 text-slate-700 shadow-2xs">
                    🧵 <strong>Textile:</strong> Flat 5% GST (Fabrics, Sarees, Handlooms)
                  </span>
                  <span className="inline-flex items-center gap-1 px-2.5 py-1 bg-white rounded-lg border border-slate-200 text-slate-700 shadow-2xs">
                    👔 <strong>Readymade:</strong> 5% GST (&lt; ₹2,500) | 18% GST (&ge; ₹2,500)
                  </span>
                </div>
              </div>
            </div>

            {isAdmin && (
              <button
                onClick={() => setIsAddHierarchyOpen(true)}
                className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-bold text-xs shadow-md transition-all whitespace-nowrap self-start md:self-auto"
              >
                <Plus className="w-4 h-4" /> Define New Hierarchy
              </button>
            )}
          </div>

          {/* Hierarchy Table View */}
          <div className="bg-white rounded-2xl shadow-sm border border-slate-200/80 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 border-b border-slate-200 text-xs uppercase font-bold text-slate-500">
                  <tr>
                    <th className="py-3 px-4">Level 1: Division</th>
                    <th className="py-3 px-4">Level 2: Section</th>
                    <th className="py-3 px-4">Level 3: Department</th>
                    <th className="py-3 px-4">Tax Type &amp; Statutory GST Rule</th>
                    {isAdmin && <th className="py-3 px-4 text-right">Actions</th>}
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredHierarchy.map((item, idx) => (
                    <tr key={item.id || idx} className="hover:bg-slate-50/50">
                      <td className="py-3.5 px-4 font-bold text-slate-900">
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-indigo-50 text-indigo-700 border border-indigo-100 font-bold">
                          {item.division}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-slate-700 font-semibold">
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-slate-100 text-slate-800">
                          {item.section}
                        </span>
                      </td>
                      <td className="py-3.5 px-4 text-slate-900 font-bold">
                        <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-emerald-50 text-emerald-800 border border-emerald-100">
                          {item.department}
                        </span>
                      </td>
                      <td className="py-3.5 px-4">
                        {item.type === 'Textile' ? (
                          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-amber-100 text-amber-900 border border-amber-200">
                            🧵 Textile &bull; Flat 5% GST
                          </div>
                        ) : (
                          <div className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-bold bg-blue-100 text-blue-900 border border-blue-200">
                            👔 Readymade &bull; Slabs (5% &lt; ₹2500, 18% &ge; ₹2500)
                          </div>
                        )}
                      </td>
                      {isAdmin && (
                        <td className="py-3.5 px-4 text-right">
                          <button
                            onClick={() => handleDeleteHierarchyItem(item)}
                            title="Delete Hierarchy Entry"
                            className="p-1.5 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        </td>
                      )}
                    </tr>
                  ))}
                  {filteredHierarchy.length === 0 && (
                    <tr>
                      <td colSpan={isAdmin ? 5 : 4} className="py-16 text-center">
                        <div className="flex flex-col items-center justify-center max-w-md mx-auto space-y-3">
                          <div className="w-12 h-12 rounded-2xl bg-indigo-50 border border-indigo-100 flex items-center justify-center text-indigo-600">
                            <FolderTree className="w-6 h-6" />
                          </div>
                          <div>
                            <h4 className="font-bold text-slate-800 text-sm">No Store Hierarchy Defined Yet</h4>
                            <p className="text-xs text-slate-500 mt-1">
                              Start by adding your first Division (e.g. MENS, WOMENS, FABRICS), then organize by Section &gt; Department.
                            </p>
                          </div>
                          {isAdmin && (
                            <button
                              onClick={() => setIsAddHierarchyOpen(true)}
                              className="inline-flex items-center gap-1.5 px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs shadow-sm transition-all"
                            >
                              <Plus className="w-4 h-4" /> + Add Division / Hierarchy
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}

      {/* SUBTAB 3: SUPPLIERS */}
      {subTab === 'suppliers' && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredSuppliers.map(s => (
            <div key={s.id} className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm space-y-3">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold px-2 py-0.5 rounded-md bg-slate-100 text-slate-700 border">
                  {s.code}
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

      {/* MODAL 1: ADD HIERARCHY LEVEL */}
      
      {isAddHierarchyOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm overflow-y-auto p-2 sm:p-4">
          <div className="min-h-full flex items-center justify-center py-6 sm:py-8">
            <div className="bg-white rounded-3xl max-w-md w-full p-5 sm:p-6 shadow-2xl border border-slate-100 my-auto">
              <div className="flex items-center justify-between pb-3 border-b">
                <div>
                  <h3 className="font-bold text-slate-900">Define Store Hierarchy</h3>
                  <p className="text-xs text-slate-500">Create Division &gt; Section &gt; Department</p>
                </div>
                <button onClick={() => setIsAddHierarchyOpen(false)}><X className="w-5 h-5 text-slate-400" /></button>
              </div>

            <form onSubmit={handleCreateHierarchyItem} className="mt-4 space-y-3">
              <div>
                <label className="text-xs font-semibold text-slate-700">1. Division *</label>
                <input
                  type="text"
                  placeholder="e.g. Men, Women, Kids, Furnishing"
                  value={newHDivision}
                  onChange={(e) => setNewHDivision(e.target.value)}
                  className="w-full px-3 py-2 border rounded-xl text-sm mt-1"
                  required
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-700">2. Section *</label>
                <input
                  type="text"
                  placeholder="e.g. Formals, Casuals, Ethnic Wear"
                  value={newHSection}
                  onChange={(e) => setNewHSection(e.target.value)}
                  className="w-full px-3 py-2 border rounded-xl text-sm mt-1"
                  required
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-700">3. Department *</label>
                <input
                  type="text"
                  placeholder="e.g. Shirts, Sarees, Trousers, Kurtis"
                  value={newHDepartment}
                  onChange={(e) => setNewHDepartment(e.target.value)}
                  className="w-full px-3 py-2 border rounded-xl text-sm mt-1"
                  required
                />
              </div>

              <div>
                <label className="text-xs font-semibold text-slate-700">Tax Type *</label>
                <div className="grid grid-cols-2 gap-2 mt-1">
                  <label className={`flex items-center gap-2 p-2.5 rounded-xl border cursor-pointer text-xs font-semibold ${
                    newHType === 'Textile' ? 'bg-amber-50 border-amber-400 text-amber-900' : 'bg-white border-slate-200'
                  }`}>
                    <input
                      type="radio"
                      name="newHType"
                      checked={newHType === 'Textile'}
                      onChange={() => setNewHType('Textile')}
                    />
                    🧵 Textile (5%)
                  </label>
                  <label className={`flex items-center gap-2 p-2.5 rounded-xl border cursor-pointer text-xs font-semibold ${
                    newHType === 'Readymade' ? 'bg-blue-50 border-blue-400 text-blue-900' : 'bg-white border-slate-200'
                  }`}>
                    <input
                      type="radio"
                      name="newHType"
                      checked={newHType === 'Readymade'}
                      onChange={() => setNewHType('Readymade')}
                    />
                    👔 Readymade
                  </label>
                </div>
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t">
                <button type="button" onClick={() => setIsAddHierarchyOpen(false)} className="px-3 py-1.5 text-xs font-semibold text-slate-600">
                  Cancel
                </button>
                <button type="submit" className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold">
                  Save Hierarchy Level
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
      )}

      {/* MODAL 3: ADD SUPPLIER */}
      {isAddSupplierOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm overflow-y-auto p-2 sm:p-4">
          <div className="min-h-full flex items-center justify-center py-6 sm:py-8">
            <div className="bg-white rounded-3xl max-w-md w-full p-5 sm:p-6 shadow-2xl border border-slate-100 my-auto">
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
      </div>
      )}

      </div>
  );
};
