# Auth API

The app is fully usable without signing in. Sign-in only unlocks account features
like friends, saving profiles, and persistent stats.

Base path: `/v1/auth`

## Register (email/password)

- Endpoint: `POST /v1/auth/register`
- JSON body:

```json
{
  "email": "alice@example.com",
  "username": "alice_123",
  "password": "at_least_12_chars"
}
```

- Validation rules:
  - `email` must be valid and is normalized to lowercase.
  - `username` must be 3-24 chars, `[A-Za-z0-9_]` only.
  - `password` must be at least 12 chars.
- Success: `201` with body:

```json
{
  "id": "user-id",
  "email": "alice@example.com",
  "username": "alice_123",
  "created_at": "2024-01-01T00:00:00Z"
}
```

- Side effect: sets an HTTP-only session cookie `mtg_session`.

## Login (email/password)

- Endpoint: `POST /v1/auth/login`
- JSON body:

```json
{
  "email": "alice@example.com",
  "password": "at_least_12_chars"
}
```

- Validation rules:
  - `email` must be valid (lowercased).
  - `password` must be non-empty.
- Rate limit: `429` if too many attempts (keyed by IP and email).
- Success: `200` with the same user JSON as register.
- Side effect: sets `mtg_session` cookie.

## Current user

- Endpoint: `GET /v1/users/me`
- Requires session cookie.
- Success: `200` with the same user JSON as register/login.

## Logout

- Endpoint: `POST /v1/auth/logout`
- Requires session cookie.
- Response: `204 No Content`, clears `mtg_session`.

## Optional external providers

- `POST /v1/auth/google` with `{ "id_token": "..." }`
- `POST /v1/auth/apple` with `{ "id_token": "..." }`
- Success: `200` with the same user JSON as register/login.

## Session cookie details

- Name: `mtg_session`
- Attributes: `HttpOnly`, `SameSite=Lax`, `Path=/`, `Max-Age` and `Expires` based
  on `APP_SESSION_TTL` (default 30 days), `Secure` if `APP_PUBLIC_URL` is https
  or `APP_ENV=prod`.
- The cookie value is HMAC-signed when `APP_COOKIE_SECRET` is set.
- Clients must send cookies on API requests (for `fetch`: `credentials: "include"`).
- Note: `SameSite=Lax` means cross-site XHR/fetch won't include the cookie. Host
  the frontend and API on the same site or use a reverse proxy if you need cookie
  auth in a SPA.

## Common error responses

- `400` with `{ "error": { "code": "validation_error" } }` for invalid input.
- `401` with `invalid_credentials` for bad login.
- `403` with `user_disabled` or `forbidden` when applicable.
- `409` with `email_taken` or `username_taken` on register.
- `429` with `rate_limited` when too many attempts are detected.

## Client fetch flows

All requests that rely on the session cookie must include `credentials: "include"`.
Treat `401` as signed out, and keep the app usable in that state.

```js
const API_BASE = "https://api.example.com";
const CURRENT_USER_PATH = "/v1/users/me";

async function signup({ email, username, password }) {
  const res = await fetch(`${API_BASE}/v1/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ email, username, password }),
  });

  if (!res.ok) {
    throw await res.json();
  }

  return res.json();
}

async function signin({ email, password }) {
  const res = await fetch(`${API_BASE}/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ email, password }),
  });

  if (!res.ok) {
    throw await res.json();
  }

  return res.json();
}

async function signout() {
  const res = await fetch(`${API_BASE}/v1/auth/logout`, {
    method: "POST",
    credentials: "include",
  });

  if (!res.ok) {
    throw await res.json();
  }
}

async function getCurrentUser() {
  const res = await fetch(`${API_BASE}${CURRENT_USER_PATH}`, {
    method: "GET",
    credentials: "include",
  });

  if (res.status === 401) {
    return null;
  }

  if (!res.ok) {
    throw await res.json();
  }

  return res.json();
}
```
