import React, { useState, useRef, useEffect, useMemo } from 'react';
import { Search, ChevronDown, Check, X } from 'lucide-react';
import { Supplier } from '../types';

interface SearchableSupplierSelectProps {
  suppliers: Supplier[];
  value: number;
  onChange: (supplierId: number) => void;
  required?: boolean;
}

export const SearchableSupplierSelect: React.FC<SearchableSupplierSelectProps> = ({
  suppliers,
  value,
  onChange,
  required = false,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [query, setQuery] = useState('');
  const containerRef = useRef<HTMLDivElement>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);

  const selectedSupplier = suppliers.find((s) => s.id === value);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setIsOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleEscape);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleEscape);
    };
  }, []);

  useEffect(() => {
    if (isOpen) {
      setTimeout(() => searchInputRef.current?.focus(), 0);
    } else {
      setQuery('');
    }
  }, [isOpen]);

  const normalizedQuery = query.trim().toLowerCase();
  const filteredSuppliers = useMemo(
    () =>
      normalizedQuery
        ? suppliers.filter(
            (s) =>
              s.name.toLowerCase().includes(normalizedQuery) ||
              (s.code && s.code.toLowerCase().includes(normalizedQuery))
          )
        : suppliers,
    [suppliers, normalizedQuery]
  );

  return (
    <div ref={containerRef} className="relative">
      {required && (
        <input
          type="text"
          value={value ? String(value) : ''}
          onChange={() => {}}
          required
          tabIndex={-1}
          aria-hidden="true"
          className="sr-only"
        />
      )}
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        className="w-full px-3 py-2.5 bg-white border border-slate-300 rounded-xl text-sm font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-500 shadow-2xs flex items-center justify-between gap-2 text-left"
      >
        <span className={`truncate ${selectedSupplier ? 'text-slate-900' : 'text-slate-500'}`}>
          {selectedSupplier
            ? `${selectedSupplier.name}${selectedSupplier.code ? ` (${selectedSupplier.code})` : ''}`
            : '-- Choose Supplier --'}
        </span>
        <ChevronDown
          className={`w-4 h-4 text-slate-400 shrink-0 transition-transform ${isOpen ? 'rotate-180' : ''}`}
        />
      </button>

      {isOpen && (
        <div className="absolute left-0 right-0 top-full mt-1.5 z-50 bg-white border border-slate-200 rounded-xl shadow-xl overflow-hidden">
          <div className="p-2 border-b border-slate-100 bg-slate-50/80">
            <div className="relative">
              <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
              <input
                ref={searchInputRef}
                type="text"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search supplier name or code..."
                className="w-full pl-8 pr-7 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-medium text-slate-800 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
              {query && (
                <button
                  type="button"
                  onClick={() => setQuery('')}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          </div>

          <div className="max-h-56 overflow-y-auto divide-y divide-slate-50">
            {filteredSuppliers.length === 0 ? (
              <div className="px-3 py-3 text-xs text-slate-400 text-center">
                No suppliers match "{query}"
              </div>
            ) : (
              filteredSuppliers.map((s) => {
                const isSelected = s.id === value;
                return (
                  <button
                    key={s.id}
                    type="button"
                    onClick={() => {
                      onChange(s.id);
                      setIsOpen(false);
                    }}
                    className={`w-full px-3 py-2 text-left text-xs font-semibold flex items-center justify-between gap-2 transition-colors ${
                      isSelected
                        ? 'bg-indigo-50 text-indigo-700'
                        : 'text-slate-800 hover:bg-slate-50'
                    }`}
                  >
                    <span className="truncate">
                      {s.name} {s.code ? <span className="text-slate-400 font-normal">({s.code})</span> : ''}
                    </span>
                    {isSelected && <Check className="w-3.5 h-3.5 text-indigo-600 shrink-0" />}
                  </button>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
};

interface SearchableDepartmentInputProps {
  options: string[];
  value: string;
  onChange: (department: string) => void;
  placeholder?: string;
  required?: boolean;
  compact?: boolean;
}

export const SearchableDepartmentInput: React.FC<SearchableDepartmentInputProps> = ({
  options,
  value,
  onChange,
  placeholder = 'Type or select Department...',
  required = false,
  compact = false,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const containerRef = useRef<HTMLDivElement>(null);
  const searchInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setIsOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    document.addEventListener('keydown', handleEscape);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
      document.removeEventListener('keydown', handleEscape);
    };
  }, []);

  useEffect(() => {
    if (!isOpen) {
      setSearchQuery('');
    }
  }, [isOpen]);

  const activeFilter = (searchQuery || value).trim().toLowerCase();
  const filteredOptions = useMemo(
    () =>
      activeFilter
        ? options.filter((opt) => opt.toLowerCase().includes(activeFilter))
        : options,
    [options, activeFilter]
  );

  return (
    <div ref={containerRef} className="relative">
      <div className="relative flex items-center">
        <input
          type="text"
          placeholder={placeholder}
          value={value}
          onFocus={() => setIsOpen(true)}
          onChange={(e) => {
            onChange(e.target.value);
            setSearchQuery(e.target.value);
            if (!isOpen) setIsOpen(true);
          }}
          className={
            compact
              ? 'px-2 py-1 pr-6 bg-white border border-slate-300 rounded-md text-[11px] font-semibold text-slate-800 focus:outline-none focus:ring-1 focus:ring-indigo-500 w-40'
              : 'w-full px-3 py-2 pr-12 bg-white border border-slate-300 rounded-xl text-sm font-semibold text-slate-900 focus:outline-none focus:ring-2 focus:ring-indigo-500 shadow-2xs'
          }
          required={required}
        />
        {value && (
          <button
            type="button"
            onClick={() => {
              onChange('');
              setSearchQuery('');
              setIsOpen(true);
            }}
            title="Clear department"
            className={`absolute ${compact ? 'right-5' : 'right-7'} text-slate-400 hover:text-slate-600 p-0.5`}
          >
            <X className={compact ? 'w-3 h-3' : 'w-3.5 h-3.5'} />
          </button>
        )}
        <button
          type="button"
          onClick={() => {
            const next = !isOpen;
            setIsOpen(next);
            if (next) {
              setSearchQuery('');
              setTimeout(() => searchInputRef.current?.focus(), 0);
            }
          }}
          title="Toggle department list"
          className={`absolute ${compact ? 'right-1' : 'right-2'} text-slate-400 hover:text-slate-600 p-0.5`}
        >
          <ChevronDown
            className={`${compact ? 'w-3.5 h-3.5' : 'w-4 h-4'} transition-transform ${isOpen ? 'rotate-180' : ''}`}
          />
        </button>
      </div>

      {isOpen && (
        <div
          className={`absolute left-0 top-full mt-1 z-50 bg-white border border-slate-200 rounded-xl shadow-xl overflow-hidden ${
            compact ? 'w-56' : 'right-0'
          }`}
        >
          <div className="p-2 border-b border-slate-100 bg-slate-50/80">
            <div className="relative">
              <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2 pointer-events-none" />
              <input
                ref={searchInputRef}
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search department..."
                className="w-full pl-8 pr-7 py-1.5 bg-white border border-slate-200 rounded-lg text-xs font-medium text-slate-800 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => setSearchQuery('')}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-600"
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>
          </div>

          <div className="max-h-48 overflow-y-auto divide-y divide-slate-50">
            {filteredOptions.length === 0 ? (
              <div className="px-3 py-2.5 text-xs text-slate-400 text-center">
                No matching department
              </div>
            ) : (
              filteredOptions.map((dept) => {
                const isSelected = dept.toLowerCase() === value.trim().toLowerCase();
                return (
                  <button
                    key={dept}
                    type="button"
                    onClick={() => {
                      onChange(dept);
                      setIsOpen(false);
                    }}
                    className={`w-full px-3 py-2 text-left text-xs font-semibold flex items-center justify-between gap-2 transition-colors ${
                      isSelected
                        ? 'bg-indigo-50 text-indigo-700'
                        : 'text-slate-800 hover:bg-slate-50'
                    }`}
                  >
                    <span className="truncate">{dept}</span>
                    {isSelected && <Check className="w-3.5 h-3.5 text-indigo-600 shrink-0" />}
                  </button>
                );
              })
            )}
          </div>
        </div>
      )}
    </div>
  );
};
