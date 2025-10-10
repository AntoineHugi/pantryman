import { useEffect, useState } from "react";
import { Link, Routes, Route, useParams, Navigate } from "react-router-dom";
import './App.css'
import api from "./api";
import ListsPage from "./ListsPage";
import ListDetails from "./ListDetails";

function App() {
  return (
    <>
      <header className="header">
        <div className="container">
          <div className="logo">
            <Link className="logo-link" to={`/lists`}>
              <span className="logo-text">PantryMan</span>
            </Link>
          </div>
        </div>
      </header>

      <main className="container">
        <Routes>
          <Route path="/" element={<Navigate to="/lists" replace />} />
          <Route path="/lists" element={<ListsPage />} />
          <Route path="/lists/:id/items" element={<ListDetails />} />
        </Routes>
      </main>
    </>
  );
}

export default App