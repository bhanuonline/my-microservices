package com.angle.trading.controller;

import com.angle.trading.dto.*;
import com.angle.trading.service.MockDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints consumed by the React frontend on port 5173.
 *
 * @RestController = @Controller + @ResponseBody on every method.
 * Return values are auto-serialized to JSON by Jackson (already on classpath).
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final MockDataService data;

    public ApiController(MockDataService data) {
        this.data = data;
    }

    @GetMapping("/holdings")
    public List<HoldingDto> holdings() {
        return data.holdings();
    }

    @GetMapping("/portfolio-series")
    public List<SeriesPoint> portfolioSeries() {
        return data.portfolioSeries();
    }

    @GetMapping("/watchlist")
    public List<WatchlistItemDto> watchlist() {
        return data.watchlist();
    }

    @GetMapping("/orders")
    public List<OrderDto> orders() {
        return data.orders();
    }

    @GetMapping("/profile")
    public ProfileDto profile() {
        return data.profile();
    }
}
