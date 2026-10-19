import { App } from '@capacitor/app';
import { registerPlugin } from '@capacitor/core';
import { isNativeApp } from './native';

// The Android APKs are attached to this repository's GitHub releases (.github/workflows/android.yml).
// Only works while the repository is public: GitHub doesn't serve a private repo's releases without a login.
const LATEST_RELEASE_URL = 'https://api.github.com/repos/AntoineHugi/pantryman/releases/latest';

// Release APKs are named "pantryman-<versionName>-<versionCode>.apk"; the version code is what Android compares
const APK_NAME = /^pantryman-.+-(\d+)\.apk$/;

interface AppUpdatePlugin {
  canInstall(): Promise<{ allowed: boolean }>;
  openInstallSettings(): Promise<void>;
  downloadAndInstall(options: { url: string }): Promise<void>;
}

// android/app/src/main/java/de/pantryman/app/AppUpdatePlugin.java
export const AppUpdate = registerPlugin<AppUpdatePlugin>('AppUpdate');

export interface AppUpdateInfo {
  versionName: string;
  url: string;
}

interface GitHubRelease {
  tag_name: string;
  assets: { name: string; browser_download_url: string }[];
}

// The latest release, if it's newer than the installed app. Null in a browser, or when the check fails.
export async function findAppUpdate(): Promise<AppUpdateInfo | null> {
  if (!isNativeApp) return null;
  const [info, response] = await Promise.all([
    App.getInfo(),
    fetch(LATEST_RELEASE_URL, { headers: { Accept: 'application/vnd.github+json' } }),
  ]);
  if (!response.ok) return null;
  const release: GitHubRelease = await response.json();
  for (const asset of release.assets) {
    const match = APK_NAME.exec(asset.name);
    if (match && Number(match[1]) > Number(info.build)) {
      return { versionName: release.tag_name, url: asset.browser_download_url };
    }
  }
  return null;
}
