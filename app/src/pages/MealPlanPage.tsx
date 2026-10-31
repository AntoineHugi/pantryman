import { useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import type { Meal, MealChoice, MealPlan, MealPlanEntry, MealSlot, RecipeSummary } from "../api/types";
import BackLink from "../components/BackLink";
import RecipePicker from "../components/RecipePicker";
import SlowServerNotice from "../components/SlowServerNotice";
import { useFeedback } from "../hooks/useFeedback";
import { useHouseholds } from "../hooks/useHouseholds";
import {
  useAddPlanDay,
  useConfirmMealPlan,
  useDeleteMealPlan,
  useMealPlan,
  useMoveMeal,
  useRemoveMeal,
  useRemovePlanDay,
  useSetMeal,
} from "../hooks/useMealPlans";
import { addDays, formatDateRange, formatDay, today } from "../utils/dates";
import { formatPeople } from "../utils/recipeText";
import "../App.css";

const MEALS: { meal: Meal; label: string }[] = [
  { meal: "lunch", label: "Lunch" },
  { meal: "dinner", label: "Dinner" },
];

const sameSlot = (a: MealSlot | null, b: MealSlot) => !!a && a.date === b.date && a.meal === b.meal;

const choiceOf = (entry: MealPlanEntry): MealChoice =>
  entry.recipeId ? { recipeId: entry.recipeId } : { text: entry.recipeName };

function MealPlanView({ plan }: { plan: MealPlan }) {
  const navigate = useNavigate();
  const { toast, confirm } = useFeedback();
  const { data: households = [] } = useHouseholds();
  const setMeal = useSetMeal(plan.id);
  const removeMeal = useRemoveMeal(plan.id);
  const moveMeal = useMoveMeal(plan.id);
  const addDay = useAddPlanDay(plan.id);
  const removeDay = useRemovePlanDay(plan.id);
  const confirmPlan = useConfirmMealPlan(plan.id);
  const deletePlan = useDeleteMealPlan();
  // The slot whose actions are open
  const [openSlot, setOpenSlot] = useState<MealSlot | null>(null);
  // The meal being moved: the next slot tapped is where it goes
  const [moving, setMoving] = useState<MealPlanEntry | null>(null);
  // The slot the meal picker fills
  const [picking, setPicking] = useState<MealSlot | null>(null);

  const showError = (fallback: string) => (err: unknown) => toast({ message: getErrorMessage(err, fallback), isError: true });
  const days = Array.from({ length: plan.days }, (_, i) => addDays(plan.startDate, i));
  const entryAt = (slot: MealSlot) => plan.entries.find((e) => sameSlot(e, slot));
  const household = households.find((h) => h.id === plan.householdId);
  const isPast = (date: string) => date < today();
  const recipeMeals = plan.entries.filter((e) => e.recipeId);
  // What confirming puts on the list: once the plan has one, only meals not on it yet
  const newMeals = plan.listId ? recipeMeals.filter((e) => !e.onList).length : recipeMeals.length;

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

  const handlePickRecipe = (recipe: RecipeSummary) => {
    if (!picking) return;
    // A new meal starts like the plan's latest one, or else at what the recipe feeds
    const current = entryAt(picking);
    const servings = current?.recipeId ? current.servings : recipeMeals.at(-1)?.servings ?? recipe.servings;
    setMeal.mutate({ slot: picking, choice: { recipeId: recipe.id }, servings }, { onError: showError("Could not add the meal") });
    // Shows the number of people, to adjust it for this meal
    setOpenSlot(picking);
    setPicking(null);
  };

  const handlePickText = (text: string) => {
    if (!picking) return;
    setMeal.mutate({ slot: picking, choice: { text }, servings: entryAt(picking)?.servings }, { onError: showError("Could not add the meal") });
    setPicking(null);
  };

  const changeServings = (entry: MealPlanEntry, delta: number) => {
    const servings = entry.servings + delta;
    if (servings < 1 || servings > 100) return;
    setMeal.mutate({ slot: entry, choice: choiceOf(entry), servings }, { onError: showError("Could not change the number of people") });
  };

  const handleRemove = (entry: MealPlanEntry) => {
    setOpenSlot(null);
    removeMeal.mutate(entry, { onError: showError("Could not remove the meal") });
    toast({
      message: `Removed ${entry.recipeName}`,
      actionLabel: "Undo",
      onAction: () =>
        setMeal.mutate({ slot: entry, choice: choiceOf(entry), servings: entry.servings, onList: entry.onList }, { onError: showError("Could not put the meal back") }),
    });
  };

  const handleRemoveDay = async (date: string) => {
    const meals = plan.entries.filter((e) => e.date === date).length;
    // A day that's over has nothing left to lose
    if (meals > 0 && !isPast(date)) {
      const confirmed = await confirm({
        title: `Remove ${formatDay(date)}?`,
        message: `${meals === 1 ? "Its meal goes" : `Its ${meals} meals go`} with it.${plan.listId ? " The grocery list stays as it is." : ""}`,
        confirmLabel: "Remove",
        danger: true,
      });
      if (!confirmed) return;
    }
    setOpenSlot(null);
    removeDay.mutate(date, { onError: showError("Could not remove the day") });
  };

  const handleConfirm = async () => {
    const count = newMeals;
    const mealCount = `${count} ${count === 1 ? "meal" : "meals"}`;
    const skipped = plan.entries.length - count;
    const confirmed = await confirm(
      plan.listId
        ? {
            title: "Add to the grocery list?",
            message: `The ingredients of ${mealCount} go on this plan's list. Optional ingredients and what's always at home are left out.`,
            confirmLabel: "Add to list",
          }
        : {
            title: "Create the grocery list?",
            message: `A new list with the ingredients of ${mealCount}, ${household ? `shared with ${household.name}` : "just for you"}, plus your usual items. Optional ingredients and what's always at home are left out${skipped > 0 ? `, and so ${skipped === 1 ? "is 1 meal" : `are ${skipped} meals`} without a recipe` : ""}.`,
            confirmLabel: "Create list",
          }
    );
    if (!confirmed) return;
    confirmPlan.mutate(undefined, {
      onSuccess: ({ list }) => navigate(`/lists/${list.id}/items`),
      onError: showError(plan.listId ? "Could not add to the list" : "Could not create the list"),
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
        {household ? `Shared with ${household.name}` : plan.householdId ? "Shared" : "Personal"}
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
            <div className="plan-day-header">
              <h2 className="plan-day-title">{formatDay(date)}</h2>
              {!moving && plan.days > 1 && (date === plan.startDate || date === plan.endDate) && (
                <button
                  className="btn btn-secondary btn-small"
                  onClick={() => handleRemoveDay(date)}
                  disabled={removeDay.isPending}
                  aria-label={`Remove ${formatDay(date)}`}
                >
                  Remove day
                </button>
              )}
            </div>
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
                        {entry.recipeId && (
                          <span className="recipe-card-meta">
                            {formatPeople(entry.servings)}{plan.listId && entry.onList ? " · on the list" : ""}
                          </span>
                        )}
                      </span>
                    ) : (
                      <span className="plan-slot-empty">{moving ? "Move here" : "+ Add a meal"}</span>
                    )}
                  </button>
                  {entry && isOpen && (
                    <div className="plan-slot-actions">
                      {entry.recipeId && (
                        <div className="stepper" aria-label="People">
                          <button type="button" onClick={() => changeServings(entry, -1)} disabled={entry.servings <= 1} aria-label="Fewer people">−</button>
                          <span className="servings-count">{formatPeople(entry.servings)}</span>
                          <button type="button" onClick={() => changeServings(entry, 1)} disabled={entry.servings >= 100} aria-label="More people">+</button>
                        </div>
                      )}
                      <div className="form-actions">
                        <button className="btn btn-secondary btn-small" onClick={() => { setMoving(entry); setOpenSlot(null); }}>Move</button>
                        <button className="btn btn-secondary btn-small" onClick={() => setPicking(slot)}>Change</button>
                        {entry.recipeId && <Link className="btn btn-secondary btn-small" to={`/recipes/${entry.recipeId}?servings=${entry.servings}`}>Recipe</Link>}
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

      <button className="btn btn-secondary btn-block plan-add-day" onClick={() => addDay.mutate(undefined, { onError: showError("Could not add a day") })} disabled={addDay.isPending}>
        + Add a day
      </button>
      <SlowServerNotice active={addDay.isPending || removeDay.isPending} />

      <div className="plan-footer">
        {plan.listId ? (
          <>
            {newMeals > 0 && (
              <button className="btn btn-primary btn-block" onClick={handleConfirm} disabled={confirmPlan.isPending}>
                Add {newMeals} new {newMeals === 1 ? "meal" : "meals"} to the list
              </button>
            )}
            <p className="muted">Changes to meals already on the grocery list don't update it.</p>
            <Link className={`btn ${newMeals > 0 ? "btn-secondary" : "btn-primary"} btn-block`} to={`/lists/${plan.listId}/items`}>Open the grocery list</Link>
          </>
        ) : (
          <button className="btn btn-primary btn-block" onClick={handleConfirm} disabled={newMeals === 0 || confirmPlan.isPending}>
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
          initialText={entryAt(picking)?.recipeId === null ? entryAt(picking)?.recipeName : undefined}
          onPick={handlePickRecipe}
          onText={handlePickText}
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
