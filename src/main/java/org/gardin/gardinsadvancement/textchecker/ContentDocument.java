package org.gardin.gardinsadvancement.textchecker;

import org.gardin.gardinsadvancement.advancementregister.GAdvancement;
import org.gardin.gardinsadvancement.tabcreater.Tab;

import java.util.List;

public class ContentDocument {
    private final List<Tab> tabs;
    private final List<GAdvancement> advancements;
    public ContentDocument(List<Tab> tabs, List<GAdvancement> advancements) {
        this.tabs = List.copyOf(tabs);
        this.advancements = List.copyOf(advancements);
    }
    public List<Tab> getTabs() {
        return tabs;
    }
    public List<GAdvancement> getAdvancements() {
        return advancements;
    }
}
