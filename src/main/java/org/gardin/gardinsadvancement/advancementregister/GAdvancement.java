package org.gardin.gardinsadvancement.advancementregister;

import java.util.List;

public class GAdvancement {
    private final String tab;
    private final String parentId;
    private final String id;
    private final AdvancementData data;
    private final String type;
    private final List<String> condition;

    public GAdvancement(
            String tab,
            String parentId,
            String id,
            String type,
            AdvancementData data,
            List<String> condition
    ) {
        this.tab = tab;
        this.parentId = parentId;
        this.id = id;
        this.type = type;
        this.condition = List.copyOf(condition);
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

    public String getParentId() {
        return parentId;
    }

    public AdvancementData getData() {
        return data;
    }

}
