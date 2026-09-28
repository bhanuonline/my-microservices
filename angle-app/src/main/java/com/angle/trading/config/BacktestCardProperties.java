package com.angle.trading.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Config for the Backtest card on the dashboard.
 *
 * enabled       = show/hide the card entirely
 * lookBackMin   = smaller number in the "N-M days" label
 * lookBackMax   = larger number in the label
 * defaultDays   = pre-filled value in the /admin/backtest form
 *
 * Change any of these in application.properties → refresh dashboard
 * (no restart with spring-boot-devtools).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "backtest.card")
public class BacktestCardProperties {

    /** Show the card on the dashboard. false = card is hidden. */
    private boolean enabled = true;

    /** Small end of the range shown on the card, e.g. 30 in "30–365 days". */
    private int lookBackMin = 30;

    /** Big end of the range, e.g. 365 in "30–365 days". */
    private int lookBackMax = 365;

    /** Default value pre-filled in the backtest form's "days back" input. */
    private int defaultDays = 30;
}
