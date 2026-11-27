import React, { useState, useEffect, useRef, lazy, Suspense } from 'react';
import { supabase } from './lib/supabase';
import { 
  User, 
  CompanyProfile, 
  Supplier, 
  Location, 
  InventoryItem, 
  PurchaseOrder, 
  PurchasePlan,
  TaxRule
} from './types';
import { Navbar } from './components/Navbar';
import { DashboardView } from './components/DashboardView';
import { LoginScreen } from './components/LoginScreen';

const PurchaseOrdersView = lazy(() =>
  import('./components/PurchaseOrdersView').then((m) => ({ default: m.PurchaseOrdersView }))
);
const TripPlannerView = lazy(() =>
  import('./components/TripPlannerView').then((m) => ({ default: m.TripPlannerView }))
);
const DeliveriesView = lazy(() =>
  import('./components/DeliveriesView').then((m) => ({ default: m.DeliveriesView }))
);
const InventorySuppliersView = lazy(() =>
  import('./components/InventorySuppliersView').then((m) => ({ default: m.InventorySuppliersView }))
);
const SettingsView = lazy(() =>
  import('./components/SettingsView').then((m) => ({ default: m.SettingsView }))
);

export function App() {
  const [currentUser, setCurrentUser] = useState<User | null>(() => {
    const saved = localStorage.getItem('popt_user_session');
    return saved ? JSON.parse(saved) : null;
  });

  const [currentTab, setCurrentTab] = useState<string>('dashboard');
  const [companyProfile, setCompanyProfile] = useState<CompanyProfile | null>(null);
  const [orders, setOrders] = useState<PurchaseOrder[]>([]);
  const [plans, setPlans] = useState<PurchasePlan[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [locations, setLocations] = useState<Location[]>([]);
  const [inventory, setInventory] = useState<InventoryItem[]>([]);
  const [taxRules, setTaxRules] = useState<TaxRule[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(true);

  // Modal / Deep navigation states
  const [isCreatePOOpen, setIsCreatePOOpen] = useState(false);
  const [isCreateTripOpen, setIsCreateTripOpen] = useState(false);
  const [selectedPOForView, setSelectedPOForView] = useState<PurchaseOrder | null>(null);

  // Fetch all initial data
  const fetchData = async () => {
    try {
      const [
        { data: profileData },
        { data: ordersData },
        { data: plansData },
        { data: suppliersData },
        { data: locationsData },
        { data: inventoryData },
        { data: taxRulesData }
      ] = await Promise.all([
        supabase.from('company_profile').select('*').single(),
        supabase.from('purchase_orders').select('*, items:po_items(*)').order('id', { ascending: false }),
        supabase.from('purchase_plans').select('*').order('id', { ascending: false }),
        supabase.from('suppliers').select('*').order('name'),
        supabase.from('locations').select('*').order('name'),
        supabase.from('inventory').select('*').order('name'),
        supabase.from('category_tax_rules').select('*').order('id', { ascending: true })
      ]);

      if (profileData) setCompanyProfile(profileData);
      if (ordersData) setOrders(ordersData);
      if (plansData) setPlans(plansData);
      if (suppliersData) setSuppliers(suppliersData);
      if (locationsData) setLocations(locationsData);
      if (inventoryData) setInventory(inventoryData);
      if (taxRulesData) setTaxRules(taxRulesData);
    } catch (err) {
      console.error('Error fetching Supabase data:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const refreshTimerRef = useRef<number | null>(null);

  useEffect(() => {
    if (currentUser) {
      fetchData();

      const scheduleRefresh = () => {
        if (refreshTimerRef.current !== null) {
          window.clearTimeout(refreshTimerRef.current);
        }
        refreshTimerRef.current = window.setTimeout(() => {
          fetchData();
        }, 300);
      };

      // Setup Realtime subscriptions for immediate sync with mobile devices (debounced)
      const channel = supabase
        .channel('schema-db-changes')
        .on(
          'postgres_changes',
          { event: '*', schema: 'public', table: 'purchase_orders' },
          scheduleRefresh
        )
        .on(
          'postgres_changes',
          { event: '*', schema: 'public', table: 'purchase_plans' },
          scheduleRefresh
        )
        .on(
          'postgres_changes',
          { event: '*', schema: 'public', table: 'inventory' },
          scheduleRefresh
        )
        .on(
          'postgres_changes',
          { event: '*', schema: 'public', table: 'category_tax_rules' },
          scheduleRefresh
        )
        .subscribe();

      return () => {
        if (refreshTimerRef.current !== null) {
          window.clearTimeout(refreshTimerRef.current);
        }
        supabase.removeChannel(channel);
      };
    }
  }, [currentUser]);

  const handleLoginSuccess = (user: User) => {
    setCurrentUser(user);
    localStorage.setItem('popt_user_session', JSON.stringify(user));
  };

  const handleLogout = () => {
    setCurrentUser(null);
    localStorage.removeItem('popt_user_session');
  };

  const handleConvertToPO = (plan: PurchasePlan) => {
    setCurrentTab('orders');
    setIsCreatePOOpen(true);
  };

  // Tab permission guard — redirect to dashboard if user lacks access
  const isAdmin = currentUser?.is_admin === true;
  const tabPermissions: Record<string, boolean> = {
    dashboard: true,
    orders: isAdmin || !!currentUser?.show_procurement,
    planner: true,
    deliveries: isAdmin || !!currentUser?.show_procurement,
    inventory: isAdmin || !!currentUser?.show_inventory,
    settings: isAdmin || !!currentUser?.show_admin_panel,
  };

  useEffect(() => {
    if (currentUser && currentTab && tabPermissions[currentTab] === false) {
      setCurrentTab('dashboard');
    }
  }, [currentTab, currentUser]);

  if (!currentUser) {
    return <LoginScreen onLoginSuccess={handleLoginSuccess} />;
  }

  const openPOCount = orders.filter(o => o.status === 'ISSUED' || o.status === 'PARTIALLY_RECEIVED').length;
  const pendingDeliveriesCount = orders.filter(o => o.status === 'ISSUED' || o.status === 'PARTIALLY_RECEIVED').length;

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col font-sans w-full max-w-full overflow-x-hidden">
      <Navbar
        currentTab={currentTab}
        setCurrentTab={setCurrentTab}
        currentUser={currentUser}
        companyProfile={companyProfile}
        onLogout={handleLogout}
        openPOCount={openPOCount}
        pendingDeliveriesCount={pendingDeliveriesCount}
      />

      <main className="flex-1 max-w-7xl w-full mx-auto px-3 sm:px-6 lg:px-8 py-5 sm:py-6 overflow-x-hidden">
        {currentTab === 'dashboard' && (
          <DashboardView
            orders={orders}
            plans={plans}
            onOpenCreatePO={() => {
              setCurrentTab('orders');
              setIsCreatePOOpen(true);
            }}
            onOpenCreateTrip={() => {
              setCurrentTab('planner');
              setIsCreateTripOpen(true);
            }}
            onNavigateToTab={(tab) => setCurrentTab(tab)}
            onViewPO={(po) => {
              setSelectedPOForView(po);
              setCurrentTab('orders');
            }}
          />
        )}

        <Suspense
          fallback={
            <div className="flex items-center justify-center py-16 text-sm font-medium text-slate-500">
              Loading...
            </div>
          }
        >
        {currentTab === 'orders' && (
          <PurchaseOrdersView
            orders={orders}
            suppliers={suppliers}
            locations={locations}
            inventory={inventory}
            taxRules={taxRules}
            companyProfile={companyProfile}
            currentUser={currentUser}
            onRefresh={fetchData}
            selectedPOForView={selectedPOForView}
            setSelectedPOForView={setSelectedPOForView}
            isCreateOpen={isCreatePOOpen}
            setIsCreateOpen={setIsCreatePOOpen}
          />
        )}

        {currentTab === 'planner' && (
          <TripPlannerView
            plans={plans}
            suppliers={suppliers}
            currentUser={currentUser}
            onRefresh={fetchData}
            onConvertToPO={handleConvertToPO}
            isCreateOpen={isCreateTripOpen}
            setIsCreateOpen={setIsCreateTripOpen}
          />
        )}

        {currentTab === 'deliveries' && (
          <DeliveriesView
            orders={orders}
            currentUser={currentUser}
            onRefresh={fetchData}
          />
        )}

        {currentTab === 'inventory' && (
          <InventorySuppliersView
            inventory={inventory}
            suppliers={suppliers}
            onRefresh={fetchData}
            currentUser={currentUser}
          />
        )}

        {currentTab === 'settings' && (
          <SettingsView
            companyProfile={companyProfile}
            currentUser={currentUser}
            taxRules={taxRules}
            onRefresh={fetchData}
          />
        )}
        </Suspense>
      </main>
    </div>
  );
}

export default App;
