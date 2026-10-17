import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { FeedbackContext, type ConfirmOptions, type ToastOptions } from './FeedbackContextValue';

const TOAST_MS = 5000;

type PendingConfirm = ConfirmOptions & { resolve: (confirmed: boolean) => void };

// One toast at a time at the bottom of the screen, and one confirm dialog
export function FeedbackProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<(ToastOptions & { id: number }) | null>(null);
  const [pendingConfirm, setPendingConfirm] = useState<PendingConfirm | null>(null);
  const nextId = useRef(0);

  const showToast = useCallback((options: ToastOptions) => {
    setToast({ ...options, id: ++nextId.current });
  }, []);

  const confirm = useCallback(
    (options: ConfirmOptions) => new Promise<boolean>((resolve) => setPendingConfirm({ ...options, resolve })),
    []
  );

  // Each new toast restarts the timer
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(() => setToast(null), TOAST_MS);
    return () => clearTimeout(timer);
  }, [toast]);

  const closeConfirm = useCallback(
    (confirmed: boolean) => {
      pendingConfirm?.resolve(confirmed);
      setPendingConfirm(null);
    },
    [pendingConfirm]
  );

  useEffect(() => {
    if (!pendingConfirm) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') closeConfirm(false);
    };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [pendingConfirm, closeConfirm]);

  const value = useMemo(() => ({ toast: showToast, confirm }), [showToast, confirm]);

  return (
    <FeedbackContext.Provider value={value}>
      {children}

      {toast && (
        <div className={`toast${toast.isError ? ' toast-error' : ''}`} role="status" key={toast.id}>
          <span>{toast.message}</span>
          {toast.actionLabel && toast.onAction && (
            <button
              className="toast-action"
              onClick={() => {
                toast.onAction?.();
                setToast(null);
              }}
            >
              {toast.actionLabel}
            </button>
          )}
        </div>
      )}

      {pendingConfirm && (
        <div className="dialog-backdrop" onClick={() => closeConfirm(false)}>
          <div
            className="dialog"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="confirm-dialog-message"
            onClick={(e) => e.stopPropagation()}
          >
            {pendingConfirm.title && <h2 className="dialog-title">{pendingConfirm.title}</h2>}
            <p id="confirm-dialog-message">{pendingConfirm.message}</p>
            <div className="dialog-actions">
              <button className="btn btn-secondary" onClick={() => closeConfirm(false)}>
                Cancel
              </button>
              <button
                className={`btn ${pendingConfirm.danger ? 'btn-danger' : 'btn-primary'}`}
                onClick={() => closeConfirm(true)}
                autoFocus
              >
                {pendingConfirm.confirmLabel ?? 'OK'}
              </button>
            </div>
          </div>
        </div>
      )}
    </FeedbackContext.Provider>
  );
}
