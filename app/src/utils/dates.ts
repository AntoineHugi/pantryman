// Plan dates are "YYYY-MM-DD" strings, read as local dates (no time zone shifts)

export function toDateString(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function parseDate(value: string): Date {
  const [y, m, d] = value.split('-').map(Number);
  return new Date(y, m - 1, d);
}

export function addDays(value: string, days: number): string {
  const date = parseDate(value);
  date.setDate(date.getDate() + days);
  return toDateString(date);
}

export function today(): string {
  return toDateString(new Date());
}

// "Tue 20 Oct"
export function formatDay(value: string): string {
  return parseDate(value).toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' });
}

// "20 Oct"
export function formatShortDate(value: string): string {
  return parseDate(value).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
}

// "20–26 Oct", or "28 Oct – 3 Nov"
export function formatDateRange(start: string, end: string): string {
  const a = parseDate(start);
  const b = parseDate(end);
  if (a.getMonth() === b.getMonth() && a.getFullYear() === b.getFullYear()) {
    return `${a.getDate()}–${formatShortDate(end)}`;
  }
  return `${formatShortDate(start)} – ${formatShortDate(end)}`;
}
