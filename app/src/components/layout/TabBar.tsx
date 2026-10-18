import { NavLink } from 'react-router-dom';
import { BookIcon, CalendarIcon, ListIcon } from '../icons';

// The app's main sections, at the bottom of the screen where thumbs reach
export default function TabBar() {
  return (
    <nav className="tab-bar" aria-label="Sections">
      <NavLink to="/lists" className="tab-bar-link">
        <ListIcon />
        <span>Lists</span>
      </NavLink>
      <NavLink to="/recipes" className="tab-bar-link">
        <BookIcon />
        <span>Recipes</span>
      </NavLink>
      <NavLink to="/plans" className="tab-bar-link">
        <CalendarIcon />
        <span>Plan</span>
      </NavLink>
    </nav>
  );
}
