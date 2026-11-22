import { useState } from 'react';
import { logout, type User } from '../lib/auth';

export type Page = 'chat' | 'tasks' | 'knowledge';

interface Props {
  user: User;
  page: Page;
  onNavigate: (page: Page) => void;
}

export default function Navbar({ user, page, onNavigate }: Props) {
  const [open, setOpen] = useState(false);
  const go = (target: Page) => {
    onNavigate(target);
    setOpen(false);
  };

  return (
    <nav className="navbar is-primary" aria-label="Hauptnavigation">
      <div className="navbar-brand">
        <span className="navbar-item has-text-weight-bold is-size-5">SAI</span>
        <a
          role="button"
          className={`navbar-burger ${open ? 'is-active' : ''}`}
          aria-label="Menue"
          aria-expanded={open}
          onClick={() => setOpen(!open)}
        >
          <span aria-hidden="true" />
          <span aria-hidden="true" />
          <span aria-hidden="true" />
          <span aria-hidden="true" />
        </a>
      </div>
      <div className={`navbar-menu ${open ? 'is-active' : ''}`}>
        <div className="navbar-start">
          <a className={`navbar-item ${page === 'chat' ? 'is-active' : ''}`} onClick={() => go('chat')}>
            Chat
          </a>
          <a className={`navbar-item ${page === 'tasks' ? 'is-active' : ''}`} onClick={() => go('tasks')}>
            Aufgaben
          </a>
          <a className={`navbar-item ${page === 'knowledge' ? 'is-active' : ''}`} onClick={() => go('knowledge')}>
            Wissensbasis
          </a>
        </div>
        <div className="navbar-end">
          <div className="navbar-item">
            <span className="mr-2">{user.name}</span>
            {user.isAdmin && <span className="tag is-warning is-light">Admin</span>}
          </div>
          <div className="navbar-item">
            <button className="button is-light is-small" onClick={logout}>
              Abmelden
            </button>
          </div>
        </div>
      </div>
    </nav>
  );
}
