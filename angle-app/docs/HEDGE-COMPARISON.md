# Hedge Strategy Comparison

> Package: `com.angle.trading.hedge`
> Feature status: Implemented

---

## Table of Contents

1. [Overview](#overview)
2. [Strategies Explained](#strategies-explained)
3. [How the Scoring Works](#how-the-scoring-works)
4. [P&L Math](#pl-math)
5. [Indicators & Regime Detection](#indicators--regime-detection)
6. [Live Data Integration](#live-data-integration)
7. [API Reference](#api-reference)
8. [UI Walkthrough](#ui-walkthrough)
9. [Technical Notes](#technical-notes)

---

## Overview

The Hedge Comparison feature lets a trader enter the details of an existing (or planned) stock position and immediately see four hedging strategies compared side-by-side:

- Which strategy fits the current market regime best (scored 1–5)
- Full P&L metrics: breakeven, max gain/loss, risk-reward ratio
- Payoff diagram across 25 price points
- Time-decay impact at four points in the option's life
- P&L scenarios at seven price levels from -20 % to +20 %
- A recommendation (highest regime-fit score wins)
- HV30 (30-day historical volatility) and a PUT-premium assessment (CHEAP / FAIR / EXPENSIVE)

All option premiums, expiry dates, and live prices come from the Angel One scrip master — no manual price entry is required (though fields are editable).

```
User fills stock position
        |
        v
App fetches live LTP, expiry dates, strike list, premiums
        |
        v
Indicators computed: RSI(14), ADX(14), EMA-21
        |
        v
Regime detected: STRONG_BULLISH / MILD_BULLISH / SIDEWAYS /
                 MILD_BEARISH / STRONG_BEARISH
        |
        v
Each of the 4 strategies scored + all metrics computed
        |
        v
Results page: payoff diagram, comparison table, time-decay table
```

---

## Strategies Explained

### 1. Protective PUT / Married PUT

```
Position:  Long Stock  +  Long PUT

Payoff at expiry
  ^
  |      /
  |     /
  |----/ <-- strike (PUT floor)
  |   |
  +---+---------> stock price
```

- **Buy the stock** (or already hold it) and **buy a PUT** at a chosen strike.
- If the stock falls below the PUT strike the PUT gains in value, capping the downside.
- Upside is unlimited; only the PUT premium reduces the effective gain.
- **Married PUT** = same structure; the name just means the PUT is bought on the same day as the stock.
- Net cost = stock price + PUT premium (debit trade).

### 2. Covered Call

```
Position:  Long Stock  +  Short CALL

Payoff at expiry
  ^
  |    /----  <-- capped at call strike
  |   /
  |  /
  | /
  +-----------> stock price
```

- **Hold the stock** and **sell a CALL** at a strike above entry.
- Earns premium upfront (credit), which lowers the effective cost basis.
- If the stock rises above the call strike the position gains no more — shares are called away (or the call must be bought back at a loss).
- Downside is the same as holding the stock, offset only by the premium received.

### 3. Collar

```
Position:  Long Stock  +  Long PUT  +  Short CALL

Payoff at expiry
  ^
  |       /----  <-- capped at call strike
  |      /
  |-----/        <-- floor at PUT strike
  |
  +-----------> stock price
```

- **Hold the stock** + **buy a PUT** + **sell a CALL**.
- The PUT premium cost is largely (or fully) offset by the CALL premium received.
- When `callPremium ≈ putPremium` the net cost is near zero (zero-cost collar).
- Both upside and downside are bounded — useful when the trader wants protection without paying out-of-pocket.
- The UI provides a "Zero-Cost Collar" quick-pick that estimates the symmetric OTM call strike: `2 × entryPrice − putStrike`.

### 4. Futures Hedge

```
Position:  Long Stock  +  Short Future

Net P&L
  ^
  |------------  <-- flat (locked in)
  |
  +-----------> stock price
```

- **Hold the stock** and **sell a futures contract** on the same underlying.
- Gains and losses from price movement cancel out almost completely.
- The only remaining exposure is the **cost-of-carry** (futures price vs spot).
- Best used when a trader wants to freeze portfolio value through a known event without liquidating.

---

## How the Scoring Works

Each strategy receives a fit score from 1 (poor fit) to 5 (ideal fit) for each detected market regime.

### Regime Definitions

| Regime | ADX | RSI | Price vs EMA-21 |
|---|---|---|---|
| STRONG_BULLISH | > 25 | 50 – 65 | Above |
| MILD_BULLISH | 15 – 25 | 50 – 65 | Above |
| SIDEWAYS | < 20 | 40 – 60 | Near |
| MILD_BEARISH | 15 – 25 | 35 – 50 | Below |
| STRONG_BEARISH | > 25 | < 35 | Below |

### Score Table

| Regime | Protective PUT | Covered Call | Collar | Futures Hedge |
|---|:---:|:---:|:---:|:---:|
| STRONG_BULLISH | 5 | 4 | 3 | 1 |
| MILD_BULLISH | 4 | 5 | 4 | 2 |
| SIDEWAYS | 2 | 5 | 4 | 3 |
| MILD_BEARISH | 3 | 2 | 5 | 4 |
| STRONG_BEARISH | 2 | 1 | 4 | 5 |

**Reading the table:**
- A score of 5 means the strategy is the ideal choice for that regime.
- A score of 1 means the strategy actively works against you in that regime.
- Example: in STRONG_BULLISH you expect the stock to keep rising, so a Protective PUT (score 5) makes sense — you keep unlimited upside while the PUT acts as insurance. A Futures Hedge (score 1) kills all the upside you were expecting, so it is the worst fit.

The strategy with the highest score for the detected regime is returned as `recommended`.

---

## P&L Math

All calculations are per-share unless stated otherwise. `qty` = number of shares.

### Net Premium

| Strategy | netPremiumPerShare |
|---|---|
| Protective PUT | `−putPremium` (debit) |
| Covered Call | `+callPremium` (credit) |
| Collar | `callPremium − putPremium` |
| Futures Hedge | `0` (no option premium) |

`totalPremiumCost = netPremiumPerShare × qty`

Negative value = you paid cash out. Positive value = you received cash.

### Breakeven

| Strategy | Breakeven price |
|---|---|
| Protective PUT | `entryPrice + putPremium` |
| Covered Call | `entryPrice − callPremium` |
| Collar | `entryPrice − callPremium + putPremium` |
| Futures Hedge | `entryPrice` (locked) |

`breakevenDistancePct = (breakeven − entryPrice) / entryPrice × 100`

### Max Gain & Max Loss

| Strategy | Max Gain | Max Loss |
|---|---|---|
| Protective PUT | Unlimited | `(entryPrice − putStrike + putPremium) × qty` |
| Covered Call | `(callStrike − entryPrice + callPremium) × qty` | `(entryPrice − callPremium) × qty` |
| Collar | `(callStrike − entryPrice + callPremium − putPremium) × qty` | `(entryPrice − putStrike − callPremium + putPremium) × qty` |
| Futures Hedge | Cost-of-carry locked in | Cost-of-carry locked in |

When Max Gain is unlimited the API returns `null` for `maxGain` and the UI displays "∞".

### Risk-Reward Ratio

```
riskRewardRatio = maxGain / maxLoss   (formatted as "X.X : 1")
```

When `maxGain` is null (unlimited), the ratio is displayed as `"∞ : 1"`.

### Daily Theta (Linear Approximation)

```
dailyTheta = netPremiumPerShare / daysToExpiry   (₹ per share per day)
```

Positive theta = time decay earns you money (credit spread). Negative theta = time decay costs you money (debit spread).

### Time Decay Table

The app shows the estimated P&L of the position at four points in time, assuming the stock stays at the entry price. The theta model uses a square-root-of-time decay:

```
remainingValue(t) = netPremium × sqrt(daysRemaining / daysToExpiry)
```

| Time Point | Days Remaining |
|---|---|
| Today | daysToExpiry |
| 2/3 through | daysToExpiry × (1/3) |
| 1/3 through | daysToExpiry × (2/3) |
| At expiry | 0 |

### P&L Scenarios

Seven fixed price offsets are evaluated:

```
-20%, -15%, -10%, -5%, 0%, +5%, +10%, +15%, +20%
```

`pnlAtPrice(S) = (S − entryPrice) × qty + optionPayoff(S) × qty + netPremiumCost`

`optionPayoff` depends on the strategy:
- Protective PUT: `max(putStrike − S, 0)`
- Covered Call: `−max(S − callStrike, 0)`
- Collar: `max(putStrike − S, 0) − max(S − callStrike, 0)`
- Futures Hedge: `futurePrice − S` (short future offsets long stock)

### Payoff Diagram

25 evenly-spaced price points from `entryPrice × 0.70` to `entryPrice × 1.30` are computed using the same formula above. These feed `chartScenarios` in the response, which the UI renders with Chart.js.

### Probability of Profit

Estimated from historical rolling returns over the same holding period as `daysToExpiry`. Returns the fraction of historical windows where the net P&L was positive for that strategy.

### Estimated Days to Breakeven

Random-walk approximation using HV30:

```
dailyMove = entryPrice × (HV30 / sqrt(252))
estimatedDaysToBreakeven = (breakevenDistance / dailyMove) ^ 2
```

---

## Indicators & Regime Detection

Indicators are computed from the last 30+ daily candles fetched from the local candle store.

### RSI (14)

Standard Wilder RSI using 14-period average gain/loss.

```
RS  = avgGain / avgLoss
RSI = 100 − (100 / (1 + RS))
```

### ADX (14)

Average Directional Index. Measures trend strength (not direction):
- ADX > 25: strong trend
- ADX 15–25: moderate trend
- ADX < 15: no clear trend / ranging

```
+DM, -DM computed from high/low
TR  = max(high-low, |high-prevClose|, |low-prevClose|)
ATR14 = Wilder smoothing of TR
+DI = 100 × EMA14(+DM) / ATR14
-DI = 100 × EMA14(-DM) / ATR14
DX  = 100 × |+DI - -DI| / (+DI + -DI)
ADX = Wilder smoothing of DX
```

### EMA-21

21-period exponential moving average of close prices. Used to confirm whether price is above or below the medium-term trend.

### IndicatorSnapshot (returned in response)

```json
{
  "rsi": 58.4,
  "adx": 27.1,
  "ema21": 685.30,
  "rsiScore": 4,
  "adxScore": 5,
  "ema21Score": 4,
  "regime": "STRONG_BULLISH"
}
```

Individual scores (1–5) reflect how clearly each indicator supports the detected regime.

### HV30 and PUT Premium Assessment

HV30 is computed from the last 30 daily closes:

```
logReturn_i = ln(close_i / close_{i-1})
variance    = sum((logReturn_i - mean)^2) / (n - 1)
HV30        = sqrt(variance × 252)   (annualised)
```

The PUT premium is then assessed against its Black-Scholes theoretical value:

| Assessment | Meaning |
|---|---|
| CHEAP | Market premium < BS value — good time to buy PUT |
| FAIR | Market premium ≈ BS value |
| EXPENSIVE | Market premium > BS value — buying insurance is costly |

---

## Live Data Integration

All live data comes from the Angel One scrip master and SmartAPI.

### Flow

```
Instrument selected in UI
        |
        v
GET /hedge/ltp/{symbolToken}           -- live spot price
GET /hedge/expiries?underlying=...     -- next 3 NSE monthly expiries
        |
        v
Expiry chosen in UI
        |
        v
GET /hedge/strikes?underlying=...&expiry=...   -- OTM/ATM strikes
        |
        v
Strikes chosen; premiums auto-fetched
        |
        v
GET /hedge/option-premium?underlying=...&strike=...&type=PE&expiry=...
GET /hedge/option-premium?underlying=...&strike=...&type=CE&expiry=...
GET /hedge/futures-price?underlying=...
```

### Expiry Date Logic

Expiry dates are the **last Thursday of each calendar month** (NSE standard). The app computes the next three such dates from today's date. If the current month's last Thursday has already passed, it skips to next month.

### INDEX_MAP

When the user selects an index from the display name dropdown, the app maps it to the scrip master name used in symbol construction:

| Display Name | Scrip Master Name |
|---|---|
| Nifty 50 | NIFTY |
| Bank Nifty | BANKNIFTY |
| Nifty Fin Service | FINNIFTY |
| Midcap Nifty | MIDCPNIFTY |
| SENSEX | SENSEX |
| BANKEX | BANKEX |

---

## API Reference

### POST /hedge/compare

Compare all four hedging strategies for a given position.

**Request body (JSON):**

```json
{
  "symbolToken": "1333",
  "entryPrice": 680.00,
  "quantity": 550,
  "putStrike": 660,
  "putPremium": 8.50,
  "callStrike": 700,
  "callPremium": 9.20,
  "futurePrice": 682.30,
  "daysToExpiry": 28
}
```

| Field | Type | Required | Notes |
|---|---|---|---|
| symbolToken | String | Yes | Angel One token for the underlying |
| entryPrice | Double | Yes | Stock entry / current price (₹) |
| quantity | Integer | Yes | Number of shares |
| putStrike | Double | Yes | Strike price of the PUT leg |
| putPremium | Double | Yes | Premium paid per share for PUT |
| callStrike | Double | Yes | Strike price of the CALL leg |
| callPremium | Double | Yes | Premium received per share for CALL |
| futurePrice | Double | Yes | Current futures price |
| daysToExpiry | Integer | No | Defaults to 30 if null |

**Response (HedgeComparisonResult):**

```json
{
  "indicatorSnapshot": {
    "rsi": 58.4,
    "adx": 27.1,
    "ema21": 685.30,
    "rsiScore": 4,
    "adxScore": 5,
    "ema21Score": 4,
    "regime": "STRONG_BULLISH"
  },
  "analyses": [
    {
      "strategyType": "PROTECTIVE_PUT",
      "regimeFitScore": 5,
      "netPremiumPerShare": -8.50,
      "totalPremiumCost": -4675.00,
      "breakeven": 688.50,
      "breakevenDistancePct": 1.25,
      "maxGain": null,
      "maxLoss": -11275.00,
      "riskRewardRatio": "∞ : 1",
      "probabilityOfProfit": 0.62,
      "estimatedDaysToBreakeven": 4,
      "dailyTheta": -303.57,
      "scenarios": [ ... ],
      "chartScenarios": [ ... ],
      "timeDecay": [ ... ]
    },
    ...
  ],
  "recommended": "PROTECTIVE_PUT",
  "hv30": 0.2134,
  "hv30Assessment": "FAIR",
  "expiryDates": ["2026-10-30", "2026-11-27", "2026-12-31"],
  "zeroCostCallStrike": 700.00,
  "daysToExpiry": 28
}
```

**Example curl:**

```bash
curl -s -X POST http://localhost:9010/hedge/compare \
  -H "Content-Type: application/json" \
  -u admin:admin123 \
  -d '{
    "symbolToken": "1333",
    "entryPrice": 680.00,
    "quantity": 550,
    "putStrike": 660,
    "putPremium": 8.50,
    "callStrike": 700,
    "callPremium": 9.20,
    "futurePrice": 682.30,
    "daysToExpiry": 28
  }' | jq .
```

---

### GET /hedge/ltp/{symbolToken}

Fetch the live last-traded price for a stock.

```bash
curl -s http://localhost:9010/hedge/ltp/1333 -u admin:admin123
```

Returns a plain double (e.g. `680.50`).

---

### GET /hedge/expiries

Get the next three NSE monthly expiry dates for an underlying.

```bash
curl -s "http://localhost:9010/hedge/expiries?underlying=HDFCBANK" -u admin:admin123
```

Returns a JSON array of ISO dates:

```json
["2026-10-30", "2026-11-27", "2026-12-31"]
```

---

### GET /hedge/strikes

Get the list of available strikes from the option chain for a given underlying and expiry.

```bash
curl -s "http://localhost:9010/hedge/strikes?underlying=HDFCBANK&expiry=2026-10-30" \
  -u admin:admin123
```

Returns a sorted array of strike prices as doubles.

---

### GET /hedge/option-premium

Fetch the live LTP for a specific option contract.

```bash
curl -s "http://localhost:9010/hedge/option-premium?underlying=HDFCBANK&strike=660&type=PE&expiry=2026-10-30" \
  -u admin:admin123
```

Returns a plain double (premium per share, e.g. `8.50`).

---

### GET /hedge/futures-price

Fetch the live price of the near-month futures contract.

```bash
curl -s "http://localhost:9010/hedge/futures-price?underlying=HDFCBANK" -u admin:admin123
```

Returns a plain double.

---

### POST /hedge/journal

Save the chosen hedge strategy to the `trade_journal` table.

```bash
curl -s -X POST http://localhost:9010/hedge/journal \
  -H "Content-Type: application/json" \
  -u admin:admin123 \
  -d '{
    "symbolToken": "1333",
    "strategyType": "COLLAR",
    "entryPrice": 680.00,
    "quantity": 550,
    "putStrike": 660,
    "putPremium": 8.50,
    "callStrike": 700,
    "callPremium": 9.20,
    "daysToExpiry": 28
  }'
```

---

### GET /hedge

Loads the Hedge Comparison UI page (Thymeleaf template).

```bash
# Open in browser:
http://localhost:9010/hedge
```

---

## UI Walkthrough

The page is divided into three logical steps followed by a results area.

### Step 1 — Stock Position

```
+--------------------------------------------+
| Underlying    [HDFCBANK dropdown]          |
| Symbol Token  [auto-filled]                |
| Entry Price   [₹ 680.00]  [Fetch LTP]     |
| Quantity      [550]                        |
+--------------------------------------------+
```

- The dropdown lists all instruments loaded from the Angel One scrip master.
- On selection, the symbol token is auto-filled and the expiry dates are fetched automatically.
- The "Fetch LTP" button calls `GET /hedge/ltp/{symbolToken}` and fills the entry price.

### Step 2 — Option Legs

```
+--------------------+  +--------------------+
|  PUT Leg           |  |  CALL Leg          |
|  Expiry  [dd/mm]   |  |  Expiry  [dd/mm]   |
|  Strike  [660 v]   |  |  Strike  [700 v]   |
|  Premium [₹ 8.50]  |  |  Premium [₹ 9.20]  |
|  [ATM] [3%OTM][5%] |  |  [ATM] [3%OTM][5%] |
+--------------------+  +--------------------+

  [₹0 Collar]   [⚡ Refresh All Prices]
```

- Expiry dropdowns show the three NSE monthly expiries fetched in Step 1.
- On expiry selection, strike dropdowns populate from `GET /hedge/strikes`.
- **ATM / 3% OTM / 5% OTM chips** instantly set the strike to the nearest available value.
- **₹0 Collar** button computes `zeroCostCallStrike = 2 × entryPrice − putStrike` and sets the call strike.
- **Refresh All Prices** fires three parallel requests (PUT premium, CALL premium, spot LTP) and fills all premium fields simultaneously.

### Step 3 — Futures Leg

```
+--------------------------------------------+
| Futures Price  [₹ 682.30]  [Fetch Future] |
+--------------------------------------------+
```

- "Fetch Future" calls `GET /hedge/futures-price` and fills the field.

### Results Area

After submitting the form, four sections appear:

#### Indicator Snapshot

```
RSI: 58.4   ADX: 27.1   EMA-21: 685.30
Regime: STRONG_BULLISH
```

#### Summary Cards (one per strategy)

```
+------------------+  +------------------+
| Protective PUT   |  | Covered Call     |
| Score: 5 ★       |  | Score: 4         |
| Max Loss: ₹11275 |  | Max Loss: ₹...   |
| Breakeven: 1.25% |  | Breakeven: ...   |
| Max Gain: ∞      |  | Max Gain: ₹...   |
| Daily Theta: -₹3 |  | Daily Theta: +₹4 |
+------------------+  +------------------+
```

The recommended strategy card is highlighted.

#### Payoff Diagram (Chart.js)

All four strategies are drawn on a single chart with price on the X-axis and P&L (₹) on the Y-axis. 25 data points per strategy. A vertical dashed line marks the entry price.

#### Comparison Table

| Metric | Protective PUT | Covered Call | Collar | Futures |
|---|---|---|---|---|
| Regime Score | 5 | 4 | 3 | 1 |
| Net Premium | -₹4675 | +₹5060 | +₹385 | ₹0 |
| Breakeven | ₹688.50 | ₹670.80 | ₹679.30 | ₹680.00 |
| Max Loss | ₹11275 | ... | ... | ... |
| Max Gain | ∞ | ... | ... | ... |
| Risk-Reward | ∞:1 | ... | ... | ... |
| PoP | 62% | ... | ... | ... |

#### Time Decay Table

| Time | Protective PUT | Covered Call | Collar | Futures |
|---|---|---|---|---|
| Today | -₹4675 | +₹5060 | +₹385 | ₹0 |
| 2/3 through | -₹3814 | +₹4133 | +₹315 | ₹0 |
| 1/3 through | -₹2702 | +₹2928 | +₹223 | ₹0 |
| At expiry | ₹0 | ₹0 | ₹0 | ₹0 |

(Values assume stock stays at entry price. Futures hedge has no time decay.)

#### P&L Scenarios Table

| Stock Price | Protective PUT | Covered Call | Collar | Futures |
|---|---|---|---|---|
| -20% (₹544) | -₹11275 | ... | ... | ~₹0 |
| -15% | ... | ... | ... | ... |
| -10% | ... | ... | ... | ... |
| -5% | ... | ... | ... | ... |
| 0% | ... | ... | ... | ... |
| +5% | ... | ... | ... | ... |
| +10% | ... | ... | ... | ... |
| +15% | ... | ... | ... | ... |
| +20% | ... | ... | ... | ... |

---

## Technical Notes

### Symbol Format

Two exchanges are supported. The symbol name is constructed differently for each.

#### NSE / NFO (equity options and futures)

```
{UNDERLYING}{DD}{MMM}{YY}{STRIKE}{CE|PE}

Examples:
  HDFCBANK29DEC26560PE
  NIFTY30OCT2623000CE
  RELIANCE29JAN2730000CE
```

| Component | Format |
|---|---|
| UNDERLYING | Uppercase scrip master name |
| DD | Day of expiry, zero-padded |
| MMM | 3-letter month, uppercase (JAN, FEB, ...) |
| YY | 2-digit year |
| STRIKE | Integer strike, no decimal |
| CE / PE | Option type |

#### BSE / BFO (equity options on BSE)

```
{UNDERLYING}{YY}{MMM}{STRIKE}{CE|PE}

Examples:
  HDFCBANK26OCT640PE
  SENSEX26OCT80000CE
```

| Component | Format |
|---|---|
| UNDERLYING | Uppercase scrip master name |
| YY | 2-digit year |
| MMM | 3-letter month, uppercase |
| STRIKE | Integer strike |
| CE / PE | Option type |

#### CDS (Currency Derivatives)

Currency option strikes in the Angel One scrip master are stored in paise multiplied by 10^5. To convert to the display value in rupees:

```
strikePaise = rawStrikeField
strikeRupees = strikePaise / 100000
```

For example, a USDINR option at ₹83.50 is stored with a raw strike of `8350000`.

### Strike Parsing from Symbol Name

The raw `strike` field in the Angel One scrip master has a known paise encoding issue where the value is not always reliable. The correct approach is to **parse the strike directly from the symbol name** using the regex patterns above. The app does this to avoid incorrect strike values caused by the paise encoding.

Example: given symbol `HDFCBANK29DEC26560PE`, the strike is extracted as the substring between `26` and `PE`, giving `560`.

### Lot Size Mismatch

The Angel One scrip master stores lot sizes for futures and options contracts. Lot sizes can differ between:

- The stock/index you are hedging
- The futures contract
- The options contract

Always verify that `quantity` entered by the user is a multiple of the lot size before computing total P&L. The app uses per-share calculations throughout; total P&L is always `perShareValue × quantity`. It does not enforce lot size multiples at the API level — this is the user's responsibility.

For index options (NIFTY, BANKNIFTY, etc.) the lot size is fixed by SEBI and changes periodically. Check the current lot size from the scrip master before placing orders.

### daysToExpiry Defaulting

If `daysToExpiry` is not provided in the request, it defaults to 30. This affects:
- `dailyTheta` calculation
- `estimatedDaysToBreakeven` calculation
- Time decay table intervals
- `probabilityOfProfit` historical window

For accurate results, always pass the actual number of calendar days until expiry.

### Futures Hedge P&L Note

The Futures Hedge strategy shows near-zero net P&L across all price scenarios. The small residual is the **cost-of-carry**:

```
costOfCarry = futurePrice - entryPrice
```

This is the profit/loss locked in regardless of price movement. If `futurePrice > entryPrice`, the trader has a small guaranteed gain (carry income). If `futurePrice < entryPrice`, there is a small guaranteed cost.

---

*End of HEDGE-COMPARISON.md*
