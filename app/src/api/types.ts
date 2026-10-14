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
  }
  
  export interface LoginRequest {
    email: string;
    password: string;
  }
  
  export interface GroceryList {
    id: string;
    name: string;
    items: Item[];
    userId: string;
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
  }
  
  export interface CreateItemRequest {
    name: string;
    quantity: number;
  }