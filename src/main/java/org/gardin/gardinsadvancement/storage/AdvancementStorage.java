package org.gardin.gardinsadvancement.storage;

import java.util.Map;
import java.util.UUID;

public interface AdvancementStorage {
    void initialize();

    boolean isAvailable();

    Map<String, PlayerAdvancementRecord> loadPlayerRecords(UUID playerUuid);

    void saveAdvancementState(UUID playerUuid, String advancementKey, boolean finished, Long completedAt);

    void close();
}
