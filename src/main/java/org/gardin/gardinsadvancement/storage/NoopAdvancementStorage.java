package org.gardin.gardinsadvancement.storage;

import java.util.Map;
import java.util.UUID;

public class NoopAdvancementStorage implements AdvancementStorage {
    @Override
    public void initialize() {
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public Map<String, PlayerAdvancementRecord> loadPlayerRecords(UUID playerUuid) {
        return Map.of();
    }

    @Override
    public void saveAdvancementState(UUID playerUuid, String advancementKey, boolean finished, Long completedAt) {
    }

    @Override
    public void close() {
    }
}
