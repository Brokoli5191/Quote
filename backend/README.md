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
  "appVersion": "2.0.0",
  "language": "en"
}
```

`text` is accepted as an alias for `quoteText`. `language` is `en` or `de` and
defaults to `en` when missing. Invisible and bidirectional control characters are
removed from every text field before validation and duplicate detection.

A new submission returns HTTP `201`:

```json
{
  "id": "b38b3171-2e9b-4f2d-b16d-baf7725db671",
  "status": "pending"
}
```

Submitting the same quote/author pair again from the same installation returns
HTTP `200` with the existing submission, so a retry after a lost response is safe:

```json
{
  "id": "b38b3171-2e9b-4f2d-b16d-baf7725db671",
  "status": "pending",
  "reviewNote": null,
  "communityQuoteId": null,
  "duplicate": true
}
```

Every error response has the shape `{"error": "...", "code": "..."}`. Codes:
`invalid_request`, `validation`, `too_large`, `rate_limited`, `duplicate`,
`already_reviewed`, `not_found`, `method_not_allowed`, `forbidden`, `server_error`.

The endpoint rejects invalid fields, bodies over 16 KiB (also without
`Content-Length`), quotes already submitted by another installation, quotes that
match an active community quote (letters and digits only, ignoring case and
punctuation), texts a moderator deleted permanently, more than five submissions per
installation in 24 hours, and more than twenty submissions per IP address (IPv6:
per /64 prefix) in 24 hours. The limits are checked atomically with the insert.
Installation IDs and IP addresses are salted and hashed before storage.

`GET /health` returns the service status without accessing D1.

`GET /api/submissions/<id>/status` returns the moderation state when supplied
with the submitting app installation UUID in the `X-Installation-Id` header:

```json
{
  "id": "b38b3171-2e9b-4f2d-b16d-baf7725db671",
  "status": "approved",
  "reviewNote": "Thanks for the submission!",
  "reviewedAt": 1793188100,
  "communityQuoteId": "96c8992d-55a3-4ae6-905a-3bdabf054b26"
}
```

`status` is `pending`, `approved`, `rejected`, or `removed` (approved, but the
community quote has since been unpublished). Withdrawn or deleted submissions
return `404`.

`DELETE /api/submissions/<id>` with the same `X-Installation-Id` header withdraws
a pending submission (`204`). It returns `404` for unknown submissions or another
installation's submission, and `409` with code `already_reviewed` once reviewed.

`GET /api/community/quotes?after=<revision>` returns approved community quote
changes only. Responses contain the next revision, an optional continuation flag,
active quote records, and IDs removed since the requested revision. Each quote has
`language` (`en` or `de`), `translation` (the moderator's translation into the
other language, or `null`), and `submissionId`. Pending and rejected submissions
are never exposed by this endpoint.

A daily cron trigger (03:17 UTC) erases IP hashes from submissions older than 30
days and fills in missing community quote comparison keys.

## Local Development

Install dependencies:

```sh
npm install
```

Create `backend/.dev.vars` for local development. Do not commit this file:

```text
RATE_LIMIT_SALT=replace-with-a-long-random-value
```

Apply the D1 migrations locally and start Wrangler:

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

Later deployments run automatically from `.github/workflows/deploy-backend.yml`
on every push to `main` that changes `backend/`. The workflow needs two GitHub
repository secrets (Settings > Secrets and variables > Actions):

- `CLOUDFLARE_ACCOUNT_ID`: shown in the Cloudflare dashboard on the account home
  page and on Workers & Pages > Overview (account details panel, "Account ID").
- `CLOUDFLARE_API_TOKEN`: create it under My Profile > API Tokens > Create Token
  with the "Edit Cloudflare Workers" template, add the permission
  Account > D1 > Edit, and limit it to this account and the `cowsay.win` zone.

`RATE_LIMIT_SALT` stays a Worker secret in Cloudflare and is kept across deploys.

To permit a future browser-based admin site to call the Worker, add its exact
origin as `ALLOWED_ORIGIN` under `vars` in `wrangler.jsonc`. Android requests do
not require CORS configuration.

## Moderation Dashboard

The dashboard is available at `https://quote.cowsay.win/admin` and is protected
by a Cloudflare Access public-hostname application matching
`quote.cowsay.win/admin*`. The Worker also verifies the Access JWT signature,
issuer, expiry, and application audience on every dashboard and admin API
request, so the `workers.dev` hostname cannot bypass Access.
