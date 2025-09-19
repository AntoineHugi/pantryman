import { useEffect, useState } from "react";
import reactLogo from './assets/react.svg'
import viteLogo from '/vite.svg'
import './App.css'
import api from "./api";

type GroceryList = {
  id: string;
  name: string;
  items: { id: string; name: string; quantity: number }[];
};

function App() {
  const [lists, setLists] = useState<GroceryList[]>([]);
  const [error, setError] = useState<string | null>(null);

    useEffect(() => {
      api.get<GroceryList[]>("/lists")
        .then((res) => setLists(res.data))
        .catch((err) => {
          console.error("Failed to fetch lists:", err);
          setError("Could not load grocery lists");
        });
    }, []);

    return (
        <div>
            <h1>Hello, I'm your Pantryman 👋</h1>
                <ul>
                    {lists.map((list) => (
                        <li key={list.id}>{list.name}</li>
                    ))}
                </ul>
        </div>
    );
}

export default App