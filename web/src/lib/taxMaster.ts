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

export interface TaxCalculationResult {
  rate: number;
  ruleSource: string;
  breakdown: {
    cgst: number;
    sgst: number;
    igst: number;
  };
}

// Supported Core Categories
export const CATEGORY_READYMADE = 'Readymade';
export const CATEGORY_TEXTILE = 'Textile';

// Recognized keywords for Textile, Handloom & Fabric items that default to 5% GST
export const TEXTILE_KEYWORDS = [
  'textile', 'textiles', 'fabric', 'fabrics', 'saree', 'sarees', 'sari', 'saris',
  'cotton', 'silk', 'linen', 'chiffon', 'georgette', 'khadi', 'handloom',
  'yarn', 'unstitched', 'wool', 'polyester', 'weaving', 'shirting', 'suiting',
  'dupatta', 'dhoti', 'lungi', 'towel', 'bedsheet', 'curtain', 'shawl',
  'crepe', 'rayon', 'viscose', 'denim fabric', 'knit fabric', 'weaving yarn'
];

/**
 * Checks if a string contains any textile keywords
 */
export function isTextileKeyword(text?: string): boolean {
  if (!text || !text.trim()) return false;
  const lower = text.trim().toLowerCase();
  return TEXTILE_KEYWORDS.some((kw) => lower.includes(kw) || kw.includes(lower));
}

/**
 * Statutory / Custom Tax Master Engine for Indian GST in Apparel & Textiles
 *
 * Evaluation Order:
 * 1. Custom Category Tax Rules (from category_tax_rules table):
 *    Matches category_name against category, division, department, section, or itemName
 *    where min_slab_amount <= unitCost <= max_slab_amount.
 * 2. Textile Rule:
 *    Matches 'Textile' category/division or textile keywords (saree, silk, cotton, etc.):
 *    ALWAYS 5% GST flat regardless of cost.
 * 3. Readymade / Standard Apparel Rule:
 *    - Unit cost < ₹2,500: 5.0% GST
 *    - Unit cost >= ₹2,500: 18.0% GST
 */
export function calculateTaxRate(params: {
  itemName?: string;
  category?: string;
  division?: string;
  department?: string;
  section?: string;
  unitCost: number;
  customRules?: TaxRule[];
  isInterState?: boolean;
}): TaxCalculationResult {
  const {
    itemName = '',
    category = '',
    division = '',
    department = '',
    section = '',
    unitCost = 0,
    customRules = [],
    isInterState = false
  } = params;

  const cost = Number(unitCost) || 0;
  const trimmedName = itemName.trim();
  const trimmedCat = category.trim();
  const trimmedDiv = division.trim();
  const trimmedDept = department.trim();
  const trimmedSec = section.trim();

  // 1. Check custom user-defined Category Tax Rules
  if (customRules && customRules.length > 0) {
    const matchedRule = customRules.find((rule) => {
      const ruleCat = (rule.category_name || '').trim().toLowerCase();
      if (!ruleCat) return false;

      const matches =
        (trimmedCat && ruleCat === trimmedCat.toLowerCase()) ||
        (trimmedDept && ruleCat === trimmedDept.toLowerCase()) ||
        (trimmedDiv && ruleCat === trimmedDiv.toLowerCase()) ||
        (trimmedSec && ruleCat === trimmedSec.toLowerCase()) ||
        (trimmedName && (ruleCat === trimmedName.toLowerCase() || trimmedName.toLowerCase().includes(ruleCat)));

      const minSlab = Number(rule.min_slab_amount) || 0;
      const maxSlab = rule.max_slab_amount != null ? Number(rule.max_slab_amount) : Infinity;
      const inSlab = cost >= minSlab && cost <= maxSlab;

      return matches && inSlab;
    });

    if (matchedRule) {
      const rate = Number(matchedRule.tax_rate_percent) || 0;
      const ruleLabel = matchedRule.rule_name || matchedRule.category_name;
      return {
        rate,
        ruleSource: `Custom Rule (${ruleLabel}): ${rate}%`,
        breakdown: computeBreakdown(rate, isInterState)
      };
    }
  }

  // 2. Check if Textile (ALWAYS 5.0% flat GST)
  const isTextile =
    trimmedCat.toLowerCase() === CATEGORY_TEXTILE.toLowerCase() ||
    trimmedDiv.toLowerCase() === CATEGORY_TEXTILE.toLowerCase() ||
    trimmedSec.toLowerCase() === CATEGORY_TEXTILE.toLowerCase() ||
    trimmedDept.toLowerCase() === CATEGORY_TEXTILE.toLowerCase() ||
    isTextileKeyword(trimmedCat) ||
    isTextileKeyword(trimmedDiv) ||
    isTextileKeyword(trimmedDept) ||
    isTextileKeyword(trimmedSec) ||
    isTextileKeyword(trimmedName);

  if (isTextile) {
    const rate = 5.0;
    return {
      rate,
      ruleSource: '5% GST (Textile / Fabric Flat Rate)',
      breakdown: computeBreakdown(rate, isInterState)
    };
  }

  // 3. Readymade / Standard Apparel Rule
  if (cost < 2500) {
    const rate = 5.0;
    return {
      rate,
      ruleSource: '5% GST (Readymade < ₹2,500)',
      breakdown: computeBreakdown(rate, isInterState)
    };
  } else {
    const rate = 18.0;
    return {
      rate,
      ruleSource: '18% GST (Readymade ≥ ₹2,500 Slab)',
      breakdown: computeBreakdown(rate, isInterState)
    };
  }
}

function computeBreakdown(rate: number, isInterState: boolean) {
  if (isInterState) {
    return {
      cgst: 0,
      sgst: 0,
      igst: rate
    };
  }
  const half = +(rate / 2).toFixed(2);
  return {
    cgst: half,
    sgst: half,
    igst: 0
  };
}

/**
 * Returns a short badge string for UI flow presentation
 */
export function getTaxFlowBadge(params: {
  itemName?: string;
  category?: string;
  division?: string;
  department?: string;
  section?: string;
  unitCost: number;
  customRules?: TaxRule[];
}): { badge: string; isTextile: boolean; isHighSlab: boolean; isCustom: boolean } {
  const result = calculateTaxRate(params);
  const cost = Number(params.unitCost) || 0;

  if (result.ruleSource.startsWith('Custom Rule')) {
    return {
      badge: `⚡ Auto ${result.rate}% (${result.ruleSource.split(':')[0]})`,
      isTextile: false,
      isHighSlab: result.rate > 5,
      isCustom: true
    };
  }

  if (result.ruleSource.includes('Textile')) {
    return {
      badge: `⚡ Auto 5% (Textile / Fabric)`,
      isTextile: true,
      isHighSlab: false,
      isCustom: false
    };
  }

  if (cost >= 2500) {
    return {
      badge: `⚡ Auto 18% (Apparel ≥ ₹2,500)`,
      isTextile: false,
      isHighSlab: true,
      isCustom: false
    };
  }

  return {
    badge: `⚡ Auto 5% (Apparel < ₹2,500)`,
    isTextile: false,
    isHighSlab: false,
    isCustom: false
  };
}
