import { useSyncExternalStore } from 'react';
import { onlineManager, useMutationState } from '@tanstack/react-query';

function useIsOnline() {
  return useSyncExternalStore(
    (onChange) => onlineManager.subscribe(onChange),
    () => onlineManager.isOnline()
  );
}

/**
 * Shown when the server can't be reached: offline (no answer, see network.ts) or a failed fetch.
 * Item changes still work then; the banner says how many are waiting to be sent.
 */
export default function OfflineBanner({ error }: { error?: unknown }) {
  const isOnline = useIsOnline();
  const waiting = useMutationState({
    filters: { mutationKey: ['items'], status: 'pending' },
    select: (mutation) => mutation.state.isPaused,
  }).filter(Boolean).length;

  if (isOnline && !error) return null;
  const changes = waiting === 1 ? '1 change is' : `${waiting} changes are`;
  return (
    <p className="offline-banner" role="status">
      {isOnline ? "Can't reach the server. Showing the last saved copy." : 'Offline. Showing the last saved copy.'}
      {waiting > 0 && ` ${changes} saved on this device and will be sent when you're back online.`}
    </p>
  );
}
