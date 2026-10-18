import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import type { Meal, MealPlan, MealPlanEntry, MealSlot } from "../api/types";
import BackLink from "../components/BackLink";
import RecipePicker from "../components/RecipePicker";
import SlowServerNotice from "../components/SlowServerNotice";
import { useFeedback } from "../hooks/useFeedback";
import { useHouseholds } from "../hooks/useHouseholds";
import {
  useConfirmMealPlan,
  useDeleteMealPlan,
  useMealPlan,
  useMoveMeal,
  useRemoveMeal,
  useSetMeal,
} from "../hooks/useMealPlans";
import { addDays, formatDateRange, formatDay, today } from "../utils/dates";
import "../App.css";

const MEALS: { meal: Meal; label: string }[] = [
  { meal: "lunch", label: "Lunch" },
  { meal: "dinner", label: "Dinner" },
];

const sameSlot = (a: MealSlot | null, b: MealSlot) => !!a && a.date === b.date && a.meal === b.meal;

function MealPlanView({ plan }: { plan: MealPlan }) {
  const navigate = useNavigate();
  const { toast, confirm } = useFeedback();
  const { data: households = [] } = useHouseholds();
  const setMeal = useSetMeal(plan.id);
  const removeMeal = useRemoveMeal(plan.id);
  const moveMeal = useMoveMeal(plan.id);
  const confirmPlan = useConfirmMealPlan(plan.id);
  const deletePlan = useDeleteMealPlan();
  // The slot whose actions are open
  const [openSlot, setOpenSlot] = useState<MealSlot | null>(null);
  // The meal being moved: the next slot tapped is where it goes
  const [moving, setMoving] = useState<MealPlanEntry | null>(null);
  // The slot the recipe picker fills
  const [picking, setPicking] = useState<MealSlot | null>(null);

  const showError = (fallback: string) => (err: unknown) => toast({ message: getErrorMessage(err, fallback), isError: true });
  const days = Array.from({ length: plan.days }, (_, i) => addDays(plan.startDate, i));
  const entryAt = (slot: MealSlot) => plan.entries.find((e) => sameSlot(e, slot));
  const household = households.find((h) => h.id === plan.householdId);
  const isPast = (date: string) => date < today();

  const handleSlotTap = (slot: MealSlot) => {
    if (moving) {
      if (!sameSlot(moving, slot)) {
        moveMeal.mutate({ from: { date: moving.date, meal: moving.meal }, to: slot }, { onError: showError("Could not move the meal") });
      }
      setMoving(null);
      return;
    }
    if (entryAt(slot)) setOpenSlot(sameSlot(openSlot, slot) ? null : slot);
    else setPicking(slot);
  };

  const handlePick = (recipeId: string) => {
    if (!picking) return;
    const current = entryAt(picking);
    setMeal.mutate(
      { slot: picking, recipeId, servings: current?.servings },
      { onError: showError("Could not add the meal") }
    );
    setPicking(null);
  };

  const changeServings = (entry: MealPlanEntry, delta: number) => {
    const servings = entry.servings + delta;
    if (servings < 1 || servings > 100) return;
    setMeal.mutate({ slot: entry, recipeId: entry.recipeId, servings }, { onError: showError("Could not change the servings") });
  };

  const handleRemove = (entry: MealPlanEntry) => {
    setOpenSlot(null);
    removeMeal.mutate(entry, { onError: showError("Could not remove the meal") });
    toast({
      message: `Removed ${entry.recipeName}`,
      actionLabel: "Undo",
      onAction: () =>
        setMeal.mutate({ slot: entry, recipeId: entry.recipeId, servings: entry.servings }, { onError: showError("Could not put the meal back") }),
    });
  };

  const handleConfirm = async () => {
    const count = plan.entries.length;
    const confirmed = await confirm({
      title: "Create the grocery list?",
      message: `A new list with the ingredients of ${count} ${count === 1 ? "meal" : "meals"}, ${household ? `shared with ${household.name}` : "just for you"}, plus your usual items. Optional ingredients and what's always at home are left out.`,
      confirmLabel: "Create list",
    });
    if (!confirmed) return;
    confirmPlan.mutate(undefined, {
      onSuccess: ({ list }) => navigate(`/lists/${list.id}/items`),
      onError: showError("Could not create the list"),
    });
  };

  const handleDelete = async () => {
    const confirmed = await confirm({
      title: "Delete plan?",
      message: plan.listId ? "The grocery list made from it stays." : "This can't be undone.",
      confirmLabel: "Delete",
      danger: true,
    });
    if (!confirmed) return;
    deletePlan.mutate(plan.id, {
      onSuccess: () => navigate("/plans", { replace: true }),
      onError: showError("Could not delete the plan"),
    });
  };

  return (
    <div className="meal-plan-page">
      <BackLink to="/plans" label="Meal plans" />
      <h1>{formatDateRange(plan.startDate, plan.endDate)}</h1>
      <p className="recipe-meta">
        {plan.people} {plan.people === 1 ? "person" : "people"} · {household ? `Shared with ${household.name}` : plan.householdId ? "Shared" : "Personal"}
      </p>

      {moving && (
        <div className="move-banner" role="status">
          <span>Tap where <strong>{moving.recipeName}</strong> should go</span>
          <button className="btn btn-secondary btn-small" onClick={() => setMoving(null)}>Cancel</button>
        </div>
      )}

      <div className="plan-days">
        {days.map((date) => (
          <section key={date} className={`card plan-day${isPast(date) ? " is-past" : ""}`}>
            <h2 className="plan-day-title">{formatDay(date)}</h2>
            {MEALS.map(({ meal, label }) => {
              const slot = { date, meal };
              const entry = entryAt(slot);
              const isOpen = !moving && sameSlot(openSlot, slot);
              return (
                <div key={meal} className={`plan-slot${entry ? " is-filled" : ""}${moving ? " is-target" : ""}${sameSlot(moving, slot) ? " is-moving" : ""}`}>
                  <button className="plan-slot-button" onClick={() => handleSlotTap(slot)} aria-expanded={entry ? isOpen : undefined}>
                    <span className="plan-slot-label">{label}</span>
                    {entry ? (
                      <span className="plan-slot-meal">
                        <span className="plan-slot-name">{entry.recipeName}</span>
                        <span className="recipe-card-meta">{entry.servings} {entry.servings === 1 ? "serving" : "servings"}</span>
                      </span>
                    ) : (
                      <span className="plan-slot-empty">{moving ? "Move here" : "+ Add a meal"}</span>
                    )}
                  </button>
                  {entry && isOpen && (
                    <div className="plan-slot-actions">
                      <div className="stepper" aria-label="Servings">
                        <button type="button" onClick={() => changeServings(entry, -1)} disabled={entry.servings <= 1} aria-label="Fewer servings">−</button>
                        <span className="servings-count">{entry.servings} {entry.servings === 1 ? "serving" : "servings"}</span>
                        <button type="button" onClick={() => changeServings(entry, 1)} disabled={entry.servings >= 100} aria-label="More servings">+</button>
                      </div>
                      <div className="form-actions">
                        <button className="btn btn-secondary btn-small" onClick={() => { setMoving(entry); setOpenSlot(null); }}>Move</button>
                        <button className="btn btn-secondary btn-small" onClick={() => setPicking(slot)}>Change</button>
                        <Link className="btn btn-secondary btn-small" to={`/recipes/${entry.recipeId}`}>Recipe</Link>
                        <button className="btn btn-danger-outline btn-small" onClick={() => handleRemove(entry)}>Remove</button>
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </section>
        ))}
      </div>

      <div className="plan-footer">
        {plan.listId ? (
          <>
            <p className="muted">The grocery list was made from this plan. Changes since then aren't on it; add extra recipes from their page.</p>
            <Link className="btn btn-primary btn-block" to={`/lists/${plan.listId}/items`}>Open the grocery list</Link>
          </>
        ) : (
          <button className="btn btn-primary btn-block" onClick={handleConfirm} disabled={plan.entries.length === 0 || confirmPlan.isPending}>
            Create grocery list
          </button>
        )}
        <SlowServerNotice active={confirmPlan.isPending} />
        <button className="btn btn-danger-outline plan-delete" onClick={handleDelete} disabled={deletePlan.isPending}>
          Delete plan
        </button>
      </div>

      {picking && (
        <RecipePicker
          title={`${MEALS.find((m) => m.meal === picking.meal)?.label}, ${formatDay(picking.date)}`}
          onPick={(recipe) => handlePick(recipe.id)}
          onClose={() => setPicking(null)}
        />
      )}
    </div>
  );
}

function MealPlanPage() {
  const { id = "" } = useParams();
  const { data: plan, isPending, error } = useMealPlan(id);

  if (plan) return <MealPlanView plan={plan} />;
  return (
    <div>
      <BackLink to="/plans" label="Meal plans" />
      {isPending ? (
        <>
          <div className="loading">Loading…</div>
          <SlowServerNotice active />
        </>
      ) : (
        <p className="form-error">{getErrorMessage(error, "Could not load the meal plan")}</p>
      )}
    </div>
  );
}

export default MealPlanPage;
