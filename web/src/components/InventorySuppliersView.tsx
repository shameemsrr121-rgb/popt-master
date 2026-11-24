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
  Info,
  Camera,
  Upload,
  Image as ImageIcon
} from 'lucide-react';
import { InventoryItem, Supplier, User, StoreHierarchyItem } from '../types';
import { supabase, uploadPoAttachment, fileToOptimizedDataUrl } from '../lib/supabase';
import { 
  getStoreHierarchy, 
  saveStoreHierarchyItem, 
  deleteStoreHierarchyItem 
} from '../lib/storeHierarchy';
import { getTaxFlowBadge } from '../lib/taxMaster';
import { CameraCaptureDialog } from './CameraCaptureDialog';

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
  const [subTab, setSubTab] = useState<'inventory' | 'suppliers' | 'hierarchy'>('inventory');
  const [searchQuery, setSearchQuery] = useState('');

  // Store Hierarchy State
  const [hierarchyList, setHierarchyList] = useState<StoreHierarchyItem[]>([]);
  const [loadingHierarchy, setLoadingHierarchy] = useState(false);

  // Modals
  const [isAddProductOpen, setIsAddProductOpen] = useState(false);
  const [isAddSupplierOpen, setIsAddSupplierOpen] = useState(false);
  const [isAddHierarchyOpen, setIsAddHierarchyOpen] = useState(false);

  // Add Product Form: 3-Level Hierarchy & Tax Type
  const [name, setName] = useState('');
  const [selectedDivision, setSelectedDivision] = useState('');
  const [isCustomDivision, setIsCustomDivision] = useState(false);
  const [customDivisionText, setCustomDivisionText] = useState('');

  const [selectedSection, setSelectedSection] = useState('');
  const [isCustomSection, setIsCustomSection] = useState(false);
  const [customSectionText, setCustomSectionText] = useState('');

  const [selectedDepartment, setSelectedDepartment] = useState('');
  const [isCustomDepartment, setIsCustomDepartment] = useState(false);
  const [customDepartmentText, setCustomDepartmentText] = useState('');

  const [productType, setProductType] = useState<'Textile' | 'Readymade'>('Readymade');
  const [unitCost, setUnitCost] = useState('500');
  const [sellingPrice, setSellingPrice] = useState('999');
  const [currentStock, setCurrentStock] = useState('50');
  const [minReorder, setMinReorder] = useState('10');
  const [selectedPhotoFile, setSelectedPhotoFile] = useState<File | null>(null);
  const [previewPhotoUrl, setPreviewPhotoUrl] = useState<string | null>(null);
  const [isCameraOpen, setIsCameraOpen] = useState(false);
  const fileUploadInputRef = useRef<HTMLInputElement | null>(null);

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

  // Cascading options derived from hierarchyList
  const divisionOptions = Array.from(new Set(hierarchyList.map(h => h.division).filter(Boolean)));

  const sectionOptions = Array.from(new Set(
    hierarchyList
      .filter(h => h.division.toLowerCase() === (isCustomDivision ? customDivisionText.trim().toLowerCase() : selectedDivision.toLowerCase()))
      .map(h => h.section)
      .filter(Boolean)
  ));

  const departmentMatches = hierarchyList.filter(
    h => h.division.toLowerCase() === (isCustomDivision ? customDivisionText.trim().toLowerCase() : selectedDivision.toLowerCase()) &&
         h.section.toLowerCase() === (isCustomSection ? customSectionText.trim().toLowerCase() : selectedSection.toLowerCase())
  );
  const departmentOptions = Array.from(new Set(departmentMatches.map(h => h.department).filter(Boolean)));

  // When division changes, adjust section
  const handleDivisionChange = (div: string) => {
    if (div === '__NEW__') {
      setIsCustomDivision(true);
      setSelectedDivision('');
      setIsCustomSection(true);
      setSelectedSection('');
      setIsCustomDepartment(true);
      setSelectedDepartment('');
      return;
    }
    setIsCustomDivision(false);
    setSelectedDivision(div);
    setIsCustomSection(false);

    // Filter available sections for this division
    const availableSections = Array.from(new Set(
      hierarchyList
        .filter(h => h.division.toLowerCase() === div.toLowerCase())
        .map(h => h.section)
        .filter(Boolean)
    ));
    if (availableSections.length > 0) {
      const firstSec = availableSections[0];
      setSelectedSection(firstSec);
      setIsCustomDepartment(false);
      const availableDepts = hierarchyList.filter(
        h => h.division.toLowerCase() === div.toLowerCase() && h.section.toLowerCase() === firstSec.toLowerCase()
      );
      if (availableDepts.length > 0) {
        setSelectedDepartment(availableDepts[0].department);
        setProductType(availableDepts[0].type);
      } else {
        setSelectedDepartment('');
      }
    } else {
      setSelectedSection('');
      setSelectedDepartment('');
    }
  };

  // When section changes, adjust department
  const handleSectionChange = (sec: string) => {
    if (sec === '__NEW__') {
      setIsCustomSection(true);
      setSelectedSection('');
      setIsCustomDepartment(true);
      setSelectedDepartment('');
      return;
    }
    setIsCustomSection(false);
    setSelectedSection(sec);
    setIsCustomDepartment(false);

    const activeDiv = isCustomDivision ? customDivisionText.trim() : selectedDivision;
    const availableDepts = hierarchyList.filter(
      h => h.division.toLowerCase() === activeDiv.toLowerCase() && h.section.toLowerCase() === sec.toLowerCase()
    );
    if (availableDepts.length > 0) {
      setSelectedDepartment(availableDepts[0].department);
      setProductType(availableDepts[0].type);
    } else {
      setSelectedDepartment('');
    }
  };

  // When department changes, auto-select type
  const handleDepartmentChange = (dept: string) => {
    if (dept === '__NEW__') {
      setIsCustomDepartment(true);
      setSelectedDepartment('');
      return;
    }
    setIsCustomDepartment(false);
    setSelectedDepartment(dept);

    const activeDiv = isCustomDivision ? customDivisionText.trim() : selectedDivision;
    const activeSec = isCustomSection ? customSectionText.trim() : selectedSection;
    const match = hierarchyList.find(
      h => h.division.toLowerCase() === activeDiv.toLowerCase() &&
           h.section.toLowerCase() === activeSec.toLowerCase() &&
           h.department.toLowerCase() === dept.toLowerCase()
    );
    if (match) {
      setProductType(match.type);
    }
  };

  // Open modal - start fresh without forcing hardcoded selections
  const handleOpenAddProduct = () => {
    // If divisions exist and user hasn't selected anything yet, default to first division
    if (divisionOptions.length > 0 && !selectedDivision && !isCustomDivision) {
      const firstDiv = divisionOptions[0];
      setSelectedDivision(firstDiv);
      setIsCustomDivision(false);

      const availSecs = Array.from(new Set(
        hierarchyList
          .filter(h => h.division.toLowerCase() === firstDiv.toLowerCase())
          .map(h => h.section)
          .filter(Boolean)
      ));
      if (availSecs.length > 0) {
        setSelectedSection(availSecs[0]);
        setIsCustomSection(false);

        const availDepts = hierarchyList.filter(
          h => h.division.toLowerCase() === firstDiv.toLowerCase() && h.section.toLowerCase() === availSecs[0].toLowerCase()
        );
        if (availDepts.length > 0) {
          setSelectedDepartment(availDepts[0].department);
          setProductType(availDepts[0].type);
          setIsCustomDepartment(false);
        } else {
          setSelectedDepartment('');
        }
      } else {
        setSelectedSection('');
        setSelectedDepartment('');
      }
    } else if (divisionOptions.length === 0) {
      // No divisions exist yet! Prompt user directly to add division
      setSelectedDivision('');
      setIsCustomDivision(true);
      setCustomDivisionText('');
      setSelectedSection('');
      setIsCustomSection(true);
      setCustomSectionText('');
      setSelectedDepartment('');
      setIsCustomDepartment(true);
      setCustomDepartmentText('');
    }
    setIsAddProductOpen(true);
  };

  const handleCreateProduct = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) {
      alert('Please enter Item Name');
      return;
    }

    const autoSku = `PROD-${Date.now().toString().slice(-6)}`;

    const finalDivision = (isCustomDivision ? customDivisionText.trim() : selectedDivision.trim());
    const finalSection = (isCustomSection ? customSectionText.trim() : selectedSection.trim());
    const finalDepartment = (isCustomDepartment ? customDepartmentText.trim() : selectedDepartment.trim());
    const finalType = productType;

    if (!finalDivision) {
      alert('Please specify a Division for this product');
      return;
    }
    if (!finalSection) {
      alert('Please specify a Section under Division: ' + finalDivision);
      return;
    }
    if (!finalDepartment) {
      alert('Please specify a Department under Section: ' + finalSection);
      return;
    }

    try {
      let uploadedPhotoUrl: string | null = null;
      if (selectedPhotoFile) {
        uploadedPhotoUrl = await uploadPoAttachment(selectedPhotoFile, 'inventory');
        if (!uploadedPhotoUrl && previewPhotoUrl) {
          uploadedPhotoUrl = previewPhotoUrl.startsWith('data:')
            ? previewPhotoUrl
            : await fileToOptimizedDataUrl(selectedPhotoFile);
        }
      }

      // 1. Insert into inventory (storing type in category column and as part of inventory record)
      const { error } = await supabase.from('inventory').insert({
        sku: autoSku,
        name: name.trim(),
        division: finalDivision,
        section: finalSection,
        department: finalDepartment,
        category: finalType, // Storing 'Textile' or 'Readymade'
        unit: 'Pcs',
        unit_cost: Number(unitCost) || 0,
        standard_selling_price: Number(sellingPrice) || 0,
        current_stock: Number(currentStock) || 0,
        min_reorder_level: Number(minReorder) || 10,
        photo_uri: uploadedPhotoUrl
      });

      if (error) {
        // Fallback without photo_uri column if table schema doesn't have it
        if (error.message?.includes('photo_uri') || error.code === 'PGRST204') {
          const { error: retryError } = await supabase.from('inventory').insert({
            sku: autoSku,
            name: name.trim(),
            division: finalDivision,
            section: finalSection,
            department: finalDepartment,
            category: finalType,
            unit: 'Pcs',
            unit_cost: Number(unitCost) || 0,
            standard_selling_price: Number(sellingPrice) || 0,
            current_stock: Number(currentStock) || 0,
            min_reorder_level: Number(minReorder) || 10
          });
          if (retryError) throw retryError;
        } else {
          throw error;
        }
      }

      // 2. Persist this hierarchy combination so future products benefit from it
      await saveStoreHierarchyItem({
        division: finalDivision,
        section: finalSection,
        department: finalDepartment,
        type: finalType
      });

      // Refresh local hierarchy list
      await loadHierarchy();

      setIsAddProductOpen(false);
      setName('');
      setSelectedPhotoFile(null);
      setPreviewPhotoUrl(null);
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

  const filteredInventory = inventory.filter(i => 
    i.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
    i.sku.toLowerCase().includes(searchQuery.toLowerCase()) ||
    (i.division && i.division.toLowerCase().includes(searchQuery.toLowerCase())) ||
    (i.section && i.section.toLowerCase().includes(searchQuery.toLowerCase())) ||
    (i.department && i.department.toLowerCase().includes(searchQuery.toLowerCase())) ||
    (i.category && i.category.toLowerCase().includes(searchQuery.toLowerCase()))
  );

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

  // Active Tax Badge preview for Add Product Modal
  const activeDivName = isCustomDivision ? customDivisionText : selectedDivision;
  const activeSecName = isCustomSection ? customSectionText : selectedSection;
  const activeDeptName = isCustomDepartment ? customDepartmentText : selectedDepartment;
  const costVal = Number(unitCost) || 0;
  const currentTaxBadge = getTaxFlowBadge({
    itemName: name,
    category: productType,
    type: productType,
    division: activeDivName,
    section: activeSecName,
    department: activeDeptName,
    unitCost: costVal
  });

  return (
    <div className="space-y-6">
      {/* Sub Tabs & Action Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl shadow-sm border border-slate-200/80">
        <div className="flex items-center gap-2 overflow-x-auto pb-1 sm:pb-0">
          <button
            onClick={() => setSubTab('inventory')}
            className={`flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-bold transition-all whitespace-nowrap ${
              subTab === 'inventory'
                ? 'bg-indigo-600 text-white shadow-sm'
                : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            <Package className="w-4 h-4" /> Inventory Master
          </button>
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

          {isAdmin && subTab === 'inventory' && (
            <button
              onClick={handleOpenAddProduct}
              className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs shadow-sm transition-all whitespace-nowrap"
            >
              <Plus className="w-4 h-4" /> Add Product
            </button>
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

      {/* SUBTAB 1: INVENTORY TABLE */}
      {subTab === 'inventory' && (
        <div className="bg-white rounded-2xl shadow-sm border border-slate-200/80 overflow-hidden">
          {filteredInventory.length === 0 ? (
            <div className="py-16 text-center text-slate-400">
              <Package className="w-12 h-12 mx-auto text-slate-300 mb-3" />
              <p className="text-base font-semibold text-slate-700">No inventory items found</p>
              {isAdmin && (
                <button
                  onClick={handleOpenAddProduct}
                  className="mt-3 px-4 py-2 bg-indigo-600 text-white text-xs font-bold rounded-xl shadow-sm hover:bg-indigo-700 inline-flex items-center gap-1.5"
                >
                  <Plus className="w-3.5 h-3.5" /> Create First Product
                </button>
              )}
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 border-b border-slate-200 text-xs uppercase font-bold text-slate-500">
                  <tr>
                    <th className="py-3 px-4">Photo</th>
                    <th className="py-3 px-4">Item Name</th>
                    <th className="py-3 px-4">Hierarchy (Div &gt; Sec &gt; Dept)</th>
                    <th className="py-3 px-4 text-center">Tax Type</th>
                    <th className="py-3 px-4 text-right">Cost Price</th>
                    <th className="py-3 px-4 text-right">Selling Price</th>
                    <th className="py-3 px-4 text-center">Stock</th>
                    <th className="py-3 px-4 text-center">Stock Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {filteredInventory.map(item => {
                    const isLowStock = item.current_stock <= item.min_reorder_level;
                    const taxType = item.category === 'Textile' || item.type === 'Textile' ? 'Textile' : 'Readymade';
                    return (
                      <tr key={item.id} className="hover:bg-slate-50/50">
                        <td className="py-3 px-4">
                          <div className="flex items-center gap-2">
                            {item.photo_uri ? (
                              <img
                                src={item.photo_uri}
                                alt={item.name}
                                className="w-8 h-8 rounded-lg object-cover border border-slate-200 shadow-2xs flex-shrink-0"
                              />
                            ) : (
                              <div className="w-8 h-8 rounded-lg bg-slate-100 flex items-center justify-center text-slate-400 flex-shrink-0">
                                <Package className="w-4 h-4" />
                              </div>
                            )}
                          </div>
                        </td>
                        <td className="py-3 px-4 font-semibold text-slate-900">{item.name}</td>
                        <td className="py-3 px-4">
                          <div className="flex items-center gap-1 text-xs text-slate-700">
                            <span className="font-semibold text-slate-900 bg-slate-100 px-2 py-0.5 rounded-md">
                              {item.division || 'General'}
                            </span>
                            <ChevronRight className="w-3 h-3 text-slate-400" />
                            <span className="text-slate-600 bg-slate-50 px-2 py-0.5 rounded-md">
                              {item.section || 'General'}
                            </span>
                            <ChevronRight className="w-3 h-3 text-slate-400" />
                            <span className="text-indigo-600 bg-indigo-50 px-2 py-0.5 rounded-md font-medium">
                              {item.department || 'General'}
                            </span>
                          </div>
                        </td>
                        <td className="py-3 px-4 text-center">
                          {taxType === 'Textile' ? (
                            <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-amber-100 text-amber-800">
                              🧵 Textile (5%)
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-blue-100 text-blue-800">
                              👔 Readymade
                            </span>
                          )}
                        </td>
                        <td className="py-3 px-4 text-right text-slate-600">₹{Number(item.unit_cost).toFixed(2)}</td>
                        <td className="py-3 px-4 text-right font-bold text-slate-900">₹{Number(item.standard_selling_price).toFixed(2)}</td>
                        <td className="py-3 px-4 text-center font-extrabold text-slate-800">{item.current_stock}</td>
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

      {/* SUBTAB 2: STORE HIERARCHY MANAGER */}
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

      {/* MODAL 1: ADD PRODUCT WITH 3-LEVEL CASCADING HIERARCHY + TAX TYPE */}
      {isAddProductOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-3xl max-w-lg w-full p-6 shadow-2xl border border-slate-100 my-8">
            <div className="flex items-center justify-between pb-3 border-b">
              <div>
                <h3 className="font-bold text-slate-900 text-lg">Add Inventory Item</h3>
                <p className="text-xs text-slate-500">3-Level Store Hierarchy &amp; Statutory GST</p>
              </div>
              <button 
                onClick={() => setIsAddProductOpen(false)}
                className="p-1.5 text-slate-400 hover:text-slate-600 hover:bg-slate-100 rounded-xl transition-all"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleCreateProduct} className="mt-4 space-y-4">
              {/* Product Info */}
              <div>
                <label className="text-xs font-bold text-slate-700">Item Name *</label>
                <input
                  type="text"
                  placeholder="e.g. Formal White Linen Shirt"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  className="w-full px-3 py-2 border rounded-xl text-sm focus:ring-2 focus:ring-indigo-500 mt-1"
                  required
                />
              </div>

              {/* 3-LEVEL CASCADING HIERARCHY BOX */}
              <div className="p-4 bg-slate-50 rounded-2xl border border-slate-200/80 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold uppercase tracking-wider text-indigo-700 flex items-center gap-1.5">
                    <Layers className="w-4 h-4" /> 3-Level Store Hierarchy
                  </span>
                  <span className="text-[11px] text-slate-500">Cascaded Level Selection</span>
                </div>

                {/* Level 1: Division */}
                <div>
                  <label className="text-xs font-semibold text-slate-700 flex items-center justify-between">
                    <span>1. Division (Root Level) *</span>
                    {divisionOptions.length > 0 && isCustomDivision && (
                      <button 
                        type="button" 
                        onClick={() => {
                          setIsCustomDivision(false);
                          if (divisionOptions.length > 0) handleDivisionChange(divisionOptions[0]);
                        }} 
                        className="text-[11px] text-indigo-600 hover:underline font-semibold"
                      >
                        Choose existing
                      </button>
                    )}
                  </label>
                  {divisionOptions.length === 0 ? (
                    <div className="space-y-1.5 mt-1">
                      <input
                        type="text"
                        placeholder="Add your first Division (e.g. MENS, WOMENS, FABRICS)"
                        value={customDivisionText}
                        onChange={(e) => {
                          setCustomDivisionText(e.target.value);
                          setIsCustomDivision(true);
                        }}
                        className="w-full px-3 py-2 border-2 border-indigo-400 rounded-xl text-sm bg-indigo-50/40 font-semibold focus:ring-2 focus:ring-indigo-500"
                        required
                      />
                      <p className="text-[11px] text-indigo-600 font-medium">✨ Enter your store's primary division to start hierarchy</p>
                    </div>
                  ) : !isCustomDivision ? (
                    <div className="flex gap-2 mt-1">
                      <select
                        value={selectedDivision}
                        onChange={(e) => handleDivisionChange(e.target.value)}
                        className="flex-1 px-3 py-2 border rounded-xl text-sm bg-white font-medium focus:ring-2 focus:ring-indigo-500"
                        required
                      >
                        <option value="">-- Select Division --</option>
                        {divisionOptions.map(div => (
                          <option key={div} value={div}>{div}</option>
                        ))}
                      </select>
                      <button
                        type="button"
                        onClick={() => {
                          setIsCustomDivision(true);
                          setSelectedDivision('');
                          setCustomDivisionText('');
                          setIsCustomSection(true);
                          setSelectedSection('');
                          setCustomSectionText('');
                          setIsCustomDepartment(true);
                          setSelectedDepartment('');
                          setCustomDepartmentText('');
                        }}
                        className="px-3 py-2 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-bold text-xs rounded-xl border border-indigo-200 whitespace-nowrap transition-colors"
                      >
                        + New Division
                      </button>
                    </div>
                  ) : (
                    <div className="space-y-1 mt-1">
                      <input
                        type="text"
                        placeholder="Enter new division name (e.g. MENS, WOMENS)"
                        value={customDivisionText}
                        onChange={(e) => setCustomDivisionText(e.target.value)}
                        className="w-full px-3 py-2 border border-indigo-300 rounded-xl text-sm bg-indigo-50/50 font-semibold focus:ring-2 focus:ring-indigo-500"
                        required
                      />
                    </div>
                  )}
                </div>

                {/* Level 2: Section (Cascaded) */}
                <div>
                  <label className="text-xs font-semibold text-slate-700 flex items-center justify-between">
                    <span>2. Section (Under {activeDivName || 'selected division'}) *</span>
                    {sectionOptions.length > 0 && isCustomSection && (
                      <button 
                        type="button" 
                        onClick={() => {
                          setIsCustomSection(false);
                          if (sectionOptions.length > 0) handleSectionChange(sectionOptions[0]);
                        }} 
                        className="text-[11px] text-indigo-600 hover:underline font-semibold"
                      >
                        Choose existing
                      </button>
                    )}
                  </label>
                  {!activeDivName ? (
                    <div className="px-3 py-2 border rounded-xl text-sm bg-slate-50 text-slate-400 mt-1 italic">
                      Please select or enter a Division first
                    </div>
                  ) : sectionOptions.length === 0 || isCustomSection ? (
                    <div className="space-y-1 mt-1">
                      <input
                        type="text"
                        placeholder={`Enter new section for ${activeDivName} (e.g. FORMALS, CASUALS)`}
                        value={customSectionText}
                        onChange={(e) => {
                          setCustomSectionText(e.target.value);
                          setIsCustomSection(true);
                        }}
                        className="w-full px-3 py-2 border border-indigo-300 rounded-xl text-sm bg-indigo-50/50 font-semibold focus:ring-2 focus:ring-indigo-500"
                        required
                      />
                      {sectionOptions.length === 0 && (
                        <p className="text-[11px] text-slate-500">No existing sections for {activeDivName}. Type a new one.</p>
                      )}
                    </div>
                  ) : (
                    <div className="flex gap-2 mt-1">
                      <select
                        value={selectedSection}
                        onChange={(e) => handleSectionChange(e.target.value)}
                        className="flex-1 px-3 py-2 border rounded-xl text-sm bg-white font-medium focus:ring-2 focus:ring-indigo-500"
                        required
                      >
                        <option value="">-- Select Section --</option>
                        {sectionOptions.map(sec => (
                          <option key={sec} value={sec}>{sec}</option>
                        ))}
                      </select>
                      <button
                        type="button"
                        onClick={() => {
                          setIsCustomSection(true);
                          setSelectedSection('');
                          setCustomSectionText('');
                          setIsCustomDepartment(true);
                          setSelectedDepartment('');
                          setCustomDepartmentText('');
                        }}
                        className="px-3 py-2 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-bold text-xs rounded-xl border border-indigo-200 whitespace-nowrap transition-colors"
                      >
                        + New Section
                      </button>
                    </div>
                  )}
                </div>

                {/* Level 3: Department (Cascaded) */}
                <div>
                  <label className="text-xs font-semibold text-slate-700 flex items-center justify-between">
                    <span>3. Department (Under {activeSecName || 'selected section'}) *</span>
                    {departmentOptions.length > 0 && isCustomDepartment && (
                      <button 
                        type="button" 
                        onClick={() => {
                          setIsCustomDepartment(false);
                          if (departmentOptions.length > 0) handleDepartmentChange(departmentOptions[0]);
                        }} 
                        className="text-[11px] text-indigo-600 hover:underline font-semibold"
                      >
                        Choose existing
                      </button>
                    )}
                  </label>
                  {!activeSecName ? (
                    <div className="px-3 py-2 border rounded-xl text-sm bg-slate-50 text-slate-400 mt-1 italic">
                      Please select or enter a Section first
                    </div>
                  ) : departmentOptions.length === 0 || isCustomDepartment ? (
                    <div className="space-y-1 mt-1">
                      <input
                        type="text"
                        placeholder={`Enter new department for ${activeSecName} (e.g. COTTON SHIRTS, LINEN)`}
                        value={customDepartmentText}
                        onChange={(e) => {
                          setCustomDepartmentText(e.target.value);
                          setIsCustomDepartment(true);
                        }}
                        className="w-full px-3 py-2 border border-indigo-300 rounded-xl text-sm bg-indigo-50/50 font-semibold focus:ring-2 focus:ring-indigo-500"
                        required
                      />
                      {departmentOptions.length === 0 && (
                        <p className="text-[11px] text-slate-500">No existing departments for {activeSecName}. Type a new one.</p>
                      )}
                    </div>
                  ) : (
                    <div className="flex gap-2 mt-1">
                      <select
                        value={selectedDepartment}
                        onChange={(e) => handleDepartmentChange(e.target.value)}
                        className="flex-1 px-3 py-2 border rounded-xl text-sm bg-white font-medium focus:ring-2 focus:ring-indigo-500"
                        required
                      >
                        <option value="">-- Select Department --</option>
                        {departmentOptions.map(dept => (
                          <option key={dept} value={dept}>{dept}</option>
                        ))}
                      </select>
                      <button
                        type="button"
                        onClick={() => {
                          setIsCustomDepartment(true);
                          setSelectedDepartment('');
                          setCustomDepartmentText('');
                        }}
                        className="px-3 py-2 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 font-bold text-xs rounded-xl border border-indigo-200 whitespace-nowrap transition-colors"
                      >
                        + New Dept
                      </button>
                    </div>
                  )}
                </div>
              </div>

              {/* TAX TYPE TOGGLE (TEXTILE VS READYMADE) */}
              <div className="p-4 bg-slate-50 rounded-2xl border border-slate-200/80 space-y-2">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-bold uppercase tracking-wider text-slate-700 flex items-center gap-1.5">
                    <Tag className="w-4 h-4 text-indigo-600" /> Statutory Tax Classification Type *
                  </label>
                  <span className="text-[11px] text-slate-500">Required for GST Engine</span>
                </div>

                <div className="grid grid-cols-2 gap-3 pt-1">
                  <button
                    type="button"
                    onClick={() => setProductType('Textile')}
                    className={`p-3 rounded-xl border text-left transition-all ${
                      productType === 'Textile'
                        ? 'border-amber-500 bg-amber-50/80 ring-2 ring-amber-500/20'
                        : 'border-slate-200 bg-white hover:bg-slate-100/60'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <span className="text-base">🧵</span>
                      <span className="font-bold text-xs text-slate-900">Textile / Fabric</span>
                    </div>
                    <p className="text-[11px] text-slate-500 mt-1 font-medium">
                      Flat 5% GST (Sarees, Dhotis, Unstitched Cloth)
                    </p>
                  </button>

                  <button
                    type="button"
                    onClick={() => setProductType('Readymade')}
                    className={`p-3 rounded-xl border text-left transition-all ${
                      productType === 'Readymade'
                        ? 'border-blue-500 bg-blue-50/80 ring-2 ring-blue-500/20'
                        : 'border-slate-200 bg-white hover:bg-slate-100/60'
                    }`}
                  >
                    <div className="flex items-center gap-2">
                      <span className="text-base">👔</span>
                      <span className="font-bold text-xs text-slate-900">Readymade Apparel</span>
                    </div>
                    <p className="text-[11px] text-slate-500 mt-1 font-medium">
                      5% &lt; ₹2500 &bull; 18% &ge; ₹2500
                    </p>
                  </button>
                </div>

                {/* Live Tax Rule Preview */}
                <div className="mt-2 p-2.5 rounded-xl bg-white border border-slate-200 flex items-center justify-between text-xs">
                  <span className="text-slate-600 flex items-center gap-1.5 font-medium">
                    <Sparkles className="w-3.5 h-3.5 text-amber-500" />
                    GST Rate for this Item:
                  </span>
                  <span className={`font-bold px-2.5 py-0.5 rounded-full text-xs ${
                    currentTaxBadge.isTextile 
                      ? 'bg-amber-100 text-amber-800' 
                      : currentTaxBadge.isHighSlab 
                        ? 'bg-rose-100 text-rose-800' 
                        : 'bg-emerald-100 text-emerald-800'
                  }`}>
                    {currentTaxBadge.badge}
                  </span>
                </div>
              </div>

              {/* Pricing & Stock */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-700">Cost Price (₹)</label>
                  <input
                    type="number"
                    value={unitCost}
                    onChange={(e) => setUnitCost(e.target.value)}
                    className="w-full px-3 py-1.5 border rounded-xl text-sm mt-1"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-700">Selling Price (₹)</label>
                  <input
                    type="number"
                    value={sellingPrice}
                    onChange={(e) => setSellingPrice(e.target.value)}
                    className="w-full px-3 py-1.5 border rounded-xl text-sm mt-1 font-bold text-slate-900"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-xs font-semibold text-slate-700">Initial Stock</label>
                  <input
                    type="number"
                    value={currentStock}
                    onChange={(e) => setCurrentStock(e.target.value)}
                    className="w-full px-3 py-1.5 border rounded-xl text-sm mt-1"
                  />
                </div>
                <div>
                  <label className="text-xs font-semibold text-slate-700">Min Reorder Level</label>
                  <input
                    type="number"
                    value={minReorder}
                    onChange={(e) => setMinReorder(e.target.value)}
                    className="w-full px-3 py-1.5 border rounded-xl text-sm mt-1"
                  />
                </div>
              </div>

              {/* Product Reference Photo */}
              <div className="pt-1">
                <label className="block text-xs font-semibold text-slate-700 mb-2">
                  Product Reference Photo (Sample / Fabric / Tag)
                </label>

                {previewPhotoUrl ? (
                  <div className="relative border-2 border-dashed border-indigo-200 bg-indigo-50/30 rounded-2xl p-3 flex items-center gap-3">
                    <img
                      src={previewPhotoUrl}
                      alt="Product preview"
                      className="w-16 h-16 rounded-xl object-cover border border-slate-200 shadow-sm flex-shrink-0"
                    />
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-bold text-slate-800 truncate">
                        {selectedPhotoFile?.name || 'Captured Photo'}
                      </p>
                      <p className="text-[11px] text-slate-500 mt-0.5">
                        {selectedPhotoFile ? `${(selectedPhotoFile.size / 1024).toFixed(1)} KB` : 'Ready to save'}
                      </p>
                      <div className="flex items-center gap-2 mt-2">
                        <button
                          type="button"
                          onClick={() => setIsCameraOpen(true)}
                          className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-white border border-slate-200 text-[11px] font-semibold text-slate-700 hover:bg-slate-50 shadow-2xs transition-colors"
                        >
                          <Camera className="w-3 h-3 text-indigo-600" /> Retake
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setSelectedPhotoFile(null);
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
                      className="flex items-center justify-center gap-2 px-3 py-2.5 rounded-xl border border-indigo-200 bg-indigo-50/60 hover:bg-indigo-100/70 text-indigo-700 font-bold text-xs shadow-2xs transition-all active:scale-[0.99]"
                    >
                      <Camera className="w-4 h-4 text-indigo-600" />
                      <span>Snap Camera Photo</span>
                    </button>

                    <button
                      type="button"
                      onClick={() => fileUploadInputRef.current?.click()}
                      className="flex items-center justify-center gap-2 px-3 py-2.5 rounded-xl border border-slate-200 bg-slate-50 hover:bg-slate-100 text-slate-700 font-semibold text-xs shadow-2xs transition-all active:scale-[0.99]"
                    >
                      <Upload className="w-4 h-4 text-slate-500" />
                      <span>Upload Photo</span>
                    </button>
                  </div>
                )}

                <input
                  ref={fileUploadInputRef}
                  type="file"
                  accept="image/*"
                  onChange={(e) => {
                    const file = e.target.files?.[0];
                    if (file) {
                      setSelectedPhotoFile(file);
                      setPreviewPhotoUrl(URL.createObjectURL(file));
                    }
                  }}
                  className="hidden"
                />
              </div>

              <div className="flex justify-end gap-2 pt-3 border-t">
                <button 
                  type="button" 
                  onClick={() => setIsAddProductOpen(false)} 
                  className="px-4 py-2 text-xs font-semibold text-slate-600 hover:bg-slate-100 rounded-xl transition-all"
                >
                  Cancel
                </button>
                <button 
                  type="submit" 
                  className="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-xs font-bold shadow-md transition-all flex items-center gap-1.5"
                >
                  <Check className="w-4 h-4" /> Save Inventory Product
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* MODAL 2: ADD HIERARCHY LEVEL */}
      {isAddHierarchyOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-md w-full p-6 shadow-2xl border border-slate-100">
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
      )}

      {/* MODAL 3: ADD SUPPLIER */}
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
      )}

      {/* Live Camera Viewfinder Dialog */}
      <CameraCaptureDialog
        isOpen={isCameraOpen}
        onClose={() => setIsCameraOpen(false)}
        title="Snap Product Reference Photo"
        onPhotoCaptured={(file, previewUrl) => {
          setSelectedPhotoFile(file);
          setPreviewPhotoUrl(previewUrl);
        }}
      />
    </div>
  );
};
