import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AppUpdate, findAppUpdate } from '../appUpdate';
import { useFeedback } from '../hooks/useFeedback';
import { isNativeApp } from '../native';

const CHECK_EVERY_MS = 6 * 60 * 60 * 1000;

// In the Android app: offers a newer APK from GitHub. Not persisted, so it's checked at each start.
export default function UpdateBanner() {
  const { toast, confirm } = useFeedback();
  const [dismissed, setDismissed] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);
  const { data: update } = useQuery({
    queryKey: ['app-update'],
    queryFn: findAppUpdate,
    enabled: isNativeApp,
    staleTime: CHECK_EVERY_MS,
    refetchInterval: CHECK_EVERY_MS,
    retry: false,
  });

  if (!update || dismissed) return null;

  const handleUpdate = async () => {
    const { allowed } = await AppUpdate.canInstall();
    if (!allowed) {
      const goToSettings = await confirm({
        title: 'Allow updates',
        message:
          'Android needs your permission for Pantryman to install its updates. Turn on "Allow from this source", come back and tap Update again.',
        confirmLabel: 'Open settings',
      });
      if (goToSettings) await AppUpdate.openInstallSettings();
      return;
    }
    setIsDownloading(true);
    try {
      // Resolves once Android's install screen is open
      await AppUpdate.downloadAndInstall({ url: update.url });
    } catch {
      toast({ message: 'Could not download the update. Try again later.', isError: true });
    } finally {
      setIsDownloading(false);
    }
  };

  return (
    <div className="update-banner" role="status">
      <span>A new version of Pantryman is available ({update.versionName}).</span>
      <div className="update-banner-actions">
        <button className="btn btn-secondary btn-small" onClick={() => setDismissed(true)} disabled={isDownloading}>
          Later
        </button>
        <button className="btn btn-primary btn-small" onClick={handleUpdate} disabled={isDownloading}>
          {isDownloading ? 'Downloading…' : 'Update'}
        </button>
      </div>
    </div>
  );
}
