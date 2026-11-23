import { StoreHierarchyItem } from '../types';
import { supabase } from './supabase';

const HIERARCHY_STORAGE_KEY = 'popt_store_hierarchy';

/**
 * Empty default hierarchy: Users configure their own hierarchy from scratch.
 * NO HARDCODED OR SEED DIVISIONS/SECTIONS/DEPARTMENTS!
 */
export const DEFAULT_STORE_HIERARCHY: StoreHierarchyItem[] = [];

/**
 * Clean legacy seed entries from localStorage if present
 */
function cleanLegacySeeds(items: StoreHierarchyItem[]): StoreHierarchyItem[] {
  return items.filter(item => {
    if (!item) return false;
    // Filter out seed ids or default seeds
    if (item.id && item.id.startsWith('seed-')) return false;
    const div = (item.division || '').trim().toLowerCase();
    const sec = (item.section || '').trim().toLowerCase();
    const dept = (item.department || '').trim().toLowerCase();
    // Also remove default seeds if they match the old hardcoded list
    const legacySeedCombos = [
      'men|formals|shirts',
      'men|formals|trousers',
      'men|formals|blazers',
      'men|casuals|t-shirts',
      'men|traditional|kurtas & dhotis',
      'women|ethnic wear|sarees',
      'women|ethnic wear|salwar kameez',
      'women|western|kurtis',
      'fabrics|cotton|shirting fabric',
      'fabrics|silk|pure silk material',
      'kids|boys|sets & rompers',
      'kids|girls|frocks & dresses',
    ];
    if (legacySeedCombos.includes(`${div}|${sec}|${dept}`)) {
      return false;
    }
    return true;
  });
}

/**
 * Fetch hierarchy records.
 * Hierarchy priority:
 * 1. Supabase `store_hierarchy` table (if present)
 * 2. `localStorage` key `'popt_store_hierarchy'`
 * 3. Sync and extract distinct division, section, department from existing `inventory` records in Supabase!
 *
 * All user-created items persist and are immediately returned.
 */
export async function getStoreHierarchy(): Promise<StoreHierarchyItem[]> {
  let list: StoreHierarchyItem[] = [];

  // 1. Try Supabase store_hierarchy table if it exists
  try {
    const { data, error } = await supabase.from('store_hierarchy').select('*');
    if (!error && Array.isArray(data) && data.length > 0) {
      list = cleanLegacySeeds(
        data.map((d: any) => ({
          id: String(d.id || `${d.division}-${d.section}-${d.department}`),
          division: d.division || '',
          section: d.section || '',
          department: d.department || '',
          type: (d.type === 'Textile' || d.type === 'Readymade') ? d.type : 'Readymade',
        }))
      );
      // Cache cleaned to localStorage
      try {
        localStorage.setItem(HIERARCHY_STORAGE_KEY, JSON.stringify(list));
      } catch {}
      return list;
    }
  } catch (err) {
    // Graceful fallback if table does not exist
  }

  // 2. Read from localStorage
  try {
    const cached = localStorage.getItem(HIERARCHY_STORAGE_KEY);
    if (cached) {
      const parsed = JSON.parse(cached);
      if (Array.isArray(parsed)) {
        list = cleanLegacySeeds(parsed);
      }
    }
  } catch {}

  // 3. Query existing inventory to discover any custom divisions/sections/departments created by users
  try {
    const { data: invData } = await supabase.from('inventory').select('division, section, department, category');
    if (invData && Array.isArray(invData)) {
      let updated = false;
      const existingKeys = new Set(
        list.map(item => `${item.division.toLowerCase()}|${item.section.toLowerCase()}|${item.department.toLowerCase()}`)
      );

      for (const inv of invData) {
        if (inv.division && inv.section && inv.department) {
          const div = String(inv.division).trim();
          const sec = String(inv.section).trim();
          const dept = String(inv.department).trim();
          const key = `${div.toLowerCase()}|${sec.toLowerCase()}|${dept.toLowerCase()}`;

          if (!existingKeys.has(key)) {
            const taxType = (inv.category === 'Textile' || div.toLowerCase().includes('textile') || dept.toLowerCase().includes('fabric'))
              ? 'Textile'
              : 'Readymade';

            list.push({
              id: `inv-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
              division: div,
              section: sec,
              department: dept,
              type: taxType,
            });
            existingKeys.add(key);
            updated = true;
          }
        }
      }
    }
  } catch {}

  // Store clean state back to localStorage
  try {
    localStorage.setItem(HIERARCHY_STORAGE_KEY, JSON.stringify(list));
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
  if (item.id && !item.id.startsWith('seed-') && !item.id.startsWith('hier-') && !item.id.startsWith('inv-')) {
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
