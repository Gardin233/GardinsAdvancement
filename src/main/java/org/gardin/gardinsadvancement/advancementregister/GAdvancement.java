package org.gardin.gardinsadvancement.advancementregister;

import java.util.List;

public class GAdvancement {
    private final String tab;
    private final String parentId;
    private final String id;
    private final AdvancementData data;
    private final AdvancementProgress progress;
    private final String type;
    private final List<String> condition;
    private final List<String> commands;

    public GAdvancement(
            String tab,
            String parentId,
            String id,
            String type,
            AdvancementData data,
            AdvancementProgress progress,
            List<String> condition,
            List<String> commands
    ) {
        this.tab = tab;
        this.parentId = parentId;
        this.id = id;
        this.type = type;
        this.progress = progress;
        this.condition = List.copyOf(condition);
        this.commands = List.copyOf(commands);
        this.data = data;
    }

    public String getTab() {
        return tab;
    }

    public String getId() {
        return id;
    }

    public String getType() {
        return this.type;
    }

    public boolean isRoot() {
        return "root".equalsIgnoreCase(type);
    }

    public List<String> getCondition() {
        return this.condition;
    }

    public List<String> getCommands() {
        return commands;
    }

    public String getParentId() {
        return parentId;
    }

    public AdvancementData getData() {
        return data;
    }

    public AdvancementProgress getProgress() {
        return progress;
    }

}
