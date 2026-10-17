import { createContext } from 'react';

export interface ToastOptions {
  message: string;
  isError?: boolean;
  // e.g. "Undo"
  actionLabel?: string;
  onAction?: () => void;
}

export interface ConfirmOptions {
  title?: string;
  message: string;
  confirmLabel?: string;
  // Red confirm button, for deleting or leaving
  danger?: boolean;
}

export interface FeedbackContextType {
  toast: (options: ToastOptions) => void;
  // Resolves to true if the user confirmed
  confirm: (options: ConfirmOptions) => Promise<boolean>;
}

// Kept apart from FeedbackProvider so that file only exports a component (react-refresh)
export const FeedbackContext = createContext<FeedbackContextType | undefined>(undefined);
