# SMC Playbook — NIFTY Intraday Setups

> Companion to `liquidity.md`. This file = **actionable setups only**. Every setup has: chart diagram, entry rule, SL, target, invalidation, and a NIFTY example with real-style numbers.

**Assumptions for all examples:**
- NIFTY spot around 25,000
- Trading on 5-min chart with 15-min bias
- 1 lot = 75 qty, 1 point = ₹75

---

## Table of Contents

1. [The 5-Step SMC Framework](#1-the-5-step-smc-framework)
2. [Setup A — Sweep + Reversal (bread & butter)](#2-setup-a--sweep--reversal)
3. [Setup B — Sweep + Continuation (breakout that sticks)](#3-setup-b--sweep--continuation)
4. [Setup C — PDH/PDL Rejection](#4-setup-c--pdhpdl-rejection)
5. [Setup D — EQH / EQL Liquidity Raid](#5-setup-d--eqh--eql-liquidity-raid)
6. [Setup E — Inducement Trap](#6-setup-e--inducement-trap)
7. [Setup F — FVG Retracement Entry](#7-setup-f--fvg-retracement-entry)
8. [Setup G — Opening Range Sweep](#8-setup-g--opening-range-sweep)
9. [Setup H — Session Transition Play (London → Retail wake up)](#9-setup-h--session-transition-play)
10. [Setup I — Expiry-Day Max Pain Pin](#10-setup-i--expiry-day-max-pain-pin)
11. [Risk & Position Sizing Rules](#11-risk--position-sizing-rules)
12. [Setup Selection Cheatsheet](#12-setup-selection-cheatsheet)

---

## 1. The 5-Step SMC Framework

Every setup below is a variation of these 5 steps. Memorize this loop.

```
  ┌─────────────────────────────────────────────────┐
  │ STEP 1: Mark the DOL (Draw on Liquidity)        │
  │         → PDH, PDL, EQH, EQL, session H/L       │
  ├─────────────────────────────────────────────────┤
  │ STEP 2: Wait for the SWEEP                      │
  │         → wick beyond level, close back inside  │
  ├─────────────────────────────────────────────────┤
  │ STEP 3: Confirm CHOCH on lower timeframe        │
  │         → structure flips (5m or 3m)            │
  ├─────────────────────────────────────────────────┤
  │ STEP 4: Enter on FVG or OB retracement          │
  │         → don't chase, wait for pullback        │
  ├─────────────────────────────────────────────────┤
  │ STEP 5: Target the OPPOSITE liquidity pool      │
  │         → BSL sweep → target SSL, vice versa    │
  └─────────────────────────────────────────────────┘
```

---

## 2. Setup A — Sweep + Reversal

**The 80% setup.** Price grabs an obvious pool, fails, reverses.

### Chart pattern
```
                    ╱╲    ← sweep wick (grabs BSL)
   ═══════════════ ╱  ╲ ═══════ ← BSL level (e.g. PDH 25,080)
                  ╱    ╲
                 ╱      ╲
        ────────╱        ╲──── ← CHOCH (5m low broken)
                          ╲
                           ╲──● entry on FVG retest
                            ╲
                             ╲
                              ↓ target = SSL below
```

### Entry rule
1. Price sweeps a marked level (wick beyond, body closes back inside)
2. Watch 5-min: previous swing low must break = **CHOCH**
3. Wait for retrace into the bearish **FVG** (or order block)
4. Enter SHORT at the FVG midpoint

### SL / Target
- **SL:** 5–10 pts above the sweep wick
- **Target 1:** Nearest internal low (1:1)
- **Target 2:** Opposite pool (SSL / PDL)
- **Trail after T1**

### NIFTY example
| Element | Value |
|---------|-------|
| PDH (DOL) | 25,080 |
| Sweep wick high | 25,092 |
| CHOCH low broken | 25,050 |
| FVG entry | 25,065 |
| SL | 25,098 (33 pts) |
| T1 | 25,020 |
| T2 (PDL/SSL) | 24,880 (185 pts) |
| R:R | ~1:5.5 to T2 |

### Invalidation
- 5-min candle closes back above sweep wick → **exit**
- No CHOCH within 30 min → **skip trade**

---

## 3. Setup B — Sweep + Continuation

**The trap-flipper.** Looks like reversal, but price keeps going after grabbing stops.

### Chart pattern
```
                              ╱↑ real continuation
                             ╱
              ● BSL grabbed ╱
    ═══════ /\ ═══════════ ╱ ═══════
           /  \           ╱
          /    \    ╱╲   ╱  ← shallow pullback (no CHOCH)
         /      \  ╱  \ ╱
   ─────         \╱    V
                  strong close ABOVE sweep = continuation signal
```

### How to tell it from Setup A
| Signal | Reversal (A) | Continuation (B) |
|--------|--------------|------------------|
| Close after sweep | Back **inside** level | Back **above** level within 2-3 candles |
| Volume on sweep | Spike then dies | Sustained above average |
| CHOCH on 5m | Yes | **No** — structure holds |
| Retrace depth | Deep (into FVG) | Shallow (rides EMA) |

### Entry rule
1. Sweep occurs
2. Next 2-3 candles close **above** the swept level (not just wick)
3. Enter LONG on first pullback to prior resistance (now support)

### NIFTY example
| Element | Value |
|---------|-------|
| Swept level (EQH) | 25,100 |
| Sweep wick | 25,115 |
| Close 3 candles later | 25,125 (holding above) |
| Pullback entry | 25,105 |
| SL | 25,088 (below sweep base, 17 pts) |
| Target (next liquidity) | 25,200 |
| R:R | ~1:5.5 |

### Invalidation
- Any 5-min close back below the swept level → **exit**

---

## 4. Setup C — PDH/PDL Rejection

Cleanest setup. PDH and PDL are the **most obvious pools** every trader watches.

### Chart pattern (PDH rejection = short)
```
     ╱╲  ← wick tags PDH
    ╱  ╲
═══════════════════ ← PDH 25,080
    ╱
   ╱ rejection candle (long upper wick, close near low)
  ╱
 ↓
```

### Entry rule
1. Price approaches PDH within first 2 hours (9:15 – 11:15)
2. Wick beyond PDH + **rejection candle** (upper wick > 2x body)
3. Enter SHORT below the rejection candle low

### NIFTY example
| Element | Value |
|---------|-------|
| PDH | 25,080 |
| Rejection wick | 25,088 |
| Rejection candle low | 25,062 |
| Entry (below low) | 25,061 |
| SL | 25,090 (29 pts) |
| Target 1 | Day open (25,000) |
| Target 2 | PDL 24,880 |

### When it fails
- Late-day PDH tag (after 2:00 PM) → usually breaks, don't fade
- News-driven approach → skip

---

## 5. Setup D — EQH / EQL Liquidity Raid

Equal highs/lows are **magnets** — they get swept 85% of the time.

### Chart pattern
```
    ●═══════●═══════●    ← 3 touches at 25,080 = EQH (scream sweep me)
    /\     /\      /\
   /  \  /   \   /  \
  /    \/     \ /    \
 /                    \
```

### Entry rule
1. Identify 2+ touches at the same price ± 5 pts
2. Mark it — this is a **high-probability DOL**
3. Wait for the sweep (Setup A or B unfolds after)
4. Trade the resulting sweep + CHOCH

### NIFTY example
> Yesterday NIFTY touched 25,080 twice. Today it opens at 25,020. **Bias for the day:** price will likely visit 25,080+ before any real downside. Plan the short **after** the sweep, not before.

### Why it works
Retail sees "double top" and shorts at the level → their SLs pile up above → institutions push through, grab all SLs, then reverse.

---

## 6. Setup E — Inducement Trap

The **most expensive lesson** if you don't know it. Price makes a fake move to trap early entries, then reverses into the real move.

### Chart pattern
```
             ↑ real move (up)
            ╱
           ╱
          ╱ ← BOS confirms uptrend
    ●────╱
     \  ╱
      \╱  ← inducement low (traps early longs, then sweeps them)
       \
        ●  ← THIS low is the real one; wait for sweep of THIS
```

### The rule
**Never enter on the first swing after a reversal — wait for the inducement low/high to be swept first.**

### NIFTY example
- 9:15 – open 25,000
- 9:30 – dumps to 24,950 (looks bearish)
- 9:45 – bounces to 25,010 (retail longs SL at 24,945)
- 10:00 – dumps to 24,940 (sweeps retail SL) ← **THIS is the real low**
- 10:15 – rockets to 25,100

**Trap:** entering long at 9:45 with SL 24,945 = stopped out at 24,940 by 5 pts.
**Correct:** wait for 24,945 to be swept, then enter long at 24,970 with SL 24,935.

---

## 7. Setup F — FVG Retracement Entry

Best **entry timing** technique. Combines with all setups above.

### What is an FVG?
A 3-candle pattern where candle 1's high and candle 3's low don't overlap:
```
Candle 1:  │▲       ← high = 25,050
           │
Candle 2:  │▲▲▲▲    ← big displacement bull candle
           │
Candle 3:      ▲│   ← low = 25,065
                    ← FVG = 25,050–25,065 (gap in delivery)
```

### Rule
Price returns to the FVG (usually the **50% midpoint**) to "fill" it before continuing.

### Entry
- Bullish FVG (in uptrend): enter LONG at midpoint, SL below FVG low
- Bearish FVG (in downtrend): enter SHORT at midpoint, SL above FVG high

### NIFTY example
| Element | Value |
|---------|-------|
| FVG range (bullish) | 25,050 – 25,065 |
| Midpoint entry | 25,058 |
| SL | 25,048 (10 pts) |
| Target (next high) | 25,120 |
| R:R | 1:6 |

**Pro tip:** Combine with Setup A → after a sweep + CHOCH, enter on the FVG the CHOCH created.

---

## 8. Setup G — Opening Range Sweep

**Time:** 9:15 – 9:30 (first 15 min). Range gets swept 70% of the time before the trend day begins.

### Chart pattern
```
9:15 ─────────────────  ← range high
       ╱╲    ╱╲
      ╱  \  ╱  \        ← 15-min opening range
     ╱    \╱    \
9:30 ─────────────────  ← range low

9:45   ╱╲              ← sweep of range HIGH
      ╱  \
     ╱    \  ← reversal begins
    ╱      \_____↓ trend day down
```

### Rule
1. Mark 9:15–9:30 high & low
2. First sweep (usually 9:45–10:15) = **the setup**
3. Trade the reversal into opposite side of range

### NIFTY example
| Element | Value |
|---------|-------|
| OR high (9:30) | 25,040 |
| OR low (9:30) | 25,010 |
| Sweep of OR high | 25,048 (10:05) |
| CHOCH on 3-min | 25,030 broken |
| Entry (bearish FVG) | 25,035 |
| SL | 25,051 (16 pts) |
| Target (OR low) | 25,010 |
| Extended target | PDL 24,880 |

---

## 9. Setup H — Session Transition Play

**Time:** ~12:30 PM IST (London open). Fresh liquidity enters, often reverses morning trend.

### Rule
- Morning trend (9:15 – 12:00) usually **gets faded** at London open
- Watch for sweep of morning session H or L around 12:30 – 1:30
- Trade the reversal

### NIFTY example
- Morning: NIFTY drops 25,000 → 24,930 (looks bearish)
- 12:35 PM: sweeps morning low at 24,928
- CHOCH up at 24,960
- Entry LONG at FVG 24,945, SL 24,922
- Target: morning high 25,000, then PDH

---

## 10. Setup I — Expiry-Day Max Pain Pin

**Time:** Thursday (weekly expiry). Price gravitates toward **Max Pain** by 3:30 PM.

### Rule
1. Calculate Max Pain from option chain (highest OI call + highest OI put balance strike)
2. If NIFTY is **above** Max Pain in the morning → bias down toward MP
3. If NIFTY is **below** MP → bias up
4. Trade rejections at Call Wall / Put Wall

### NIFTY example
| Element | Value |
|---------|-------|
| Call Wall (highest call OI) | 25,100 |
| Put Wall (highest put OI) | 24,800 |
| Max Pain | 24,950 |
| NIFTY at 10:00 AM | 25,080 |
| Bias | Down toward 24,950 |
| Entry | Short on rejection at 25,090 |
| SL | 25,110 |
| Target | 24,950 (Max Pain pin) |

### Warning
- Max Pain fails on **event days** (RBI policy, budget, results)
- Fails if a huge OI shift happens intraday — recheck at 1 PM

---

## 11. Risk & Position Sizing Rules

### Per-trade risk
- **Max risk per trade:** 1% of capital
- Example: ₹5L capital → max ₹5,000 risk per trade

### Position sizing formula
```
Lots = (Capital × 1%) / (SL in pts × 75)

Example:
Capital: ₹5,00,000
Max risk: ₹5,000
SL: 20 pts
Lots = 5000 / (20 × 75) = 3.33 → round DOWN to 3 lots
```

### Daily limits
- **Max 3 trades per day** on setups A–D
- **Stop trading after 2 consecutive losses** — you're out of sync
- **Max daily loss: 3%** — walk away, come back tomorrow

### Trade log columns (minimum)
| Date | Setup | Entry | SL | T1 | T2 | Exit | R:R | Notes |

---

## 12. Setup Selection Cheatsheet

| Market condition | Best setup |
|------------------|-----------|
| Range-bound day | Setup A (Sweep + Reversal) |
| Strong trend day | Setup B (Sweep + Continuation) |
| Clean PDH/PDL nearby | Setup C |
| EQH/EQL visible | Setup D |
| Choppy open | Setup G (Opening Range) |
| Post-lunch (12:30+) | Setup H (Session Transition) |
| Thursday afternoon | Setup I (Max Pain) |
| Any time — entry timing | Setup F (FVG) — layer on A/B/C/D |
| First 30 min | **Avoid Inducement** — apply Setup E filter to everything |

---

## Final Rules

1. **No setup? No trade.** Sitting on hands is a position.
2. **Never enter without a marked DOL.** If you can't name the pool, you're guessing.
3. **CHOCH before entry.** No structure flip = no trade.
4. **FVG for timing.** Don't chase — wait for the retracement.
5. **Target opposite liquidity.** That's the whole game — one pool grabbed, the next becomes the magnet.

> **Read this file before market open every day for 30 days.** After that, the patterns start appearing on the chart automatically.
