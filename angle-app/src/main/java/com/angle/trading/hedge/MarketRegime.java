package com.angle.trading.hedge;

public enum MarketRegime {
    STRONG_BULLISH("Strong Bullish",   "ADX > 25, RSI 50–65, Price > EMA-21"),
    MILD_BULLISH  ("Mild Bullish",     "ADX 20–25, RSI 45–55, Price > EMA-21"),
    SIDEWAYS      ("Sideways/Ranging", "ADX < 20, RSI 40–60"),
    MILD_BEARISH  ("Mild Bearish",     "RSI 35–45 or Price near EMA-21"),
    STRONG_BEARISH("Strong Bearish",   "ADX > 25, RSI < 35, Price < EMA-21");

    private final String label;
    private final String description;

    MarketRegime(String label, String description) {
        this.label       = label;
        this.description = description;
    }

    public String getLabel()       { return label; }
    public String getDescription() { return description; }
}
