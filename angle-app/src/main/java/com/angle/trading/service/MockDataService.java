package com.angle.trading.service;

import com.angle.trading.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.IntStream;

/**
 * In-memory fake data. This is a placeholder — later we'll replace with a
 * database-backed repository, and the controllers won't need to change.
 */
@Service
public class MockDataService {

    public List<HoldingDto> holdings() {
        return List.of(
                new HoldingDto("NBEAM", "Northbeam Composite", 42,   118.20, 128.42,  2.14),
                new HoldingDto("AUR",   "Auralite Metals",     120,   58.00,  64.10, -0.42),
                new HoldingDto("VLTX",  "Voltix Energy",        15,  195.40, 212.87,  1.05),
                new HoldingDto("BTC",   "Bitcoin",            0.35, 58200.0, 61240.0, 3.10),
                new HoldingDto("CORV",  "Corvex Bio",          200,   12.50,  11.90, -1.80)
        );
    }

    public List<SeriesPoint> portfolioSeries() {
        return IntStream.range(0, 30)
                .mapToObj(i -> new SeriesPoint(
                        "D" + (i + 1),
                        45000 + Math.round(Math.sin(i * 0.4) * 2000 + i * 180)
                ))
                .toList();
    }

    public List<WatchlistItemDto> watchlist() {
        return List.of(
                new WatchlistItemDto("NBEAM", "Northbeam Composite", 128.42,   2.14),
                new WatchlistItemDto("AUR",   "Auralite Metals",      64.10,  -0.42),
                new WatchlistItemDto("VLTX",  "Voltix Energy",       212.87,   1.05),
                new WatchlistItemDto("BTC",   "Bitcoin",           61240.00,   3.10),
                new WatchlistItemDto("TSLA",  "Tesla",               245.10,  -0.85),
                new WatchlistItemDto("AAPL",  "Apple",               189.50,   0.62)
        );
    }

    public List<OrderDto> orders() {
        return List.of(
                new OrderDto("O-24019", "NBEAM", "BUY",  10,   128.40, "FILLED",    "2026-07-30 10:12"),
                new OrderDto("O-24018", "AUR",   "SELL", 50,    64.20, "FILLED",    "2026-07-29 15:44"),
                new OrderDto("O-24017", "BTC",   "BUY",  0.05, 60800,  "PENDING",   "2026-07-29 09:22"),
                new OrderDto("O-24016", "VLTX",  "BUY",  5,    210.00, "CANCELLED", "2026-07-28 12:00"),
                new OrderDto("O-24015", "CORV",  "BUY",  200,   12.50, "FILLED",    "2026-07-27 11:33")
        );
    }

    public ProfileDto profile() {
        return new ProfileDto(
                "Alex Kim",
                "alex.kim@example.com",
                "Mar 2024",
                "VERIFIED",
                "Silver"
        );
    }
}
