import { useState } from 'react';
import { useNavigate, useSearchParams, useLocation, Link } from 'react-router-dom';
import { isAxiosError } from 'axios';
import { useAuth } from '../hooks/useAuth';
import { getErrorMessage } from '../api/client';
import SlowServerNotice from '../components/SlowServerNotice';
import { isInviteLink, safeRedirectPath, withNext } from '../utils/redirectPath';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const location = useLocation();
  // state.from is set by ProtectedRoute; ?next= comes from the signup page or the verification email
  const next = safeRedirectPath((location.state as { from?: string } | null)?.from ?? searchParams.get('next'));
  const redirectTo = next ?? '/lists';
  const verified = searchParams.get('verified') === 'true';
  const passwordReset = searchParams.get('reset') === 'true';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsSubmitting(true);

    try {
      await login({ email, password });
      navigate(redirectTo, { replace: true });
    } catch (err) {
      if (isAxiosError(err) && err.response?.status === 403) {
        setError('Please verify your email before logging in.');
      } else {
        setError(getErrorMessage(err, 'Login failed'));
      }
      setIsSubmitting(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="card auth-card">
        <h1>Log in</h1>
        {verified && (
          <p className="form-success">
            Email verified! You can now log in.
          </p>
        )}
        {passwordReset && (
          <p className="form-success">
            Password changed. You can now log in with your new password.
          </p>
        )}
        {!verified && isInviteLink(next) && (
          <p>
            You've been invited to a household. Log in, or sign up if you're new, to join it.
          </p>
        )}
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <input
              type="email"
              className="form-input"
              placeholder="Email"
              autoComplete="email"
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
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>
          {error && <p className="form-error">{error}</p>}
          <button type="submit" className="btn btn-primary btn-block" disabled={isSubmitting}>
            {isSubmitting ? 'Logging in…' : 'Log in'}
          </button>
          <SlowServerNotice active={isSubmitting} />
        </form>
        <p className="auth-footer">
          <Link to="/forgot-password">Forgot your password?</Link>
        </p>
        <p className="auth-footer">
          Don't have an account? <Link to={withNext('/signup', next)}>Sign up</Link>
        </p>
      </div>
    </div>
  );
}