import { useEffect, useState } from 'react';
import { currentUser, initAuth, login, type User } from './lib/auth';
import Navbar, { type Page } from './components/Navbar';
import ChatPage from './components/ChatPage';
import KnowledgePage from './components/KnowledgePage';
import TasksPage from './components/TasksPage';

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState<Page>('chat');

  useEffect(() => {
    initAuth()
      .then((authenticated) => (authenticated ? setUser(currentUser()) : login()))
      .catch((e: Error) => setError(e.message));
  }, []);

  if (error) {
    return (
      <section className="section">
        <div className="notification is-danger">
          <p className="mb-3">{error}</p>
          <button className="button is-light" onClick={() => login()}>
            Erneut anmelden
          </button>
        </div>
      </section>
    );
  }

  if (!user) {
    return (
      <section className="hero is-fullheight">
        <div className="hero-body has-text-centered">
          <div className="container">
            <p className="title">SAI</p>
            <p className="subtitle">Anmeldung wird vorbereitet …</p>
            <progress className="progress is-small is-primary" max={100} />
          </div>
        </div>
      </section>
    );
  }

  return (
    <div className="app">
      <Navbar user={user} page={page} onNavigate={setPage} />
      <main className="app-main">
        {page === 'chat' && <ChatPage />}
        {page === 'tasks' && <TasksPage />}
        {page === 'knowledge' && <KnowledgePage user={user} />}
      </main>
    </div>
  );
}
