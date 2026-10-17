import { useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import type { Household } from "../api/types";
import { useAuth } from "../hooks/useAuth";
import { publicAppUrl, shareText } from "../native";
import {
  useCreateHousehold,
  useHouseholds,
  useJoinHousehold,
  useLeaveHousehold,
  useRegenerateInviteCode,
  useRemoveMember,
  useRenameHousehold,
} from "../hooks/useHouseholds";
import "../App.css";

function inviteLink(code: string) {
  return `${publicAppUrl}/households?join=${code}`;
}

function HouseholdCard({ household }: { household: Household }) {
  const { userId } = useAuth();
  const leave = useLeaveHousehold();
  const regenerate = useRegenerateInviteCode();
  const renameHousehold = useRenameHousehold();
  const removeMember = useRemoveMember();
  const [copied, setCopied] = useState(false);
  const [isEditingName, setIsEditingName] = useState(false);
  const [editedName, setEditedName] = useState("");
  const isOwner = household.ownerId === userId;

  const handleSaveName = (e: React.FormEvent) => {
    e.preventDefault();
    if (!editedName.trim()) return;
    renameHousehold.mutate(
      { householdId: household.id, name: editedName },
      {
        onSuccess: () => setIsEditingName(false),
        onError: (err) => alert(getErrorMessage(err, "Could not rename household")),
      }
    );
  };

  const handleRemoveMember = (memberId: string, email: string) => {
    if (!confirm(`Remove ${email} from "${household.name}"? Lists they shared stay in the household and become yours. The invite code will change so they can't rejoin with it.`)) return;
    removeMember.mutate(
      { householdId: household.id, userId: memberId },
      { onError: (err) => alert(getErrorMessage(err, "Could not remove member")) }
    );
  };

  const handleShare = async () => {
    const link = inviteLink(household.inviteCode);
    const text = `Join "${household.name}" on Pantryman: ${link}`;
    try {
      // The share sheet on phones (WhatsApp, SMS, ...), the clipboard elsewhere
      if (!(await shareText("Pantryman invite", text))) {
        await navigator.clipboard.writeText(link);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }
    } catch {
      // Share sheet dismissed, or clipboard blocked: the code is on screen anyway
    }
  };

  const handleRegenerate = () => {
    if (!confirm("Make a new invite code? The current code and link will stop working.")) return;
    regenerate.mutate(household.id, {
      onError: (err) => alert(getErrorMessage(err, "Could not make a new invite code")),
    });
  };

  const handleLeave = () => {
    const isLast = household.members.length === 1;
    const message = isLast
      ? `Leave "${household.name}"? You are the last member, so the household will be deleted. Its lists become your personal lists.`
      : `Leave "${household.name}"? Lists you shared with it stay with the other members.` +
        (isOwner ? " The longest-standing member becomes the owner." : "");
    if (!confirm(message)) return;
    leave.mutate(household.id, {
      onError: (err) => alert(getErrorMessage(err, "Could not leave household")),
    });
  };

  return (
    <div className="card household-card">
      {isEditingName ? (
        <form className="household-rename" onSubmit={handleSaveName}>
          <input
            type="text"
            className="form-input"
            value={editedName}
            onChange={(e) => setEditedName(e.target.value)}
            maxLength={100}
            autoFocus
          />
          <button type="submit" className="btn btn-primary btn-small" disabled={renameHousehold.isPending}>
            Save
          </button>
          <button type="button" className="btn btn-secondary btn-small" onClick={() => setIsEditingName(false)}>
            Cancel
          </button>
        </form>
      ) : (
        <div className="title-row">
          <h3>{household.name}</h3>
          {isOwner && (
            <button
              className="edit-btn"
              onClick={() => {
                setEditedName(household.name);
                setIsEditingName(true);
              }}
              title="Rename household"
            >
              <svg
                width="18"
                height="18"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
              </svg>
            </button>
          )}
        </div>
      )}

      <p className="household-label">Members</p>
      <ul className="household-members">
        {household.members.map((m) => (
          <li key={m.userId}>
            <span>
              {m.email}
              {m.userId === userId && <span className="muted"> (you)</span>}
              {m.userId === household.ownerId && <span className="muted"> · owner</span>}
            </span>
            {isOwner && m.userId !== userId && (
              <button
                className="btn btn-secondary btn-small"
                onClick={() => handleRemoveMember(m.userId, m.email)}
                disabled={removeMember.isPending}
              >
                Remove
              </button>
            )}
          </li>
        ))}
      </ul>

      <p className="household-label">Invite code</p>
      <div className="invite-row">
        <code className="invite-code">{household.inviteCode}</code>
        <button className="btn btn-primary btn-small" onClick={handleShare}>
          {copied ? "Link copied" : "Share invite link"}
        </button>
      </div>

      <div className="form-actions">
        <button className="btn btn-secondary btn-small" onClick={handleRegenerate} disabled={regenerate.isPending}>
          New code
        </button>
        <button className="btn btn-danger btn-small" onClick={handleLeave} disabled={leave.isPending}>
          Leave
        </button>
      </div>
    </div>
  );
}

function HouseholdsPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const { data: households = [], isPending, error } = useHouseholds();
  const createHousehold = useCreateHousehold();
  const joinHousehold = useJoinHousehold();

  const [newName, setNewName] = useState("");
  // An invite link (/households?join=CODE) pre-fills the code
  const [inviteCode, setInviteCode] = useState(searchParams.get("join") ?? "");
  const [joinMessage, setJoinMessage] = useState<{ text: string; isError: boolean } | null>(null);

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newName.trim()) return;
    createHousehold.mutate(newName, {
      onSuccess: () => setNewName(""),
      onError: (err) => alert(getErrorMessage(err, "Could not create household")),
    });
  };

  const handleJoin = (e: React.FormEvent) => {
    e.preventDefault();
    if (!inviteCode.trim()) return;
    setJoinMessage(null);
    joinHousehold.mutate(inviteCode, {
      onSuccess: (household) => {
        setInviteCode("");
        setSearchParams({}, { replace: true });
        setJoinMessage({ text: `You joined "${household.name}". Its lists are now on your Lists page.`, isError: false });
      },
      onError: (err) => setJoinMessage({ text: getErrorMessage(err, "Could not join household"), isError: true }),
    });
  };

  return (
    <div>
      <div className="page-title">
        <div className="title-with-edit">
          <button className="btn btn-secondary back-btn-small" onClick={() => navigate(-1)}>
            ← Back
          </button>
          <h1>Households</h1>
        </div>
      </div>

      <p className="settings-warning">
        Share grocery lists with the people you live or shop with. Everyone in a household can see and edit the
        lists shared with it.
      </p>

      {error && <p style={{ color: "red" }}>{getErrorMessage(error, "Could not load households")}</p>}
      {isPending && !error && <div className="loading">Loading...</div>}

      {households.length > 0 && (
        <div className="household-list">
          {households.map((h) => (
            <HouseholdCard key={h.id} household={h} />
          ))}
        </div>
      )}

      <div className="section">
        <h2>Join a household</h2>
        <form className="settings-form" onSubmit={handleJoin}>
          <div className="form-group">
            <input
              type="text"
              className="form-input"
              placeholder="Invite code"
              value={inviteCode}
              onChange={(e) => setInviteCode(e.target.value)}
              autoCapitalize="characters"
              autoFocus={!!searchParams.get("join")}
            />
          </div>
          {joinMessage && (
            <p className={joinMessage.isError ? "settings-error" : "settings-success"}>{joinMessage.text}</p>
          )}
          <button type="submit" className="btn btn-primary" disabled={joinHousehold.isPending || !inviteCode.trim()}>
            Join
          </button>
        </form>
      </div>

      <div className="section">
        <h2>Create a household</h2>
        <form className="settings-form" onSubmit={handleCreate}>
          <div className="form-group">
            <input
              type="text"
              className="form-input"
              placeholder="Name, e.g. Home"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              maxLength={100}
            />
          </div>
          <button type="submit" className="btn btn-primary" disabled={createHousehold.isPending || !newName.trim()}>
            Create
          </button>
        </form>
      </div>
    </div>
  );
}

export default HouseholdsPage;
