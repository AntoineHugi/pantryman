import type { CapacitorConfig } from '@capacitor/cli';

// The Android app bundles the built web app (dist/) and serves it from https://localhost,
// which is why that origin is in the backend's CORS allow-list.
const config: CapacitorConfig = {
  appId: 'de.pantryman.app',
  appName: 'Pantryman',
  webDir: 'dist',
  plugins: {
    // Capacitor pads the WebView so the page never sits under the status or navigation bar
    SystemBars: {
      insetsHandling: 'native',
    },
  },
};

export default config;
