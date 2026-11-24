import React, { useState, useEffect, useRef } from 'react';
import { 
  LayoutDashboard, 
  FileText, 
  Calendar, 
  Truck, 
  Package, 
  FolderTree,
  BarChart3, 
  Settings, 
  LogOut, 
  Download,
  User as UserIcon,
  Menu,
  X
} from 'lucide-react';
import { User, CompanyProfile } from '../types';

interface NavbarProps {
  currentTab: string;
  setCurrentTab: (tab: string) => void;
  currentUser: User | null;
  companyProfile: CompanyProfile | null;
  onLogout: () => void;
  openPOCount: number;
  pendingDeliveriesCount: number;
}

export const Navbar: React.FC<NavbarProps> = ({
  currentTab,
  setCurrentTab,
  currentUser,
  companyProfile,
  onLogout,
  openPOCount,
  pendingDeliveriesCount
}) => {
  const [deferredPrompt, setDeferredPrompt] = useState<any>(null);
  const [isInstallable, setIsInstallable] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const [isIOS, setIsIOS] = useState(false);
  const [showIOSInstallGuide, setShowIOSInstallGuide] = useState(false);
  const tabsNavRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (typeof window !== 'undefined') {
      const ua = window.navigator.userAgent.toLowerCase();
      setIsIOS(/iphone|ipad|ipod/.test(ua));
    }
  }, []);

  // Detect standalone mode (APK webview / PWA) or prior download / dismissal
  const [isInstalled, setIsInstalled] = useState<boolean>(() => {
    if (typeof window === 'undefined') return false;
    return (
      window.matchMedia('(display-mode: standalone)').matches ||
      (window.navigator as any).standalone === true ||
      document.referrer.includes('android-app://') ||
      window.location.search.includes('source=pwa') ||
      window.location.search.includes('source=apk') ||
      localStorage.getItem('popt_installed') === 'true'
    );
  });

  useEffect(() => {
    const handleBeforeInstallPrompt = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e);
      setIsInstallable(true);
    };

    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
    return () => window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
  }, []);

  // Smooth scroll active tab into view horizontally on mobile
  useEffect(() => {
    if (tabsNavRef.current) {
      const activeEl = tabsNavRef.current.querySelector<HTMLElement>('[data-active="true"]');
      if (activeEl) {
        activeEl.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'center' });
      }
    }
  }, [currentTab]);

  const handleInstallClick = async () => {
    if (!deferredPrompt) {
      alert("To install on your phone: Tap browser menu (⋮ or share icon) and select 'Add to Home screen' / 'Install app'");
      return;
    }
    deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    if (outcome === 'accepted') {
      setIsInstallable(false);
    }
    setDeferredPrompt(null);
  };

  const isAdmin = currentUser?.is_admin === true;

  const navItems = [
    { id: 'dashboard', label: 'Dashboard', icon: LayoutDashboard, visible: true },
    {
      id: 'orders',
      label: 'Purchase Orders',
      icon: FileText,
      badge: openPOCount,
      visible: isAdmin || currentUser?.show_procurement === true,
    },
    { id: 'planner', label: 'Trip Planner', icon: Calendar, visible: true },
    {
      id: 'deliveries',
      label: 'Deliveries',
      icon: Truck,
      badge: pendingDeliveriesCount,
      visible: isAdmin || currentUser?.show_procurement === true,
    },
    {
      id: 'inventory',
      label: 'Store Hierarchy & Vendors',
      icon: FolderTree,
      visible: isAdmin || currentUser?.show_inventory === true,
    },
    {
      id: 'reports',
      label: 'Spend & Reports',
      icon: BarChart3,
      visible: isAdmin || currentUser?.show_report === true,
    },
    {
      id: 'settings',
      label: 'Settings',
      icon: Settings,
      visible: isAdmin || currentUser?.show_admin_panel === true,
    },
  ];

  const handleTabSelect = (tabId: string) => {
    setCurrentTab(tabId);
    setIsMobileMenuOpen(false);
  };

  return (
    <header className="bg-slate-900 text-white sticky top-0 z-40 shadow-md w-full max-w-full overflow-hidden">
      {/* Top Banner Bar */}
      <div className="max-w-7xl mx-auto px-3 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16 gap-2">
          {/* Logo & Company Name */}
          <div className="flex items-center gap-2 sm:gap-3 min-w-0 flex-1">
            <div className="w-9 h-9 sm:w-10 sm:h-10 shrink-0 rounded-xl bg-gradient-to-tr from-indigo-600 to-violet-500 flex items-center justify-center font-bold text-lg sm:text-xl text-white shadow-lg shadow-indigo-500/30">
              P
            </div>
            <div className="min-w-0">
              <span className="font-extrabold text-base sm:text-lg tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-white via-slate-100 to-indigo-200 block truncate">
                POPT
              </span>
              <p className="text-[11px] sm:text-xs text-slate-400 truncate max-w-[140px] xs:max-w-[200px] sm:max-w-md">
                {companyProfile?.company_name || 'Purchase Order Master'}
              </p>
            </div>
          </div>

          {/* User Session Info, Install App, Logout & Mobile Hamburger */}
          <div className="flex items-center gap-1.5 sm:gap-3 shrink-0">
            {/* Download APK / iPhone Install Button */}
            {!isInstalled && (
              isIOS ? (
                <button
                  type="button"
                  onClick={() => setShowIOSInstallGuide(true)}
                  className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-lg bg-sky-600/30 border border-sky-500/40 text-xs font-semibold text-sky-200 hover:bg-sky-600 hover:text-white transition-all shadow-sm"
                  title="Install on iPhone / iPad"
                >
                  <Download className="w-3.5 h-3.5" />
                  <span>Install on iPhone</span>
                </button>
              ) : (
                <div className="flex items-center rounded-lg bg-emerald-600/30 border border-emerald-500/40 shadow-sm overflow-hidden group">
                  <a
                    href="/POPT-Master.apk"
                    download="POPT-Master.apk"
                    onClick={() => {
                      localStorage.setItem('popt_installed', 'true');
                      setIsInstalled(true);
                    }}
                    className="flex items-center gap-1 px-2 py-1.5 sm:px-2.5 sm:py-1.5 text-xs font-semibold text-emerald-300 hover:bg-emerald-600 hover:text-white transition-all"
                    title="Download Android APK"
                  >
                    <Download className="w-3.5 h-3.5" />
                    <span className="hidden sm:inline">Download APK</span>
                    <span className="sm:hidden text-[11px]">APK</span>
                  </a>
                  <button
                    type="button"
                    onClick={(e) => {
                      e.preventDefault();
                      e.stopPropagation();
                      localStorage.setItem('popt_installed', 'true');
                      setIsInstalled(true);
                    }}
                    className="p-1.5 text-emerald-400/70 hover:text-white hover:bg-emerald-600/50 transition-colors border-l border-emerald-500/30"
                    title="Dismiss APK download button"
                  >
                    <X className="w-3 h-3" />
                  </button>
                </div>
              )
            )}

            {/* Install PWA Button */}
            <button
              onClick={handleInstallClick}
              className="hidden md:flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-indigo-600/30 text-indigo-300 border border-indigo-500/40 hover:bg-indigo-600 hover:text-white transition-all shadow-sm"
              title="Install Web App on phone or desktop"
            >
              <span>Install PWA</span>
            </button>

            <div className="hidden lg:flex flex-col text-right">
              <span className="text-sm font-semibold text-slate-200">
                {currentUser?.full_name || currentUser?.username}
              </span>
              <span className="text-xs text-indigo-400 font-medium">
                {currentUser?.role || (currentUser?.is_admin ? 'Administrator' : 'User')}
              </span>
            </div>

            <div className="w-8 h-8 sm:w-9 sm:h-9 rounded-full bg-slate-800 border border-slate-700 flex items-center justify-center text-slate-300 shrink-0">
              <UserIcon className="w-4 h-4 sm:w-5 sm:h-5" />
            </div>

            <button
              onClick={onLogout}
              title="Log Out"
              className="p-1.5 sm:p-2 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-slate-800/80 transition-colors"
            >
              <LogOut className="w-4 h-4 sm:w-5 sm:h-5" />
            </button>

            {/* Mobile Hamburger Menu Toggle */}
            <button
              onClick={() => setIsMobileMenuOpen(!isMobileMenuOpen)}
              className="p-1.5 sm:p-2 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800 transition-colors md:hidden"
              title="Toggle Menu"
            >
              {isMobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>
      </div>

      {/* Navigation Tabs Bar (Desktop and Horizontal Scroll on Mobile with no-scrollbar) */}
      <div className="bg-slate-800/90 border-t border-slate-700/60 backdrop-blur-sm w-full">
        <div className="max-w-7xl mx-auto px-2 sm:px-6 lg:px-8">
          <nav
            ref={tabsNavRef}
            className="flex space-x-1 sm:space-x-3 overflow-x-auto py-2 no-scrollbar scroll-smooth"
          >
            {navItems.map((item) => {
              if (!item.visible) return null;
              const Icon = item.icon;
              const isActive = currentTab === item.id;

              return (
                <button
                  key={item.id}
                  data-active={isActive ? 'true' : 'false'}
                  onClick={() => handleTabSelect(item.id)}
                  className={`flex items-center gap-1.5 sm:gap-2 px-2.5 sm:px-3 py-1.5 sm:py-2 rounded-lg text-xs sm:text-sm font-medium transition-all whitespace-nowrap shrink-0 ${
                    isActive
                      ? 'bg-indigo-600 text-white shadow-sm shadow-indigo-600/30'
                      : 'text-slate-300 hover:text-white hover:bg-slate-700/60'
                  }`}
                >
                  <Icon className={`w-3.5 h-3.5 sm:w-4 sm:h-4 ${isActive ? 'text-white' : 'text-slate-400'}`} />
                  <span>{item.label}</span>
                  {item.badge !== undefined && item.badge > 0 && (
                    <span className={`px-1.5 py-0.2 sm:py-0.5 text-[10px] sm:text-xs font-bold rounded-full ${
                      isActive ? 'bg-white text-indigo-700' : 'bg-indigo-500/30 text-indigo-300'
                    }`}>
                      {item.badge}
                    </span>
                  )}
                </button>
              );
            })}
          </nav>
        </div>
      </div>

      {/* Mobile Drawer / Dropdown Menu */}
      {isMobileMenuOpen && (
        <div className="md:hidden bg-slate-900 border-t border-slate-800 px-3 py-3 space-y-1 shadow-2xl animate-in slide-in-from-top-2 duration-200">
          <div className="px-3 py-2 border-b border-slate-800 mb-2 flex items-center justify-between">
            <div>
              <p className="text-xs font-bold text-white">
                {currentUser?.full_name || currentUser?.username}
              </p>
              <p className="text-[11px] text-indigo-400">
                {currentUser?.role || (currentUser?.is_admin ? 'Administrator' : 'User')}
              </p>
            </div>
            {isInstallable && (
              <button
                onClick={handleInstallClick}
                className="px-2.5 py-1 text-xs rounded bg-indigo-600 text-white font-medium"
              >
                Install App
              </button>
            )}
          </div>
          {navItems.map((item) => {
            if (!item.visible) return null;
            const Icon = item.icon;
            const isActive = currentTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => handleTabSelect(item.id)}
                className={`w-full flex items-center justify-between px-3 py-2.5 rounded-xl text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-indigo-600 text-white font-semibold'
                    : 'text-slate-300 hover:bg-slate-800 hover:text-white'
                }`}
              >
                <div className="flex items-center gap-3">
                  <Icon className={`w-4 h-4 ${isActive ? 'text-white' : 'text-slate-400'}`} />
                  <span>{item.label}</span>
                </div>
                {item.badge !== undefined && item.badge > 0 && (
                  <span className={`px-2 py-0.5 text-xs font-bold rounded-full ${
                    isActive ? 'bg-white text-indigo-700' : 'bg-indigo-500/30 text-indigo-300'
                  }`}>
                    {item.badge}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      )}

      {/* iOS Add to Home Screen Instructions Modal */}
      {showIOSInstallGuide && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center p-4 bg-slate-950/70 backdrop-blur-sm animate-fadeIn">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl w-full max-w-sm p-5 text-white shadow-2xl space-y-4">
            <div className="flex items-center justify-between pb-2 border-b border-slate-800">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 rounded-lg bg-indigo-600 flex items-center justify-center font-bold text-white text-base">
                  P
                </div>
                <div>
                  <h3 className="font-bold text-sm">Install POPT on iPhone</h3>
                  <p className="text-xs text-slate-400">Add to Home Screen</p>
                </div>
              </div>
              <button
                onClick={() => setShowIOSInstallGuide(false)}
                className="p-1 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <p className="text-xs text-slate-300 leading-relaxed">
              Apple does not allow direct APK downloads. You can install POPT directly onto your iPhone home screen in 2 easy steps:
            </p>

            <div className="space-y-3 bg-slate-800/60 p-3.5 rounded-xl border border-slate-700/60 text-xs text-slate-200">
              <div className="flex items-start gap-2.5">
                <span className="w-5 h-5 rounded-full bg-indigo-600 font-bold flex items-center justify-center shrink-0 text-[11px] mt-0.5">
                  1
                </span>
                <span>
                  Tap the Safari <strong>Share</strong> icon (the square with an arrow pointing up at the bottom of your screen).
                </span>
              </div>
              <div className="flex items-start gap-2.5">
                <span className="w-5 h-5 rounded-full bg-indigo-600 font-bold flex items-center justify-center shrink-0 text-[11px] mt-0.5">
                  2
                </span>
                <span>
                  Scroll down and tap <strong>Add to Home Screen</strong> (<span className="text-indigo-400 font-bold">+</span>), then tap <strong>Add</strong>.
                </span>
              </div>
            </div>

            <button
              onClick={() => {
                setShowIOSInstallGuide(false);
                localStorage.setItem('popt_installed', 'true');
                setIsInstalled(true);
              }}
              className="w-full py-2.5 bg-indigo-600 hover:bg-indigo-500 font-semibold text-xs rounded-xl text-white transition-all shadow-md"
            >
              Got it
            </button>
          </div>
        </div>
      )}
    </header>
  );
};
