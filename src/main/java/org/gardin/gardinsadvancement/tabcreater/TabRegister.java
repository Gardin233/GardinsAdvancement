package org.gardin.gardinsadvancement.tabcreater;

import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import org.gardin.gardinsadvancement.conf.Gconfig;
import org.gardin.gardinsadvancement.util.GLogger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TabRegister {
    private final Map<String, AdvancementTab> tablist = new LinkedHashMap<>();
    private final List<Tab> tabs;
    private final UltimateAdvancementAPI api;
    private final Gconfig gconfig;

    public TabRegister(UltimateAdvancementAPI api, List<Tab> tabs, Gconfig gconfig) {
        this.api = api;
        this.tabs = new ArrayList<>(tabs);
        this.gconfig = gconfig;
    }

    public void init() {
        for (Tab t : tabs) {
            create(t);
        }
    }

    public AdvancementTab create(Tab t) {
        AdvancementTab tab = api.isAdvancementTabRegistered(t.getNameSpace())
                ? api.getAdvancementTab(t.getNameSpace())
                : api.createAdvancementTab(t.getNameSpace());
        tablist.put(t.getNameSpace(), tab);
        GLogger.debug("注册 Tab: " + t.getNameSpace());
        return tab;
    }

    public List<Tab> getTabs() {
        return List.copyOf(this.tabs);
    }

    public Map<String, AdvancementTab> getTabList() {
        return Map.copyOf(this.tablist);
    }
}
