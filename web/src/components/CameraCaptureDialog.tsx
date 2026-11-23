import React, { useRef, useState, useEffect } from 'react';
import { Camera, X, RefreshCw, Check, AlertCircle, Image as ImageIcon } from 'lucide-react';

interface CameraCaptureDialogProps {
  isOpen: boolean;
  onClose: () => void;
  onPhotoCaptured: (file: File, previewUrl: string) => void;
  title?: string;
}

export const CameraCaptureDialog: React.FC<CameraCaptureDialogProps> = ({
  isOpen,
  onClose,
  onPhotoCaptured,
  title = 'Take Live Photo'
}) => {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const fileFallbackRef = useRef<HTMLInputElement | null>(null);
  
  const [stream, setStream] = useState<MediaStream | null>(null);
  const [capturedDataUrl, setCapturedDataUrl] = useState<string | null>(null);
  const [cameraError, setCameraError] = useState<string | null>(null);
  const [facingMode, setFacingMode] = useState<'environment' | 'user'>('environment');
  const [isStartingStream, setIsStartingStream] = useState(false);
  const [hasMultipleCameras, setHasMultipleCameras] = useState(false);

  // Check camera devices
  useEffect(() => {
    if (!isOpen) return;

    if (navigator.mediaDevices && navigator.mediaDevices.enumerateDevices) {
      navigator.mediaDevices.enumerateDevices().then(devices => {
        const videoInputs = devices.filter(d => d.kind === 'videoinput');
        if (videoInputs.length > 1) {
          setHasMultipleCameras(true);
        }
      }).catch(() => {});
    }
  }, [isOpen]);

  // Start video stream when modal opens
  useEffect(() => {
    if (!isOpen) {
      stopStream();
      setCapturedDataUrl(null);
      setCameraError(null);
      return;
    }

    startCamera(facingMode);

    return () => {
      stopStream();
    };
  }, [isOpen, facingMode]);

  const stopStream = () => {
    if (stream) {
      stream.getTracks().forEach(track => track.stop());
      setStream(null);
    }
  };

  const startCamera = async (mode: 'environment' | 'user') => {
    stopStream();
    setCameraError(null);
    setIsStartingStream(true);

    if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
      setCameraError('Live camera is not supported on this browser or connection. You can still use the direct camera capture option below.');
      setIsStartingStream(false);
      return;
    }

    try {
      const mediaStream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: { ideal: mode },
          width: { ideal: 1920 },
          height: { ideal: 1080 }
        },
        audio: false
      });

      setStream(mediaStream);
      if (videoRef.current) {
        videoRef.current.srcObject = mediaStream;
        videoRef.current.play().catch(e => console.error('Error playing video:', e));
      }
    } catch (err: any) {
      console.warn('getUserMedia error:', err);
      // Fallback try without facingMode constraints
      try {
        const fallbackStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
        setStream(fallbackStream);
        if (videoRef.current) {
          videoRef.current.srcObject = fallbackStream;
          videoRef.current.play().catch(e => console.error('Error playing fallback video:', e));
        }
      } catch (e: any) {
        setCameraError(
          err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError'
            ? 'Camera permission was denied. Please allow camera access in your browser or choose a photo from files.'
            : 'Unable to start camera stream. You can capture directly using your device camera.'
        );
      }
    } finally {
      setIsStartingStream(false);
    }
  };

  const toggleCamera = () => {
    setFacingMode(prev => (prev === 'environment' ? 'user' : 'environment'));
  };

  const handleSnap = () => {
    if (!videoRef.current) return;
    const video = videoRef.current;
    
    // Create canvas matching video resolution
    const canvas = document.createElement('canvas');
    canvas.width = video.videoWidth || 1280;
    canvas.height = video.videoHeight || 720;
    
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // Flip horizontal if front camera
    if (facingMode === 'user') {
      ctx.translate(canvas.width, 0);
      ctx.scale(-1, 1);
    }

    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);
    const dataUrl = canvas.toDataURL('image/jpeg', 0.9);
    setCapturedDataUrl(dataUrl);
    stopStream();
  };

  const handleRetake = () => {
    setCapturedDataUrl(null);
    startCamera(facingMode);
  };

  const handleConfirmPhoto = () => {
    if (!capturedDataUrl) return;

    // Convert dataUrl to File
    const arr = capturedDataUrl.split(',');
    const mimeMatch = arr[0].match(/:(.*?);/);
    const mime = mimeMatch ? mimeMatch[1] : 'image/jpeg';
    const bstr = atob(arr[1]);
    let n = bstr.length;
    const u8arr = new Uint8Array(n);
    while (n--) {
      u8arr[n] = bstr.charCodeAt(n);
    }
    const file = new File([u8arr], `camera_${Date.now()}.jpg`, { type: mime });
    onPhotoCaptured(file, capturedDataUrl);
    onClose();
  };

  // Device Native Camera fallback trigger
  const handleDeviceCameraCapture = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      const previewUrl = URL.createObjectURL(file);
      onPhotoCaptured(file, previewUrl);
      onClose();
    }
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/80 backdrop-blur-sm flex items-center justify-center p-3 sm:p-4">
      <div className="bg-slate-900 text-white rounded-3xl max-w-lg w-full overflow-hidden shadow-2xl border border-slate-700 flex flex-col max-h-[92vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-800 bg-slate-950/40">
          <div className="flex items-center gap-2">
            <Camera className="w-5 h-5 text-indigo-400" />
            <h3 className="font-bold text-base text-slate-100">{title}</h3>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-white rounded-xl hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Viewfinder / Captured Preview Area */}
        <div className="relative bg-black flex-1 flex items-center justify-center min-h-[300px] max-h-[460px] overflow-hidden">
          {capturedDataUrl ? (
            <div className="w-full h-full flex items-center justify-center bg-black">
              <img
                src={capturedDataUrl}
                alt="Captured sample"
                className="max-h-[450px] w-full object-contain"
              />
            </div>
          ) : cameraError ? (
            <div className="p-6 text-center space-y-4 max-w-sm">
              <AlertCircle className="w-12 h-12 text-amber-400 mx-auto" />
              <p className="text-sm text-slate-300 font-medium">{cameraError}</p>
              
              <button
                type="button"
                onClick={() => fileFallbackRef.current?.click()}
                className="w-full py-2.5 px-4 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-sm font-semibold flex items-center justify-center gap-2 transition-all shadow-md"
              >
                <Camera className="w-4 h-4" /> Open Device Camera
              </button>
            </div>
          ) : (
            <>
              <video
                ref={videoRef}
                autoPlay
                playsInline
                muted
                className={`w-full h-full object-cover max-h-[460px] ${facingMode === 'user' ? 'scale-x-[-1]' : ''}`}
              />

              {isStartingStream && (
                <div className="absolute inset-0 bg-black/70 flex flex-col items-center justify-center gap-2 text-slate-300 text-xs">
                  <RefreshCw className="w-6 h-6 animate-spin text-indigo-400" />
                  <span>Connecting to camera...</span>
                </div>
              )}

              {/* Grid guide overlay */}
              <div className="absolute inset-4 border border-white/20 rounded-2xl pointer-events-none flex flex-col justify-between p-2">
                <div className="flex justify-between">
                  <div className="w-4 h-4 border-t-2 border-l-2 border-indigo-400" />
                  <div className="w-4 h-4 border-t-2 border-r-2 border-indigo-400" />
                </div>
                <div className="flex justify-between">
                  <div className="w-4 h-4 border-b-2 border-l-2 border-indigo-400" />
                  <div className="w-4 h-4 border-b-2 border-r-2 border-indigo-400" />
                </div>
              </div>

              {/* Flip camera button */}
              {hasMultipleCameras && (
                <button
                  type="button"
                  onClick={toggleCamera}
                  className="absolute top-4 right-4 p-2.5 rounded-full bg-slate-900/60 backdrop-blur-md text-white hover:bg-slate-900 border border-white/10 shadow-lg"
                  title="Switch Camera"
                >
                  <RefreshCw className="w-4 h-4" />
                </button>
              )}
            </>
          )}
        </div>

        {/* Hidden input for device camera fallback */}
        <input
          ref={fileFallbackRef}
          type="file"
          accept="image/*"
          capture="environment"
          onChange={handleDeviceCameraCapture}
          className="hidden"
        />

        {/* Bottom Shutter & Action Bar */}
        <div className="p-4 bg-slate-950/70 border-t border-slate-800 flex items-center justify-between gap-3">
          {capturedDataUrl ? (
            <div className="flex items-center justify-between w-full gap-3">
              <button
                type="button"
                onClick={handleRetake}
                className="flex-1 py-2.5 px-4 rounded-xl border border-slate-700 bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm font-semibold flex items-center justify-center gap-2 transition-all"
              >
                <RefreshCw className="w-4 h-4" /> Retake
              </button>
              <button
                type="button"
                onClick={handleConfirmPhoto}
                className="flex-1 py-2.5 px-4 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-semibold flex items-center justify-center gap-2 transition-all shadow-md shadow-emerald-900/40"
              >
                <Check className="w-4 h-4" /> Use Photo
              </button>
            </div>
          ) : (
            <div className="flex items-center justify-between w-full">
              {/* Native device camera prompt button */}
              <button
                type="button"
                onClick={() => fileFallbackRef.current?.click()}
                className="p-2.5 rounded-xl bg-slate-800/80 hover:bg-slate-700 text-slate-300 text-xs font-medium flex items-center gap-1.5 transition-all"
                title="Use Phone Native Camera App"
              >
                <ImageIcon className="w-4 h-4 text-indigo-400" />
                <span className="hidden sm:inline">Phone Camera App</span>
              </button>

              {/* Shutter Button */}
              <button
                type="button"
                onClick={handleSnap}
                disabled={isStartingStream || !!cameraError}
                className="w-14 h-14 rounded-full border-4 border-indigo-400 bg-white hover:bg-slate-200 flex items-center justify-center shadow-lg shadow-indigo-500/20 active:scale-95 transition-transform disabled:opacity-40 disabled:cursor-not-allowed mx-auto"
                title="Snap Photo"
              >
                <div className="w-10 h-10 rounded-full bg-indigo-600" />
              </button>

              <button
                type="button"
                onClick={onClose}
                className="px-3 py-2 text-xs font-semibold text-slate-400 hover:text-slate-200 transition-colors"
              >
                Cancel
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
