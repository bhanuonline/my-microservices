package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Market calendar — holidays when NSE/BSE/MCX are closed.
 *
 * Update once per year when the exchange publishes the official list.
 * Sources:
 *   NSE — https://www.nseindia.com/resources/exchange-communication-holidays
 *   BSE — https://www.bseindia.com/static/markets/marketinfo/listholi.aspx
 *
 * Format (application.properties):
 *   bias.calendar.holidays[0]=2026-01-26
 *   bias.calendar.holidays[1]=2026-10-02
 *   ...
 *
 * OR comma-separated (Spring auto-converts List<LocalDate>):
 *   bias.calendar.holidays=2026-01-26,2026-10-02,...
 *
 * Empty list → only weekend rule applies.
 */
@Data
@Configuration
@RefreshScope
@ConfigurationProperties(prefix = "bias.calendar")
public class CalendarProperties {

    /** Master toggle. false = ignore this list, only weekends skip. */
    private boolean enabled = true;

    /** Dates when markets are closed. Sat/Sun already handled separately. */
    private List<LocalDate> holidays = new ArrayList<>();
}
