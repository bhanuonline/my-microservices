# Angle App

A Spring Boot application for algorithmic trading on Indian stock markets (Nifty).
Supports multiple brokers (Angel One, Upstox, Zerodha Kite), historical backtesting,
and pluggable trading strategies.

## Features

- Multi-broker abstraction (Angel One, Upstox, Kite)
- Historical candle data loading (CSV)
- Pluggable strategy interface
- Simple Moving Average (SMA) crossover strategy included
- Backtesting engine
- REST API for triggering analysis
- Spring Security (custom config)

## Quick Links

- [SETUP.md](SETUP.md) - How to run the app locally
- [CONFIGURATION.md](CONFIGURATION.md) - Application properties reference
- [API.md](API.md) - REST endpoint reference
- [ARCHITECTURE.md](ARCHITECTURE.md) - High-level design
- [BROKER-INTEGRATION.md](BROKER-INTEGRATION.md) - How brokers plug in
- [STRATEGY-GUIDE.md](STRATEGY-GUIDE.md) - Writing your own strategy
- [BACKTESTING.md](BACKTESTING.md) - Running backtests

## Tech Stack

- Java 17+
- Spring Boot
- Spring Security
- Lombok
- Maven

## Project Structure

```
angle-app/
├── src/main/java/com/angle/trading/
│   ├── backtest/         # Backtesting engine
│   ├── broker/           # Broker integrations (Angel, Upstox, Kite)
│   ├── config/           # Spring configuration
│   ├── controller/       # REST controllers
│   ├── indicator/        # Technical indicators (SMA)
│   ├── marketdata/       # Market data loaders/services
│   └── strategy/         # Trading strategies
└── src/main/resources/
    ├── application.properties
    └── nifty/            # Historical Nifty data
```
