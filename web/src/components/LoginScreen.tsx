import React, { useState } from 'react';
import { Lock, User as UserIcon, ArrowRight, ShieldCheck } from 'lucide-react';
import { User } from '../types';
import { supabase } from '../lib/supabase';

interface LoginScreenProps {
  onLoginSuccess: (user: User) => void;
}

export const LoginScreen: React.FC<LoginScreenProps> = ({ onLoginSuccess }) => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [loginAttempts, setLoginAttempts] = useState(0);
  const [lockoutUntil, setLockoutUntil] = useState<number | null>(null);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();

    // Rate-limiting check: Prevent brute-force password guessing
    if (lockoutUntil && Date.now() < lockoutUntil) {
      const remainingSec = Math.ceil((lockoutUntil - Date.now()) / 1000);
      setErrorMessage(`Too many failed attempts. Please wait ${remainingSec} seconds.`);
      return;
    }

    setIsLoading(true);
    setErrorMessage('');

    try {
      const sanitizedUser = username.trim().toLowerCase();
      const sanitizedPass = password.trim();

      const { data, error } = await supabase
        .from('users')
        .select('id, username, full_name, role, is_admin, show_procurement, show_inventory, show_admin_panel, password_hash')
        .eq('username', sanitizedUser)
        .single();

      if (error || !data) {
        handleFailedAttempt();
        setIsLoading(false);
        return;
      }

      if (data.password_hash !== sanitizedPass) {
        handleFailedAttempt();
        setIsLoading(false);
        return;
      }

      // Reset attempts on successful authentication
      setLoginAttempts(0);
      setLockoutUntil(null);

      // Sanitize user object to never hold password_hash in memory
      const safeUser = { ...data };
      delete (safeUser as any).password_hash;

      onLoginSuccess(safeUser as User);
    } catch (err: any) {
      setErrorMessage('Login failed. Please check network connectivity.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleFailedAttempt = () => {
    const nextCount = loginAttempts + 1;
    setLoginAttempts(nextCount);
    if (nextCount >= 5) {
      setLockoutUntil(Date.now() + 30000); // 30-second lockout
      setErrorMessage('Too many failed attempts. Account locked for 30 seconds.');
    } else {
      setErrorMessage('Invalid username or password. Please try again.');
    }
  };

  return (
    <div className="min-h-screen bg-slate-900 flex items-center justify-center p-4 relative overflow-hidden">
      {/* Background Glow */}
      <div className="absolute top-1/4 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 bg-indigo-500/20 rounded-full blur-3xl pointer-events-none" />

      <div className="bg-slate-800/90 border border-slate-700/80 rounded-3xl max-w-md w-full p-8 shadow-2xl backdrop-blur-xl relative z-10">
        {/* Logo & Header */}
        <div className="text-center mb-8">
          <div className="w-14 h-14 mx-auto rounded-2xl bg-gradient-to-tr from-indigo-600 to-violet-500 flex items-center justify-center font-black text-2xl text-white shadow-xl shadow-indigo-500/40 mb-4">
            P
          </div>
          <h1 className="text-2xl font-black text-white tracking-tight">POPT Master</h1>
          <p className="text-xs text-slate-400 mt-1">Purchase Order Master, Sourcing Planner & Receiving</p>
        </div>

        {errorMessage && (
          <div className="mb-5 p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs font-semibold text-center">
            {errorMessage}
          </div>
        )}

        <form onSubmit={handleLogin} className="space-y-4">
          <div>
            <label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-1.5">
              Username
            </label>
            <div className="relative">
              <UserIcon className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Enter username"
                className="w-full pl-10 pr-4 py-2.5 bg-slate-900/80 border border-slate-700 rounded-xl text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                required
              />
            </div>
          </div>

          <div>
            <label className="block text-xs font-bold uppercase tracking-wider text-slate-400 mb-1.5">
              Password
            </label>
            <div className="relative">
              <Lock className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-500" />
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Enter password"
                className="w-full pl-10 pr-4 py-2.5 bg-slate-900/80 border border-slate-700 rounded-xl text-sm text-white placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                required
              />
            </div>
          </div>

          <button
            type="submit"
            disabled={isLoading}
            className="w-full py-3 rounded-xl bg-gradient-to-r from-indigo-600 to-indigo-700 hover:from-indigo-500 hover:to-indigo-600 text-white font-bold text-sm shadow-lg shadow-indigo-600/30 transition-all flex items-center justify-center gap-2 mt-2 disabled:opacity-50"
          >
            {isLoading ? 'Signing In...' : 'Sign In to Dashboard'}
            <ArrowRight className="w-4 h-4" />
          </button>
        </form>
      </div>
    </div>
  );
};
