import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.pomaster.popt',
  appName: 'POPT',
  webDir: 'dist',
  server: {
    androidScheme: 'https'
  }
};

export default config;
