import { useState } from 'react';
import { useNavigate, useSearchParams, useLocation, Link } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { useAuth } from '../hooks/useAuth';
import { getErrorMessage } from '../api/client';
import { isInviteLink, safeRedirectPath, withNext } from '../utils/redirectPath';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const { login } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const location = useLocation();
  // state.from is set by ProtectedRoute; ?next= comes from the signup page or the verification email
  const next = safeRedirectPath((location.state as { from?: string } | null)?.from ?? searchParams.get('next'));
  const redirectTo = next ?? '/lists';
  const verified = searchParams.get('verified') === 'true';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');

    try {
      await login({ email, password });
      navigate(redirectTo, { replace: true });
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 403) {
        setError('Please verify your email before logging in.');
      } else {
        setError(getErrorMessage(err, 'Login failed'));
      }
    }
  };

  return (
    <div className="auth-page">
      <div className="card" style={{ maxWidth: '400px', margin: '3rem auto' }}>
        <h1 style={{ marginTop: 0 }}>Login</h1>
        {verified && (
          <p style={{ color: 'green', marginBottom: '1rem' }}>
            Email verified! You can now log in.
          </p>
        )}
        {!verified && isInviteLink(next) && (
          <p style={{ marginBottom: '1rem' }}>
            You've been invited to a household. Log in, or sign up if you're new, to join it.
          </p>
        )}
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <input
              type="email"
              className="form-input"
              placeholder="Email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoFocus
            />
          </div>
          <div className="form-group">
            <input
              type="password"
              className="form-input"
              placeholder="Password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          {error && <p style={{ color: 'red', marginBottom: '1rem' }}>{error}</p>}
          <button type="submit" className="btn btn-primary" style={{ width: '100%' }}>
            Login
          </button>
        </form>
        <p style={{ textAlign: 'center', marginTop: '1.5rem', marginBottom: 0 }}>
          Don't have an account? <Link to={withNext('/signup', next)}>Sign up</Link>
        </p>
      </div>
    </div>
  );
}