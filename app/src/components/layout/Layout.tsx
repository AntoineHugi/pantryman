import { Outlet } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import Header from './Header';
import TabBar from './TabBar';

export default function Layout() {
  const { isAuthenticated } = useAuth();
  return (
    <>
      <Header />
      <main className={isAuthenticated ? 'container has-tab-bar' : 'container'}>
        <Outlet />
      </main>
      {isAuthenticated && <TabBar />}
    </>
  );
}
