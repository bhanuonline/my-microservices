package com.angle.trading.dto;

/**
 * Result status for each symbol in a bulk analysis response.
 *
 * <ul>
 *   <li>OK    — analysis computed successfully with fresh data</li>
 *   <li>STALE — analysis returned but from cached data (Angel API was rate-limited)</li>
 *   <li>ERROR — analysis failed (symbol not found, unexpected error)</li>
 * </ul>
 */
public enum AnalysisStatus {
    OK,
    STALE,
    ERROR
}
