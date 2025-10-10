import { useEffect, useState } from "react";
import { Link, Routes, Route, useParams, Navigate } from "react-router-dom";
import './App.css'
import api from "./api";

type GroceryList = {
  id: string;
  name: string;
  items: { id: string; name: string; quantity: number }[];
};

function ListsPage() {
  const [lists, setLists] = useState<GroceryList[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api
      .get<GroceryList[]>("/lists")
      .then((res) => setLists(res.data))
      .catch((err) => {
        console.error("Failed to fetch lists:", err);
        setError("Could not load grocery lists");
      });
  }, []);

  return (
    <>
      <h1>Hello, I'm your Pantryman 👋</h1>
      <h2>Here are your current Grocery lists</h2>
      {error && <p style={{ color: "red" }}>{error}</p>}
      <ul>
        {lists.map((list) => (
          <li key={list.id}>
            <Link className="list-link" to={`/lists/${list.id}/items`}>{list.name}</Link>
          </li>
        ))}
      </ul>
    </>
  );
}

export default ListsPage
