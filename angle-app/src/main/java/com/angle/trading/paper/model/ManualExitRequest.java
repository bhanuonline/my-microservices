package com.angle.trading.paper.model;

import java.math.BigDecimal;

/**
 * Body for POST /api/paper/sessions/{id}/exit
 *
 *   exitPrice — the price at which to close the open position (required)
 */
public record ManualExitRequest(BigDecimal exitPrice) {}
