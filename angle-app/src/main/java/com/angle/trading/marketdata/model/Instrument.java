package com.angle.trading.marketdata.model;

import com.angle.trading.broker.model.Exchange;
import com.angle.trading.broker.model.OptionType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * One row from Angel's scrip master file
 * (https://openapi.angelbroking.com/OpenAPI_File/files/OpenAPIScripMaster.json).
 *
 * Angel returns everything as strings; we keep them as strings on the wire
 * and expose typed accessors (strikeValue, expiryDate, optionType) for
 * anything strategy code needs.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Instrument(
        String token,
        String symbol,
        String name,
        String expiry,
        String strike,
        String lotsize,
        @JsonProperty("instrumenttype") String instrumentType,
        @JsonProperty("exch_seg") String exchSeg,
        @JsonProperty("tick_size") String tickSize
) {

    // Angel returns the month in ALL-CAPS ("15SEP2026"). Java's default MMM
    // pattern expects title-case ("Sep"), so we build the formatter with
    // parseCaseInsensitive() to accept SEP / Sep / sep alike.
    private static final DateTimeFormatter EXPIRY_FMT =
            new DateTimeFormatterBuilder()
                    .parseCaseInsensitive()
                    .appendPattern("ddMMMuuuu")
                    .toFormatter(Locale.ENGLISH);

    /** Parsed exchange enum. */
    public Exchange exchange() {
        return Exchange.valueOf(exchSeg);
    }

    /** Expiry as LocalDate. Only meaningful for F&O contracts. */
    public LocalDate expiryDate() {
        if (expiry == null || expiry.isBlank()) return null;
        try {
            return LocalDate.parse(expiry, EXPIRY_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Strike value as BigDecimal. Angel encodes strike as either
     *   "24700.000000" (already in rupees) or "2470000" (paise ×100).
     * We normalise to rupees.
     */
    public BigDecimal strikeValue() {
        if (strike == null || strike.isBlank()) return null;
        BigDecimal raw = new BigDecimal(strike);
        // Heuristic: values > 100000 for a Nifty-range strike must be paise-encoded.
        if (raw.stripTrailingZeros().scale() <= 0 && raw.compareTo(BigDecimal.valueOf(100_000)) > 0) {
            return raw.movePointLeft(2);
        }
        return raw;
    }

    /** Option type parsed from the symbol suffix (CE/PE) or null for non-options. */
    public OptionType optionType() {
        if (symbol == null) return null;
        if (symbol.endsWith("CE")) return OptionType.CE;
        if (symbol.endsWith("PE")) return OptionType.PE;
        return null;
    }

    public int lotSizeInt() {
        return lotsize == null || lotsize.isBlank() ? 1 : Integer.parseInt(lotsize);
    }

    /**
     * True for any options contract. Angel uses:
     *   OPTIDX — index options (NIFTY, BANKNIFTY, ...)
     *   OPTSTK — stock options
     *   OPTCUR — currency options (USDINR, ...)
     *   OPTFUT — options on futures (rare)
     *   OPTBLN — options on commodity futures (crude, gold ...)
     */
    public boolean isOption() {
        return instrumentType != null && instrumentType.startsWith("OPT");
    }

    /**
     * True for any futures contract. Angel uses:
     *   FUTIDX — index futures (NIFTY, BANKNIFTY, ...)
     *   FUTSTK — stock futures
     *   FUTCOM — commodity futures (CRUDEOIL, GOLD, SILVER, ...)   ← MCX
     *   FUTCUR — currency futures (USDINR, ...)                    ← CDS
     *   FUTBAS — basis futures / other future types
     *   FUTIRT — interest rate futures
     */
    public boolean isFuture() {
        return instrumentType != null && instrumentType.startsWith("FUT");
    }
}
