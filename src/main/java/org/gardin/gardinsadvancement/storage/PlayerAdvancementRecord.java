package org.gardin.gardinsadvancement.storage;

public record PlayerAdvancementRecord(
        String advancementKey,
        boolean finished,
        Long completedAt
) {
}
