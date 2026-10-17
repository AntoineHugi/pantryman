import { useContext } from 'react';
import { FeedbackContext } from '../contexts/FeedbackContextValue';

// Toasts and confirm dialogs, instead of alert() and confirm(), which look out of place in the app
export const useFeedback = () => {
  const context = useContext(FeedbackContext);

  if (context === undefined) {
    throw new Error('useFeedback must be used within a FeedbackProvider');
  }

  return context;
};
