import { useEffect, useState } from 'react';

const DELAY_MS = 3000;

// The backend (Render free plan) sleeps when idle and takes up to a minute to wake.
// Shown when a request has been running for a few seconds, so that doesn't look like a hang.
export default function SlowServerNotice({ active }: { active: boolean }) {
  const [isSlow, setIsSlow] = useState(false);

  useEffect(() => {
    if (!active) return;
    const timer = setTimeout(() => setIsSlow(true), DELAY_MS);
    return () => {
      clearTimeout(timer);
      setIsSlow(false);
    };
  }, [active]);

  if (!active || !isSlow) return null;
  return (
    <p className="slow-server-notice" role="status">
      The server is waking up. The first request can take up to a minute.
    </p>
  );
}
