package org.gardin.gardinsadvancement.advancementregister;

import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.UltimateAdvancementAPI;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.BaseAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.RootAdvancement;
import com.fren_gor.ultimateAdvancementAPI.advancement.display.AdvancementDisplay;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.gardin.gardinsadvancement.conf.Gconfig;
import org.gardin.gardinsadvancement.tabcreater.Tab;
import org.gardin.gardinsadvancement.tabcreater.TabDisplayMode;
import org.gardin.gardinsadvancement.textchecker.ContentDocument;
import org.gardin.gardinsadvancement.util.GLogger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AdvancementRegister {
    private final UltimateAdvancementAPI api;
    private final Gconfig gconfig;
    private final List<GAdvancement> gAdvancements;
    private final Map<String, AdvancementTab> advancementTabs;
    private final Map<String, Tab> tabs;
    private final Map<String, RootAdvancement> roots = new LinkedHashMap<>();
    private final Map<String, Advancement> advancements = new LinkedHashMap<>();

    public AdvancementRegister(
            UltimateAdvancementAPI api,
            Gconfig gconfig,
            ContentDocument contentDocument,
            Map<String, AdvancementTab> advancementTabs
    ) {
        this.api = api;
        this.gconfig = gconfig;
        this.gAdvancements = new ArrayList<>(contentDocument.getAdvancements());
        this.advancementTabs = new LinkedHashMap<>(advancementTabs);
        this.tabs = contentDocument.getTabs().stream().collect(
                Collectors.toMap(
                        Tab::getNameSpace,
                        tab -> tab,
                        (left, right) -> left,
                        LinkedHashMap::new
                )
        );
    }

    public void init() {
        GLogger.infoLang("advancement.register_start", gAdvancements.size());
        Map<String, List<GAdvancement>> grouped = gAdvancements.stream().collect(
                Collectors.groupingBy(
                        GAdvancement::getTab,
                        LinkedHashMap::new,
                        Collectors.toList()
                )
        );
        for (Map.Entry<String, List<GAdvancement>> entry : grouped.entrySet()) {
            GLogger.debugLang("advancement.preparing_tab", entry.getKey(), entry.getValue().size());
            registerTab(entry.getKey(), entry.getValue());
        }
        GLogger.infoLang("advancement.register_complete", advancements.size());
    }

    private void registerTab(String tabId, List<GAdvancement> definitions) {
        AdvancementTab tab = advancementTabs.get(tabId);
        if (tab == null) {
            GLogger.errorLang("advancement.tab_missing", tabId);
            return;
        }
        Tab tabMeta = tabs.get(tabId);
        List<GAdvancement> rootsInTab = definitions.stream()
                .filter(GAdvancement::isRoot)
                .toList();
        if (rootsInTab.isEmpty()) {
            GLogger.errorLang("advancement.root_missing", tabId);
            return;
        }
        if (rootsInTab.size() > 1) {
            GLogger.warningLang("advancement.multiple_roots", tabId);
        }
        GAdvancement rootDefinition = rootsInTab.getFirst();
        GLogger.infoLang("advancement.root_registered", tabId, rootDefinition.getId());
        RootAdvancement rootAdvancement = createRootAdvancement(tab, tabMeta, rootDefinition);
        roots.put(buildKey(tabId, rootDefinition.getId()), rootAdvancement);
        advancements.put(buildKey(tabId, rootDefinition.getId()), rootAdvancement);

        Map<String, Advancement> created = new LinkedHashMap<>();
        created.put(rootDefinition.getId(), rootAdvancement);

        List<GAdvancement> unresolved = definitions.stream()
                .filter(definition -> !definition.isRoot())
                .sorted(Comparator.comparing(GAdvancement::getId))
                .collect(Collectors.toCollection(ArrayList::new));

        LinkedHashSet<BaseAdvancement> children = new LinkedHashSet<>();
        boolean progressed;
        do {
            progressed = false;
            var iterator = unresolved.iterator();
            while (iterator.hasNext()) {
                GAdvancement definition = iterator.next();
                Advancement parent = created.get(definition.getParentId());
                if (parent == null) {
                    GLogger.debugLang(
                            "advancement.waiting_parent",
                            buildKey(tabId, definition.getId()),
                            definition.getParentId()
                    );
                    continue;
                }
                BaseAdvancement advancement = createAdvancement(definition, parent);
                created.put(definition.getId(), advancement);
                advancements.put(buildKey(tabId, definition.getId()), advancement);
                children.add(advancement);
                GLogger.infoLang(
                        "advancement.child_registered",
                        buildKey(tabId, definition.getId()),
                        definition.getParentId()
                );
                iterator.remove();
                progressed = true;
            }
        } while (progressed && !unresolved.isEmpty());

        for (GAdvancement definition : unresolved) {
            GLogger.errorLang(
                    "advancement.missing_parent",
                    buildKey(tabId, definition.getId()),
                    definition.getParentId()
            );
        }
        tab.registerAdvancements(rootAdvancement, children);
        configureTabDisplay(tab, tabMeta, rootAdvancement);
        GLogger.debugLang("advancement.tab_complete", tabId, children.size() + 1);
    }

    private String buildKey(String tabId, String advancementId) {
        return tabId + ":" + advancementId;
    }

    public RootAdvancement createRootAdvancement(AdvancementTab tab, Tab tabMeta, GAdvancement gAdv) {
        AdvancementDisplay ad = gAdv.getData().createDisplay();
        String background = tabMeta == null
                ? gconfig.getDefaultTabBackground()
                : tabMeta.getBackgroundTexture();
        GLogger.debugLang("advancement.root_created", tab.getNamespace(), gAdv.getId(), background);
        AdvancementProgress progress = gAdv.getProgress();
        if (progress == null) {
            return new RootAdvancement(
                    tab,
                    gAdv.getId(),
                    ad,
                    background
            );
        }
        GLogger.debugLang("advancement.progress_enabled", buildKey(gAdv.getTab(), gAdv.getId()), progress.placeholder(), progress.max());
        return new RootAdvancement(
                tab,
                gAdv.getId(),
                ad,
                background,
                progress.getDisplayMaxProgression()
        );
    }

    public BaseAdvancement createAdvancement(GAdvancement gAdv, Advancement parent) {
        AdvancementDisplay display = gAdv.getData().createDisplay();
        GLogger.debugLang("advancement.base_created", gAdv.getId(), gAdv.getParentId(), gAdv.getTab());
        AdvancementProgress progress = gAdv.getProgress();
        if (progress == null) {
            return new BaseAdvancement(gAdv.getId(), display, parent);
        }
        GLogger.debugLang("advancement.progress_enabled", buildKey(gAdv.getTab(), gAdv.getId()), progress.placeholder(), progress.max());
        return new BaseAdvancement(gAdv.getId(), display, parent, progress.getDisplayMaxProgression());
    }

    public Map<String, Advancement> getRegisteredAdvancements() {
        return Map.copyOf(advancements);
    }

    public Advancement findRegisteredAdvancement(String advancementKey) {
        if (advancementKey == null || advancementKey.isBlank()) {
            return null;
        }
        return advancements.get(advancementKey.trim());
    }

    public int countRegisteredAdvancements() {
        return advancements.size();
    }

    public int countRegisteredAdvancements(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return 0;
        }
        String normalized = namespace.trim();
        int count = 0;
        for (String key : advancements.keySet()) {
            if (key.regionMatches(true, 0, normalized, 0, normalized.length())
                    && key.length() > normalized.length()
                    && key.charAt(normalized.length()) == ':') {
                count++;
            }
        }
        return count;
    }

    public int countFinishedAdvancements(Player player) {
        if (player == null) {
            return 0;
        }
        int count = 0;
        for (Advancement advancement : advancements.values()) {
            if (advancement.isGranted(player)) {
                count++;
            }
        }
        return count;
    }

    private void configureTabDisplay(AdvancementTab tab, Tab tabMeta, RootAdvancement rootAdvancement) {
        TabDisplayMode displayMode = tabMeta == null ? TabDisplayMode.DIRECT : tabMeta.getDisplayMode();
        switch (displayMode) {
            case DIRECT -> {
                tab.automaticallyShowToPlayers();
                GLogger.infoLang("advancement.display_direct", tab.getNamespace());
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!tab.isShownTo(player)) {
                        tab.showTab(player);
                    }
                }
            }
            case INDIRECT -> {
                tab.automaticallyGrantRootAdvancement();
                GLogger.infoLang("advancement.display_indirect", tab.getNamespace());
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!rootAdvancement.isGranted(player)) {
                        tab.grantRootAdvancement(player, false);
                    }
                }
            }
            case MANUAL -> GLogger.infoLang("advancement.display_manual", tab.getNamespace());
        }
    }
}
