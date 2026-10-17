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
    isFavorite: boolean;
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