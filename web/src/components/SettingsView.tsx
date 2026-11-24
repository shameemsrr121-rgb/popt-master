import React, { useState, useEffect } from 'react';
import {
  Building2,
  Users,
  MapPin,
  Save,
  Plus,
  Trash2,
  Database,
  Lock,
  Warehouse,
  CheckCircle,
  Percent,
  Calculator,
  Download,
} from 'lucide-react';
import { CompanyProfile, User, Location, TaxRule } from '../types';
import { supabase } from '../lib/supabase';

interface SettingsViewProps {
  companyProfile: CompanyProfile | null;
  currentUser: User | null;
  taxRules?: TaxRule[];
  onRefresh: () => void;
}

export const SettingsView: React.FC<SettingsViewProps> = ({
  companyProfile,
  currentUser,
  taxRules = [],
  onRefresh
}) => {
  const [activeSubTab, setActiveSubTab] = useState<'profile' | 'users' | 'locations' | 'taxes'>('profile');
  const [usersList, setUsersList] = useState<User[]>([]);
  const [locationsList, setLocationsList] = useState<Location[]>([]);
  const [isLoadingLocations, setIsLoadingLocations] = useState(false);

  // Tax Master Rules Form State
  const [taxRuleCategory, setTaxRuleCategory] = useState('');
  const [taxRuleHsn, setTaxRuleHsn] = useState('');
  const [taxRulePercent, setTaxRulePercent] = useState('5.0');
  const [taxRuleMinSlab, setTaxRuleMinSlab] = useState('0');
  const [taxRuleMaxSlab, setTaxRuleMaxSlab] = useState('9999999');
  const [taxRuleName, setTaxRuleName] = useState('');
  const [taxRuleDescription, setTaxRuleDescription] = useState('');
  const [isCreatingTaxRule, setIsCreatingTaxRule] = useState(false);

  // Locations Form State
  const [locCode, setLocCode] = useState('');
  const [locName, setLocName] = useState('');
  const [locType, setLocType] = useState('Warehouse');
  const [locAddress, setLocAddress] = useState('');
  const [isCreatingLocation, setIsCreatingLocation] = useState(false);

  // Company Profile form
  const [companyName, setCompanyName] = useState(companyProfile?.company_name || '');
  const [address, setAddress] = useState(companyProfile?.address || '');
  const [phone, setPhone] = useState(companyProfile?.phone || '');
  const [email, setEmail] = useState(companyProfile?.email || '');
  const [gstNumber, setGstNumber] = useState(companyProfile?.gst_number || '');
  const [isSavingProfile, setIsSavingProfile] = useState(false);

  // New user form
  const [newUsername, setNewUsername] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [newFullName, setNewFullName] = useState('');
  const [newRole, setNewRole] = useState('Procurement Lead');
  const [isCreatingUser, setIsCreatingUser] = useState(false);

  // Module permissions for new user
  const [modProcurement, setModProcurement] = useState(true);
  const [modInventory, setModInventory] = useState(true);
  const [modReport, setModReport] = useState(true);
  const [modMargin, setModMargin] = useState(false);
  const [modGSheets, setModGSheets] = useState(false);
  const [modAdminPanel, setModAdminPanel] = useState(false);
  const [modCanDeletePO, setModCanDeletePO] = useState(false);

  // Auto-check / auto-uncheck when role changes
  useEffect(() => {
    if (newRole === 'Administrator') {
      setModProcurement(true);
      setModInventory(true);
      setModReport(true);
      setModMargin(true);
      setModGSheets(true);
      setModAdminPanel(true);
      setModCanDeletePO(true);
    } else {
      setModAdminPanel(false);
    }
  }, [newRole]);

  useEffect(() => {
    fetchUsers();
    fetchLocations();
  }, []);

  const fetchUsers = async () => {
    const { data } = await supabase.from('users').select('*').order('id', { ascending: true });
    setUsersList(data || []);
  };

  const fetchLocations = async () => {
    setIsLoadingLocations(true);
    try {
      const { data, error } = await supabase.from('locations').select('*').order('name');
      if (error) throw error;
      setLocationsList(data || []);
    } catch (err: any) {
      console.error('Error fetching locations:', err);
    } finally {
      setIsLoadingLocations(false);
    }
  };

  const handleCreateLocation = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!locCode.trim() || !locName.trim()) {
      alert('Branch Code and Branch Name are required');
      return;
    }

    setIsCreatingLocation(true);
    try {
      const { error } = await supabase.from('locations').insert({
        code: locCode.trim().toUpperCase(),
        name: locName.trim(),
        type: locType,
        address: locAddress.trim(),
        is_active: true
      });

      if (error) throw error;
      alert(`Branch / Location "${locName.trim()}" created successfully!`);
      setLocCode('');
      setLocName('');
      setLocType('Warehouse');
      setLocAddress('');
      await fetchLocations();
      onRefresh();
    } catch (err: any) {
      alert(`Error creating branch: ${err.message}`);
    } finally {
      setIsCreatingLocation(false);
    }
  };

  const handleDeleteLocation = async (locationId: number, locationName: string) => {
    if (confirm(`Are you sure you want to delete branch "${locationName}"?`)) {
      try {
        const { error } = await supabase.from('locations').delete().eq('id', locationId);
        if (error) throw error;
        await fetchLocations();
        onRefresh();
      } catch (err: any) {
        alert(`Error deleting branch: ${err.message}`);
      }
    }
  };

  const handleSaveProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSavingProfile(true);
    try {
      const { error } = await supabase.from('company_profile').upsert({
        id: 1,
        company_name: companyName,
        address: address,
        phone: phone,
        email: email,
        gst_number: gstNumber,
        updated_at: new Date().toISOString()
      });

      if (error) throw error;
      alert('Company profile updated successfully!');
      onRefresh();
    } catch (err: any) {
      alert(`Error saving profile: ${err.message}`);
    } finally {
      setIsSavingProfile(false);
    }
  };

  const handleCreateUser = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newUsername.trim() || !newPassword.trim()) {
      alert('Username and password are required');
      return;
    }

    setIsCreatingUser(true);
    try {
      const isAdminRole = newRole === 'Administrator';
      const { error } = await supabase.from('users').insert({
        username: newUsername.trim().toLowerCase(),
        password_hash: newPassword.trim(),
        full_name: newFullName.trim(),
        role: newRole,
        is_admin: isAdminRole,
        can_delete_po: modCanDeletePO,
        show_procurement: modProcurement,
        show_inventory: modInventory,
        show_supplier: modInventory,
        show_report: modReport,
        show_margin: modMargin,
        show_google_sheets: modGSheets,
        show_admin_panel: isAdminRole || modAdminPanel,
      });

      if (error) throw error;
      alert(`User ${newUsername} created successfully!`);
      setNewUsername('');
      setNewPassword('');
      setNewFullName('');
      setNewRole('Procurement Lead');
      setModProcurement(true);
      setModInventory(true);
      setModReport(true);
      setModMargin(false);
      setModGSheets(false);
      setModAdminPanel(false);
      setModCanDeletePO(false);
      fetchUsers();
    } catch (err: any) {
      alert(`Error creating user: ${err.message}`);
    } finally {
      setIsCreatingUser(false);
    }
  };

  const handleDeleteUser = async (userId: number, username: string) => {
    if (username === 'admin') {
      alert('The primary admin account cannot be deleted.');
      return;
    }
    if (confirm(`Delete user account "${username}"?`)) {
      await supabase.from('users').delete().eq('id', userId);
      fetchUsers();
    }
  };

  const handleCreateTaxRule = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!taxRuleCategory.trim()) {
      alert('Category / Keyword is required for the tax rule');
      return;
    }

    setIsCreatingTaxRule(true);
    try {
      const { error } = await supabase.from('category_tax_rules').insert({
        category_name: taxRuleCategory.trim(),
        hsn_code: taxRuleHsn.trim(),
        tax_rate_percent: Number(taxRulePercent) || 5.0,
        min_slab_amount: Number(taxRuleMinSlab) || 0.0,
        max_slab_amount: Number(taxRuleMaxSlab) || 9999999.0,
        rule_name: taxRuleName.trim() || `${taxRuleCategory.trim()} ${taxRulePercent}% Rule`,
        description: taxRuleDescription.trim()
      });

      if (error) throw error;
      alert(`Tax rule for "${taxRuleCategory.trim()}" added successfully!`);
      setTaxRuleCategory('');
      setTaxRuleHsn('');
      setTaxRulePercent('5.0');
      setTaxRuleMinSlab('0');
      setTaxRuleMaxSlab('9999999');
      setTaxRuleName('');
      setTaxRuleDescription('');
      onRefresh();
    } catch (err: any) {
      alert(`Error creating tax rule: ${err.message}`);
    } finally {
      setIsCreatingTaxRule(false);
    }
  };

  const handleDeleteTaxRule = async (ruleId: number, name: string) => {
    if (confirm(`Delete custom tax rule "${name}"?`)) {
      try {
        const { error } = await supabase.from('category_tax_rules').delete().eq('id', ruleId);
        if (error) throw error;
        onRefresh();
      } catch (err: any) {
        alert(`Error deleting tax rule: ${err.message}`);
      }
    }
  };

  // Access denied guard
  const hasAccess = currentUser?.is_admin === true || currentUser?.show_admin_panel === true;

  if (!hasAccess) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center">
        <div className="w-16 h-16 rounded-2xl bg-rose-100 flex items-center justify-center mb-4">
          <Lock className="w-8 h-8 text-rose-500" />
        </div>
        <h2 className="text-xl font-bold text-slate-900 mb-2">Access Denied</h2>
        <p className="text-sm text-slate-500 max-w-sm">
          You do not have permission to view the Admin &amp; Settings panel. Please contact your administrator.
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl shadow-sm border border-slate-200/80">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">System &amp; Admin Settings</h1>
          <p className="text-sm text-slate-500 mt-1">
            Configure company branding, manage team access credentials, and system settings
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <button
            onClick={() => setActiveSubTab('profile')}
            className={`px-4 py-2 rounded-xl text-sm font-bold transition-all ${
              activeSubTab === 'profile' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            Company Profile
          </button>
          <button
            onClick={() => setActiveSubTab('users')}
            className={`px-4 py-2 rounded-xl text-sm font-bold transition-all ${
              activeSubTab === 'users' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            Users &amp; Roles
          </button>
          <button
            onClick={() => setActiveSubTab('locations')}
            className={`px-4 py-2 rounded-xl text-sm font-bold transition-all ${
              activeSubTab === 'locations' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            Branches &amp; Locations
          </button>
          <button
            onClick={() => setActiveSubTab('taxes')}
            className={`px-4 py-2 rounded-xl text-sm font-bold transition-all ${
              activeSubTab === 'taxes' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-600 hover:bg-slate-100'
            }`}
          >
            Tax Master &amp; GST Rules
          </button>
        </div>
      </div>

      {/* System & Database Info Card */}
      <div className="p-4 rounded-2xl bg-slate-900 border border-slate-800 text-white flex flex-col sm:flex-row sm:items-center justify-between gap-4 shadow-sm">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-300">
            <Database className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-semibold text-sm text-slate-200">System Database</span>
              <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-medium bg-slate-800 text-emerald-400 border border-emerald-500/30">
                Connected
              </span>
            </div>
            <p className="text-xs text-slate-400 mt-0.5">Real-time sync enabled across web portal and mobile app</p>
          </div>
        </div>
        <div className="flex items-center gap-3">
          <a
            href="/POPT-Master.apk"
            download="POPT-Master.apk"
            className="inline-flex items-center gap-2 px-3 py-1.5 rounded-xl text-xs font-semibold bg-slate-800 text-indigo-300 hover:text-white hover:bg-indigo-600 transition-all border border-slate-700"
            title="Download Android APK (4.2 MB)"
          >
            <Download className="w-3.5 h-3.5" />
            <span>Download Android APK</span>
          </a>
        </div>
      </div>

      {/* Subtab: Company Profile */}
      {activeSubTab === 'profile' && (
        <div className="bg-white rounded-2xl shadow-sm border border-slate-200/80 p-6 max-w-2xl">
          <h2 className="text-lg font-bold text-slate-900 mb-4 flex items-center gap-2">
            <Building2 className="w-5 h-5 text-indigo-600" /> Company Master Information
          </h2>
          <form onSubmit={handleSaveProfile} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Company / Store Name *</label>
              <input
                type="text"
                value={companyName}
                onChange={(e) => setCompanyName(e.target.value)}
                className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm"
                required
              />
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Store Address / Branches</label>
              <input
                type="text"
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm"
              />
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Phone Number</label>
                <input
                  type="text"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Email Address</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm"
                />
              </div>
            </div>
            <div>
              <label className="block text-xs font-semibold text-slate-700 mb-1">Company GSTIN Number</label>
              <input
                type="text"
                value={gstNumber}
                onChange={(e) => setGstNumber(e.target.value)}
                className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm font-mono"
              />
            </div>
            <div className="pt-3">
              <button
                type="submit"
                disabled={isSavingProfile}
                className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-sm shadow-sm transition-all"
              >
                <Save className="w-4 h-4" />
                {isSavingProfile ? 'Saving...' : 'Save Profile Changes'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* Subtab: Users & Roles */}
      {activeSubTab === 'users' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* User List */}
          <div className="lg:col-span-2 bg-white rounded-2xl shadow-sm border border-slate-200/80 p-5">
            <h2 className="text-lg font-bold text-slate-900 mb-4 flex items-center gap-2">
              <Users className="w-5 h-5 text-indigo-600" /> Authorized Team Users
            </h2>
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead className="bg-slate-50 border-b border-slate-200 text-xs uppercase font-bold text-slate-500">
                  <tr>
                    <th className="py-3 px-4">User</th>
                    <th className="py-3 px-4">Full Name</th>
                    <th className="py-3 px-4">Role</th>
                    <th className="py-3 px-4">Module Access</th>
                    <th className="py-3 px-4 text-right">Action</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-100">
                  {usersList.map(u => (
                    <tr key={u.id} className="hover:bg-slate-50/50 align-top">
                      <td className="py-3 px-4 font-bold text-indigo-600 font-mono text-xs">{u.username}</td>
                      <td className="py-3 px-4 font-medium text-slate-800 text-sm">{u.full_name || '—'}</td>
                      <td className="py-3 px-4">
                        <span className={`px-2 py-0.5 rounded-full text-xs font-semibold ${
                          u.is_admin ? 'bg-violet-100 text-violet-700' : 'bg-slate-100 text-slate-700'
                        }`}>
                          {u.role}
                        </span>
                      </td>
                      <td className="py-3 px-4">
                        <div className="flex flex-wrap gap-1">
                          {u.is_admin && (
                            <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-violet-100 text-violet-700">Admin</span>
                          )}
                          {u.show_procurement && (
                            <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-blue-100 text-blue-700">PO</span>
                          )}
                          {u.show_inventory && (
                            <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-emerald-100 text-emerald-700">Hierarchy</span>
                          )}
                          {u.show_margin && (
                            <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-orange-100 text-orange-700">Margin</span>
                          )}
                          {u.show_google_sheets && (
                            <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-green-100 text-green-700">Sheets</span>
                          )}
                          {u.can_delete_po && (
                            <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-rose-100 text-rose-700">Del PO</span>
                          )}
                        </div>
                      </td>
                      <td className="py-3 px-4 text-right">
                        {u.username !== 'admin' && (
                          <button
                            onClick={() => handleDeleteUser(u.id, u.username)}
                            className="p-1 text-slate-400 hover:text-rose-600 rounded"
                          >
                            <Trash2 className="w-4 h-4" />
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Create User Form */}
          <div className="bg-white rounded-2xl shadow-sm border border-slate-200/80 p-5">
            <h2 className="text-lg font-bold text-slate-900 mb-4 flex items-center gap-2">
              <Plus className="w-5 h-5 text-indigo-600" /> Add Team Member
            </h2>
            <form onSubmit={handleCreateUser} className="space-y-3">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Username *</label>
                <input
                  type="text"
                  placeholder="e.g. buyer1"
                  value={newUsername}
                  onChange={(e) => setNewUsername(e.target.value)}
                  className="w-full px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-sm"
                  required
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Password *</label>
                <input
                  type="password"
                  placeholder="Password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  className="w-full px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-sm"
                  required
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Full Name</label>
                <input
                  type="text"
                  placeholder="e.g. Rajesh Kumar"
                  value={newFullName}
                  onChange={(e) => setNewFullName(e.target.value)}
                  className="w-full px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-sm"
                />
              </div>
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Designation / Role</label>
                <select
                  value={newRole}
                  onChange={(e) => setNewRole(e.target.value)}
                  className="w-full px-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-sm"
                >
                  <option value="Procurement Lead">Procurement Lead / Buyer</option>
                  <option value="Store Keeper">Store Keeper / Warehouse</option>
                  <option value="Administrator">Administrator</option>
                </select>
              </div>

              {/* Module Access Checkboxes */}
              <div className="pt-1">
                <p className="text-xs font-bold text-slate-700 mb-2 uppercase tracking-wide">Module Access</p>
                <div className="space-y-2 bg-slate-50 rounded-xl p-3 border border-slate-200">
                  {([
                    { label: 'Purchase Orders & Procurement', checked: modProcurement, onChange: setModProcurement, id: 'mod_proc' },
                    { label: 'Store Hierarchy & Supplier Directory', checked: modInventory, onChange: setModInventory, id: 'mod_inv' },
                    { label: 'Margin / Cost Analysis', checked: modMargin, onChange: setModMargin, id: 'mod_margin' },
                    { label: 'Google Sheets Export', checked: modGSheets, onChange: setModGSheets, id: 'mod_gsh' },
                    { label: 'Can Delete Purchase Orders', checked: modCanDeletePO, onChange: setModCanDeletePO, id: 'mod_delpo' },
                  ] as { label: string; checked: boolean; onChange: (v: boolean) => void; id: string }[]).map((item) => (
                    <label key={item.id} className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="checkbox"
                        id={item.id}
                        checked={item.checked}
                        onChange={(e) => item.onChange(e.target.checked)}
                        className="rounded text-indigo-600 focus:ring-indigo-500"
                      />
                      <span className="text-xs text-slate-700">{item.label}</span>
                    </label>
                  ))}

                  {/* Admin Panel — only enabled when role = Administrator */}
                  <label className={`flex items-center gap-2 ${newRole === 'Administrator' ? 'cursor-pointer' : 'opacity-40 cursor-not-allowed'}`}>
                    <input
                      type="checkbox"
                      checked={modAdminPanel}
                      onChange={(e) => { if (newRole === 'Administrator') setModAdminPanel(e.target.checked); }}
                      disabled={newRole !== 'Administrator'}
                      className="rounded text-indigo-600 focus:ring-indigo-500"
                    />
                    <span className="text-xs text-slate-700">Admin &amp; Settings Panel</span>
                    {newRole !== 'Administrator' && (
                      <span className="text-[10px] text-slate-400">(Admins only)</span>
                    )}
                  </label>
                </div>
              </div>

              <div className="pt-2">
                <button
                  type="submit"
                  disabled={isCreatingUser}
                  className="w-full py-2 bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs rounded-xl shadow-sm transition-all"
                >
                  {isCreatingUser ? 'Creating...' : 'Create User Account'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Subtab: Branches & Locations */}
      {activeSubTab === 'locations' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Left 2 Cols: Locations List */}
          <div className="lg:col-span-2 space-y-4">
            <div className="bg-white rounded-2xl shadow-sm border border-slate-200/80 p-5">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 mb-4">
                <div>
                  <h2 className="text-lg font-bold text-slate-900 flex items-center gap-2">
                    <Warehouse className="w-5 h-5 text-indigo-600" /> Operational Branches &amp; Hubs
                  </h2>
                  <p className="text-xs text-slate-500 mt-0.5">
                    Warehouses and retail outlets available across Purchase Orders and Inventory tracking
                  </p>
                </div>
                <span className="text-xs font-semibold px-2.5 py-1 bg-slate-100 text-slate-700 rounded-lg self-start sm:self-auto">
                  {locationsList.length} {locationsList.length === 1 ? 'Location' : 'Locations'}
                </span>
              </div>

              {isLoadingLocations ? (
                <div className="py-12 text-center text-slate-400 text-sm">Loading locations...</div>
              ) : locationsList.length === 0 ? (
                <div className="py-12 text-center text-slate-400 text-sm">
                  No branches or locations found. Add your first location using the form on the right.
                </div>
              ) : (
                <div className="space-y-3">
                  {locationsList.map((loc) => (
                    <div
                      key={loc.id}
                      className="p-4 rounded-xl border border-slate-200/80 bg-slate-50/50 hover:bg-slate-50 flex flex-col sm:flex-row sm:items-center justify-between gap-3 transition-colors"
                    >
                      <div className="flex items-start gap-3">
                        <div className="w-10 h-10 rounded-xl bg-indigo-50 border border-indigo-100 flex items-center justify-center text-indigo-600 shrink-0 mt-0.5">
                          <MapPin className="w-5 h-5" />
                        </div>
                        <div>
                          <div className="flex flex-wrap items-center gap-2">
                            <span className="font-bold text-sm text-slate-900">{loc.name}</span>
                            <span className="font-mono text-xs px-2 py-0.5 rounded bg-indigo-100 text-indigo-800 font-semibold">
                              {loc.code}
                            </span>
                            <span className="text-xs px-2 py-0.5 rounded-full bg-slate-200 text-slate-700 font-medium">
                              {loc.type || 'Warehouse'}
                            </span>
                            {loc.is_active && (
                              <span className="inline-flex items-center gap-1 text-[11px] font-semibold text-emerald-600">
                                <CheckCircle className="w-3 h-3" /> Active
                              </span>
                            )}
                          </div>
                          {loc.address && (
                            <p className="text-xs text-slate-500 mt-1 whitespace-pre-line leading-relaxed">
                              {loc.address}
                            </p>
                          )}
                        </div>
                      </div>

                      <div className="flex items-center self-end sm:self-center gap-2">
                        <button
                          onClick={() => handleDeleteLocation(loc.id, loc.name)}
                          className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors"
                          title="Delete Location"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* Right Col: Add Location Form */}
          <div className="bg-white rounded-2xl shadow-sm border border-slate-200/80 p-5 h-fit">
            <h2 className="text-base font-bold text-slate-900 mb-1 flex items-center gap-2">
              <Plus className="w-4 h-4 text-indigo-600" /> Add New Branch / Location
            </h2>
            <p className="text-xs text-slate-500 mb-4">
              Add a physical warehouse, depot, or retail storefront
            </p>

            <form onSubmit={handleCreateLocation} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Branch / Location Code *
                </label>
                <input
                  type="text"
                  placeholder="e.g. BR01, WH02, CHN01"
                  value={locCode}
                  onChange={(e) => setLocCode(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm uppercase font-mono focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Branch / Store Name *
                </label>
                <input
                  type="text"
                  placeholder="e.g. Madurai Retail Branch, Coimbatore Hub"
                  value={locName}
                  onChange={(e) => setLocName(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Location Type
                </label>
                <select
                  value={locType}
                  onChange={(e) => setLocType(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                >
                  <option value="Warehouse">Warehouse</option>
                  <option value="Retail Outlet">Retail Outlet</option>
                  <option value="Distribution Hub">Distribution Hub</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">
                  Address
                </label>
                <textarea
                  placeholder="Street address, city, pin code..."
                  value={locAddress}
                  onChange={(e) => setLocAddress(e.target.value)}
                  rows={3}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:ring-2 focus:ring-indigo-500 focus:outline-none resize-none"
                />
              </div>

              <div className="pt-2">
                <button
                  type="submit"
                  disabled={isCreatingLocation}
                  className="w-full py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs rounded-xl shadow-sm transition-all disabled:opacity-50"
                >
                  {isCreatingLocation ? 'Saving...' : 'Add Branch / Location'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* SUBTAB 4: TAX MASTER & GST RULES */}
      {activeSubTab === 'taxes' && (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Rules List (Left 2 cols) */}
          <div className="lg:col-span-2 space-y-4">
            <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm">
              <div className="flex items-center justify-between mb-4">
                <div>
                  <h3 className="font-bold text-slate-800 text-base flex items-center gap-2">
                    <Percent className="w-5 h-5 text-indigo-600" />
                    Statutory &amp; Custom Tax Rules
                  </h3>
                  <p className="text-xs text-slate-500 mt-0.5">
                    Rules loaded directly from Central Database and synced to Web and Mobile apps
                  </p>
                </div>
                <span className="px-2.5 py-1 rounded-full text-xs font-bold bg-indigo-50 text-indigo-700 border border-indigo-100">
                  {taxRules.length} Active Rules
                </span>
              </div>

              {/* Statutory Rules Explanation Banner */}
              <div className="mb-4 p-4 rounded-xl bg-gradient-to-r from-indigo-50 via-slate-50 to-emerald-50 border border-indigo-100/80 text-xs text-slate-700 space-y-2">
                <div className="font-bold text-indigo-900 flex items-center gap-1.5">
                  <Calculator className="w-4 h-4 text-indigo-600" />
                  Statutory Indian GST Textile &amp; Apparel Rules (Inbuilt):
                </div>
                <ul className="list-disc list-inside space-y-1 text-slate-600 pl-1">
                  <li>
                    <strong className="text-emerald-700">Textile / Fabrics / Sarees:</strong> Always <strong>5.0% flat GST</strong> across all price slabs.
                  </li>
                  <li>
                    <strong className="text-blue-700">Readymade Apparel &lt; ₹2,500:</strong> Automatic <strong>5.0% GST</strong> (CGST 2.5% + SGST 2.5%).
                  </li>
                  <li>
                    <strong className="text-rose-700">Readymade Apparel ≥ ₹2,500:</strong> Automatic <strong>18.0% GST</strong> (CGST 9.0% + SGST 9.0%).
                  </li>
                  <li>
                    <strong className="text-amber-700">Custom Category Rules:</strong> Override the statutory default when price falls within configured min/max slab.
                  </li>
                </ul>
              </div>

              {/* Table of Custom / DB Rules */}
              <div className="overflow-x-auto border border-slate-200 rounded-xl">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50 border-b border-slate-200 font-bold text-slate-600">
                    <tr>
                      <th className="p-3">Category / Name</th>
                      <th className="p-3">HSN</th>
                      <th className="p-3">Slab Range</th>
                      <th className="p-3 text-center">Tax Rate</th>
                      <th className="p-3">Description</th>
                      <th className="p-3 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100">
                    {taxRules.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="p-6 text-center text-slate-400">
                          No custom tax rules in database. System using statutory defaults (5% Textile / 5% &amp; 18% Readymade).
                        </td>
                      </tr>
                    ) : (
                      taxRules.map((rule) => (
                        <tr key={rule.id} className="hover:bg-slate-50/50">
                          <td className="p-3 font-semibold text-slate-800">
                            <div>{rule.category_name}</div>
                            {rule.rule_name && (
                              <span className="text-[10px] text-slate-400">{rule.rule_name}</span>
                            )}
                          </td>
                          <td className="p-3 font-mono text-slate-600">
                            {rule.hsn_code || '—'}
                          </td>
                          <td className="p-3 text-slate-600">
                            ₹{Number(rule.min_slab_amount).toLocaleString('en-IN')} - ₹{Number(rule.max_slab_amount).toLocaleString('en-IN')}
                          </td>
                          <td className="p-3 text-center">
                            <span className="px-2 py-0.5 rounded-full text-xs font-black bg-indigo-100 text-indigo-700">
                              {rule.tax_rate_percent}%
                            </span>
                          </td>
                          <td className="p-3 text-slate-500 text-[11px] max-w-xs truncate">
                            {rule.description || '—'}
                          </td>
                          <td className="p-3 text-right">
                            {rule.id && (
                              <button
                                onClick={() => handleDeleteTaxRule(rule.id!, rule.rule_name || rule.category_name)}
                                className="p-1 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded"
                                title="Delete Rule"
                              >
                                <Trash2 className="w-3.5 h-3.5" />
                              </button>
                            )}
                          </td>
                        </tr>
                      ))
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          {/* Create Tax Rule Form (Right 1 col) */}
          <div className="bg-white p-5 rounded-2xl border border-slate-200/80 shadow-sm h-fit">
            <h3 className="font-bold text-slate-800 text-sm mb-4 flex items-center gap-2">
              <Plus className="w-4 h-4 text-indigo-600" />
              Add Custom Tax Rule
            </h3>

            <form onSubmit={handleCreateTaxRule} className="space-y-3.5 text-xs">
              <div>
                <label className="block font-semibold text-slate-700 mb-1">
                  Category / Division / Keyword *
                </label>
                <input
                  type="text"
                  placeholder="e.g. Readymade, Silk, Yarn, Cotton"
                  value={taxRuleCategory}
                  onChange={(e) => setTaxRuleCategory(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  required
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">
                  Rule Name / Title
                </label>
                <input
                  type="text"
                  placeholder="e.g. Premium Silk Flat 5% Rule"
                  value={taxRuleName}
                  onChange={(e) => setTaxRuleName(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">
                    GST Rate % *
                  </label>
                  <select
                    value={taxRulePercent}
                    onChange={(e) => setTaxRulePercent(e.target.value)}
                    className="w-full px-2 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-bold focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  >
                    <option value="0">0%</option>
                    <option value="5.0">5.0%</option>
                    <option value="12.0">12.0%</option>
                    <option value="18.0">18.0%</option>
                    <option value="28.0">28.0%</option>
                  </select>
                </div>
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">
                    HSN Code
                  </label>
                  <input
                    type="text"
                    placeholder="e.g. 5208, 6203"
                    value={taxRuleHsn}
                    onChange={(e) => setTaxRuleHsn(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs font-mono focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">
                    Min Slab Amount (₹)
                  </label>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={taxRuleMinSlab}
                    onChange={(e) => setTaxRuleMinSlab(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  />
                </div>
                <div>
                  <label className="block font-semibold text-slate-700 mb-1">
                    Max Slab Amount (₹)
                  </label>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={taxRuleMaxSlab}
                    onChange={(e) => setTaxRuleMaxSlab(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs focus:ring-2 focus:ring-indigo-500 focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">
                  Description / Remarks
                </label>
                <textarea
                  placeholder="Optional rule description..."
                  value={taxRuleDescription}
                  onChange={(e) => setTaxRuleDescription(e.target.value)}
                  rows={2}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-xs focus:ring-2 focus:ring-indigo-500 focus:outline-none resize-none"
                />
              </div>

              <div className="pt-2">
                <button
                  type="submit"
                  disabled={isCreatingTaxRule}
                  className="w-full py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-xs rounded-xl shadow-sm transition-all disabled:opacity-50"
                >
                  {isCreatingTaxRule ? 'Saving Rule...' : 'Save Custom Tax Rule'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
