import { Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/layout/Layout';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignUpPage';
import VerifyEmailPage from './pages/VerifyEmailPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import ListsPage from './pages/ListsPage';
import ListDetailsPage from './pages/ListDetailsPage';
import SettingsPage from './pages/SettingsPage';
import HouseholdsPage from './pages/HouseholdsPage';
import RecipesPage from './pages/RecipesPage';
import RecipePage from './pages/RecipePage';
import { EditRecipePage, NewRecipePage } from './pages/RecipeFormPage';
import RecipeImportPage from './pages/RecipeImportPage';
import MealPlansPage from './pages/MealPlansPage';
import MealPlanPage from './pages/MealPlanPage';
import { useAuth } from './hooks/useAuth';
import { useNativeAppEvents } from './hooks/useNativeAppEvents';
import './App.css';

function HomePage() {
  const { isAuthenticated } = useAuth();
  return <Navigate to={isAuthenticated ? "/lists" : "/login"} replace />;
}

function App() {
  useNativeAppEvents();

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/verify" element={<VerifyEmailPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      <Route element={<Layout />}>
        <Route path="/" element={<HomePage />} />
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
        <Route
          path="/recipes"
          element={
            <ProtectedRoute>
              <RecipesPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/recipes/new"
          element={
            <ProtectedRoute>
              <NewRecipePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/recipes/import"
          element={
            <ProtectedRoute>
              <RecipeImportPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/recipes/:id"
          element={
            <ProtectedRoute>
              <RecipePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/recipes/:id/edit"
          element={
            <ProtectedRoute>
              <EditRecipePage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/plans"
          element={
            <ProtectedRoute>
              <MealPlansPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/plans/:id"
          element={
            <ProtectedRoute>
              <MealPlanPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/households"
          element={
            <ProtectedRoute>
              <HouseholdsPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/settings"
          element={
            <ProtectedRoute>
              <SettingsPage />
            </ProtectedRoute>
          }
        />
      </Route>
    </Routes>
  );
}

export default App;