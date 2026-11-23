export interface User {
  id: number;
  username: string;
  password_hash: string;
  full_name: string;
  role: string;
  is_admin: boolean;
  can_delete_po: boolean;
  show_procurement: boolean;
  show_inventory: boolean;
  show_supplier: boolean;
  show_report: boolean;
  show_margin: boolean;
  show_google_sheets: boolean;
  show_admin_panel: boolean;
}

export interface CompanyProfile {
  id: number;
  company_name: string;
  address: string;
  phone: string;
  email: string;
  gst_number: string;
  website: string;
  logo_uri?: string | null;
  bank_name: string;
  account_number: string;
  ifsc_code: string;
  terms_and_conditions: string;
}

export interface Supplier {
  id: number;
  code: string;
  name: string;
  contact_person: string;
  phone: string;
  email: string;
  address: string;
  payment_terms: string;
  rating: number;
  category: string;
  default_margin_percent: number;
  gst_number: string;
  is_active: boolean;
}

export interface Location {
  id: number;
  code: string;
  name: string;
  address: string;
  type: string;
  is_active: boolean;
}

export interface InventoryItem {
  id: number;
  sku: string;
  name: string;
  category: string;
  unit: string;
  unit_cost: number;
  standard_selling_price: number;
  current_stock: number;
  min_reorder_level: number;
  default_supplier_id?: number | null;
  division: string;
  section: string;
  department: string;
  category1: string; // Brand
  category2: string; // Season
  category3: string; // Fabric
  category4: string; // Fit
  category5: string; // Size
  category6: string; // Color
}

export interface PurchaseOrderItem {
  id?: number;
  po_id?: number;
  inventory_item_id?: number | null;
  item_sku: string;
  item_name: string;
  quantity_ordered: number;
  quantity_received: number;
  unit_price: number;
  tax_percent: number;
  taxable_amount: number;
  tax_amount: number;
  total_price: number;
  photo_uri?: string | null;
  location: string;
  division?: string;
  section?: string;
  department?: string;
  c1_brand?: string;
  c2_size?: string;
  c3_design?: string;
  c4_colour?: string;
}

export interface PurchaseOrder {
  id: number;
  po_number: string;
  supplier_id: number;
  supplier_name: string;
  order_date_millis: number;
  expected_delivery_date_millis: number;
  payment_terms: string;
  status: 'DRAFT' | 'ISSUED' | 'PARTIALLY_RECEIVED' | 'RECEIVED' | 'CANCELLED';
  subtotal: number;
  tax_amount: number;
  total_amount: number;
  notes: string;
  photo_uri?: string | null;
  location: string;
  created_by: string;
  created_at?: string;
  items?: PurchaseOrderItem[];
}

export interface PurchasePlan {
  id: number;
  title: string;
  planned_date_millis: number;
  to_date_millis: number;
  location: string;
  notes: string;
  estimated_budget: number;
  category_or_supplier: string;
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
  status: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CONVERTED_TO_PO';
  reminder_days_before: number;
  converted_po_id?: number | null;
  created_by: string;
  created_at?: string;
}

export interface TaxRule {
  id?: number;
  category_name: string;
  hsn_code?: string;
  tax_rate_percent: number;
  min_slab_amount: number;
  max_slab_amount: number;
  description?: string;
  rule_name?: string;
}

