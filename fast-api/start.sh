#!/bin/bash
# ─────────────────────────────────────────────────────────────────────────────
# start.sh — Run the API with all JVM performance flags
#
# Requirements: Java 21+
# Usage:        chmod +x start.sh && ./start.sh
# ─────────────────────────────────────────────────────────────────────────────

APP_JAR="target/fast-api-1.0.0.jar"
ENV="${APP_ENV:-prod}"

java \
  # ── JVM Mode ──────────────────────────────────────────────────────────────
  -server \

  # ── Garbage Collector ─────────────────────────────────────────────────────
  # ZGC: sub-1ms GC pauses — critical for consistent low-latency responses
  -XX:+UseZGC \
  -XX:+ZGenerational \               # Java 21+ — generational ZGC, more efficient

  # ── Memory ────────────────────────────────────────────────────────────────
  # Set min = max to avoid heap resize pauses during traffic spikes
  -Xms512m \
  -Xmx2g \
  -XX:+AlwaysPreTouch \              # Pre-allocate all heap pages at startup
                                     # Prevents memory page faults during first traffic

  # ── Compiler ──────────────────────────────────────────────────────────────
  -XX:+OptimizeStringConcat \        # Optimize String concatenation bytecode
  -XX:+UseStringDeduplication \      # Deduplicate identical String objects in heap

  # ── JIT Compiler Tuning ───────────────────────────────────────────────────
  -XX:ReservedCodeCacheSize=256m \   # More space for JIT-compiled code
  -XX:+TieredCompilation \           # Fast C1 compile first, then C2 for hot paths

  # ── Thread Stack ──────────────────────────────────────────────────────────
  # Virtual threads use tiny stacks — reduce default to save memory
  -Xss256k \

  # ── Diagnostics (optional — remove in prod) ───────────────────────────────
  # -XX:+PrintGCDetails \
  # -XX:+PrintGCDateStamps \
  # -Xloggc:logs/gc.log \

  # ── App ───────────────────────────────────────────────────────────────────
  -Dspring.profiles.active=${ENV} \
  -jar ${APP_JAR}
