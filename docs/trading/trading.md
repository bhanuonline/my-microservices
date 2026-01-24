| Indicator                | Purpose                       | Best Market Type |
| ------------------------ | ----------------------------- | ---------------- |
| **EMA**                  | Fast entry timing             | Trending         |
| **MA / SMA**             | Trend direction               | Trending         |
| **Momentum (MOM)**       | Strength confirmation         | Breakouts        |
| **RSI**                  | Strength + fake signal filter | All markets      |
| **MACD**                 | Trend continuation            | Trending         |
| **Supertrend**           | Trailing SL + trend           | Trending         |
| **Bollinger Bands (BB)** | Reversal + volatility         | Sideways         |

ADX decides your strategy.
VWAP decides your direction.
EMA / BB decide your entry.

| Trading Type | TF       |
| ------------ | -------- |
| Scalping     | 3m / 5m  |
| Intraday     | 5m / 15m |
| Positional   | 1H / 4H  |
| Swing        | Daily    |



TREND FOLLOWING – EMA + MA + RSI + VWAP
Use when market trending strongly

| Condition     | Buy          | Sell          |
| ------------- | ------------ | ------------- |
| Price vs VWAP | Above        | Below         |
| EMA Cross     | 9 > 21       | 9 < 21        |
| MA Direction  | 50 MA rising | 50 MA falling |
| RSI           | > 55         | < 45          |
TF: 5m / 15m


BREAKOUT POWER – MOM + MACD + EMA
TF: 15m / 30m

| Condition | Buy                  | Sell                 |
| --------- | -------------------- | -------------------- |
| MOM       | > 0 rising           | < 0 falling          |
| MACD      | Histogram increasing | Histogram decreasing |
| EMA       | 20 EMA breakout      | 20 EMA breakdown     |



TRAILING TREND – Supertrend + EMA
TF: 15m / 1H

| Condition  | Buy                | Sell               |
| ---------- | ------------------ | ------------------ |
| Supertrend | Green              | Red                |
| EMA        | Price above 20 EMA | Price below 20 EMA |


**SIDEWAYS / REVERSAL – Bollinger Band + RSI**
TF: 5m / 15m

| Condition | Buy              | Sell             |
| --------- | ---------------- | ---------------- |
| BB        | Touch lower band | Touch upper band |
| RSI       | < 30 → bounce    | > 70 → fall      |


**ADX (14)**

| ADX      | Meaning                   |
| -------- | ------------------------- |
| Below 20 | Sideways → use BB         |
| Above 25 | Trending → use EMA + VWAP |

**❌ Indicator Usage Mistakes**

| Wrong                       | Right                  |
| --------------------------- | ---------------------- |
| Using BB in trending market | Use EMA setup          |
| Using MACD in sideways      | Use BB                 |
| Trading RSI alone           | Always with EMA / VWAP |
| Supertrend for entry        | Use for SL only        |


VWAP RULE (MOST IMPORTANT)

| Price Location           | Action    |
|--------------------------| --------- |
| Above VWAP               | Only BUY  |
| Below VWAP               | Only SELL |
| Cutting/Touching VWAP both sides | NO TRADE  |


| Time        | What 15-min does         |
| ----------- | ------------------------ |
| 9:15–9:30   | Ignore                   |
| 9:30–10:00  | Decide market mode       |
| 10:00–12:30 | Trade based on that bias |
| 12:30       | Re-check                 |
| After 2:30  | No fresh trades          |


| Chart      | Role                        |
| ---------- | --------------------------- |
| **1-Hour** | Decide the market direction |
| **15-Min** | Wait for setup              |
| **5-Min**  | Take entry                  |


| Condition on 1H                      | Market Mode      |
| ------------------------------------ | ---------------- |
| ADX > 25 + Price above VWAP & 50 EMA | **Bullish Day**  |
| ADX > 25 + Price below VWAP & 50 EMA | **Bearish Day**  |
| ADX < 20                             | **Sideways Day** |
| Price cutting VWAP                   | No trade bias    |


BUY Entry
| Rule                       |
| -------------------------- |
| 1H bias bullish            |
| Price above VWAP           |
| 9 EMA crosses above 21 EMA |
| RSI > 55                   |

SELL Entry

| Rule                       |
| -------------------------- |
| 1H bias bearish            |
| Price below VWAP           |
| 9 EMA crosses below 21 EMA |
| RSI < 45                   |


How RSI is Used in This System
RSI is only a filter, not an entry.

| Condition | Meaning        |
| --------- | -------------- |
| RSI > 55  | Buyers strong  |
| RSI < 45  | Sellers strong |
| RSI 45–55 | No trade zone  |



🟡 When to Use Bollinger Bands
Only when 1-Hour ADX < 20.
This means market is sideways → trend system will fail.

| BB Setup                        |
| ------------------------------- |
| Buy near lower band + RSI < 30  |
| Sell near upper band + RSI > 70 |
| Target = VWAP / middle band     |


🔴 When to Use MACD
MACD is optional and only for confirmation on breakouts.
Use only when:
ADX > 25
Price breaking previous high/low
MACD histogram increasing = breakout strength.
👉 Confirms bearish continuation / weakness


🧠 Final Rule

| Market Type         | System                 |
| ------------------- | ---------------------- |
| Trending (ADX > 25) | EMA + VWAP + RSI       |
| Sideways (ADX < 20) | Bollinger Bands        |
| Breakout            | MACD confirmation only |



🔹 What is Open Interest?
Open Interest = Total number of active (not yet closed) contracts in the market.
It shows how much big money is actually involved.
Price tells what is happening.
OI tells who is in control.

.

🔹 How OI is formed

| Action                          | OI Change        |
| ------------------------------- | ---------------- |
| New buyer + new seller          | **OI increases** |
| Buyer exits + seller exits      | **OI decreases** |
| Buyer exits + new seller enters | OI same          |
| Seller exits + new buyer enters | OI same          |



FINAL DAILY ROUTINE

Check ADX
If ADX > 25 → Trend Setup
If ADX < 20 → BB Reversal Setup
Always align with VWAP



**setup : for 1h**

    Look only at:
    ADX
    VWAP
    EMA 50 
        → Decide market bias.
    
    When on 15-Minute
    Ignore EMA 50.
    Use:
    VWAP
    EMA 9 & 21
    RSI
    → Prepare setup.
    
    When on 5-Minute
    Same indicators as 15m → Take entry.



**Indicators & Conditions for 1-Hour Bias**

| Indicator    | Condition                                                          | Meaning                                         |
| ------------ | ------------------------------------------------------------------ | ----------------------------------------------- |
| **ADX (14)** | > 25 → Trending, < 20 → Sideways                                   | Determines if the market is in trend or range   |
| **VWAP**     | Price **above VWAP** → bullish, Price **below VWAP** → bearish     | Shows institutional bias; confirms direction    |
| **EMA 50**   | Price **above EMA 50** → bullish, Price **below EMA 50** → bearish | Confirms the overall trend / support-resistance |


✅ How to Decide Market Bias

1.Check ADX (14)
ADX > 25 → Trend mode → proceed to next steps
ADX < 20 → Sideways → focus on range trades or BB later
ADX 20–25 → No clear bias → wait
2.Check VWAP vs Price
Current Nifty above VWAP → bullish bias
Current Nifty below VWAP → bearish bias
3.Check EMA 50 vs Price
Price above EMA 50 → bullish trend
Price below EMA 50 → bearish trend
✅ Only if all 3 align — ADX trending, price above VWAP, and price above EMA 50 → Market bias = bullish
✅ For bearish → ADX trending, price below VWAP, price below EMA 50

🔹 Important Notes
If VWAP and EMA 50 disagree (e.g., price above VWAP but below EMA 50) → no clear bias → wait for alignment.
1-Hour bias doesn’t lock the whole day; recheck every 1–2 hours.
Example (Bullish Bias):
ADX = 30 → trending
Nifty price = 18,520, VWAP = 18,500 → price above VWAP
EMA 50 = 18,480 → price above EMA 50
➡ Market bias = bullish → take only BUY setups on 15m/5m charts.






Setup one :
1-Hour Chart

Indicators
ADX (14)
VWAP (Session)
EMA 50

Rule :

| Condition                | Meaning                 |
| ------------------------ | ----------------------- |
| ADX > 25                 | Trending day            |
| ADX < 20                 | Sideways / reversal day |
| Price above VWAP & EMA50 | Bullish bias            |
| Price below VWAP & EMA50 | Bearish bias            |
👉 NO TRADE if price is between VWAP & EMA50

🎯 STEP-2 : TRADE DIRECTION CONFIRMATION
📈 15-Minute Chart

Indicators
VWAP
EMA 9
EMA 21
RSI (14)

BUY Conditions
EMA 9 > EMA 21
Price above VWAP
RSI > 55

SELL Conditions
EMA 9 < EMA 21
Price below VWAP
RSI < 45

INDICATORS NOT REQUIRED DAILY
Use ONLY if needed:

| Indicator       | Use                           |
| --------------- | ----------------------------- |
| Bollinger Bands | Only when ADX < 20            |
| MACD            | Higher timeframe confirmation |
| Supertrend      | Beginners only                |


📊 ADX + Direction (How to use it correctly)
Case 1: Strong Uptrend
ADX > 25
Price above VWAP & EMA 50
Higher highs & higher lows
➡ Strong BUY trend

Case 2: Strong Downtrend
ADX > 25
Price below VWAP & EMA 50
Lower highs & lower lows
➡ Strong SELL trend

🧠 Very Important Mapping (Remember this)

| I say | You do (Options) |
| ----- | ---------------- |
| BUY   | Buy Call         |
| SELL  | Buy Put          |

⚡ STEP-4 : ENTRY TIMING (15-Min + 5-Min)
15-Min:
EMA 9 > EMA 21 (BUY)
EMA 9 < EMA 21 (SELL)
RSI > 55 / < 45


**🔵 Case-1: BUY (Bullish Trend)**

All must be true:
ADX > 25 and rising
Price above VWAP
Price above EMA 50
EMA 9 > EMA 21
➡ BUY = Buy CALL (CE)

**🔴 Case-2: SELL (Bearish Trend)**

All must be true:
ADX > 25 and rising
Price below VWAP
Price below EMA 50
EMA 9 < EMA 21
➡ SELL = Buy PUT (PE)



**RSI :**
RSI does NOT give buy/sell alone
RSI only tells WHO is in control.
✅ Correct meaning:
RSI above 50–60 = Bullish control
RSI below 50–40 = Bearish control

**PC:**
Price Channel = market boundary
🔴 Top line → where price is getting rejected
🔴 Bottom line → where price is getting support
🔵 Middle line → trend direction
