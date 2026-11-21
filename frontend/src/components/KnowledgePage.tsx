import { useEffect, useState } from 'react';
import { api } from '../lib/api';
import type { User } from '../lib/auth';
import type { Agent, KnowledgeDocument } from '../lib/types';

const formatSize = (bytes: number) =>
  bytes < 1024 ? `${bytes} B` : bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`;

export default function KnowledgePage({ user }: { user: User }) {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [agentId, setAgentId] = useState('');
  const [documents, setDocuments] = useState<KnowledgeDocument[]>([]);
  const [file, setFile] = useState<File | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [info, setInfo] = useState<string | null>(null);

  useEffect(() => {
    api
      .agents()
      .then((all) => {
        const withKnowledge = all.filter((a) => a.knowledge);
        setAgents(withKnowledge);
        if (withKnowledge.length) setAgentId(withKnowledge[0].id);
      })
      .catch((e: Error) => setError(e.message));
  }, []);

  useEffect(() => {
    if (!agentId) return;
    api
      .documents(agentId)
      .then(setDocuments)
      .catch((e: Error) => setError(e.message));
  }, [agentId]);

  const upload = async () => {
    if (!file) return;
    setUploading(true);
    setError(null);
    setInfo(null);
    try {
      const doc = await api.uploadDocument(agentId, file);
      setDocuments((list) => [doc, ...list]);
      setInfo(`"${doc.filename}" wurde in ${doc.chunkCount} Abschnitte zerlegt und indexiert.`);
      setFile(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setUploading(false);
    }
  };

  const remove = async (doc: KnowledgeDocument) => {
    if (!window.confirm(`"${doc.filename}" aus der Wissensbasis entfernen?`)) return;
    try {
      await api.deleteDocument(agentId, doc.id);
      setDocuments((list) => list.filter((d) => d.id !== doc.id));
    } catch (e) {
      setError((e as Error).message);
    }
  };

  return (
    <section className="section scroll-y" style={{ height: '100%' }}>
      <div className="container">
        <h1 className="title">Wissensbasis</h1>
        <p className="subtitle is-6">
          Dokumente werden pro Agent indexiert (PDF, DOCX, TXT, Markdown, HTML u.a.).
          {!user.isAdmin && ' Hochladen und Loeschen ist Administratoren vorbehalten.'}
        </p>

        {error && (
          <div className="notification is-danger is-light">
            <button className="delete" onClick={() => setError(null)} />
            {error}
          </div>
        )}
        {info && (
          <div className="notification is-success is-light">
            <button className="delete" onClick={() => setInfo(null)} />
            {info}
          </div>
        )}

        {agents.length === 0 ? (
          <div className="notification is-info is-light">
            Kein Agent hat eine Wissensbasis aktiviert (<code>rag: true</code> oder Tool <code>knowledge</code>).
          </div>
        ) : (
          <>
            <div className="field">
              <label className="label" htmlFor="knowledge-agent">
                Agent
              </label>
              <div className="control">
                <div className="select">
                  <select id="knowledge-agent" value={agentId} onChange={(e) => setAgentId(e.target.value)}>
                    {agents.map((a) => (
                      <option key={a.id} value={a.id}>
                        {a.name}
                      </option>
                    ))}
                  </select>
                </div>
              </div>
            </div>

            {user.isAdmin && (
              <div className="box">
                <div className="field is-grouped is-grouped-multiline">
                  <div className="control">
                    <div className={`file ${file ? 'has-name' : ''}`}>
                      <label className="file-label">
                        <input
                          className="file-input"
                          type="file"
                          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                        />
                        <span className="file-cta">
                          <span className="file-label">Datei waehlen …</span>
                        </span>
                        {file && <span className="file-name">{file.name}</span>}
                      </label>
                    </div>
                  </div>
                  <div className="control">
                    <button
                      className={`button is-primary ${uploading ? 'is-loading' : ''}`}
                      disabled={!file}
                      onClick={upload}
                    >
                      Hochladen
                    </button>
                  </div>
                </div>
              </div>
            )}

            <div className="table-container">
              <table className="table is-fullwidth is-striped is-hoverable">
                <thead>
                  <tr>
                    <th>Datei</th>
                    <th>Groesse</th>
                    <th>Abschnitte</th>
                    <th>Hochgeladen von</th>
                    <th>Datum</th>
                    {user.isAdmin && <th />}
                  </tr>
                </thead>
                <tbody>
                  {documents.length === 0 && (
                    <tr>
                      <td colSpan={user.isAdmin ? 6 : 5} className="has-text-grey">
                        Noch keine Dokumente.
                      </td>
                    </tr>
                  )}
                  {documents.map((d) => (
                    <tr key={d.id}>
                      <td>{d.filename}</td>
                      <td>{formatSize(d.sizeBytes)}</td>
                      <td>{d.chunkCount}</td>
                      <td>{d.uploadedBy}</td>
                      <td>{new Date(d.createdAt).toLocaleString('de-DE')}</td>
                      {user.isAdmin && (
                        <td className="has-text-right">
                          <button className="button is-small is-danger is-light" onClick={() => remove(d)}>
                            Loeschen
                          </button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </div>
    </section>
  );
}
