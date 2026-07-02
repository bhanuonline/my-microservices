package solveproblem.urlshortener.sharding;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.TreeMap;

@Component
public class ConsistentHashRing {

    private final TreeMap<Long, String> ring = new TreeMap<>();
    private final int virtualNodesPerShard = 100; // more virtual nodes = better balance

    public ConsistentHashRing() {
        // Local test setup: 4 shards, matching the 4 Docker containers.
        List<String> shardIds = List.of("shard-1", "shard-2", "shard-3", "shard-4");
        for (String shardId : shardIds) {
            addShard(shardId);
        }
    }

    public void addShard(String shardId) {
        for (int i = 0; i < virtualNodesPerShard; i++) {
            long hash = md5Hash(shardId + "-VN" + i);
            ring.put(hash, shardId);
        }
    }

    public void removeShard(String shardId) {
        for (int i = 0; i < virtualNodesPerShard; i++) {
            long hash = md5Hash(shardId + "-VN" + i);
            ring.remove(hash);
        }
    }

    /**
     * Given a key (short_code), find which shard it belongs to
     * by walking clockwise on the ring to the nearest shard point.
     */
    public String getShardForKey(String key) {
        if (ring.isEmpty()) {
            throw new IllegalStateException("No shards registered in the ring");
        }
        long hash = md5Hash(key);
        var entry = ring.ceilingEntry(hash); // first shard point >= hash
        if (entry == null) {
            entry = ring.firstEntry(); // wrap around the ring
        }
        return entry.getValue();
    }

    // Using MD5 for a well-distributed hash (better than String.hashCode() for this purpose)
    private long md5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            // take first 8 bytes of the digest to form a long
            long hash = 0L;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (digest[i] & 0xFF);
            }
            return Math.abs(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
}
