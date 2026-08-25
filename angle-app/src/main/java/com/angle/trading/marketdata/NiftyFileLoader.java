package com.angle.trading.marketdata;

import com.angle.trading.broker.model.Candle;
import com.angle.trading.config.AnalysisProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads historical Nifty candles from a text file for backtesting.
 *
 * Expected CSV format (one line per candle):
 *   date,open,high,low,close,volume
 *   2024-01-01,21750.5,21780.2,21740.1,21770.8,12345
 *
 * Lines starting with '#' or empty lines are ignored.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NiftyFileLoader {

    private final AnalysisProperties analysisProperties;
    private final ResourceLoader resourceLoader;

    public List<Candle> load() {
        String location = analysisProperties.getNifty().getDataFile();
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("Nifty data file not found: " + location);
        }

        List<Candle> candles = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                try {
                    candles.add(parseLine(trimmed));
                } catch (RuntimeException e) {
                    log.warn("Skipping bad line {}: '{}' ({})", lineNo, trimmed, e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + location, e);
        }
        log.info("Loaded {} candles from {}", candles.size(), location);
        return candles;
    }

    private static Candle parseLine(String line) {
        String[] parts = line.split(",");
        if (parts.length < 6) {
            throw new IllegalArgumentException("Expected 6 columns, got " + parts.length);
        }
        LocalDate date = LocalDate.parse(parts[0].trim());
        return new Candle(
                date.atStartOfDay(ZoneOffset.UTC).toInstant(),
                new BigDecimal(parts[1].trim()),
                new BigDecimal(parts[2].trim()),
                new BigDecimal(parts[3].trim()),
                new BigDecimal(parts[4].trim()),
                Long.parseLong(parts[5].trim())
        );
    }
}
