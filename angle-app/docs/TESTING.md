# Testing

## Run All Tests

```bash
./mvnw test
```

## Test Layout

```
src/test/java/com/angle/trading/
├── backtest/         # Backtester unit tests
├── broker/           # Broker client tests (mocked HTTP)
├── indicator/        # SMA + indicator tests
├── strategy/         # Strategy evaluation tests
└── controller/       # MockMvc controller tests
```

## Unit Test Guidelines

- One test class per production class
- Name tests `should<Expected>When<Condition>()`
- No Spring context for pure logic — plain JUnit + Mockito
- Use `@DataJpaTest` / `@WebMvcTest` slices where Spring is needed

## Example: Indicator

```java
@Test
void shouldReturnAverageOfLastNCloses() {
    List<Candle> candles = candles(10, 20, 30, 40, 50);
    SimpleMovingAverage sma = new SimpleMovingAverage(3);
    assertEquals(40.0, sma.compute(candles, 4));
}
```

## Example: Strategy

```java
@Test
void shouldBuyOnGoldenCross() {
    List<Candle> candles = crossoverCandles();
    Signal s = strategy.evaluate(candles, candles.size() - 1);
    assertEquals(Signal.BUY, s);
}
```

## Example: Controller

```java
@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {
    @Autowired MockMvc mvc;
    @MockBean Backtester backtester;

    @Test
    void backtestEndpointReturns200() throws Exception {
        mvc.perform(get("/api/analysis/backtest"))
           .andExpect(status().isOk());
    }
}
```

## Integration Tests

- Use `@SpringBootTest` sparingly (slow)
- Mock broker HTTP calls with WireMock
- Never hit real broker APIs from tests

## Coverage

```bash
./mvnw jacoco:report
open target/site/jacoco/index.html
```

Aim for ≥ 70% on `strategy/`, `indicator/`, `backtest/` — these are pure logic
and easy to cover.
