# Strategy Guide

How to write a trading strategy.

## Strategy Interface

```java
public interface Strategy {
    String getName();
    Signal evaluate(List<Candle> candles, int currentIndex);
}
```

- `getName()` — unique name; matches `analysis.strategy.default`
- `evaluate()` — given all candles + current index, return `BUY`, `SELL`, or `HOLD`

## Signal Enum

```java
public enum Signal { BUY, SELL, HOLD }
```

## Built-in Strategy: Moving Average Crossover

`strategy/impl/MovingAverageCrossover.java`

- Uses two SMAs: short (default 20) and long (default 50)
- **BUY** when short SMA crosses above long SMA
- **SELL** when short SMA crosses below long SMA
- **HOLD** otherwise

Config:
```properties
analysis.strategy.sma.short-period=20
analysis.strategy.sma.long-period=50
```

## Writing Your Own Strategy

1. Create a class under `strategy/impl/`
2. Implement `Strategy`
3. Annotate `@Component`
4. Return a unique `getName()` (e.g. `"rsi-mean-reversion"`)
5. Set `analysis.strategy.default=rsi-mean-reversion` to use it

Example:

```java
@Component
public class RsiMeanReversion implements Strategy {
    @Override public String getName() { return "rsi-mean-reversion"; }

    @Override public Signal evaluate(List<Candle> candles, int i) {
        double rsi = computeRsi(candles, i, 14);
        if (rsi < 30) return Signal.BUY;
        if (rsi > 70) return Signal.SELL;
        return Signal.HOLD;
    }
}
```

## Using Indicators

Indicators live under `indicator/`. Reuse `SimpleMovingAverage` or add new ones:

```java
public interface Indicator {
    double compute(List<Candle> candles, int index);
}
```

Strategies should compose indicators rather than reimplementing math.
