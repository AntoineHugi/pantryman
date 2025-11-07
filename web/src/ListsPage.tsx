import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import "./App.css";
import api from "./api";

type GroceryList = {
  id: string;
  name: string;
  items: { id: string; name: string; quantity: number }[];
};

function ListsPage() {
  const [lists, setLists] = useState<GroceryList[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [newListName, setNewListName] = useState("");

  useEffect(() => {
    api
      .get<GroceryList[]>("/lists")
      .then((res) => setLists(res.data))
      .catch((err) => {
        console.error("Failed to fetch lists:", err);
        setError("Could not load grocery lists");
      });
  }, []);

  const handleAddList = async () => {
    if (!newListName.trim()) return alert("List name cannot be empty!");

    try {
      const response = await api.post("/lists", { name: newListName });
      const createdList: GroceryList = response.data;
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
