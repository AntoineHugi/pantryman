import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { getErrorMessage } from '../api/client';

export default function SignupPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const { signup } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    try {
      const response = await signup({ email, password });
      setSuccess(response.message);
    } catch (err) {
      setError(getErrorMessage(err, 'Signup failed'));
    }
  };

  if (success) {
    return (
      <div className="auth-page">
        <div className="card" style={{ maxWidth: '400px', margin: '3rem auto' }}>
          <h1 style={{ marginTop: 0 }}>Check your email</h1>
          <p>We've sent a verification link to <strong>{email}</strong>.</p>
          <p>Click the link in the email to verify your account, then you can log in.</p>
          <Link to="/login">
            <button className="btn btn-primary" style={{ width: '100%', marginTop: '1rem' }}>
              Go to Login
            </button>
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="auth-page">
      <div className="card" style={{ maxWidth: '400px', margin: '3rem auto' }}>
        <h1 style={{ marginTop: 0 }}>Sign Up</h1>
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
              placeholder="Password (min 8 characters)"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={8}
            />
          </div>
          <div className="form-group">
            <input
              type="password"
              className="form-input"
              placeholder="Confirm password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              minLength={8}
            />
          </div>
          {error && <p style={{ color: 'red', marginBottom: '1rem' }}>{error}</p>}
          <button type="submit" className="btn btn-primary" style={{ width: '100%' }}>
            Sign Up
          </button>
        </form>
        <p style={{ textAlign: 'center', marginTop: '1.5rem', marginBottom: 0 }}>
          Already have an account? <Link to="/login">Login</Link>
        </p>
      </div>
    </div>
  );
}
