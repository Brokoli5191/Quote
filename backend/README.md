# Quote Submission API

Cloudflare Worker and D1 moderation queue for quotes submitted by the Android app.
Submissions are stored as `pending`; this service does not publish them to the app's
quote database.

## API

`POST /api/submissions` accepts `application/json`:

```json
{
  "quoteText": "The obstacle is the way.",
  "author": "Marcus Aurelius",
  "category": "Stoicism",
  "tags": ["wisdom", "resilience"],
  "installationId": "123e4567-e89b-42d3-a456-426614174000",
  "appVersion": "1.3.2"
}
```

A successful request returns HTTP `201`:

```json
{
  "id": "b38b3171-2e9b-4f2d-b16d-baf7725db671",
  "status": "pending"
}
```

The endpoint rejects invalid fields, duplicate quote/author pairs, more than five
accepted submissions per installation in 24 hours, and more than twenty accepted
submissions per IP address in 24 hours. Installation IDs and IP addresses are
salted and hashed before storage.

`GET /health` returns the service status without accessing D1.

`GET /api/submissions/<id>/status` returns the moderation state when supplied
with the submitting app installation UUID in the `X-Installation-Id` header.
The server verifies the stored salted installation hash before returning data.

`GET /api/community/quotes?after=<revision>` returns approved community quote
changes only. Responses contain the next revision, an optional continuation flag,
active quote records, and IDs removed since the requested revision. Pending and
rejected submissions are never exposed by this endpoint.

## Local Development

Install dependencies:

```sh
npm install
```

Create `backend/.dev.vars` for local development. Do not commit this file:

```text
RATE_LIMIT_SALT=replace-with-a-long-random-value
```

Apply the D1 migration locally and start Wrangler:

```sh
npx wrangler d1 migrations apply quote-submissions --local
npm run dev
```

Run validation tests and type checking:

```sh
npm test
npm run check
```

## First Deployment

Authenticate Wrangler and create the production D1 database:

```sh
npx wrangler login
npx wrangler d1 create quote-submissions
```

Copy the returned `database_id` into `wrangler.jsonc`, replacing the existing
database ID when setting up a different Cloudflare account. Then configure a
private hashing salt, migrate the remote database, and deploy:

```sh
npx wrangler secret put RATE_LIMIT_SALT
npx wrangler d1 migrations apply quote-submissions --remote
npx wrangler deploy
```

To permit a future browser-based admin site to call the Worker, add its exact
origin as `ALLOWED_ORIGIN` under `vars` in `wrangler.jsonc`. Android requests do
not require CORS configuration.

## Moderation Dashboard

The dashboard is available at `https://quote.cowsay.win/admin` and is protected
by a Cloudflare Access public-hostname application matching
`quote.cowsay.win/admin*`. The Worker also verifies the Access JWT signature,
issuer, expiry, and application audience on every dashboard and admin API
request, so the `workers.dev` hostname cannot bypass Access.
