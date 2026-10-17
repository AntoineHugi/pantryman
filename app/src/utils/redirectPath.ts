// Where to go after logging in, e.g. a household invite link (/households?join=CODE).
// Only in-app paths are allowed, so a crafted link can't send users to another site.
// Same rule as isSafeRedirectPath on the backend.
export function safeRedirectPath(path: string | null | undefined): string | null {
  if (!path || path.length > 500) return null;
  if (!path.startsWith('/') || path.startsWith('//')) return null;
  if (path.includes('\\') || /[\s\p{Cc}]/u.test(path)) return null;
  return path;
}

// "/signup" or "/signup?next=..." so the redirect path survives moving between login and signup
export function withNext(page: string, next: string | null): string {
  return next ? `${page}?next=${encodeURIComponent(next)}` : page;
}

export function isInviteLink(path: string | null): boolean {
  return !!path && path.startsWith('/households?') && new URLSearchParams(path.split('?')[1]).has('join');
}
