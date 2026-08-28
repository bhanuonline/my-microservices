package com.angle.trading.strategy;

import com.angle.trading.config.AnalysisProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Looks up strategies by name.
 *
 * Spring auto-injects every Strategy bean; we index them by {@link Strategy#name()}
 * so callers can pick one via config or a query param without hardcoding
 * concrete classes.
 */
@Slf4j
@Service
public class StrategyRegistry {

    private final Map<String, Strategy> byName;
    private final AnalysisProperties analysisProperties;

    public StrategyRegistry(List<Strategy> strategies, AnalysisProperties analysisProperties) {
        this.byName = strategies.stream()
                .collect(Collectors.toUnmodifiableMap(Strategy::name, Function.identity()));
        this.analysisProperties = analysisProperties;
        log.info("StrategyRegistry initialised with strategies: {}", byName.keySet());
    }

    /** Get a strategy by name. Throws IllegalArgumentException if unknown. */
    public Strategy get(String name) {
        Strategy s = byName.get(name);
        if (s == null) {
            throw new IllegalArgumentException(
                    "Unknown strategy: " + name + ". Available: " + byName.keySet());
        }
        return s;
    }

    /** The strategy named by analysis.strategy.default-strategy. */
    public Strategy getDefault() {
        String name = analysisProperties.getStrategy().getDefaultStrategy();
        return get(name);
    }

    public Set<String> availableNames() {
        return byName.keySet();
    }
}
