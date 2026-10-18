import { Link, useNavigate } from 'react-router-dom';
import { useState, useRef, useEffect } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { MenuIcon } from '../icons';

export default function Header() {
  const { isAuthenticated, logout } = useAuth();
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();

  useEffect(() => {
    if (!isMenuOpen) return;

    function handlePointerDown(event: PointerEvent) {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsMenuOpen(false);
      }
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setIsMenuOpen(false);
    }

    document.addEventListener('pointerdown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('pointerdown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isMenuOpen]);

  const go = (path: string) => {
    setIsMenuOpen(false);
    navigate(path);
  };

  const handleLogout = () => {
    logout();
    go('/login');
  };

  return (
    <header className="header">
      <div className="container header-content">
        <Link className="logo-link" to="/lists">
          <img src="/favicon.svg" alt="" width="28" height="28" />
          <span className="logo-text">Pantryman</span>
        </Link>
        {isAuthenticated && (
          <div className="header-menu" ref={menuRef}>
            <button
              className="menu-button"
              onClick={() => setIsMenuOpen(!isMenuOpen)}
              aria-label="Menu"
              aria-expanded={isMenuOpen}
              aria-haspopup="menu"
            >
              <MenuIcon />
            </button>
            {isMenuOpen && (
              <div className="dropdown-menu" role="menu">
                <button className="dropdown-item" role="menuitem" onClick={() => go('/lists')}>
                  Lists
                </button>
                <button className="dropdown-item" role="menuitem" onClick={() => go('/pantry')}>
                  Usual items
                </button>
                <button className="dropdown-item" role="menuitem" onClick={() => go('/households')}>
                  Households
                </button>
                <button className="dropdown-item" role="menuitem" onClick={() => go('/settings')}>
                  Settings
                </button>
                <button className="dropdown-item" role="menuitem" onClick={handleLogout}>
                  Log out
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </header>
  );
}
