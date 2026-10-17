import { Capacitor } from '@capacitor/core';
import { Share } from '@capacitor/share';

// True inside the Android app (Capacitor), false in a browser
export const isNativeApp = Capacitor.isNativePlatform();

// The app runs at https://localhost, so links meant for other people use the public site.
// Opening those links on a phone with the app installed opens the app (Android App Links).
export const publicAppUrl = isNativeApp ? 'https://pantryman.de' : window.location.origin;

// Opens the phone's share sheet (WhatsApp, SMS, ...). Returns false where there is none,
// so the caller can fall back to the clipboard.
export async function shareText(title: string, text: string): Promise<boolean> {
  if (isNativeApp) {
    await Share.share({ title, text });
    return true;
  }
  if (navigator.share) {
    await navigator.share({ title, text });
    return true;
  }
  return false;
}
