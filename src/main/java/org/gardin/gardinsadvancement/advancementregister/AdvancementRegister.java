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
        GLogger.info("&f开始注册 Advancement，共待处理 " + gAdvancements.size() + " 个定义");
        Map<String, List<GAdvancement>> grouped = gAdvancements.stream().collect(
                Collectors.groupingBy(
                        GAdvancement::getTab,
                        LinkedHashMap::new,
                        Collectors.toList()
                )
        );
        for (Map.Entry<String, List<GAdvancement>> entry : grouped.entrySet()) {
            GLogger.debug("准备注册 Tab " + entry.getKey() + " 下的 " + entry.getValue().size() + " 个进度");
            registerTab(entry.getKey(), entry.getValue());
        }
        GLogger.info("&fAdvancement 注册流程结束，共注册 " + advancements.size() + " 个进度对象");
    }

    private void registerTab(String tabId, List<GAdvancement> definitions) {
        AdvancementTab tab = advancementTabs.get(tabId);
        if (tab == null) {
            GLogger.error("找不到已注册的 Tab: " + tabId);
            return;
        }
        Tab tabMeta = tabs.get(tabId);
        List<GAdvancement> rootsInTab = definitions.stream()
                .filter(GAdvancement::isRoot)
                .toList();
        if (rootsInTab.isEmpty()) {
            GLogger.error("Tab " + tabId + " 缺少 root 进度，已跳过");
            return;
        }
        if (rootsInTab.size() > 1) {
            GLogger.warning("Tab " + tabId + " 存在多个 root，仅使用第一个");
        }
        GAdvancement rootDefinition = rootsInTab.getFirst();
        GLogger.info("&f注册 Tab " + tabId + " 的 root 进度: " + rootDefinition.getId());
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
                    GLogger.debug("进度 " + buildKey(tabId, definition.getId())
                            + " 等待父进度 " + definition.getParentId() + " 先完成注册");
                    continue;
                }
                BaseAdvancement advancement = createAdvancement(definition, parent);
                created.put(definition.getId(), advancement);
                advancements.put(buildKey(tabId, definition.getId()), advancement);
                children.add(advancement);
                GLogger.info("&f已注册普通进度: " + buildKey(tabId, definition.getId())
                        + " -> parent=" + definition.getParentId());
                iterator.remove();
                progressed = true;
            }
        } while (progressed && !unresolved.isEmpty());

        for (GAdvancement definition : unresolved) {
            GLogger.error("进度 " + buildKey(tabId, definition.getId()) + " 找不到父进度 " + definition.getParentId());
        }
        tab.registerAdvancements(rootAdvancement, children);
        configureTabDisplay(tab, tabMeta, rootAdvancement);
        GLogger.debug("完成注册 Tab " + tabId + "，共 " + (children.size() + 1) + " 个进度");
    }

    private String buildKey(String tabId, String advancementId) {
        return tabId + ":" + advancementId;
    }

    public RootAdvancement createRootAdvancement(AdvancementTab tab, Tab tabMeta, GAdvancement gAdv) {
        AdvancementDisplay ad = gAdv.getData().createDisplay();
        String background = tabMeta == null
                ? gconfig.getDefaultTabBackground()
                : tabMeta.getBackgroundTexture();
        GLogger.debug("创建 RootAdvancement: tab=" + tab.getNamespace()
                + ", id=" + gAdv.getId()
                + ", background=" + background);
        return new RootAdvancement(
                tab,
                gAdv.getId(),
                ad,
                background
        );
    }

    public BaseAdvancement createAdvancement(GAdvancement gAdv, Advancement parent) {
        AdvancementDisplay display = gAdv.getData().createDisplay();
        GLogger.debug("创建 BaseAdvancement: id=" + gAdv.getId()
                + ", parent=" + gAdv.getParentId()
                + ", tab=" + gAdv.getTab());
        return new BaseAdvancement(gAdv.getId(), display, parent);
    }

    public Map<String, Advancement> getRegisteredAdvancements() {
        return Map.copyOf(advancements);
    }

    private void configureTabDisplay(AdvancementTab tab, Tab tabMeta, RootAdvancement rootAdvancement) {
        TabDisplayMode displayMode = tabMeta == null ? TabDisplayMode.DIRECT : tabMeta.getDisplayMode();
        switch (displayMode) {
            case DIRECT -> {
                tab.automaticallyShowToPlayers();
                GLogger.info("&fTab " + tab.getNamespace() + " 已启用直接显示模式");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!tab.isShownTo(player)) {
                        tab.showTab(player);
                    }
                }
            }
            case INDIRECT -> {
                tab.automaticallyGrantRootAdvancement();
                GLogger.info("&fTab " + tab.getNamespace() + " 已启用间接显示模式，将自动授予 root");
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!rootAdvancement.isGranted(player)) {
                        tab.grantRootAdvancement(player, false);
                    }
                }
            }
            case MANUAL -> GLogger.info("&fTab " + tab.getNamespace() + " 已启用手动显示模式，不自动展示");
        }
    }
}
