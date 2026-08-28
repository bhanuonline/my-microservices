# Setup

How to run angle-app locally.

## Prerequisites

- Java 17 or later
- Maven 3.8+
- Git

## Clone and Build

```bash
git clone <repo-url>
cd my-microservices/angle-app
./mvnw clean install
```

## Configure Environment

Export broker credentials (only for brokers you enable):

```bash
export ANGEL_API_KEY=your_key
export ANGEL_CLIENT_CODE=your_code
export ANGEL_PASSWORD=your_password
export ANGEL_TOTP_SECRET=your_totp_secret
```

For a first run with only backtesting on CSV data, you can skip broker env vars.

## Run

```bash
./mvnw spring-boot:run
```

App starts on `http://localhost:9010`.

## Verify

```bash
curl http://localhost:9010/api/analysis/backtest
```

You should see a `BacktestResult` JSON payload.

## IDE Setup

- Import as Maven project
- Enable Lombok annotation processing
- Set Java 17 as project SDK

## Troubleshooting

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
