import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { authApi } from '../api/auth';
import { useAuth } from '../hooks/useAuth';
import '../App.css';

function SettingsPage() {
  const navigate = useNavigate();
  const { logout } = useAuth();

  // Change password state
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordMessage, setPasswordMessage] = useState<{ text: string; isError: boolean } | null>(null);
  const [passwordLoading, setPasswordLoading] = useState(false);

  // Delete account state
  const [deletePassword, setDeletePassword] = useState('');
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [deleteMessage, setDeleteMessage] = useState<{ text: string; isError: boolean } | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordMessage(null);

    if (newPassword !== confirmPassword) {
      setPasswordMessage({ text: 'New passwords do not match.', isError: true });
      return;
    }

    if (newPassword.length < 6) {
      setPasswordMessage({ text: 'New password must be at least 6 characters.', isError: true });
      return;
    }

    setPasswordLoading(true);
    try {
      await authApi.changePassword(currentPassword, newPassword);
      setPasswordMessage({ text: 'Password changed successfully.', isError: false });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch {
      setPasswordMessage({ text: 'Failed to change password. Check your current password.', isError: true });
    } finally {
      setPasswordLoading(false);
    }
  };

  const handleDeleteAccount = async () => {
    setDeleteMessage(null);
    setDeleteLoading(true);
    try {
      await authApi.deleteAccount(deletePassword);
      logout();
      navigate('/login');
    } catch {
      setDeleteMessage({ text: 'Failed to delete account. Check your password.', isError: true });
    } finally {
      setDeleteLoading(false);
    }
  };

  return (
    <div>
      <div className="page-title">
        <div className="title-with-edit">
          <button className="btn btn-secondary back-btn-small" onClick={() => navigate(-1)}>
            ← Back
          </button>
          <h1>Settings</h1>
        </div>
      </div>

      <div className="section">
        <h2>Change Password</h2>
        <form className="settings-form" onSubmit={handleChangePassword}>
          <div className="form-group">
            <input
              type="password"
              className="form-input"
              placeholder="Current password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              required
            />
          </div>
          <div className="form-group">
            <input
              type="password"
              className="form-input"
              placeholder="New password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              required
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
            />
          </div>
          {passwordMessage && (
            <p className={passwordMessage.isError ? 'settings-error' : 'settings-success'}>
              {passwordMessage.text}
            </p>
          )}
          <button type="submit" className="btn btn-primary" disabled={passwordLoading}>
            {passwordLoading ? 'Changing...' : 'Change Password'}
          </button>
        </form>
      </div>

      <div className="section">
        <h2>Delete Account</h2>
        <p className="settings-warning">
          This action is permanent and cannot be undone. All your data will be deleted.
        </p>
        {!showDeleteConfirm ? (
          <button className="btn btn-danger" onClick={() => setShowDeleteConfirm(true)}>
            Delete My Account
          </button>
        ) : (
          <div className="settings-form">
            <div className="form-group">
              <input
                type="password"
                className="form-input"
                placeholder="Enter your password to confirm"
                value={deletePassword}
                onChange={(e) => setDeletePassword(e.target.value)}
              />
            </div>
            {deleteMessage && (
              <p className="settings-error">{deleteMessage.text}</p>
            )}
            <div className="form-actions">
              <button
                className="btn btn-danger"
                onClick={handleDeleteAccount}
                disabled={deleteLoading || !deletePassword}
              >
                {deleteLoading ? 'Deleting...' : 'Confirm Delete'}
              </button>
              <button
                className="btn btn-secondary"
                onClick={() => {
                  setShowDeleteConfirm(false);
                  setDeletePassword('');
                  setDeleteMessage(null);
                }}
              >
                Cancel
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

export default SettingsPage;
