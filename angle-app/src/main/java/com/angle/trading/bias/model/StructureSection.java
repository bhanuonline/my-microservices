package com.angle.trading.bias.model;

import com.angle.trading.marketstructure.model.Direction;
import com.angle.trading.marketstructure.model.StructureEvent;
import com.angle.trading.marketstructure.model.SwingPoint;

/**
 * Section 9 of the bias sheet — market structure from SMC analyzer.
 */
public record StructureSection(
        Direction bias,                 // BULLISH / BEARISH / null
        StructureEvent lastEvent,       // BOS or CHoCH — null if no events yet
        SwingPoint latestSwingHigh,
        SwingPoint latestSwingLow
) {}
