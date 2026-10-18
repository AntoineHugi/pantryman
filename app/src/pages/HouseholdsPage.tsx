import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import type { Household } from "../api/types";
import BackLink from "../components/BackLink";
import SlowServerNotice from "../components/SlowServerNotice";
import { PencilIcon } from "../components/icons";
import { useAuth } from "../hooks/useAuth";
import { useFeedback } from "../hooks/useFeedback";
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

// "Sun 25 Oct"
function formatExpiry(iso: string) {
  return new Date(iso).toLocaleDateString("en-GB", { weekday: "short", day: "numeric", month: "short" });
}

function HouseholdCard({ household }: { household: Household }) {
  const { userId } = useAuth();
  const leave = useLeaveHousehold();
  const regenerate = useRegenerateInviteCode();
  const renameHousehold = useRenameHousehold();
  const removeMember = useRemoveMember();
  const { toast, confirm } = useFeedback();
  const showError = (fallback: string) => (err: unknown) =>
    toast({ message: getErrorMessage(err, fallback), isError: true });
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
        onError: showError("Could not rename household"),
      }
    );
  };

  const handleRemoveMember = async (memberId: string, email: string) => {
    const confirmed = await confirm({
      title: "Remove member?",
      message: `Remove ${email} from "${household.name}"? Lists they shared stay in the household and become yours. The invite code will change so they can't rejoin with it.`,
      confirmLabel: "Remove",
      danger: true,
    });
    if (!confirmed) return;
    removeMember.mutate(
      { householdId: household.id, userId: memberId },
      { onError: showError("Could not remove member") }
    );
  };

  const handleShare = async () => {
    const link = inviteLink(household.inviteCode);
    const validUntil = household.inviteCodeExpiresAt ? ` (valid until ${formatExpiry(household.inviteCodeExpiresAt)})` : "";
    const text = `Join "${household.name}" on Pantryman: ${link}${validUntil}`;
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

  const handleRegenerate = async () => {
    const confirmed = await confirm({
      title: "New invite code?",
      message: "The current code and link will stop working.",
      confirmLabel: "Make new code",
    });
    if (!confirmed) return;
    regenerate.mutate(household.id, {
      onError: showError("Could not make a new invite code"),
    });
  };

  const handleLeave = async () => {
    const isLast = household.members.length === 1;
    const message = isLast
      ? `Leave "${household.name}"? You are the last member, so the household will be deleted. Its lists become your personal lists.`
      : `Leave "${household.name}"? Lists you shared with it stay with the other members.` +
        (isOwner ? " The longest-standing member becomes the owner." : "");
    if (!(await confirm({ title: "Leave household?", message, confirmLabel: "Leave", danger: true }))) return;
    leave.mutate(household.id, {
      onError: showError("Could not leave household"),
    });
  };

  return (
    <div className="card household-card">
      {isEditingName ? (
        <form className="inline-edit-form" onSubmit={handleSaveName}>
          <input
            type="text"
            className="form-input"
            value={editedName}
            onChange={(e) => setEditedName(e.target.value)}
            aria-label="Household name"
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
              className="icon-btn"
              onClick={() => {
                setEditedName(household.name);
                setIsEditingName(true);
              }}
              aria-label="Rename household"
            >
              <PencilIcon />
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
      {household.inviteCodeExpiresAt && (
        <p className="invite-expiry muted">Valid until {formatExpiry(household.inviteCodeExpiresAt)}</p>
      )}

      <div className="form-actions">
        <button className="btn btn-secondary btn-small" onClick={handleRegenerate} disabled={regenerate.isPending}>
          New code
        </button>
        <button className="btn btn-danger-outline btn-small" onClick={handleLeave} disabled={leave.isPending}>
          Leave
        </button>
      </div>
    </div>
  );
}

function HouseholdsPage() {
  const { toast } = useFeedback();
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
      onError: (err) => toast({ message: getErrorMessage(err, "Could not create household"), isError: true }),
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
      <BackLink />
      <div className="page-header">
        <h1>Households</h1>
      </div>

      <p className="page-intro">
        Share grocery lists with the people you live or shop with. Everyone in a household can see and edit the
        lists shared with it.
      </p>

      {error && <p className="form-error">{getErrorMessage(error, "Could not load households")}</p>}
      {isPending && !error && (
        <>
          <div className="loading">Loading…</div>
          <SlowServerNotice active />
        </>
      )}

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
            <p className={joinMessage.isError ? "form-error" : "form-success"}>{joinMessage.text}</p>
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
