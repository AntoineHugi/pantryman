import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from "react-router-dom";
import { PersistQueryClientProvider } from '@tanstack/react-query-persist-client';
import { AuthProvider } from './contexts/AuthContext';
import { FeedbackProvider } from './contexts/FeedbackContext';
import { itemMutationDefaults } from './hooks/useGroceryLists';
import { persistOptions, queryClient } from './queryClient';
import './index.css'
import App from './App.tsx'

// Before the cache is restored: item changes saved while offline need these to be sent
queryClient.setMutationDefaults(['items'], itemMutationDefaults(queryClient));

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <PersistQueryClientProvider
      client={queryClient}
      persistOptions={persistOptions}
      // Sends item changes left over from the last time (paused again if still offline).
      // Not awaited: the provider shows no data until onSuccess settles, which offline is never.
      onSuccess={() => {
        void queryClient.resumePausedMutations();
      }}
    >
      <BrowserRouter>
        <AuthProvider>
          <FeedbackProvider>
            <App />
          </FeedbackProvider>
        </AuthProvider>
      </BrowserRouter>
    </PersistQueryClientProvider>
  </StrictMode>
);
