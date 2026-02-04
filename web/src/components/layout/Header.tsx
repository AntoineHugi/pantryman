import { Link } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';

export default function Header() {
  const { isAuthenticated, logout } = useAuth();

  return (
    <header className="header">
      <div className="container">
        <div className="logo">
          <Link className="logo-link" to="/lists">
            <span className="logo-text">PantryMan</span>
          </Link>
        </div>
        {isAuthenticated && (
          <nav>
            <button onClick={logout}>Logout</button>
          </nav>
        )}
      </div>
    </header>
  );
}