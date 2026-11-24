import { createClient } from '@supabase/supabase-js';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL || 'https://jprczpvqumxwsjzjtvgo.supabase.co';
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY || 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImpwcmN6cHZxdW14d3Nqemp0dmdvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTUxODYzMjQsImV4cCI6MjExMDc2MjMyNH0._mPpVWy_G9MWmw8vwpQjk-2q5eyW5p2ZZsOBBKpK5aY';

export const supabase = createClient(supabaseUrl, supabaseAnonKey);

/**
 * Optimizes an image File using an offscreen HTMLCanvasElement and returns a compact base64 Data URL.
 * Scales dimensions (default maxWidth = 800px) and compresses to JPEG (quality 0.7).
 * This typically reduces a 3MB-10MB mobile photo to ~30KB-60KB, which easily fits inside
 * PostgreSQL TEXT columns with zero network lag.
 */
export async function fileToOptimizedDataUrl(file: File, maxWidth = 800, quality = 0.7): Promise<string> {
  return new Promise((resolve, reject) => {
    // If it's a PDF or non-image, read directly as data URL
    if (!file.type.startsWith('image/')) {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = (e) => reject(e);
      reader.readAsDataURL(file);
      return;
    }

    const reader = new FileReader();
    reader.onload = (readerEvent) => {
      const img = new Image();
      img.onload = () => {
        try {
          let { width, height } = img;
          if (width > maxWidth || height > maxWidth) {
            if (width > height) {
              height = Math.round((height * maxWidth) / width);
              width = maxWidth;
            } else {
              width = Math.round((width * maxWidth) / height);
              height = maxWidth;
            }
          }

          const canvas = document.createElement('canvas');
          canvas.width = width;
          canvas.height = height;

          const ctx = canvas.getContext('2d');
          if (!ctx) {
            // Fallback to original read result if canvas context unavailable
            resolve(readerEvent.target?.result as string);
            return;
          }

          // Draw image to canvas
          ctx.drawImage(img, 0, 0, width, height);

          // Export as compressed JPEG
          const dataUrl = canvas.toDataURL('image/jpeg', quality);
          resolve(dataUrl);
        } catch (canvasErr) {
          console.warn('Canvas optimization error, falling back to raw data URL:', canvasErr);
          resolve(readerEvent.target?.result as string);
        }
      };
      img.onerror = () => {
        // If Image decoding fails, return raw data URL
        resolve(readerEvent.target?.result as string);
      };
      img.src = readerEvent.target?.result as string;
    };
    reader.onerror = (e) => reject(e);
    reader.readAsDataURL(file);
  });
}

/**
 * Uploads an attachment to Supabase Storage (`po-attachments` bucket).
 * If storage upload fails (bucket missing, RLS policy 403, network offline, etc.),
 * it AUTOMATICALLY falls back to an optimized base64 Data URL so the application NEVER fails!
 */
export async function uploadPoAttachment(file: File, prefix: string = 'po'): Promise<string | null> {
  try {
    const fileExt = file.name.split('.').pop() || 'jpg';
    const fileName = `${prefix}_${Date.now()}_${Math.random().toString(36).substring(2, 7)}.${fileExt}`;
    const filePath = `uploads/${fileName}`;

    const { error: uploadError } = await supabase.storage
      .from('po-attachments')
      .upload(filePath, file, {
        cacheControl: '3600',
        upsert: false
      });

    if (uploadError) {
      console.warn('Supabase storage upload failed or not configured, using optimized base64 data URL fallback:', uploadError.message);
      // Bulletproof fallback: convert to optimized base64 Data URL
      return await fileToOptimizedDataUrl(file);
    }

    const { data } = supabase.storage
      .from('po-attachments')
      .getPublicUrl(filePath);

    if (data?.publicUrl) {
      return data.publicUrl;
    }

    return await fileToOptimizedDataUrl(file);
  } catch (err) {
    console.warn('Error during storage upload, falling back to optimized base64 data URL:', err);
    try {
      return await fileToOptimizedDataUrl(file);
    } catch (fallbackErr) {
      console.error('Failed to generate base64 fallback:', fallbackErr);
      return null;
    }
  }
}
