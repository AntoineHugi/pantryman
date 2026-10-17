import { useEffect } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { authApi } from '../api/auth';
import { getErrorMessage } from '../api/client';
import { safeRedirectPath, withNext } from '../utils/redirectPath';

// Verification emails link here (/verify?token=...&next=...). On a phone with the app
// installed, the link opens the app on this page.
export default function VerifyEmailPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';
  const next = safeRedirectPath(searchParams.get('next'));

  // A query rather than an effect so StrictMode's double render sends one request:
  // the token only works once, so a second call would report failure
  const verification = useQuery({
    queryKey: ['verify-email', token],
    queryFn: () => authApi.verifyEmail(token),
    enabled: !!token,
    retry: false,
    staleTime: Infinity,
  });

  useEffect(() => {
    if (verification.data === true) {
      const params = new URLSearchParams({ verified: 'true' });
      if (next) params.set('next', next);
      navigate(`/login?${params}`, { replace: true });
    }
  }, [verification.data, next, navigate]);

  let message = 'Verifying your email…';
  if (!token) message = 'This verification link is incomplete.';
  else if (verification.isError) message = getErrorMessage(verification.error, 'Could not verify your email.');
  else if (verification.data === false) message = 'This link has expired or was already used. If you already verified your email, you can log in.';
  const failed = !token || verification.isError || verification.data === false;

  return (
    <div className="auth-page">
      <div className="card" style={{ maxWidth: '400px', margin: '3rem auto' }}>
        <h1 style={{ marginTop: 0 }}>Email verification</h1>
        <p>{message}</p>
        {failed && (
          <Link to={withNext('/login', next)}>
            <button className="btn btn-primary" style={{ width: '100%', marginTop: '1rem' }}>
              Go to Login
            </button>
          </Link>
        )}
      </div>
    </div>
  );
}
