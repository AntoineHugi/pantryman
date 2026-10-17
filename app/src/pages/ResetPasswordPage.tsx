import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from '../api/auth';
import { getErrorMessage } from '../api/client';

// Password reset emails link here (/reset-password?token=...)
export default function ResetPasswordPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }
    setIsSaving(true);
    try {
      await authApi.resetPassword(token, password);
      navigate('/login?reset=true', { replace: true });
    } catch (err) {
      setError(getErrorMessage(err, 'Could not reset your password'));
      setIsSaving(false);
    }
  };

  if (!token) {
    return (
      <div className="auth-page">
        <div className="card" style={{ maxWidth: '400px', margin: '3rem auto' }}>
          <h1 style={{ marginTop: 0 }}>Reset password</h1>
          <p>This reset link is incomplete.</p>
          <Link to="/forgot-password">Ask for a new link</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <div className="card" style={{ maxWidth: '400px', margin: '3rem auto' }}>
        <h1 style={{ marginTop: 0 }}>Choose a new password</h1>
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <input
              type="password"
              className="form-input"
              placeholder="New password (min 8 characters)"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={8}
              autoFocus
            />
          </div>
          <div className="form-group">
            <input
              type="password"
              className="form-input"
              placeholder="Confirm new password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              minLength={8}
            />
          </div>
          {error && <p style={{ color: 'red', marginBottom: '1rem' }}>{error}</p>}
          <button type="submit" className="btn btn-primary" style={{ width: '100%' }} disabled={isSaving}>
            {isSaving ? 'Saving…' : 'Set new password'}
          </button>
        </form>
        <p style={{ textAlign: 'center', marginTop: '1.5rem', marginBottom: 0 }}>
          <Link to="/forgot-password">Ask for a new link</Link>
        </p>
      </div>
    </div>
  );
}
