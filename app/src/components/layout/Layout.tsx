import { Outlet } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import Header from './Header';
import TabBar from './TabBar';
import UpdateBanner from '../UpdateBanner';

export default function Layout() {
  const { isAuthenticated } = useAuth();
  return (
    <>
      <Header />
      <main className={isAuthenticated ? 'container has-tab-bar' : 'container'}>
        {isAuthenticated && <UpdateBanner />}
        <Outlet />
      </main>
      {isAuthenticated && <TabBar />}
    </>
  );
}
