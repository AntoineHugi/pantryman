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
    quantity: number;
    isChecked: boolean;
  }
  
  export interface CreateListRequest {
    name: string;
    householdId?: string | null;
  }

  export interface HouseholdMember {
    userId: string;
    email: string;
  }

  export interface Household {
    id: string;
    name: string;
    inviteCode: string;
    // Can rename the household and remove members
    ownerId: string;
    members: HouseholdMember[];
  }
  
  export interface CreateItemRequest {
    name: string;
    quantity: number;
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
