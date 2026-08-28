# Environment Variables

All secrets are pulled from environment variables. Nothing in
`application.properties` should contain a real credential.

## Angel One

| Variable | Required if `broker.angel.enabled=true` | Purpose |
|----------|----------------------------------------|---------|
| `ANGEL_API_KEY` | yes | SmartAPI key from Angel One portal |
| `ANGEL_CLIENT_CODE` | yes | Your Angel client code |
| `ANGEL_PASSWORD` | yes | Angel password or MPIN |
| `ANGEL_TOTP_SECRET` | yes | Base32 TOTP seed from Angel 2FA setup |

## Upstox

| Variable | Required if `broker.upstox.enabled=true` | Purpose |
|----------|----------------------------------------|---------|
| `UPSTOX_API_KEY` | yes | API key |
| `UPSTOX_API_SECRET` | yes | API secret |
| `UPSTOX_REDIRECT_URI` | yes | OAuth callback URI |

## Kite (Zerodha)

| Variable | Required if `broker.kite.enabled=true` | Purpose |
|----------|----------------------------------------|---------|
| `KITE_API_KEY` | yes | API key |
| `KITE_API_SECRET` | yes | API secret |

## Example: Local Setup

Create a `.env` file (never commit it):

```bash
export ANGEL_API_KEY=xxxxxxxx
export ANGEL_CLIENT_CODE=A123456
export ANGEL_PASSWORD=your-password
export ANGEL_TOTP_SECRET=ABCDEF1234567890
```

Load it before running:

```bash
source .env
./mvnw spring-boot:run
```

## Example: Production (systemd)

```ini
[Service]
Environment="ANGEL_API_KEY=xxxx"
Environment="ANGEL_CLIENT_CODE=A123456"
EnvironmentFile=/etc/angle-app/secrets.env
```

Restrict `secrets.env` to `root:root 0600`.

## Example: Docker

```bash
docker run \
  -e ANGEL_API_KEY=xxxx \
  -e ANGEL_CLIENT_CODE=A123456 \
  -e ANGEL_PASSWORD=... \
  -e ANGEL_TOTP_SECRET=... \
  angle-app
```

Or use `--env-file .env`.

## Verifying

If a required env var is missing, the corresponding property resolves to
empty string. The broker will fail on first API call with an auth error.
Check logs — they should mention which credential was rejected.
