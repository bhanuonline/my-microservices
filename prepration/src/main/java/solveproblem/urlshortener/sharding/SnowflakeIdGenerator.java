package solveproblem.urlshortener.sharding;

import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {

    private final long epoch = 1704067200000L; // custom epoch: Jan 1, 2024

    private final long machineId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    private final long sequenceBits = 12L;
    private final long machineIdBits = 10L;

    private final long maxSequence = ~(-1L << sequenceBits); // 4095
    private final long machineIdShift = sequenceBits;               // 12
    private final long timestampShift = sequenceBits + machineIdBits; // 22

    public SnowflakeIdGenerator() {
        // For local testing, machineId is fixed at 1 since we only run one app instance.
        // In production this would come from an env variable / pod ordinal.
        this.machineId = Long.parseLong(System.getenv().getOrDefault("MACHINE_ID", "1"));
        if (machineId < 0 || machineId > 1023) {
            throw new IllegalArgumentException("machineId must be between 0 and 1023");
        }
    }

    public synchronized long generateId() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate id.");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & maxSequence;
            if (sequence == 0) {
                while (timestamp <= lastTimestamp) {
                    timestamp = System.currentTimeMillis();
                }
            }
        } else {
            sequence = 0;
        }

        lastTimestamp = timestamp;

        return ((timestamp - epoch) << timestampShift)
                | (machineId << machineIdShift)
                | sequence;
    }
}
