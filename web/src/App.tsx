import { Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/layout/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignUpPage';
import ListsPage from './pages/ListsPage';
import ListDetailsPage from './pages/ListDetailsPage';
import './App.css';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      
      <Route element={<Layout />}>
        <Route path="/" element={<Navigate to="/lists" replace />} />
        <Route
          path="/lists"
          element={
            <ProtectedRoute>
              <ListsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/lists/:id/items"
          element={
            <ProtectedRoute>
              <ListDetailsPage />
            </ProtectedRoute>
          }
        />
      </Route>
    </Routes>
  );
}

export default App;