# Daily Bias & Trade Plan — YYYY-MM-DD

> Copy this file each morning. Rename to `docs/journal/YYYY-MM-DD.md`.
> Fill top-down BEFORE 9:15. Review bottom-up at 15:35.

---

## 0. Pre-Market (fill before 9:00 IST)

| Item | Value | Notes |
|------|-------|-------|
| **GIFT Nifty** (Singapore) | ______ | +/- vs Indian close = expected gap |
| **Dow / S&P** overnight close | ______ | +2%: bullish tone; -2%: bearish |
| **Nasdaq futures** | ______ | Big tech direction |
| **USDINR** | ______ | Rising = FII selling risk |
| **Brent crude** | ______ | > $85 = pressure on Indian mkts |
| **DXY** (dollar index) | ______ | Rising = EM outflow risk |
| **Asian markets** (Nikkei / Hang Seng) | ______ | Direction hint |
| **Overnight news / events** | ______ | Fed / RBI / geopolitics / budget |
| **Today's scheduled events** | ______ | RBI, CPI data, expiry Thursday? |

### Session archetype guess
- [ ] Gap-and-go (trend continues)
- [ ] Gap-and-fade (reverses)
- [ ] Range-bound
- [ ] News-driven / event day
- [ ] Expiry chop

---

## 1. Market Context (fill by 9:20 after open)

| Metric | Value | Reference |
|--------|-------|-----------|
| **India VIX** | ______ | <13 calm · 13-18 normal · >18 volatile |
| **Previous Day High (PDH)** | ______ | Liquidity above |
| **Previous Day Low (PDL)** | ______ | Liquidity below |
| **Previous Close** | ______ | Gap reference |
| **Day Open** | ______ | Today's anchor |
| **Gap vs prev close** | +/- ______ | Big gap changes rules |
| **Opening range (9:15-9:30) high / low** | ______ / ______ | ORB reference |

### 1.1 Advance / Decline breadth (fill 9:25 – 9:35)

Market breadth = the "is this move real?" filter. If Nifty is up but
most stocks are down, the rally is narrow and likely fails.

| Scope | Advances | Declines | Ratio (A:D) | Reading |
|-------|:--------:|:--------:|:-----------:|---------|
| **Nifty 50** | ______ | ______ | ____ : ____ | |
| **All NSE** | ______ | ______ | ____ : ____ | |
| **NSE 500** *(optional)* | ______ | ______ | ____ : ____ | |

**Interpretation (Nifty 50 out of 50 stocks):**
- 40+ : 10- → very strong bullish breadth
- 30 : 20 → mild bullish
- 25 : 25 → neutral / choppy
- 20 : 30 → mild bearish
- 10- : 40+ → very strong bearish breadth

**Divergence check (most important):**
| Nifty direction | A/D direction | Signal |
|-----------------|---------------|--------|
| Nifty ↑ + A/D ↑ | ✅ Real rally — trade longs |
| Nifty ↑ + A/D ↓ | 🔴 FAKE rally — narrow leadership, expect reversal |
| Nifty ↓ + A/D ↓ | ✅ Real selloff — trade shorts |
| Nifty ↓ + A/D ↑ | 🔴 FAKE dip — expect recovery |

**Where to check:**
- Angel / Zerodha Kite: market breadth widget
- MoneyControl: Market Stats → Breadth
- NSE website: Market Data → Advances/Declines
- TradingView symbol: `ADVDECLINE` or `ADRATIO`

**Check again at:** 11:30 (mid-morning) and 14:30 (afternoon confirmation).

---

## 2. Multi-Timeframe Bias

| Timeframe | Bias | Why (1 line) |
|-----------|------|--------------|
| **Daily (1D)** | ⬜ Bull ⬜ Bear ⬜ Neutral | |
| **Hourly (1H)** | ⬜ Bull ⬜ Bear ⬜ Neutral | |
| **15-min** | ⬜ Bull ⬜ Bear ⬜ Neutral | |
| **5-min** | ⬜ Bull ⬜ Bear ⬜ Neutral | |

**Aligned?** ⬜ Yes (all same) ⬜ No (mixed — trade cautiously)

---

## 3. Trend Filters

| Indicator | Reading | Signal |
|-----------|---------|--------|
| **EMA 20 vs EMA 50** (5M) | ⬜ 20>50 ⬜ 20<50 | Bull / Bear |
| **EMA 20 vs EMA 200** (15M) | ⬜ Above ⬜ Below | Macro filter |
| **Price vs VWAP** | ⬜ Above ⬜ Below | Intraday direction |
| **ADX(14)** | ______ | <20 chop · 20-25 developing · >25 strong |
| **ATR(14)** — % of price | ______ | Rising = more volatility |
| **RSI(14)** — 5M | ______ | >50 bull, <50 bear |
| **MACD** (12,26,9) — histogram | ⬜ + ⬜ − | Momentum sign |

---

## 4. Market Structure (SMC)

| Item | State |
|------|-------|
| **Structure (5M)** | ⬜ Bullish (HH+HL) ⬜ Bearish (LH+LL) ⬜ Ranging |
| **Structure (15M)** | ⬜ Bullish ⬜ Bearish ⬜ Ranging |
| **Last event** | ⬜ BOS (continuation) ⬜ CHoCH (reversal) |
| **Direction of last event** | ⬜ Bullish ⬜ Bearish |
| **Latest swing high** | ______ |
| **Latest swing low** | ______ |

---

## 5. Zones & Liquidity

**Fill from `/api/analysis/zones` or your chart**

| Type | Price levels |
|------|-------------|
| **Active bullish OBs** (below price) | ______ , ______ |
| **Active bearish OBs** (above price) | ______ , ______ |
| **Unmitigated bullish FVGs** | ______ , ______ |
| **Unmitigated bearish FVGs** | ______ , ______ |
| **Unswept BSL** (nearest above) | ______ |
| **Unswept SSL** (nearest below) | ______ |
| **Equal highs (resting liquidity)** | ______ |
| **Equal lows (resting liquidity)** | ______ |

---

## 6. Cross-Market Confirmation

| Instrument | Direction | Confirms Nifty? |
|-----------|-----------|:---------------:|
| **Bank Nifty** | ⬜ Up ⬜ Down | ⬜ Yes ⬜ No (divergence!) |
| **Sector index** (if stock trade) | ⬜ Up ⬜ Down | ⬜ Yes ⬜ No |
| **India VIX direction** | ⬜ Up ⬜ Down | Rising VIX during rally = warning |

---

## 7. Options Context (Nifty options only — skip if equity)

| Metric | Value | Reading |
|--------|-------|---------|
| **DTE** (days to expiry) | ______ | <2 = high theta |
| **ATM IV** | ______ | vs IV rank |
| **PCR** (put-call ratio) | ______ | >1 bearish, <1 bullish |
| **Max Pain** | ______ | Price tends to gravitate here on expiry |
| **Max Call OI strike** | ______ | Resistance |
| **Max Put OI strike** | ______ | Support |
| **Change in OI** (biggest add) | ______ | Fresh positioning |

---

## 8. Consolidated Bias

**Score each dimension: +1 bullish, -1 bearish, 0 neutral**

| Dimension | Score (+1/0/-1) |
|-----------|:---------------:|
| Multi-TF bias | ___ |
| EMA trend | ___ |
| VWAP | ___ |
| ADX + direction | ___ |
| Market structure | ___ |
| Cross-market (Bank Nifty) | ___ |
| Options bias | ___ |
| **TOTAL** | **___** |

**Interpretation:**
- **+5 to +7** → Strong BULLISH bias — look for longs only
- **+2 to +4** → Mild bullish — trade cautiously
- **-1 to +1** → No edge — stand aside or trade very small
- **-2 to -4** → Mild bearish
- **-5 to -7** → Strong BEARISH — look for shorts only

**Final bias for the day:** ⬜ LONG-ONLY ⬜ SHORT-ONLY ⬜ NO-TRADE

---

## 9. Trade Plan

### Setup you're waiting for
- [ ] Liquidity sweep → CHoCH → FVG retest
- [ ] EMA pullback in trending market
- [ ] VWAP rejection
- [ ] Breakout + retest of PDH/PDL
- [ ] ORB break
- [ ] Support / resistance rejection

### If setup fires

| Item | Plan |
|------|------|
| **Entry trigger** | (specific price / candle pattern) |
| **Stop-loss** | (level + rupees) |
| **Target 1** | ______ (50% out here) |
| **Target 2** | ______ (trail rest) |
| **Risk/Reward** | ______ (min 1:1.5, prefer 1:2+) |
| **Position size** | qty = (capital × risk%) / (entry - stop) = ______ |

---

## 10. Time-of-Day Windows

| Window (IST) | What to do |
|-------------|-----------|
| **9:15 – 9:45** | Observe only. Let opening volatility settle. |
| **9:45 – 11:30** | Best trending window. Take clean setups. |
| **11:30 – 13:30** | Midday chop — mean reversion trades or wait |
| **13:30 – 15:00** | Trend continuation or reversal — best window #2 |
| **15:00 – 15:15** | Square off MTF. No fresh entries. |
| **15:15 – 15:30** | Close everything. Log the day. |

---

## 11. Discipline Gate (check EVERY trade)

Before pressing BUY / SELL, honestly:
- [ ] Am I revenge trading? (angry after a loss?)
- [ ] Is this setup on my plan (section 9)?
- [ ] Is my stop at a technical level, not arbitrary?
- [ ] Is my position size within my daily risk limit?
- [ ] Would I be OK if this hit stop?
- [ ] Have I hit my max losses for today?

If any answer is uncomfortable → **SKIP THE TRADE**.

---

## 12. Daily Risk Rules (fixed — non-negotiable)

| Rule | Value |
|------|-------|
| **Max risk per trade** | ______% of capital (e.g. 1%) |
| **Max daily loss** | ______% (e.g. 3%) → STOP for the day |
| **Max losing trades** | ______ (e.g. 3 losses) → STOP |
| **Max positions at once** | ______ (e.g. 2) |
| **Max leverage used** | ______x |

---

## 13. Trade Log (fill as trades happen)

| # | Time | Side | Symbol | Entry | Stop | Target | Exit | P&L | Setup | Notes |
|---|------|------|--------|-------|------|--------|------|-----|-------|-------|
| 1 |      |      |        |       |      |        |      |     |       |       |
| 2 |      |      |        |       |      |        |      |     |       |       |
| 3 |      |      |        |       |      |        |      |     |       |       |

---

## 14. End-of-Day Review (fill at 15:35 IST)

| Question | Answer |
|----------|--------|
| **Did I follow the plan?** | ⬜ Fully ⬜ Mostly ⬜ Broke it |
| **What did I do RIGHT?** | |
| **What did I do WRONG?** | |
| **Best trade — why?** | |
| **Worst trade — why?** | |
| **Emotional state during losses** | |
| **Did I overtrade?** | ⬜ Yes ⬜ No |
| **P&L today** | ______ |
| **Cumulative P&L this week** | ______ |
| **Idea for tomorrow** | |

---

## 15. What to Fill FROM THE APP

The `angle-app` gives you these fields automatically — don't calculate by hand:

| Sheet field | Where to get it |
|-------------|-----------------|
| Multi-TF bias | `GET /api/analysis/analyst?broker=ANGEL&symbolToken=...&interval=ONE_DAY` (and 1H, 15M, 5M) |
| ADX / EMA / RSI / MACD | Currently need TradingView (indicators not exposed via REST yet) |
| VWAP | TradingView / Angel chart |
| Market structure | `GET /api/analysis/structure` |
| Zones (OB, FVG, liquidity) | `GET /api/analysis/zones` |
| Full context snapshot | `GET /api/analysis/context` |
| Consolidated recommendation | `GET /api/analysis/analyst` |
| Position size formula | Calculator / spreadsheet |

**Missing from the app today** (worth adding):
- REST endpoint for indicator values (ADX, EMA, RSI, MACD, VWAP, ATR) — currently only inside strategies
- India VIX + Bank Nifty snapshot in one call
- Options chain / OI / Max Pain endpoint
- Time-of-day gate on live sessions

---

## 16. Rule of Thumb (final)

> **Bias tells you which side to look at.**
> **Structure tells you WHEN to look.**
> **Liquidity + FVG + OB tells you WHERE to enter.**
> **ATR + capital tells you HOW MUCH.**
> **Discipline gate tells you IF.**

Never enter on one indicator alone. Always confirm with **at least 2** of: trend + structure + liquidity + volume.
