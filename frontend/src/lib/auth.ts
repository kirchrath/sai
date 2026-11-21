// OIDC Authorization Code Flow mit PKCE gegen Keycloak – ohne zusaetzliche Bibliothek.
import { sha256 } from './sha256';

const AUTHORITY = import.meta.env.VITE_OIDC_AUTHORITY ?? `${window.location.origin}/auth/realms/sai`;
const CLIENT_ID = import.meta.env.VITE_OIDC_CLIENT_ID ?? 'sai-frontend';
const ENDPOINTS = {
  authorize: `${AUTHORITY}/protocol/openid-connect/auth`,
  token: `${AUTHORITY}/protocol/openid-connect/token`,
  logout: `${AUTHORITY}/protocol/openid-connect/logout`,
};
const STORAGE_KEY = 'sai.tokens';
const PKCE_KEY = 'sai.pkce';

interface TokenSet {
  accessToken: string;
  refreshToken?: string;
  idToken?: string;
  expiresAt: number;
}

export interface User {
  id: string;
  username: string;
  name: string;
  roles: string[];
  isAdmin: boolean;
}

const redirectUri = () => `${window.location.origin}/`;

function base64Url(bytes: Uint8Array): string {
  let binary = '';
  bytes.forEach((b) => (binary += String.fromCharCode(b)));
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function randomString(length = 48): string {
  return base64Url(crypto.getRandomValues(new Uint8Array(length)));
}

async function challengeFor(verifier: string): Promise<string> {
  const data = new TextEncoder().encode(verifier);
  if (window.isSecureContext && crypto.subtle) {
    return base64Url(new Uint8Array(await crypto.subtle.digest('SHA-256', data)));
  }
  return base64Url(sha256(data));
}

function decodeJwt(token: string): Record<string, unknown> {
  const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
  const json = decodeURIComponent(
    atob(payload)
      .split('')
      .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
      .join(''),
  );
  return JSON.parse(json);
}

function load(): TokenSet | null {
  const raw = sessionStorage.getItem(STORAGE_KEY);
  return raw ? (JSON.parse(raw) as TokenSet) : null;
}

function store(response: Record<string, unknown>): TokenSet {
  const tokens: TokenSet = {
    accessToken: response.access_token as string,
    refreshToken: response.refresh_token as string | undefined,
    idToken: (response.id_token as string | undefined) ?? load()?.idToken,
    expiresAt: Date.now() + Number(response.expires_in ?? 60) * 1000,
  };
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
  return tokens;
}

async function tokenRequest(params: Record<string, string>): Promise<TokenSet> {
  const response = await fetch(ENDPOINTS.token, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: CLIENT_ID, ...params }),
  });
  if (!response.ok) {
    throw new Error(`Token-Anfrage fehlgeschlagen (${response.status})`);
  }
  return store(await response.json());
}

export async function login(): Promise<never> {
  const verifier = randomString();
  const state = randomString(16);
  sessionStorage.setItem(PKCE_KEY, JSON.stringify({ verifier, state }));
  const params = new URLSearchParams({
    client_id: CLIENT_ID,
    response_type: 'code',
    scope: 'openid profile email',
    redirect_uri: redirectUri(),
    state,
    code_challenge: await challengeFor(verifier),
    code_challenge_method: 'S256',
  });
  window.location.assign(`${ENDPOINTS.authorize}?${params}`);
  return new Promise<never>(() => {});
}

/** Verarbeitet den Redirect von Keycloak. Liefert true, wenn ein gueltiges Token vorliegt. */
export async function initAuth(): Promise<boolean> {
  const url = new URL(window.location.href);
  const code = url.searchParams.get('code');
  if (code) {
    const pending = JSON.parse(sessionStorage.getItem(PKCE_KEY) ?? 'null') as {
      verifier: string;
      state: string;
    } | null;
    sessionStorage.removeItem(PKCE_KEY);
    window.history.replaceState({}, '', redirectUri());
    if (!pending || pending.state !== url.searchParams.get('state')) {
      throw new Error('Ungueltiger Login-Status, bitte erneut anmelden');
    }
    await tokenRequest({
      grant_type: 'authorization_code',
      code,
      redirect_uri: redirectUri(),
      code_verifier: pending.verifier,
    });
    return true;
  }
  if (url.searchParams.get('error')) {
    window.history.replaceState({}, '', redirectUri());
    throw new Error(url.searchParams.get('error_description') ?? 'Anmeldung fehlgeschlagen');
  }
  try {
    return (await getAccessToken()) !== null;
  } catch {
    return false;
  }
}

let refreshing: Promise<TokenSet> | null = null;

/** Liefert ein gueltiges Access-Token und erneuert es bei Bedarf. */
export async function getAccessToken(): Promise<string | null> {
  const tokens = load();
  if (!tokens) return null;
  if (tokens.expiresAt - Date.now() > 30_000) return tokens.accessToken;
  if (!tokens.refreshToken) return null;
  refreshing ??= tokenRequest({ grant_type: 'refresh_token', refresh_token: tokens.refreshToken }).finally(
    () => (refreshing = null),
  );
  try {
    return (await refreshing).accessToken;
  } catch {
    sessionStorage.removeItem(STORAGE_KEY);
    return null;
  }
}

export function currentUser(): User | null {
  const tokens = load();
  if (!tokens) return null;
  const claims = decodeJwt(tokens.accessToken);
  const roles = ((claims.realm_access as { roles?: string[] } | undefined)?.roles ?? []).slice();
  const username = (claims.preferred_username as string) ?? (claims.sub as string);
  return {
    id: claims.sub as string,
    username,
    name: (claims.name as string) ?? username,
    roles,
    isAdmin: roles.includes('admin'),
  };
}

export function logout(): void {
  const tokens = load();
  sessionStorage.removeItem(STORAGE_KEY);
  const params = new URLSearchParams({ client_id: CLIENT_ID, post_logout_redirect_uri: redirectUri() });
  if (tokens?.idToken) params.set('id_token_hint', tokens.idToken);
  window.location.assign(`${ENDPOINTS.logout}?${params}`);
}
