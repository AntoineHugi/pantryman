import { onlineManager } from '@tanstack/react-query';
import axios from 'axios';
import API_BASE_URL from './config';

// How often to check whether the server can be reached again after a request got no answer
const PROBE_MS = 10_000;

let probe: ReturnType<typeof setInterval> | undefined;

// A request that got no answer at all: no connection, or it dropped. Not a server error.
export function isNetworkError(err: unknown): boolean {
  return axios.isAxiosError(err) && !err.response && err.code !== axios.AxiosError.ERR_CANCELED;
}

/**
 * Phones often report a connection that doesn't work (one bar in a supermarket), so the browser's
 * online/offline events aren't enough. When a request gets no answer, TanStack Query is told we're
 * offline, which pauses queued item changes, and the server is checked every few seconds until it answers.
 */
export function markOffline() {
  if (!onlineManager.isOnline()) return;
  onlineManager.setOnline(false);
  probe ??= setInterval(checkServer, PROBE_MS);
}

async function checkServer() {
  try {
    // no-cors: any answer at all (even a 404) means the server can be reached
    await fetch(API_BASE_URL, { mode: 'no-cors', cache: 'no-store' });
    onlineManager.setOnline(true);
  } catch {
    // Still offline
  }
}

// Back online (the probe, or the browser's own "online" event): resumes paused changes
onlineManager.subscribe((isOnline) => {
  if (isOnline && probe) {
    clearInterval(probe);
    probe = undefined;
  }
});
