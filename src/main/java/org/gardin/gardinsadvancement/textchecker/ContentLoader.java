package org.gardin.gardinsadvancement.textchecker;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.gardin.gardinsadvancement.advancementregister.AdvancementData;
import org.gardin.gardinsadvancement.advancementregister.GAdvancement;
import org.gardin.gardinsadvancement.conf.Gconfig;
import org.gardin.gardinsadvancement.tabcreater.Tab;
import org.gardin.gardinsadvancement.tabcreater.TabDisplayMode;
import org.gardin.gardinsadvancement.util.GLogger;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class ContentLoader {
    private final JavaPlugin plugin;
    private final Gconfig gconfig;

    public ContentLoader(JavaPlugin plugin, Gconfig gconfig) {
        this.plugin = plugin;
        this.gconfig = gconfig;
    }

    public ContentDocument init() {
        File folder = prepareContentFolder();
        GLogger.info("&f开始扫描内容目录: " + folder.getAbsolutePath());
        List<Tab> tabs = new ArrayList<>();
        List<GAdvancement> advancements = new ArrayList<>();
        Set<String> knownTabs = new HashSet<>();
        Set<String> knownAdvancements = new HashSet<>();
        List<String> files = loadFiles(folder, tabs, advancements, knownTabs, knownAdvancements);
        GLogger.info("&f内容目录扫描完成，共读取 " + files.size() + " 个 yml 文件");
        GLogger.debug("已加载文件: " + files);
        return new ContentDocument(tabs, advancements);
    }

    public List<String> loadFiles(
            File folder,
            List<Tab> tabs,
            List<GAdvancement> advancements,
            Set<String> knownTabs,
            Set<String> knownAdvancements
    ) {
        List<String> files = new ArrayList<>();
        if (!folder.exists()) {
            folder.mkdirs();
        }
        File[] ymlFiles = folder.listFiles(
                file -> file.getName().endsWith(".yml")
        );
        if (ymlFiles == null) {
            GLogger.warning("内容目录无法读取: " + folder.getAbsolutePath());
            return files;
        }
        GLogger.debug("检测到 " + ymlFiles.length + " 个候选内容文件");
        for (File file : ymlFiles) {
            GLogger.info("&f读取内容文件: " + file.getName());
            YamlConfiguration yaml =
                    YamlConfiguration.loadConfiguration(file);
            parse(yaml, file.getName(), tabs, advancements, knownTabs, knownAdvancements);
            files.add(file.getName());
        }
        return files;
    }

    private File prepareContentFolder() {
        File folder = new File(plugin.getDataFolder(), gconfig.getContentFolder());
        if (!folder.exists()) {
            folder.mkdirs();
            GLogger.info("&f内容目录不存在，已创建: " + folder.getAbsolutePath());
        }
        if (gconfig.isCopyExampleContent() && "content".equals(gconfig.getContentFolder())) {
            File exampleFile = new File(folder, "example.yml");
            if (!exampleFile.exists()) {
                plugin.saveResource("content/example.yml", false);
                GLogger.info("&f已复制示例内容文件: " + exampleFile.getName());
            }
        }
        return folder;
    }

    private void parse(
            YamlConfiguration yaml,
            String source,
            List<Tab> tabs,
            List<GAdvancement> advancements,
            Set<String> knownTabs,
            Set<String> knownAdvancements
    ) {
        ConfigurationSection section = yaml.getConfigurationSection("tabs");
        if (section == null) {
            GLogger.warning(source + " 缺少 tabs 根节点，已跳过");
            return;
        }
        for (String tabId : section.getKeys(false)) {
            ConfigurationSection tabSection = section.getConfigurationSection(tabId);
            if (tabSection == null) {
                GLogger.warning(source + " 的 tabs." + tabId + " 不是有效节点，已跳过");
                continue;
            }
            if (!knownTabs.add(tabId)) {
                GLogger.warning(source + " 中发现重复 Tab: " + tabId + "，后者已跳过");
                continue;
            }
            Tab tab = parseTab(tabId, tabSection);
            tabs.add(tab);
            GLogger.info("&f已解析 Tab: " + tabId + "，display-mode=" + tab.getDisplayMode().name().toLowerCase());
            parseAdvancements(source, tabId, tabSection, advancements, knownAdvancements);
        }
    }

    private Tab parseTab(String tabId, ConfigurationSection section) {
        String background = YamlLexicalParser.readString(
                section,
                "background",
                gconfig.getDefaultTabBackground()
        );
        TabDisplayMode displayMode = TabDisplayMode.parse(
                section.getString("display-mode"),
                "tabs." + tabId
        );
        return new Tab(tabId, background, displayMode);
    }

    private void parseAdvancements(
            String source,
            String tabId,
            ConfigurationSection tabSection,
            List<GAdvancement> advancements,
            Set<String> knownAdvancements
    ) {
        ConfigurationSection advancementSection = tabSection.getConfigurationSection("advancements");
        if (advancementSection == null) {
            GLogger.warning(source + " 的 tabs." + tabId + " 缺少 advancements 节点");
            return;
        }
        for (String advancementId : advancementSection.getKeys(false)) {
            ConfigurationSection section = advancementSection.getConfigurationSection(advancementId);
            if (section == null) {
                GLogger.warning(source + " 的 " + tabId + "." + advancementId + " 不是有效节点，已跳过");
                continue;
            }
            String uniqueKey = tabId + ":" + advancementId;
            if (!knownAdvancements.add(uniqueKey)) {
                GLogger.warning(source + " 中发现重复进度: " + uniqueKey + "，后者已跳过");
                continue;
            }
            advancements.add(parseAdvancement(tabId, advancementId, section, source));
        }
    }

    private GAdvancement parseAdvancement(
            String tabId,
            String advancementId,
            ConfigurationSection section,
            String source
    ) {
        String location = source + " -> tabs." + tabId + ".advancements." + advancementId;
        String type = YamlLexicalParser.parseAdvancementType(section.getString("type"), location);
        String parentId = YamlLexicalParser.readString(section, "parent", null);
        if ("root".equals(type)) {
            parentId = null;
        }
        ConfigurationSection dataSection = section.getConfigurationSection("data");
        if (dataSection == null) {
            dataSection = new YamlConfiguration();
        }
        AdvancementData advancementData = new AdvancementData(
                advancementId,
                YamlLexicalParser.readString(dataSection, "title", advancementId),
                YamlLexicalParser.parseIcon(
                        dataSection.getString("icon"),
                        gconfig.getFallbackIcon(),
                        location + ".data.icon"
                ),
                YamlLexicalParser.parseFrameType(dataSection.getString("frame"), location + ".data.frame"),
                dataSection.getBoolean("show_toast", true),
                dataSection.getBoolean("announce_chat", true),
                (float) dataSection.getDouble("x", 0.0D),
                (float) dataSection.getDouble("y", 0.0D),
                YamlLexicalParser.parseColor(dataSection.getString("color"), location + ".data.color"),
                YamlLexicalParser.readStringList(dataSection, "description")
        );
        List<String> conditions = YamlLexicalParser.readStringList(section, "conditions");
        GLogger.debug("加载进度: " + tabId + ":" + advancementId
                + " -> type=" + type
                + ", parent=" + parentId
                + ", conditions=" + conditions.size()
                + ", title=" + advancementData.getTitle());
        return new GAdvancement(tabId, parentId, advancementId, type, advancementData, conditions);
    }

}
