import { Link } from 'react-router-dom';

// A link rather than history.back(), which goes nowhere when the page was opened
// from a link or after a reload
export default function BackLink({ to = '/lists', label = 'Lists' }: { to?: string; label?: string }) {
  return (
    <Link className="back-link" to={to}>
      ← {label}
    </Link>
  );
}
