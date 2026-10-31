export interface User {
    id: string;
    email: string;
  }
  
  export interface AuthResponse {
    token: string;
    userId: string;
    email: string;
  }

  export interface SignupResponse {
    message: string;
    email: string;
  }
  
  export interface SignupRequest {
    email: string;
    password: string;
    // In-app path to open after verifying and logging in, e.g. a household invite link
    redirectPath?: string;
  }
  
  export interface LoginRequest {
    email: string;
    password: string;
  }
  
  export interface GroceryList {
    id: string;
    name: string;
    items: Item[];
    // The owner: only they can change who the list is shared with
    userId: string;
    // null for a personal list
    householdId?: string | null;
  }
  
  export interface Item {
    id: string;
    name: string;
    // Can be a decimal (0.5 l)
    quantity: number;
    isChecked: boolean;
    // null for a count ("3 eggs"). Missing from older servers' answers.
    unit?: string | null;
    // e.g. "Bolognese, Lasagna" when added from recipes
    fromRecipes?: string | null;
  }
  
  export interface CreateListRequest {
    name: string;
    householdId?: string | null;
    // Pantry defaults to put on the new list; omitted = all of them
    defaultItemIds?: string[];
  }

  export interface HouseholdMember {
    userId: string;
    email: string;
  }

  export interface Household {
    id: string;
    name: string;
    inviteCode: string;
    // ISO-8601 instant, 7 days after the code was made. The server renews an expired code when a member loads it.
    // Optional: missing from households cached before expiry existed.
    inviteCodeExpiresAt?: string;
    // Can rename the household and remove members
    ownerId: string;
    members: HouseholdMember[];
  }
  
  export interface CreateItemRequest {
    name: string;
    quantity: number;
    unit?: string | null;
    // Made by the app, so an item added offline keeps its ID. Older servers ignore it.
    id?: string;
    // For Undo, which puts a ticked item back ticked
    isChecked?: boolean;
  }

  export interface AddRecipeToListRequest {
    recipeId: string;
    servings: number;
    // Positions in the recipe's ingredients
    ingredientIndexes: number[];
  }

  export interface AddRecipeToListResponse {
    list: GroceryList;
    // New items, and items already on the list whose quantity went up
    added: number;
    merged: number;
  }
  // Recipes are shared with every user; only the creator (createdBy) can edit or delete one.
  // The format is in docs/recipe-format.md.
  export interface RecipeIngredient {
    name: string;
    // null for "salt to taste"
    amount: number | null;
    // null for a count ("2 eggs")
    unit: string | null;
    note?: string | null;
    section?: string | null;
    optional?: boolean;
  }

  export interface RecipeSummary {
    id: string;
    // null once the creator has deleted their account
    createdBy: string | null;
    name: string;
    servings: number;
    description?: string | null;
    prepMinutes?: number | null;
    cookMinutes?: number | null;
    tags: string[];
  }

  export interface Recipe extends RecipeSummary {
    sourceUrl?: string | null;
    ingredients: RecipeIngredient[];
    steps: string[];
    notes?: string | null;
  }

  export interface RecipeInput {
    name: string;
    servings: number;
    description?: string | null;
    prepMinutes?: number | null;
    cookMinutes?: number | null;
    tags: string[];
    sourceUrl?: string | null;
    ingredients: RecipeIngredient[];
    steps: string[];
    notes?: string | null;
  }

  export interface RecipeImportResponse {
    imported: RecipeSummary[];
    // Names of recipes the user had already added
    skipped: string[];
  }

  // Meal plans: lunch and dinner for a few days. Like a list, personal or shared with a household.
  export type Meal = 'lunch' | 'dinner';

  export interface MealSlot {
    // "2026-10-20"
    date: string;
    meal: Meal;
  }

  export interface MealPlanEntry extends MealSlot {
    // Null for a free-text meal
    recipeId: string | null;
    // The recipe's name or the free text
    recipeName: string;
    servings: number;
  }

  // What goes in a slot: a recipe or free text
  export type MealChoice = { recipeId: string } | { text: string };

  export interface MealPlan {
    id: string;
    userId: string;
    householdId: string | null;
    startDate: string;
    // The last day, included
    endDate: string;
    days: number;
    // Default servings for a new meal
    people: number;
    // The list made on confirming; null before that, or once the list is deleted
    listId: string | null;
    entries: MealPlanEntry[];
  }

  export interface CreateMealPlanRequest {
    startDate: string;
    days: number;
    householdId: string | null;
    people: number;
  }

  export interface ConfirmMealPlanResponse {
    plan: MealPlan;
    list: GroceryList;
  }

  // Usual items of a household, or the user's own for personal lists
  export type PantryKind = 'default' | 'staple';

  export interface PantryItem {
    id: string;
    // null for personal items
    householdId: string | null;
    name: string;
    // default: added to new lists. staple: always at home, so recipes skip it.
    kind: PantryKind;
    quantity: number;
    unit: string | null;
  }

  export interface AddPantryItemRequest {
    householdId: string | null;
    name: string;
    kind: PantryKind;
    quantity?: number;
    unit?: string | null;
  }
