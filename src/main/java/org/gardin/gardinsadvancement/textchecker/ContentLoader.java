package org.gardin.gardinsadvancement.textchecker;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.gardin.gardinsadvancement.advancementregister.AdvancementData;
import org.gardin.gardinsadvancement.advancementregister.AdvancementProgress;
import org.gardin.gardinsadvancement.advancementregister.GAdvancement;
import org.gardin.gardinsadvancement.conf.Gconfig;
import org.gardin.gardinsadvancement.tabcreater.Tab;
import org.gardin.gardinsadvancement.tabcreater.TabDisplayMode;
import org.gardin.gardinsadvancement.util.GLogger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        List<LoadedContentFile> files = loadFiles(folder);
        Map<String, BranchTemplate> branches = registerBranches(files);
        assembleTabs(files, branches, tabs, advancements);

        GLogger.infoLang("content.scan_complete", files.size());
        GLogger.debugLang(
                "content.loaded_files",
                files.stream().map(LoadedContentFile::source).collect(Collectors.toList())
        );
        return new ContentDocument(tabs, advancements);
    }

    public List<LoadedContentFile> loadFiles(File folder) {
        List<LoadedContentFile> files = new ArrayList<>();
        if (!folder.exists()) {
            folder.mkdirs();
        }

        List<File> ymlFiles = collectYmlFiles(folder);
        GLogger.debugLang("content.candidate_count", ymlFiles.size());
        for (File file : ymlFiles) {
            String source = relativizePath(folder, file);
            GLogger.infoLang("content.file_read", source);

            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            boolean hasTabsRoot = yaml.contains("tabs", false);
            boolean hasBranchRoot = yaml.contains("branchs", false);
            if (!hasTabsRoot && !hasBranchRoot) {
                GLogger.warningLang("content.missing_content_root", source);
            }
            files.add(new LoadedContentFile(source, yaml, hasTabsRoot, hasBranchRoot));
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
            copyExampleIfAbsent(folder, "example.yml");
            copyExampleIfAbsent(folder, "branch_example.yml");
        }
        return folder;
    }

    private void copyExampleIfAbsent(File folder, String fileName) {
        File targetFile = new File(folder, fileName);
        if (!targetFile.exists()) {
            plugin.saveResource("content/" + fileName, false);
            GLogger.infoLang("content.example_copied", targetFile.getName());
        }
    }

    private List<File> collectYmlFiles(File folder) {
        try (Stream<Path> stream = Files.walk(folder.toPath())) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(file -> file.getName().endsWith(".yml"))
                    .sorted((left, right) -> left.getAbsolutePath().compareToIgnoreCase(right.getAbsolutePath()))
                    .toList();
        } catch (IOException ex) {
            GLogger.warningLang("content.folder_unreadable", folder.getAbsolutePath());
            return List.of();
        }
    }

    private String relativizePath(File folder, File file) {
        return folder.toPath()
                .relativize(file.toPath())
                .toString()
                .replace(File.separatorChar, '/');
    }

    private Map<String, BranchTemplate> registerBranches(List<LoadedContentFile> files) {
        Map<String, BranchTemplate> branches = new LinkedHashMap<>();
        for (LoadedContentFile file : files) {
            if (!file.hasBranchRoot()) {
                continue;
            }

            ConfigurationSection branchRoot = file.yaml().getConfigurationSection("branchs");
            if (branchRoot == null) {
                GLogger.warningLang("content.invalid_branch_root", file.source());
                continue;
            }

            for (String branchId : branchRoot.getKeys(false)) {
                ConfigurationSection branchSection = branchRoot.getConfigurationSection(branchId);
                if (branchSection == null) {
                    GLogger.warningLang("content.invalid_branch_section", file.source(), branchId);
                    continue;
                }
                if (branches.containsKey(branchId)) {
                    GLogger.warningLang("content.duplicate_branch", file.source(), branchId);
                    continue;
                }

                BranchTemplate template = parseBranchTemplate(file.source(), branchId, branchSection);
                if (template == null) {
                    GLogger.warningLang("content.branch_discarded", file.source(), branchId);
                    continue;
                }

                branches.put(branchId, template);
                GLogger.infoLang("content.branch_registered", branchId, template.advancements().size());
            }
        }
        return branches;
    }

    private BranchTemplate parseBranchTemplate(
            String source,
            String branchId,
            ConfigurationSection branchSection
    ) {
        ConfigurationSection advancementSection = branchSection.getConfigurationSection("advancements");
        if (advancementSection == null) {
            GLogger.warningLang("content.branch_missing_advancements", source, branchId);
            return null;
        }

        LinkedHashMap<String, ParsedAdvancement> parsedAdvancements = new LinkedHashMap<>();
        for (String advancementId : advancementSection.getKeys(false)) {
            ConfigurationSection advancementNode = advancementSection.getConfigurationSection(advancementId);
            if (advancementNode == null) {
                GLogger.warningLang(
                        "content.invalid_advancement_section",
                        source,
                        "branchs." + branchId + ".advancements",
                        advancementId
                );
                return null;
            }
            parsedAdvancements.put(
                    advancementId,
                    parseAdvancementDefinition(
                            null,
                            advancementId,
                            advancementNode,
                            source,
                            "branchs." + branchId + ".advancements." + advancementId
                    )
            );
        }

        List<ParsedAdvancement> roots = parsedAdvancements.values().stream()
                .filter(ParsedAdvancement::isRoot)
                .toList();
        if (roots.isEmpty()) {
            GLogger.warningLang("content.branch_root_missing", source, branchId);
            return null;
        }
        if (roots.size() > 1) {
            GLogger.warningLang("content.branch_multiple_roots", source, branchId);
            return null;
        }
        String rootId = roots.getFirst().id();
        if (!validateBranchTemplate(source, branchId, parsedAdvancements, rootId)) {
            return null;
        }
        return new BranchTemplate(source, branchId, rootId, parsedAdvancements);
    }

    private void assembleTabs(
            List<LoadedContentFile> files,
            Map<String, BranchTemplate> branches,
            List<Tab> tabs,
            List<GAdvancement> advancements
    ) {
        Set<String> knownTabs = new HashSet<>();
        Set<String> usedBranches = new LinkedHashSet<>();

        for (LoadedContentFile file : files) {
            if (!file.hasTabsRoot()) {
                continue;
            }

            ConfigurationSection tabRoot = file.yaml().getConfigurationSection("tabs");
            if (tabRoot == null) {
                GLogger.warningLang("content.invalid_tabs_root", file.source());
                continue;
            }

            for (String tabId : tabRoot.getKeys(false)) {
                ConfigurationSection tabSection = tabRoot.getConfigurationSection(tabId);
                if (tabSection == null) {
                    GLogger.warningLang("content.invalid_tab_section", file.source(), tabId);
                    continue;
                }
                if (!knownTabs.add(tabId)) {
                    GLogger.warningLang("content.duplicate_tab", file.source(), tabId);
                    continue;
                }

                Tab tab = parseTab(tabId, tabSection);
                tabs.add(tab);
                GLogger.infoLang("content.parsed_tab", tabId, tab.getDisplayMode().name().toLowerCase());
                advancements.addAll(parseTabAdvancements(file.source(), tabId, tabSection, branches, usedBranches));
            }
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

    private List<GAdvancement> parseTabAdvancements(
            String source,
            String tabId,
            ConfigurationSection tabSection,
            Map<String, BranchTemplate> branches,
            Set<String> usedBranches
    ) {
        ConfigurationSection advancementSection = tabSection.getConfigurationSection("advancements");
        if (advancementSection == null) {
            GLogger.warningLang("content.missing_advancements", source, tabId);
            return List.of();
        }

        LinkedHashMap<String, ParsedAdvancement> assembledNodes = new LinkedHashMap<>();
        for (String advancementId : advancementSection.getKeys(false)) {
            ConfigurationSection section = advancementSection.getConfigurationSection(advancementId);
            if (section == null) {
                GLogger.warningLang("content.invalid_advancement_section", source, tabId, advancementId);
                continue;
            }
            if (assembledNodes.containsKey(advancementId)) {
                GLogger.warningLang("content.duplicate_advancement", source, tabId + ":" + advancementId);
                continue;
            }

            assembledNodes.put(
                    advancementId,
                    parseAdvancementDefinition(
                            tabId,
                            advancementId,
                            section,
                            source,
                            "tabs." + tabId + ".advancements." + advancementId
                    )
            );
        }

        for (ParsedAdvancement advancement : new ArrayList<>(assembledNodes.values())) {
            mountBranches(
                    source,
                    tabId,
                    advancement.id(),
                    advancement.branchRefs(),
                    assembledNodes,
                    branches,
                    usedBranches
            );
        }

        return assembledNodes.values().stream()
                .map(ParsedAdvancement::toRuntimeAdvancement)
                .toList();
    }

    private void mountBranches(
            String source,
            String tabId,
            String parentId,
            List<String> branchRefs,
            LinkedHashMap<String, ParsedAdvancement> assembledNodes,
            Map<String, BranchTemplate> branches,
            Set<String> usedBranches
    ) {
        if (branchRefs.isEmpty()) {
            return;
        }

        for (String branchId : branchRefs) {
            BranchExpansion expansion = buildBranchExpansion(
                    source,
                    tabId,
                    branchId,
                    parentId,
                    Map.copyOf(assembledNodes),
                    branches,
                    usedBranches,
                    Set.of(),
                    new ArrayDeque<>()
            );
            if (expansion == null) {
                continue;
            }

            assembledNodes.putAll(expansion.advancements());
            usedBranches.addAll(expansion.usedBranches());
            GLogger.infoLang(
                    "content.branch_mounted",
                    branchId,
                    tabId + ":" + parentId,
                    expansion.advancements().size()
            );
        }
    }

    private BranchExpansion buildBranchExpansion(
            String source,
            String tabId,
            String branchId,
            String mountParentId,
            Map<String, ParsedAdvancement> existingNodes,
            Map<String, BranchTemplate> branches,
            Set<String> usedBranches,
            Set<String> reservedBranches,
            Deque<String> stack
    ) {
        if (usedBranches.contains(branchId) || reservedBranches.contains(branchId)) {
            GLogger.warningLang("content.branch_reused", source, branchId, tabId + ":" + mountParentId);
            return null;
        }
        if (stack.contains(branchId)) {
            List<String> cyclePath = new ArrayList<>(stack);
            cyclePath.add(branchId);
            GLogger.warningLang("content.branch_reference_cycle", source, String.join(" -> ", cyclePath), branchId);
            return null;
        }

        BranchTemplate template = branches.get(branchId);
        if (template == null) {
            GLogger.warningLang("content.branch_not_found", source, branchId, tabId + ":" + mountParentId);
            return null;
        }

        Deque<String> nextStack = new ArrayDeque<>(stack);
        nextStack.addLast(branchId);
        Set<String> nextReservedBranches = new LinkedHashSet<>(reservedBranches);
        nextReservedBranches.add(branchId);

        LinkedHashMap<String, ParsedAdvancement> segmentNodes = new LinkedHashMap<>();
        List<ParsedAdvancement> ownNodes = new ArrayList<>();
        for (ParsedAdvancement definition : template.advancements().values()) {
            ParsedAdvancement mounted = definition.mountTo(tabId, template.rootId(), mountParentId);
            if (existingNodes.containsKey(mounted.id()) || segmentNodes.containsKey(mounted.id())) {
                GLogger.warningLang("content.branch_id_conflict", source, branchId, tabId + ":" + mounted.id());
                return null;
            }
            segmentNodes.put(mounted.id(), mounted);
            ownNodes.add(mounted);
        }

        Set<String> mountedBranches = new LinkedHashSet<>();
        mountedBranches.add(branchId);
        for (ParsedAdvancement mounted : ownNodes) {
            for (String nestedBranchId : mounted.branchRefs()) {
                LinkedHashMap<String, ParsedAdvancement> visibleNodes = new LinkedHashMap<>(existingNodes);
                visibleNodes.putAll(segmentNodes);

                BranchExpansion nestedExpansion = buildBranchExpansion(
                        source,
                        tabId,
                        nestedBranchId,
                        mounted.id(),
                        visibleNodes,
                        branches,
                        usedBranches,
                        nextReservedBranches,
                        nextStack
                );
                if (nestedExpansion == null) {
                    return null;
                }

                segmentNodes.putAll(nestedExpansion.advancements());
                mountedBranches.addAll(nestedExpansion.usedBranches());
                nextReservedBranches.addAll(nestedExpansion.usedBranches());
            }
        }

        if (!validateBranchSegment(source, branchId, tabId, template.rootId(), existingNodes, segmentNodes)) {
            return null;
        }
        return new BranchExpansion(segmentNodes, mountedBranches);
    }

    private boolean validateBranchTemplate(
            String source,
            String branchId,
            Map<String, ParsedAdvancement> templateNodes,
            String rootId
    ) {
        for (ParsedAdvancement advancement : templateNodes.values()) {
            if (rootId.equals(advancement.id())) {
                if (advancement.parentId() != null && !advancement.parentId().isBlank()) {
                    GLogger.warningLang(
                            "content.branch_parent_missing",
                            source,
                            branchId,
                            advancement.id(),
                            advancement.parentId()
                    );
                    return false;
                }
                continue;
            }

            String parentId = advancement.parentId();
            if (parentId == null || parentId.isBlank() || !templateNodes.containsKey(parentId)) {
                GLogger.warningLang(
                        "content.branch_parent_missing",
                        source,
                        branchId,
                        advancement.id(),
                        parentId == null ? "null" : parentId
                );
                return false;
            }
        }

        for (ParsedAdvancement advancement : templateNodes.values()) {
            Set<String> visited = new LinkedHashSet<>();
            String current = advancement.id();
            while (current != null) {
                if (!visited.add(current)) {
                    List<String> cyclePath = new ArrayList<>(visited);
                    cyclePath.add(current);
                    GLogger.warningLang(
                            "content.branch_cycle_detected",
                            source,
                            branchId,
                            advancement.id(),
                            String.join(" -> ", cyclePath)
                    );
                    return false;
                }
                ParsedAdvancement currentNode = templateNodes.get(current);
                current = currentNode == null ? null : currentNode.parentId();
            }
        }
        return true;
    }

    private boolean validateBranchSegment(
            String source,
            String branchId,
            String tabId,
            String rootId,
            Map<String, ParsedAdvancement> existingNodes,
            Map<String, ParsedAdvancement> segmentNodes
    ) {
        for (ParsedAdvancement advancement : segmentNodes.values()) {
            String parentId = advancement.parentId();
            if (parentId == null || parentId.isBlank()) {
                GLogger.warningLang("content.branch_parent_missing", source, branchId, tabId + ":" + advancement.id(), "null");
                return false;
            }
            boolean isRootNode = rootId.equals(advancement.id());
            if (isRootNode) {
                if (!existingNodes.containsKey(parentId)) {
                    GLogger.warningLang("content.branch_parent_missing", source, branchId, tabId + ":" + advancement.id(), parentId);
                    return false;
                }
                continue;
            }
            if (!segmentNodes.containsKey(parentId)) {
                GLogger.warningLang("content.branch_parent_missing", source, branchId, tabId + ":" + advancement.id(), parentId);
                return false;
            }

            Set<String> visited = new LinkedHashSet<>();
            String current = advancement.id();
            while (current != null && segmentNodes.containsKey(current)) {
                if (!visited.add(current)) {
                    List<String> cyclePath = new ArrayList<>(visited);
                    cyclePath.add(current);
                    GLogger.warningLang(
                            "content.branch_cycle_detected",
                            source,
                            branchId,
                            tabId + ":" + advancement.id(),
                            String.join(" -> ", cyclePath)
                    );
                    return false;
                }
                ParsedAdvancement currentNode = segmentNodes.get(current);
                current = currentNode == null ? null : currentNode.parentId();
            }
        }
        return true;
    }

    private ParsedAdvancement parseAdvancementDefinition(
            String tabId,
            String advancementId,
            ConfigurationSection section,
            String source,
            String path
    ) {
        String location = source + " -> " + path;
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
                YamlLexicalParser.readPositiveCoordinate(dataSection, "x", 1.0F, location + ".data"),
                YamlLexicalParser.readPositiveCoordinate(dataSection, "y", 1.0F, location + ".data"),
                YamlLexicalParser.parseColor(dataSection.getString("color"), location + ".data.color"),
                YamlLexicalParser.readColoredStringList(dataSection, "description")
        );

        AdvancementProgress progress = parseProgress(section.getConfigurationSection("progress"), location + ".progress");
        List<String> conditions = YamlLexicalParser.readStringList(section, "conditions");
        List<String> commands = YamlLexicalParser.readMultilineStringList(section, "commands");
        List<String> branchRefs = YamlLexicalParser.readStringList(section, "branch");
        String runtimeKey = tabId == null ? advancementId : tabId + ":" + advancementId;
        GLogger.debugLang(
                "content.advancement_loaded",
                runtimeKey,
                type,
                parentId,
                progress == null ? "-" : progress.placeholder(),
                progress == null ? 0 : progress.max(),
                conditions.size(),
                commands.size(),
                advancementData.getTitle()
        );

        return new ParsedAdvancement(
                tabId,
                parentId,
                advancementId,
                type,
                advancementData,
                progress,
                conditions,
                commands,
                branchRefs
        );
    }

    private AdvancementProgress parseProgress(ConfigurationSection progressSection, String source) {
        if (progressSection == null) {
            return null;
        }
        String placeholder = YamlLexicalParser.readRequiredPlaceholder(progressSection, "placeholder", source);
        if (placeholder == null) {
            return null;
        }
        int max = YamlLexicalParser.readPositiveInteger(progressSection, "max", -1, source);
        if (max <= 0) {
            return null;
        }
        return new AdvancementProgress(placeholder, max);
    }

    public record LoadedContentFile(
            String source,
            YamlConfiguration yaml,
            boolean hasTabsRoot,
            boolean hasBranchRoot
    ) {
    }

    private record BranchTemplate(
            String source,
            String id,
            String rootId,
            LinkedHashMap<String, ParsedAdvancement> advancements
    ) {
    }

    private record BranchExpansion(
            LinkedHashMap<String, ParsedAdvancement> advancements,
            Set<String> usedBranches
    ) {
    }

    private record ParsedAdvancement(
            String tabId,
            String parentId,
            String id,
            String type,
            AdvancementData data,
            AdvancementProgress progress,
            List<String> conditions,
            List<String> commands,
            List<String> branchRefs
    ) {
        private boolean isRoot() {
            return "root".equalsIgnoreCase(type);
        }

        private ParsedAdvancement mountTo(String mountedTabId, String branchRootId, String mountParentId) {
            boolean isMountedRoot = id.equals(branchRootId);
            return new ParsedAdvancement(
                    mountedTabId,
                    isMountedRoot ? mountParentId : parentId,
                    id,
                    isMountedRoot ? "common" : type,
                    data,
                    progress,
                    conditions,
                    commands,
                    branchRefs
            );
        }

        private GAdvancement toRuntimeAdvancement() {
            return new GAdvancement(tabId, parentId, id, type, data, progress, conditions, commands);
        }
    }
}
