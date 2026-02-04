import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listsApi } from "../api/lists";
import { useAuth } from "../hooks/useAuth";
import type { GroceryList } from "../api/types";
import "../App.css";

function ListsPage() {
  const [lists, setLists] = useState<GroceryList[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [newListName, setNewListName] = useState("");
  const { userId } = useAuth();

  useEffect(() => {
    listsApi
      .getAll()
      .then((data) => setLists(data))
      .catch((err) => {
        console.error("Failed to fetch lists:", err);
        setError("Could not load grocery lists");
      });
  }, []);

  const handleAddList = async () => {
    if (!newListName.trim()) return alert("List name cannot be empty!");
    if (!userId) return alert("You must be logged in to create a list");

    try {
      const createdList = await listsApi.create({
        name: newListName,
        userId: userId,
      });
      setLists((prev) => [...prev, createdList]);
      setNewListName("");
      setShowForm(false);
    } catch (err) {
      console.error("Failed to create list:", err);
      alert("Could not create list");
    }
  };

  return (
    <>
      <h1>Hello, I'm your Pantryman 👋</h1>
      <h2>Here are your current grocery lists</h2>
      {error && <p style={{ color: "red" }}>{error}</p>}
      <ul>
        {lists.map((list) => (
          <li key={list.id}>
            <Link className="list-link" to={`/lists/${list.id}/items`}>
              {list.name}
            </Link>
          </li>
        ))}
      </ul>
      {!showForm && (
        <button onClick={() => setShowForm(true)}>Add list</button>
      )}
      {showForm && (
        <div className="add-list-form">
          <input
            type="text"
            placeholder="Enter list name"
            value={newListName}
            onChange={(e) => setNewListName(e.target.value)}
          />
          <button onClick={handleAddList}>Create</button>
          <button onClick={() => setShowForm(false)}>Cancel</button>
        </div>
      )}
    </>
  );
}

export default ListsPage;