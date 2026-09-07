# Liquidity — Complete Guide (with Diagrams & NIFTY Examples)

> **Core idea:** "Liquidity" = **money resting as pending orders** (stop-losses, limits, breakout orders). Big players (Smart Money) push price *toward* these pools to fill their own large orders. If you understand where liquidity sits, you understand where price *wants* to go.

---

## 1. Core Liquidity Terms

### Liquidity
The **ease of buying/selling** without moving price. NIFTY = highly liquid. A small illiquid stock = 1 order moves price 2%.

### Liquidity Pool
A **cluster of pending orders** sitting at one price zone.
```
Price ────────────────────────
              ┌──────────┐
              │  25,000  │  ← 10,000 stop-loss orders resting here = POOL
              └──────────┘
```
**NIFTY ex:** If everyone put SL at 25,000, that's a pool of sell-stops waiting to fire.

### Liquidity Zone / Area / Level
Same idea, different scale:
- **Level** = a single price line (e.g. 25,000)
- **Zone** = a narrow band (24,990–25,010)
- **Area** = a wide region (24,950–25,050)

### Liquidity Sweep / Grab / Hunt / Run / Raid
**All 5 words mean the same thing:** price spikes into a pool, triggers those orders, then reverses.
```
        ▲ spike triggers stops
        │╱╲
────────╱──╲──────── ← liquidity level
       ╱    ╲
      ╱      ╲___ price reverses after grabbing
```
**NIFTY ex:** Price runs to 25,010, hits all SLs above 25,000, then dumps to 24,900. That wick = the sweep.

---

## 2. Buy-Side vs Sell-Side Liquidity

### Buy-Side Liquidity (BSL)
Buy-stop orders **above** highs (short-sellers' SLs, breakout buyers).
### Sell-Side Liquidity (SSL)
Sell-stop orders **below** lows (long-holders' SLs, breakdown sellers).
```
        ═══════════════  ← BSL (buy stops above)
           /\    /\
          /  \  /  \
         /    \/    \
        ═══════════════  ← SSL (sell stops below)
```
**NIFTY ex:** Above yesterday's high 25,100 = BSL. Below yesterday's low 24,850 = SSL.

### Internal vs External Liquidity
- **Internal** = liquidity **inside** the current range (minor swing highs/lows)
- **External** = liquidity **outside** the range (major swing extremes)
```
        ═══════ EXTERNAL (range high)
          ─── internal high
           ── internal low
        ═══════ EXTERNAL (range low)
```

### Resting / Visible / Hidden Liquidity
- **Resting** = pending orders sitting in the book
- **Visible** = shown in DOM/order book
- **Hidden** = iceberg orders, dark pools — you can't see them

---

## 3. Liquidity Around Price Structure

### Swing High / Swing Low
A **pivot** where price turned. Stops sit just beyond these.
```
       SH ●        ● SH
         /\       /\
        /  \  /\ /  \
       /    \/  V    \
      /              \
     ● SL          SL ●
```

### PDH / PDL / PWH / PWL
- **PDH** = Previous Day High → BSL magnet
- **PDL** = Previous Day Low → SSL magnet
- **PWH / PWL** = same for the week
**NIFTY ex:** Monday's high 25,080 becomes Tuesday's magnet. Price often sweeps it before real direction shows.

### Equal Highs (EQH) / Equal Lows (EQL)
Two or more touches at the same price = **huge liquidity magnet** (obvious SL cluster).
```
      ●═══════●═══════●   ← EQH (screams "sweep me")
      /\     /\      /\
     /  \  /   \   /  \
```

### Double Top / Double Bottom Liquidity
Same as EQH/EQL but recognized as classical pattern. Stops sit right above/below.

### Range High / Range Low Liquidity
When price consolidates, both edges accumulate stops → one side gets swept first.

---

## 4. Stop / Order Related Terms

| Term | Meaning |
|------|---------|
| **Stop-Loss Liquidity** | The pool that stop-losses create |
| **Stop Orders** | Orders that trigger at a price (buy-stop above, sell-stop below) |
| **Stop Run / Stop Hunt** | Price runs to trigger those stops |
| **Resting Orders** | Any pending order sitting in the book |
| **Pending Orders** | Same as resting (not yet filled) |
| **Limit Orders** | "Fill me at X or better" — passive, adds liquidity |
| **Market Orders** | "Fill me NOW at any price" — aggressive, takes liquidity |
| **Order Flow** | Real-time stream of buys vs sells |
| **Order Book** | List of all pending limit orders |
| **Depth of Market (DOM)** | Visual of the order book (bid/ask ladder) |

```
DOM example (NIFTY):
    Price   Bid    Ask
    25,010          500  ← asks (sellers)
    25,005          300
    25,000  ──── LAST ────
    24,995   400
    24,990   700         ← bids (buyers)
```

---

## 5. Smart Money / SMC Terms

### Smart Money
Institutions, banks, FIIs — the "big hands" that need liquidity to fill huge positions.

### SMC (Smart Money Concepts)
The trading framework built around tracking these players via liquidity + structure.

### IRL / ERL
- **IRL** = Internal Range Liquidity (inside the range)
- **ERL** = External Range Liquidity (outside the range)
**Rule of thumb:** Price moves ERL → IRL → ERL (sweeps one extreme, moves to other).

### Inducement
A **fake move** that lures retail into a trade before the real move.
```
        real move ↑
       ╱
      ╱      ← BOS up
     ╱
    ╱ ● inducement low (traps early longs)
   ╱ /\
  ╱ /  \___ SL taken here first
```

### Draw on Liquidity (DOL)
Where price is **magnetically attracted** to next — the obvious pool ahead.
**NIFTY ex:** Price at 25,000, EQH sitting at 25,080 → DOL = 25,080.

### Liquidity Void / Fair Value Gap (FVG) / Imbalance
A **gap in price delivery** — 3 candles where the wicks don't overlap.
```
Candle 1:    │▲
             │
Candle 2:    │▲▲▲   ← big displacement candle
             │
Candle 3:      ▲│   ← gap between C1 high & C3 low = FVG
```
Price often returns to fill this gap.

### Displacement
A **strong, aggressive move** (big-body candle) — sign of institutional intent.

---

## 6. Market Structure

### BOS (Break of Structure)
Price breaks a prior swing **in the direction of trend** → trend continues.
```
Uptrend BOS:
              ● broken → BOS ↑
             /
      SH ●──/
        /\ /
       /  V
```

### CHOCH (Change of Character)
Price breaks a swing **against** the trend → possible reversal.
```
Uptrend → CHOCH:
      ●  ●
     /\ /\
    /  V  \
        SL●───↓ broken = CHOCH (uptrend weakening)
```

### MSS (Market Structure Shift)
A **confirmed CHOCH with displacement** → high-probability reversal.

---

## 7. Advanced Liquidity Concepts

| Term | Meaning |
|------|---------|
| **Sweep + Reversal** | Grab stops, then reverse hard (most common trap) |
| **Sweep + Continuation** | Grab stops, keep going same way (breakout is real) |
| **Buy-Side Sweep** | Runs above highs, hits buy-stops |
| **Sell-Side Sweep** | Runs below lows, hits sell-stops |
| **Liquidity Magnet** | Any obvious pool pulling price toward it |
| **Liquidity Vacuum** | Thin zone with few orders → price flies through |
| **Liquidity Absorption** | Big player eats orders quietly without moving price |
| **Liquidity Injection** | New orders flooding in (news, session open) |
| **Liquidity Drain** | Orders getting consumed → thin book → volatility spike |
| **Consolidation** | Sideways = building liquidity both sides |
| **Distribution** | Smart money offloading to retail near tops |

---

## 8. Trading Session Liquidity

```
IST timeline (India):
  05:30 ──── Asian session ──── 12:30
  12:30 ──── London opens ───── 18:00  (volatility ↑)
  18:00 ──── NY opens ───────── 02:30  (biggest moves)
```

- **Session High/Low** = extremes of that session (become tomorrow's PDH/PDL)
- **Opening Range Liquidity** = first 15-30 min high/low
- **Overnight High/Low** = pre-market extremes
- **Previous Session H/L** = magnets for the next session

**NIFTY ex:** First 15-min range (9:15–9:30) often gets swept before the real trend day.

---

## 9. Options / Market Liquidity Terms

### Open Interest (OI)
Total open option contracts. **High OI = high liquidity at that strike.**

### OI Concentration / Wall
Strike with unusually high OI = a **wall** price struggles to cross.
- **Call Wall** = resistance (call writers defend)
- **Put Wall** = support (put writers defend)

### Max Pain
Strike where **most option buyers lose max money** at expiry. Price often gravitates here on expiry day.
```
NIFTY expiry example:
  Call OI peak: 25,100 ← resistance
  Put OI peak:  24,800 ← support
  Max Pain:     24,950 ← where price wants to close
```

### Volume Liquidity
Actual traded volume (not just pending) at each strike.

### Bid / Ask Liquidity & Spread
- **Bid** = highest price buyers will pay
- **Ask** = lowest price sellers accept
- **Spread** = ask − bid (tight spread = liquid; wide = illiquid)

### Market Depth / Option Chain Liquidity
How much size is available at each strike/price without slippage.

---

## 10. What Matters Most for NIFTY Intraday

Don't try to learn all 92 terms. **Master these 15 first:**

| # | Term | Why it matters for NIFTY |
|---|------|-------------------------|
| 1 | Liquidity | The whole game |
| 2 | Liquidity Pool | Know where stops cluster |
| 3 | BSL | Above PDH, swing highs |
| 4 | SSL | Below PDL, swing lows |
| 5 | Liquidity Sweep | The setup itself |
| 6 | Liquidity Grab | Same as sweep — entry trigger |
| 7 | Stop-Loss Liquidity | Where retail dies = where you enter |
| 8 | Swing High | Marks BSL |
| 9 | Swing Low | Marks SSL |
| 10 | EQH | Screaming BSL magnet |
| 11 | EQL | Screaming SSL magnet |
| 12 | Internal Liquidity | Intraday pivots |
| 13 | External Liquidity | Session/day extremes |
| 14 | DOL | The obvious next target |
| 15 | Inducement | The fake move to avoid |

Once these 15 feel natural, layer on:
- **FVG** (fair value gap — where price returns to)
- **BOS** (trend continuation confirmation)
- **CHOCH** (early reversal signal)
- **MSS** (confirmed reversal with displacement)

---

## Master Playbook: How All Terms Connect

```
1. Identify DOL       →  Where's the obvious liquidity? (PDH, EQH, etc.)
2. Watch Inducement   →  Wait for the fake move that traps retail
3. Wait for Sweep     →  Price grabs the pool (wick beyond level)
4. Look for CHOCH/MSS →  Structure flips on lower timeframe
5. Enter on FVG       →  Retracement into fair value gap
6. Target next pool   →  Opposite side's liquidity = your TP
```

**NIFTY intraday walk-through:**
> PDH = 25,080. Price opens at 25,000, rallies to 25,090 (sweeps BSL), forms CHOCH on 5-min, retraces into a bearish FVG at 25,060 — you short with SL above 25,095, target SSL at PDL 24,850. That's the whole SMC playbook in one trade.
