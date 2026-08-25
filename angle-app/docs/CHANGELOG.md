# Changelog

All notable changes to this project.
Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Multi-broker abstraction: `BrokerClient` interface with Angel, Upstox, Kite implementations
- Angel One integration: `AngelClient`, `AngelAuthService`, `AngelHeaders`, `TotpGenerator`
- Market data layer: `MarketDataService`, `NiftyFileLoader`
- Strategy framework: `Strategy` interface, `Signal` enum
- `MovingAverageCrossover` strategy (SMA 20/50)
- `SimpleMovingAverage` indicator
- `Backtester` + `BacktestResult`
- `AnalysisController` with `/api/analysis/backtest` and `/api/analysis/candles`
- Config properties: `BrokerProperties`, `AnalysisProperties`
- `RestClientConfig` for broker HTTP calls
- `CorsConfig` for cross-origin requests
- Documentation set under `docs/`

### Changed
- `AngleAppApplication` now actually runs (previously only printed "hello")
- `application.properties` extended with broker + analysis config sections
- Debug logging enabled for `com.angle.trading`

### Security
- All broker credentials pulled from env vars — nothing hardcoded

## [0.1.0] - Initial

- Skeleton Spring Boot app
- `spring.application.name=angle-app`
- Basic security scaffolding
