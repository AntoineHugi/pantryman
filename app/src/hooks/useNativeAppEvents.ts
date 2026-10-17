import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { focusManager } from '@tanstack/react-query';
import { App } from '@capacitor/app';
import { isNativeApp } from '../native';
import { safeRedirectPath } from '../utils/redirectPath';

// Android app only: open pantryman.de links (invites, email verification) inside the app,
// and refetch when the app comes back to the foreground, like window focus does in a browser.
export function useNativeAppEvents() {
  const navigate = useNavigate();

  useEffect(() => {
    if (!isNativeApp) return;

    const urlOpen = App.addListener('appUrlOpen', ({ url }) => {
      const parsed = new URL(url);
      const path = safeRedirectPath(parsed.pathname + parsed.search);
      if (path) navigate(path);
    });
    const stateChange = App.addListener('appStateChange', ({ isActive }) => {
      focusManager.setFocused(isActive);
    });

    return () => {
      urlOpen.then((listener) => listener.remove());
      stateChange.then((listener) => listener.remove());
    };
  }, [navigate]);
}
