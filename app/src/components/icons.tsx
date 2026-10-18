// Inline SVG icons (Lucide shapes). Decorative: the button around them carries the label.

const svgProps = {
  width: 20,
  height: 20,
  viewBox: '0 0 24 24',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  'aria-hidden': true,
};

export function PencilIcon() {
  return (
    <svg {...svgProps}>
      <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
      <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
    </svg>
  );
}

export function MenuIcon() {
  return (
    <svg {...svgProps}>
      <line x1="4" y1="6" x2="20" y2="6" />
      <line x1="4" y1="12" x2="20" y2="12" />
      <line x1="4" y1="18" x2="20" y2="18" />
    </svg>
  );
}

export function ChevronIcon({ open }: { open: boolean }) {
  return (
    <svg {...svgProps} width={16} height={16} style={{ transform: open ? 'rotate(90deg)' : undefined }}>
      <path d="m9 18 6-6-6-6" />
    </svg>
  );
}

export function ListIcon() {
  return (
    <svg {...svgProps} width={22} height={22}>
      <path d="M9 6h11M9 12h11M9 18h11" />
      <path d="m3 6 1 1 2-2M3 12l1 1 2-2M3 18l1 1 2-2" />
    </svg>
  );
}

export function BookIcon() {
  return (
    <svg {...svgProps} width={22} height={22}>
      <path d="M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20" />
    </svg>
  );
}
