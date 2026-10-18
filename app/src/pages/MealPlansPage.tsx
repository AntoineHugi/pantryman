import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { getErrorMessage } from "../api/client";
import type { MealPlan } from "../api/types";
import SlowServerNotice from "../components/SlowServerNotice";
import { useHouseholds } from "../hooks/useHouseholds";
import { useCreateMealPlan, useMealPlans } from "../hooks/useMealPlans";
import { formatDateRange, today } from "../utils/dates";
import "../App.css";

const DAY_CHOICES = [3, 4, 5, 6, 7, 10, 14];

function planSummary(plan: MealPlan, householdName: string | undefined) {
  const meals = plan.entries.length;
  return [
    `${meals} ${meals === 1 ? "meal" : "meals"}`,
    householdName ?? (plan.householdId ? "Shared" : "Personal"),
    plan.listId ? "List made" : null,
  ]
    .filter(Boolean)
    .join(" · ");
}

function MealPlansPage() {
  const navigate = useNavigate();
  const { data: plans = [], isPending, error } = useMealPlans();
  const { data: households = [] } = useHouseholds();
  const createPlan = useCreateMealPlan();
  const [showForm, setShowForm] = useState(false);
  const [startDate, setStartDate] = useState(today());
  const [days, setDays] = useState(7);
  const [people, setPeople] = useState(2);
  // "" = personal; undefined = not chosen yet, which defaults to the first household
  const [chosenHouseholdId, setChosenHouseholdId] = useState<string | undefined>(undefined);
  const householdId = chosenHouseholdId ?? households[0]?.id ?? "";

  const handleCreate = (e: React.FormEvent) => {
    e.preventDefault();
    if (!startDate) return;
    createPlan.mutate(
      { startDate, days, people, householdId: householdId || null },
      { onSuccess: (plan) => navigate(`/plans/${plan.id}`) }
    );
  };

  const form = (
    <form className="add-form" onSubmit={handleCreate}>
      <div className="form-row form-row-2">
        <div className="form-group">
          <label className="form-label" htmlFor="plan-start">Starts</label>
          <input id="plan-start" type="date" className="form-input" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
        </div>
        <div className="form-group">
          <label className="form-label" htmlFor="plan-days">Days</label>
          <select id="plan-days" className="form-input" value={days} onChange={(e) => setDays(Number(e.target.value))}>
            {DAY_CHOICES.map((d) => (
              <option key={d} value={d}>{d} days</option>
            ))}
          </select>
        </div>
      </div>
      <div className="form-group">
        <span className="form-label" id="plan-people-label">People</span>
        <div className="stepper" aria-labelledby="plan-people-label">
          <button type="button" onClick={() => setPeople(people - 1)} disabled={people <= 1} aria-label="Fewer people">−</button>
          <span className="servings-count">{people} {people === 1 ? "person" : "people"}</span>
          <button type="button" onClick={() => setPeople(people + 1)} disabled={people >= 20} aria-label="More people">+</button>
        </div>
      </div>
      {households.length > 0 && (
        <div className="form-group">
          <select className="form-input" value={householdId} onChange={(e) => setChosenHouseholdId(e.target.value)} aria-label="Share with">
            {households.map((h) => (
              <option key={h.id} value={h.id}>Shared with {h.name}</option>
            ))}
            <option value="">Personal (only me)</option>
          </select>
        </div>
      )}
      {createPlan.isError && <p className="form-error">{getErrorMessage(createPlan.error, "Could not create the plan")}</p>}
      <SlowServerNotice active={createPlan.isPending} />
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={createPlan.isPending}>Create plan</button>
        <button type="button" className="btn btn-secondary" onClick={() => setShowForm(false)}>Cancel</button>
      </div>
    </form>
  );

  return (
    <div>
      <div className="page-header">
        <h1>Meal plans</h1>
        <p className="page-intro">Plan lunches and dinners, then turn the plan into a grocery list.</p>
      </div>

      {error && plans.length === 0 && <p className="form-error">{getErrorMessage(error, "Could not load meal plans")}</p>}
      {error && plans.length > 0 && <p className="offline-banner">Can't reach the server. Showing the last saved copy.</p>}
      {isPending && (
        <>
          <div className="loading">Loading…</div>
          <SlowServerNotice active />
        </>
      )}

      {showForm ? form : (
        <button className="btn btn-primary" onClick={() => setShowForm(true)}>+ New plan</button>
      )}

      {plans.length > 0 && (
        <ul className="card-list plan-list">
          {plans.map((plan) => (
            <li key={plan.id}>
              <Link className="list-card recipe-card" to={`/plans/${plan.id}`}>
                <span className="list-card-name">{formatDateRange(plan.startDate, plan.endDate)}</span>
                <span className="recipe-card-meta">
                  {planSummary(plan, households.find((h) => h.id === plan.householdId)?.name)}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default MealPlansPage;
