import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { focusManager } from '@tanstack/react-query';
import { App } from '@capacitor/app';
import { isNativeApp } from '../native';
import { safeRedirectPath } from '../utils/redirectPath';

// Pages where the back button leaves the app instead of going back
const HOME_PAGES = ['/', '/lists', '/login'];

// Android app only: open pantryman.de links (invites, email verification) inside the app,
// refetch when the app comes back to the foreground, like window focus does in a browser,
// and handle the back button.
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
    // Like other Android apps: back goes to the previous page, and on the main pages (or with
    // nothing to go back to) it returns to the home screen. The app stays in the background
    // rather than closing, so reopening it is instant.
    const backButton = App.addListener('backButton', ({ canGoBack }) => {
      if (canGoBack && !HOME_PAGES.includes(window.location.pathname)) {
        window.history.back();
      } else {
        App.minimizeApp();
      }
    });

    return () => {
      urlOpen.then((listener) => listener.remove());
      stateChange.then((listener) => listener.remove());
      backButton.then((listener) => listener.remove());
    };
  }, [navigate]);
}
