import { StoreHierarchyItem } from '../types';
import { supabase } from './supabase';

const HIERARCHY_STORAGE_KEY = 'popt_store_hierarchy';

export const DEFAULT_STORE_HIERARCHY: StoreHierarchyItem[] = [
  { id: 'seed-1', division: 'Men', section: 'Formals', department: 'Shirts', type: 'Readymade' },
  { id: 'seed-2', division: 'Men', section: 'Formals', department: 'Trousers', type: 'Readymade' },
  { id: 'seed-3', division: 'Men', section: 'Formals', department: 'Blazers', type: 'Readymade' },
  { id: 'seed-4', division: 'Men', section: 'Casuals', department: 'T-Shirts', type: 'Readymade' },
  { id: 'seed-5', division: 'Men', section: 'Traditional', department: 'Kurtas & Dhotis', type: 'Textile' },
  { id: 'seed-6', division: 'Women', section: 'Ethnic Wear', department: 'Sarees', type: 'Textile' },
  { id: 'seed-7', division: 'Women', section: 'Ethnic Wear', department: 'Salwar Kameez', type: 'Readymade' },
  { id: 'seed-8', division: 'Women', section: 'Western', department: 'Kurtis', type: 'Readymade' },
  { id: 'seed-9', division: 'Fabrics', section: 'Cotton', department: 'Shirting Fabric', type: 'Textile' },
  { id: 'seed-10', division: 'Fabrics', section: 'Silk', department: 'Pure Silk Material', type: 'Textile' },
  { id: 'seed-11', division: 'Kids', section: 'Boys', department: 'Sets & Rompers', type: 'Readymade' },
  { id: 'seed-12', division: 'Kids', section: 'Girls', department: 'Frocks & Dresses', type: 'Readymade' },
];

/**
 * Fetch hierarchy records.
 * First tries Supabase `store_hierarchy`. If table doesn't exist,
 * attempts to sync/read from inventory distinct entries, merges with localStorage and default seeds.
 */
export async function getStoreHierarchy(): Promise<StoreHierarchyItem[]> {
  let list: StoreHierarchyItem[] = [];

  // 1. Try Supabase store_hierarchy table
  try {
    const { data, error } = await supabase.from('store_hierarchy').select('*');
    if (!error && Array.isArray(data) && data.length > 0) {
      list = data.map((d: any) => ({
        id: String(d.id || `${d.division}-${d.section}-${d.department}`),
        division: d.division || '',
        section: d.section || '',
        department: d.department || '',
        type: (d.type === 'Textile' || d.type === 'Readymade') ? d.type : 'Readymade',
      }));
      // Cache to localStorage
      try {
        localStorage.setItem(HIERARCHY_STORAGE_KEY, JSON.stringify(list));
      } catch {}
      return list;
    }
  } catch (err) {
    // Graceful fallback
  }

  // 2. Read from localStorage
  try {
    const cached = localStorage.getItem(HIERARCHY_STORAGE_KEY);
    if (cached) {
      const parsed = JSON.parse(cached);
      if (Array.isArray(parsed) && parsed.length > 0) {
        list = parsed;
      }
    }
  } catch {}

  // 3. Fallback or merge with defaults
  if (list.length === 0) {
    list = [...DEFAULT_STORE_HIERARCHY];
    try {
      localStorage.setItem(HIERARCHY_STORAGE_KEY, JSON.stringify(list));
    } catch {}
  } else {
    // Ensure all default seeds exist if not already present
    const existingKeys = new Set(list.map(item => `${item.division.toLowerCase()}|${item.section.toLowerCase()}|${item.department.toLowerCase()}`));
    for (const seed of DEFAULT_STORE_HIERARCHY) {
      const key = `${seed.division.toLowerCase()}|${seed.section.toLowerCase()}|${seed.department.toLowerCase()}`;
      if (!existingKeys.has(key)) {
        list.push(seed);
        existingKeys.add(key);
      }
    }
    try {
      localStorage.setItem(HIERARCHY_STORAGE_KEY, JSON.stringify(list));
    } catch {}
  }

  // Also query inventory to see if there are any custom divisions/sections/departments created
  try {
    const { data: invData } = await supabase.from('inventory').select('division, section, department, category');
    if (invData && Array.isArray(invData)) {
      let updated = false;
      const existingKeys = new Set(list.map(item => `${item.division.toLowerCase()}|${item.section.toLowerCase()}|${item.department.toLowerCase()}`));
      for (const inv of invData) {
        if (inv.division && inv.section && inv.department) {
          const key = `${inv.division.toLowerCase()}|${inv.section.toLowerCase()}|${inv.department.toLowerCase()}`;
          if (!existingKeys.has(key)) {
            const taxType = (inv.category === 'Textile' || inv.division.toLowerCase().includes('textile') || inv.department.toLowerCase().includes('fabric')) ? 'Textile' : 'Readymade';
            list.push({
              id: `inv-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
              division: inv.division,
              section: inv.section,
              department: inv.department,
              type: taxType,
            });
            existingKeys.add(key);
            updated = true;
          }
        }
      }
      if (updated) {
        try {
          localStorage.setItem(HIERARCHY_STORAGE_KEY, JSON.stringify(list));
        } catch {}
      }
    }
  } catch {}

  return list;
}

/**
 * Save / Add a new hierarchy entry
 */
export async function saveStoreHierarchyItem(item: Omit<StoreHierarchyItem, 'id'>): Promise<StoreHierarchyItem> {
  const newItem: StoreHierarchyItem = {
    id: `hier-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`,
    division: item.division.trim(),
    section: item.section.trim(),
    department: item.department.trim(),
    type: item.type === 'Textile' ? 'Textile' : 'Readymade',
  };

  // Try saving to Supabase if store_hierarchy table exists
  try {
    const { data, error } = await supabase.from('store_hierarchy').insert({
      division: newItem.division,
      section: newItem.section,
      department: newItem.department,
      type: newItem.type,
    }).select().single();

    if (!error && data) {
      newItem.id = String(data.id);
    }
  } catch {}

  // Save to localStorage
  try {
    const current = await getStoreHierarchy();
    // Check if duplicate
    const exists = current.some(
      c => c.division.toLowerCase() === newItem.division.toLowerCase() &&
           c.section.toLowerCase() === newItem.section.toLowerCase() &&
           c.department.toLowerCase() === newItem.department.toLowerCase()
    );
    if (!exists) {
      current.push(newItem);
      localStorage.setItem(HIERARCHY_STORAGE_KEY, JSON.stringify(current));
    }
  } catch {}

  return newItem;
}

/**
 * Delete a hierarchy entry
 */
export async function deleteStoreHierarchyItem(item: StoreHierarchyItem): Promise<void> {
  // Try Supabase delete
  if (item.id && !item.id.startsWith('seed-') && !item.id.startsWith('hier-')) {
    try {
      await supabase.from('store_hierarchy').delete().eq('id', item.id);
    } catch {}
  }

  // Remove from localStorage
  try {
    const current = await getStoreHierarchy();
    const filtered = current.filter(
      c => !(c.division.toLowerCase() === item.division.toLowerCase() &&
             c.section.toLowerCase() === item.section.toLowerCase() &&
             c.department.toLowerCase() === item.department.toLowerCase())
    );
    localStorage.setItem(HIERARCHY_STORAGE_KEY, JSON.stringify(filtered));
  } catch {}
}
