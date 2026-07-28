package org.gardin.gardinsadvancement.advancementregister;

public record AdvancementProgress(
        String placeholder,
        int max
) {
    public int getDisplayMaxProgression() {
        return max;
    }
}
