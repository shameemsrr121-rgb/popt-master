import { createClient } from '@supabase/supabase-js';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL || 'https://jprczpvqumxwsjzjtvgo.supabase.co';
const supabaseAnonKey = import.meta.env.VITE_SUPABASE_ANON_KEY || 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImpwcmN6cHZxdW14d3Nqemp0dmdvIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTUxODYzMjQsImV4cCI6MjExMDc2MjMyNH0._mPpVWy_G9MWmw8vwpQjk-2q5eyW5p2ZZsOBBKpK5aY';

export const supabase = createClient(supabaseUrl, supabaseAnonKey);

export async function uploadPoAttachment(file: File, prefix: string = 'po'): Promise<string | null> {
  try {
    const fileExt = file.name.split('.').pop();
    const fileName = `${prefix}_${Date.now()}_${Math.random().toString(36).substring(2, 7)}.${fileExt}`;
    const filePath = `uploads/${fileName}`;

    const { error: uploadError } = await supabase.storage
      .from('po-attachments')
      .upload(filePath, file, {
        cacheControl: '3600',
        upsert: false
      });

    if (uploadError) {
      console.error('Storage upload error:', uploadError);
      return null;
    }

    const { data } = supabase.storage
      .from('po-attachments')
      .getPublicUrl(filePath);

    return data.publicUrl;
  } catch (err) {
    console.error('Error uploading file to Supabase storage:', err);
    return null;
  }
}
