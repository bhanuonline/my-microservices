package com.angle.trading.hedge;

public enum HedgeStrategyType {
    PROTECTIVE_MARRIED_PUT,
    COVERED_CALL,
    COLLAR,
    FUTURES_HEDGE;

    public String getDisplayName() {
        return switch (this) {
            case PROTECTIVE_MARRIED_PUT -> "Protective / Married PUT";
            case COVERED_CALL           -> "Covered Call";
            case COLLAR                 -> "Collar";
            case FUTURES_HEDGE          -> "Futures Hedge";
        };
    }

    public String getPosition() {
        return switch (this) {
            case PROTECTIVE_MARRIED_PUT -> "Buy stock + Buy PUT";
            case COVERED_CALL           -> "Buy stock + Sell CALL";
            case COLLAR                 -> "Buy stock + Buy PUT + Sell CALL";
            case FUTURES_HEDGE          -> "Buy stock + Sell Future";
        };
    }
}
