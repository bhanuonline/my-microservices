package com.angle.trading.ai.agent;

import java.util.Map;

/**
 * One callable tool the research agent can give to Claude.
 *
 * Claude sees: name + description + inputSchema (JSON Schema).
 * When Claude calls it, execute(args) runs and the result goes back to Claude.
 */
public interface ResearchTool {
    String name();
    String description();
    Map<String, Object> inputSchema();
    String execute(Map<String, Object> args);
}
