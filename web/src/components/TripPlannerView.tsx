import React, { useState, useRef } from 'react';
import { 
  Calendar as CalendarIcon, 
  Plus, 
  MapPin, 
  IndianRupee, 
  Clock, 
  ArrowRight, 
  CheckCircle2, 
  AlertCircle,
  FileText,
  X,
  Trash2,
  Camera,
  Upload,
  Image as ImageIcon
} from 'lucide-react';
import { PurchasePlan, User, Supplier } from '../types';
import { supabase, uploadPoAttachment, fileToOptimizedDataUrl } from '../lib/supabase';
import { CameraCaptureDialog } from './CameraCaptureDialog';

interface TripPlannerViewProps {
  plans: PurchasePlan[];
  suppliers: Supplier[];
  currentUser: User | null;
  onRefresh: () => void;
  onConvertToPO: (plan: PurchasePlan) => void;
  isCreateOpen: boolean;
  setIsCreateOpen: (open: boolean) => void;
}

export const TripPlannerView: React.FC<TripPlannerViewProps> = ({
  plans,
  suppliers,
  currentUser,
  onRefresh,
  onConvertToPO,
  isCreateOpen,
  setIsCreateOpen
}) => {
  const [title, setTitle] = useState('');
  const [location, setLocation] = useState('Bombay');
  const [fromDate, setFromDate] = useState(new Date().toISOString().split('T')[0]);
  const [toDate, setToDate] = useState(
    new Date(Date.now() + 3 * 86400000).toISOString().split('T')[0]
  );
  const [budget, setBudget] = useState('500000');
  const [categoryOrSupplier, setCategoryOrSupplier] = useState('');
  const [priority, setPriority] = useState<'HIGH' | 'MEDIUM' | 'LOW'>('MEDIUM');
  const [notes, setNotes] = useState('');
  const [reminderDays, setReminderDays] = useState(2);
  const [selectedPhotoFile, setSelectedPhotoFile] = useState<File | null>(null);
  const [previewPhotoUrl, setPreviewPhotoUrl] = useState<string | null>(null);
  const [isCameraOpen, setIsCameraOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const fileUploadInputRef = useRef<HTMLInputElement | null>(null);

  const handleCreatePlan = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim()) {
      alert('Please enter a trip title');
      return;
    }

    setIsSubmitting(true);
    try {
      let uploadedPhotoUrl: string | null = null;
      if (selectedPhotoFile) {
        uploadedPhotoUrl = await uploadPoAttachment(selectedPhotoFile, 'trip');
        if (!uploadedPhotoUrl && previewPhotoUrl) {
          uploadedPhotoUrl = previewPhotoUrl.startsWith('data:')
            ? previewPhotoUrl
            : await fileToOptimizedDataUrl(selectedPhotoFile);
        }
      }

      const plannedDateMillis = new Date(fromDate).getTime();
      const toDateMillis = toDate ? new Date(toDate).getTime() : 0;

      const { error } = await supabase.from('purchase_plans').insert({
        title: title.trim(),
        planned_date_millis: plannedDateMillis,
        to_date_millis: toDateMillis,
        location: location.trim(),
        notes: notes.trim(),
        estimated_budget: Number(budget) || 0,
        category_or_supplier: categoryOrSupplier.trim(),
        priority: priority,
        status: 'PLANNED',
        reminder_days_before: Number(reminderDays) || 1,
        photo_uri: uploadedPhotoUrl,
        created_by: currentUser?.full_name || currentUser?.username || 'Web Portal'
      });

      if (error) {
        if (error.message?.includes('photo_uri') || error.code === 'PGRST204') {
          const { error: retryError } = await supabase.from('purchase_plans').insert({
            title: title.trim(),
            planned_date_millis: plannedDateMillis,
            to_date_millis: toDateMillis,
            location: location.trim(),
            notes: notes.trim(),
            estimated_budget: Number(budget) || 0,
            category_or_supplier: categoryOrSupplier.trim(),
            priority: priority,
            status: 'PLANNED',
            reminder_days_before: Number(reminderDays) || 1,
            created_by: currentUser?.full_name || currentUser?.username || 'Web Portal'
          });
          if (retryError) throw retryError;
        } else {
          throw error;
        }
      }

      setIsCreateOpen(false);
      setTitle('');
      setNotes('');
      setSelectedPhotoFile(null);
      setPreviewPhotoUrl(null);
      onRefresh();
    } catch (err: any) {
      alert(`Error scheduling trip: ${err.message}`);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleUpdateStatus = async (planId: number, newStatus: string) => {
    await supabase
      .from('purchase_plans')
      .update({ status: newStatus })
      .eq('id', planId);
    onRefresh();
  };

  const handleDeletePlan = async (planId: number) => {
    if (confirm('Delete this sourcing trip plan?')) {
      await supabase.from('purchase_plans').delete().eq('id', planId);
      onRefresh();
    }
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-white p-5 rounded-2xl shadow-sm border border-slate-200/80">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 tracking-tight">Sourcing Trip & Order Planner</h1>
          <p className="text-sm text-slate-500 mt-1">
            Schedule textile & readymade buying trips, track budgets, and convert plans directly into POs
          </p>
        </div>
        <button
          onClick={() => setIsCreateOpen(true)}
          className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-sm shadow-sm shadow-indigo-600/30 transition-all hover:scale-[1.02]"
        >
          <Plus className="w-4 h-4" />
          Schedule New Trip
        </button>
      </div>

      {/* Grid of Planned Trips */}
      {plans.length === 0 ? (
        <div className="bg-white rounded-2xl p-16 text-center border border-slate-200/80 shadow-sm text-slate-400">
          <CalendarIcon className="w-12 h-12 mx-auto text-slate-300 mb-3" />
          <h3 className="text-base font-semibold text-slate-700">No sourcing trips planned yet</h3>
          <p className="text-xs text-slate-400 mt-1">Schedule trips to Surat, Bombay, or Tirupur to coordinate buying</p>
          <button
            onClick={() => setIsCreateOpen(true)}
            className="mt-4 px-4 py-2 bg-indigo-600 text-white text-xs font-semibold rounded-xl hover:bg-indigo-700"
          >
            Schedule Trip
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {plans.map(plan => {
            const startDateStr = new Date(Number(plan.planned_date_millis)).toLocaleDateString('en-IN', {
              day: 'numeric',
              month: 'short',
              year: 'numeric'
            });
            const endDateStr = plan.to_date_millis > 0 
              ? new Date(Number(plan.to_date_millis)).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })
              : null;

            return (
              <div 
                key={plan.id}
                className="bg-white rounded-2xl p-5 shadow-sm border border-slate-200/80 flex flex-col justify-between hover:shadow-md transition-shadow"
              >
                <div>
                  {/* Top Badges */}
                  <div className="flex items-center justify-between gap-2">
                    <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-slate-100 text-slate-700 border">
                      <MapPin className="w-3 h-3 text-indigo-500" />
                      {plan.location || 'Local Sourcing'}
                    </span>
                    <span className={`px-2 py-0.5 rounded-full text-[11px] font-bold ${
                      plan.priority === 'HIGH'
                        ? 'bg-rose-100 text-rose-700'
                        : plan.priority === 'LOW'
                        ? 'bg-slate-100 text-slate-600'
                        : 'bg-amber-100 text-amber-700'
                    }`}>
                      {plan.priority} PRIORITY
                    </span>
                  </div>

                  {/* Title & Dates */}
                  <h3 className="text-base font-bold text-slate-900 mt-3">{plan.title}</h3>
                  <div className="flex items-center gap-1.5 text-xs text-slate-500 mt-1">
                    <Clock className="w-3.5 h-3.5 text-slate-400" />
                    <span>{startDateStr} {endDateStr && `to ${endDateStr}`}</span>
                  </div>

                  {/* Budget & Target */}
                  <div className="mt-4 p-3 rounded-xl bg-slate-50 border border-slate-100 flex items-center justify-between text-xs">
                    <div>
                      <span className="text-slate-400 block text-[10px] uppercase font-bold">Estimated Budget</span>
                      <span className="font-extrabold text-slate-900 text-sm">
                        ₹{Number(plan.estimated_budget || 0).toLocaleString('en-IN')}
                      </span>
                    </div>
                    {plan.category_or_supplier && (
                      <div className="text-right max-w-[140px]">
                        <span className="text-slate-400 block text-[10px] uppercase font-bold">Target</span>
                        <span className="font-semibold text-slate-700 truncate block">
                          {plan.category_or_supplier}
                        </span>
                      </div>
                    )}
                  </div>

                  {/* Reference Photo / Sample thumbnail if available */}
                  {plan.photo_uri && (
                    <div className="mt-3 rounded-xl overflow-hidden border border-slate-200 bg-slate-50 relative group">
                      <img
                        src={plan.photo_uri}
                        alt={plan.title}
                        className="w-full h-32 object-cover"
                      />
                      <a
                        href={plan.photo_uri}
                        target="_blank"
                        rel="noreferrer"
                        className="absolute bottom-2 right-2 px-2 py-1 bg-black/60 hover:bg-black/80 text-white rounded text-[10px] font-medium backdrop-blur-xs flex items-center gap-1"
                      >
                        <ImageIcon className="w-3 h-3" /> View Photo
                      </a>
                    </div>
                  )}

                  {/* Notes */}
                  {plan.notes && (
                    <p className="text-xs text-slate-600 mt-3 line-clamp-3 bg-slate-50/50 p-2.5 rounded-lg border border-slate-100">
                      {plan.notes}
                    </p>
                  )}
                </div>

                {/* Bottom Actions */}
                <div className="mt-5 pt-3 border-t border-slate-100 flex items-center justify-between">
                  {/* Status Dropdown */}
                  <select
                    value={plan.status}
                    onChange={(e) => handleUpdateStatus(plan.id, e.target.value)}
                    className="text-xs font-semibold px-2 py-1 rounded-lg border border-slate-200 bg-white"
                  >
                    <option value="PLANNED">Planned</option>
                    <option value="IN_PROGRESS">In Progress</option>
                    <option value="COMPLETED">Completed</option>
                    <option value="CONVERTED_TO_PO">Converted to PO</option>
                  </select>

                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => onConvertToPO(plan)}
                      title="Convert this trip into a Purchase Order"
                      className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-indigo-50 hover:bg-indigo-100 text-indigo-700 text-xs font-bold transition-colors"
                    >
                      <FileText className="w-3.5 h-3.5" /> Convert to PO
                    </button>
                    <button
                      onClick={() => handleDeletePlan(plan.id)}
                      className="p-1.5 text-slate-400 hover:text-rose-600"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* SCHEDULE TRIP MODAL */}
      {isCreateOpen && (
        <div className="fixed inset-0 z-50 bg-slate-900/60 backdrop-blur-sm overflow-y-auto p-2 sm:p-4">
          <div className="min-h-full flex items-center justify-center py-6 sm:py-8">
            <div className="bg-white rounded-3xl max-w-lg w-full p-5 sm:p-6 shadow-2xl border border-slate-100 my-auto">
              <div className="flex items-center justify-between pb-4 border-b border-slate-100">
                <div>
                  <h2 className="text-xl font-bold text-slate-900">Schedule Sourcing Trip</h2>
                  <p className="text-xs text-slate-500">Plan buying travels and set budgets</p>
                </div>
                <button
                  onClick={() => setIsCreateOpen(false)}
                  className="p-2 text-slate-400 hover:text-slate-600 rounded-xl"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

            <form onSubmit={handleCreatePlan} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Trip Title *</label>
                <input
                  type="text"
                  placeholder="e.g. Bombay Trip - Silk & Readymade Sourcing"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  required
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Destination Location</label>
                  <input
                    type="text"
                    placeholder="e.g. Surat / Bombay / Tirupur"
                    value={location}
                    onChange={(e) => setLocation(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Priority</label>
                  <select
                    value={priority}
                    onChange={(e: any) => setPriority(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  >
                    <option value="HIGH">High Priority</option>
                    <option value="MEDIUM">Medium Priority</option>
                    <option value="LOW">Low Priority</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">From Date *</label>
                  <input
                    type="date"
                    value={fromDate}
                    onChange={(e) => setFromDate(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                    required
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">To Date</label>
                  <input
                    type="date"
                    value={toDate}
                    onChange={(e) => setToDate(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Estimated Budget (₹)</label>
                  <input
                    type="number"
                    min="0"
                    step="1000"
                    value={budget}
                    onChange={(e) => setBudget(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">Target Vendor / Category</label>
                  <input
                    type="text"
                    placeholder="e.g. Silk Mills / Kurtis"
                    value={categoryOrSupplier}
                    onChange={(e) => setCategoryOrSupplier(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-1">Notes / Sourcing Requirements</label>
                <textarea
                  rows={3}
                  value={notes}
                  onChange={(e) => setNotes(e.target.value)}
                  placeholder="Items to inspect, sample criteria, market addresses..."
                  className="w-full px-3 py-2 bg-slate-50 border border-slate-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
              </div>

              {/* Sourcing Fabric / Sample Photo */}
              <div>
                <label className="block text-xs font-semibold text-slate-700 mb-2">
                  Sample Garment / Fabric Reference Photo
                </label>

                {previewPhotoUrl ? (
                  <div className="relative border-2 border-dashed border-indigo-200 bg-indigo-50/30 rounded-2xl p-3 flex items-center gap-3">
                    <img
                      src={previewPhotoUrl}
                      alt="Sample preview"
                      className="w-16 h-16 rounded-xl object-cover border border-slate-200 shadow-sm flex-shrink-0"
                    />
                    <div className="flex-1 min-w-0">
                      <p className="text-xs font-bold text-slate-800 truncate">
                        {selectedPhotoFile?.name || 'Attached Photo'}
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

              <div className="flex items-center justify-end gap-3 pt-3 border-t border-slate-100">
                <button
                  type="button"
                  onClick={() => setIsCreateOpen(false)}
                  className="px-4 py-2 rounded-xl text-sm font-semibold text-slate-600 hover:bg-slate-100"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting}
                  className="px-5 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-700 text-white font-semibold text-sm shadow-md shadow-indigo-600/30 transition-all disabled:opacity-50"
                >
                  {isSubmitting ? 'Saving...' : 'Save Sourcing Trip'}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
      )}

      {/* Live Camera Viewfinder Dialog */}
      <CameraCaptureDialog
        isOpen={isCameraOpen}
        onClose={() => setIsCameraOpen(false)}
        title="Snap Trip / Sample Reference Photo"
        onPhotoCaptured={(file, previewUrl) => {
          setSelectedPhotoFile(file);
          setPreviewPhotoUrl(previewUrl);
        }}
      />
    </div>
  );
};
