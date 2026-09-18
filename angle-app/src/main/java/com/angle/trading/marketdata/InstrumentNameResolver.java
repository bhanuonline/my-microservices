package com.angle.trading.marketdata;

import com.angle.trading.marketdata.model.Instrument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns numeric symbol tokens into human-readable labels.
 *
 * Wraps {@link InstrumentMasterService} with:
 *   - Small in-memory cache (tokens never change name during runtime)
 *   - Well-known-index override (Angel scrip master uses short/cryptic names
 *     for indices, so we hand-map the popular ones for readability)
 *   - Safe fallback — if resolution fails, returns the token itself
 *
 * Called from paper session sources and the bias dashboard to display
 * "Nifty 50" instead of "99926000".
 */
@Service
@RequiredArgsConstructor
public class InstrumentNameResolver {

    /** Popular indices with fixed tokens — labelled explicitly for readability. */
    private static final Map<String, String> WELL_KNOWN = Map.of(
            "99926000", "Nifty 50",
            "99926009", "Bank Nifty",
            "99926013", "Nifty Next 50",
            "99926037", "Fin Nifty",
            "99926011", "Nifty Midcap 50",
            "99919000", "Sensex",
            "99919011", "India VIX"
    );

    private final InstrumentMasterService instrumentMasterService;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /** Human-readable name for a token, or the token itself if not resolvable. */
    public String resolve(String symbolToken) {
        if (symbolToken == null || symbolToken.isBlank()) return "—";
        String cached = cache.get(symbolToken);
        if (cached != null) return cached;

        String resolved = doResolve(symbolToken);
        cache.put(symbolToken, resolved);
        return resolved;
    }

    /** Pretty-print for logs: "Nifty 50 (99926000)". */
    public String describe(String symbolToken) {
        String name = resolve(symbolToken);
        return symbolToken.equals(name) ? symbolToken : name + " (" + symbolToken + ")";
    }

    private String doResolve(String symbolToken) {
        String wellKnown = WELL_KNOWN.get(symbolToken);
        if (wellKnown != null) return wellKnown;

        Optional<Instrument> byToken = instrumentMasterService.findByToken(symbolToken);
        if (byToken.isEmpty()) return symbolToken;

        Instrument i = byToken.get();
        // Options / futures: prefer human "SYMBOL" (e.g. NIFTY25SEP2624700CE)
        // Equity: "name" is the ticker (e.g. RELIANCE)
        String name = i.symbol();
        if (name == null || name.isBlank()) name = i.name();
        return (name == null || name.isBlank()) ? symbolToken : name;
    }
}
