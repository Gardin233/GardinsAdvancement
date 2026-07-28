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
        GLogger.infoLang("content.scan_start", folder.getAbsolutePath());
        List<Tab> tabs = new ArrayList<>();
        List<GAdvancement> advancements = new ArrayList<>();
        Set<String> knownTabs = new HashSet<>();
        Set<String> knownAdvancements = new HashSet<>();
        List<String> files = loadFiles(folder, tabs, advancements, knownTabs, knownAdvancements);
        GLogger.infoLang("content.scan_complete", files.size());
        GLogger.debugLang("content.loaded_files", files);
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
            GLogger.warningLang("content.folder_unreadable", folder.getAbsolutePath());
            return files;
        }
        GLogger.debugLang("content.candidate_count", ymlFiles.length);
        for (File file : ymlFiles) {
            GLogger.infoLang("content.file_read", file.getName());
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
            GLogger.infoLang("content.folder_created", folder.getAbsolutePath());
        }
        if (gconfig.isCopyExampleContent() && "content".equals(gconfig.getContentFolder())) {
            File exampleFile = new File(folder, "example.yml");
            if (!exampleFile.exists()) {
                plugin.saveResource("content/example.yml", false);
                GLogger.infoLang("content.example_copied", exampleFile.getName());
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
            GLogger.warningLang("content.missing_tabs_root", source);
            return;
        }
        for (String tabId : section.getKeys(false)) {
            ConfigurationSection tabSection = section.getConfigurationSection(tabId);
            if (tabSection == null) {
                GLogger.warningLang("content.invalid_tab_section", source, tabId);
                continue;
            }
            if (!knownTabs.add(tabId)) {
                GLogger.warningLang("content.duplicate_tab", source, tabId);
                continue;
            }
            Tab tab = parseTab(tabId, tabSection);
            tabs.add(tab);
            GLogger.infoLang("content.parsed_tab", tabId, tab.getDisplayMode().name().toLowerCase());
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
            GLogger.warningLang("content.missing_advancements", source, tabId);
            return;
        }
        for (String advancementId : advancementSection.getKeys(false)) {
            ConfigurationSection section = advancementSection.getConfigurationSection(advancementId);
            if (section == null) {
                GLogger.warningLang("content.invalid_advancement_section", source, tabId, advancementId);
                continue;
            }
            String uniqueKey = tabId + ":" + advancementId;
            if (!knownAdvancements.add(uniqueKey)) {
                GLogger.warningLang("content.duplicate_advancement", source, uniqueKey);
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
                YamlLexicalParser.readColoredString(dataSection, "title", advancementId),
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
                YamlLexicalParser.readColoredStringList(dataSection, "description")
        );
        List<String> conditions = YamlLexicalParser.readStringList(section, "conditions");
        List<String> commands = YamlLexicalParser.readMultilineStringList(section, "commands");
        GLogger.debugLang(
                "content.advancement_loaded",
                tabId + ":" + advancementId,
                type,
                parentId,
                conditions.size(),
                commands.size(),
                advancementData.getTitle()
        );
        return new GAdvancement(tabId, parentId, advancementId, type, advancementData, conditions, commands);
    }

}
