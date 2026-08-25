# Data Format

## Candle CSV

Location: `src/main/resources/nifty/nifty-daily-candles.csv`

Format:
```
date,open,high,low,close,volume
2026-01-01,22000.00,22150.00,21980.00,22100.00,1250000
2026-01-02,22100.00,22200.00,22050.00,22180.00,1180000
```

Columns:
| Column | Type | Notes |
|--------|------|-------|
| `date` | ISO date | `YYYY-MM-DD` |
| `open` | double | Opening price |
| `high` | double | Session high |
| `low` | double | Session low |
| `close` | double | Closing price |
| `volume` | long | Traded volume |

## Candle Java Model

```java
public class Candle {
    LocalDateTime timestamp;
    double open;
    double high;
    double low;
    double close;
    long volume;
}
```

## Interval Enum

```java
public enum Interval {
    ONE_MINUTE, FIVE_MINUTE, FIFTEEN_MINUTE, THIRTY_MINUTE,
    ONE_HOUR, ONE_DAY
}
```

## Quote Model

```java
public class Quote {
    String symbolToken;
    double lastPrice;
    double open, high, low, close;
    long volume;
    LocalDateTime timestamp;
}
```

## Broker-Specific DTOs

Under `broker/<name>/dto/`. These are the raw shapes returned by each broker's
API. They are converted to shared `Candle` / `Quote` in the broker client
before returning to callers.
