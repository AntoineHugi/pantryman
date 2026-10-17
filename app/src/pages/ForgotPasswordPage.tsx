import { useState } from 'react';
import { Link } from 'react-router-dom';
import { authApi } from '../api/auth';
import { getErrorMessage } from '../api/client';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [sentMessage, setSentMessage] = useState('');
  const [isSending, setIsSending] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setIsSending(true);
    try {
      setSentMessage(await authApi.forgotPassword(email));
    } catch (err) {
      setError(getErrorMessage(err, 'Could not send the reset email. Try again in a minute.'));
    } finally {
      setIsSending(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="card" style={{ maxWidth: '400px', margin: '3rem auto' }}>
        <h1 style={{ marginTop: 0 }}>Forgot password</h1>
        {sentMessage ? (
          <>
            <p>{sentMessage}</p>
            <p>The link works once, for one hour. Check your spam folder if it doesn't arrive.</p>
          </>
        ) : (
          <form onSubmit={handleSubmit}>
            <p style={{ marginTop: 0 }}>Enter your email and we'll send you a link to choose a new password.</p>
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
            {error && <p style={{ color: 'red', marginBottom: '1rem' }}>{error}</p>}
            <button type="submit" className="btn btn-primary" style={{ width: '100%' }} disabled={isSending}>
              {isSending ? 'Sending…' : 'Send reset link'}
            </button>
          </form>
        )}
        <p style={{ textAlign: 'center', marginTop: '1.5rem', marginBottom: 0 }}>
          <Link to="/login">Back to login</Link>
        </p>
      </div>
    </div>
  );
}
