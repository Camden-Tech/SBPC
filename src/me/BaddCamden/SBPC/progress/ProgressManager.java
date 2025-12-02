package me.BaddCamden.SBPC.progress;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import me.BaddCamden.SBPC.config.MessageConfig;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.entity.EntityDeathEvent;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.PlayerInventory;

import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import me.BaddCamden.SBPC.progress.ProgressEntry.EntryKind;


import me.BaddCamden.SBPC.SBPCPlugin;
import me.BaddCamden.SBPC.events.RelatedResourceTimeSkipEvent;

public class ProgressManager implements Listener {

    private final SBPCPlugin plugin;

    private final Map<UUID, PlayerProgress> progressMap = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    private final List<SectionDefinition> sections = new ArrayList<>();
    private final List<ProgressEntry> allEntries = new ArrayList<>();
    private double globalSpeedMultiplier = 1.0;

    class SavedState {
        int currentIndex;
        int remainingSeconds;
        boolean firstStepsIntroShown;
        Set<String> unlocked = new HashSet<>();
        double adminSpeedMultiplier = 1.0;
    }


    private final Map<UUID, SavedState> savedStates = new HashMap<>();
    private final File playersFolder;
    
    public ProgressManager(SBPCPlugin plugin) {
        this.plugin = plugin;
        this.playersFolder = new File(plugin.getDataFolder(), "Players");
        reloadFromConfig();
    }


    // ------------------------------------------------------------------------
    // Config + stored progress
    // ------------------------------------------------------------------------

    public void reloadFromConfig() {
        sections.clear();
        allEntries.clear();

        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection secRoot = cfg.getConfigurationSection("progression.sections");
        if (secRoot == null) {
            plugin.getLogger().warning("No progression.sections defined in config!");
        } else {
            for (String secId : secRoot.getKeys(false)) {
                ConfigurationSection sec = secRoot.getConfigurationSection(secId);
                if (sec == null) continue;

                String displayName = sec.getString("display-name", secId);
                String specialInfo = sec.getString("special-info", "");
                String colorCode = sec.getString("color", "&e");

                // NEW: section type
                // If "type" is present in config, use it.
                // Otherwise, infer:
                //  - if special-info is non-empty -> "SPECIAL"
                //  - else -> "NORMAL"
                String type = sec.getString("type", null);
                if (type == null || type.isEmpty()) {
                    if (specialInfo != null && !specialInfo.isEmpty()) {
                        type = "SPECIAL";
                    } else {
                        type = "NORMAL";
                    }
                }

                List<String> relatedNames = sec.getStringList("related-materials");
                Set<Material> related = new HashSet<>();
                for (String s : relatedNames) {
                    try {
                        related.add(Material.valueOf(s.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("Unknown material in section " + secId + ": " + s);
                    }
                }

                List<ProgressEntry> sectionEntries = new ArrayList<>();
                ConfigurationSection entriesSec = sec.getConfigurationSection("entries");
                if (entriesSec != null) {
                	for (String entryId : entriesSec.getKeys(false)) {
                	    ConfigurationSection e = entriesSec.getConfigurationSection(entryId);
                	    if (e == null) continue;

                	    String name = e.getString("name", entryId);
                	    String typeStr = e.getString("type", "ITEM").toUpperCase();
                	    EntryKind kind;

                	    try {
                	        kind = EntryKind.valueOf(typeStr);
                	    } catch (IllegalArgumentException ex) {
                	        // Fallback: treat unknown types as MECHANIC (purely virtual unlock)
                	        kind = EntryKind.MECHANIC;
                	    }

                	    Material mat = null;
                	    Enchantment ench = null;
                	    int enchLevel = 0;
                	    String customKey = null;
                	    int seconds = e.getInt("seconds", 60);

                	    switch (kind) {
                	        case ENCHANT: {
                	            String enchKey = e.getString("enchant-key", "");
                	            enchLevel = e.getInt("level", 1);
                	            if (!enchKey.isEmpty()) {
                	                ench = Enchantment.getByKey(NamespacedKey.minecraft(enchKey.toLowerCase()));
                	            }
                	            break;
                	        }
                	        case ITEM: {
                	            String matName = e.getString("material", "");
                	            if (!matName.isEmpty()) {
                	                try {
                	                    mat = Material.valueOf(matName.toUpperCase());
                	                } catch (IllegalArgumentException ignored) {}
                	            }
                	            break;
                	        }
                	        case CUSTOM_ITEM:
                	        case MECHANIC: {
                	            // Completely abstract – SBPC does not look at any Bukkit object for these.
                	            // They are identified only by "custom-key", which the hook plugin interprets.
                	            customKey = e.getString("custom-key", entryId);
                	            break;
                	        }
                	    }

                	    ProgressEntry entry = new ProgressEntry(
                	            entryId,
                	            secId,
                	            name,
                	            kind,
                	            mat,
                	            ench,
                	            enchLevel,
                	            customKey,
                	            seconds
                	    );

                	    sectionEntries.add(entry);
                	    allEntries.add(entry);
                	}

                }
                SectionDefinition def = new SectionDefinition(
                        secId,
                        displayName,
                        sectionEntries,
                        related,
                        specialInfo,
                        colorCode,
                        type 
                );
                sections.add(def);
            }
        }

        plugin.getLogger().info("Loaded " + sections.size() + " sections and " + allEntries.size() + " progression entries.");

        loadProgressFromDisk();

        for (Map.Entry<UUID, PlayerProgress> e : progressMap.entrySet()) {
            SavedState st = savedStates.get(e.getKey());
            if (st != null) {
                e.getValue().loadState(st.currentIndex, st.remainingSeconds, st.unlocked, st.adminSpeedMultiplier);
                e.getValue().setFirstStepsIntroShown(st.firstStepsIntroShown);
                e.getValue().updateBossBar();
            }
        }
    }

    private void loadProgressFromDisk() {
        savedStates.clear();

        if (!playersFolder.exists() || !playersFolder.isDirectory()) {
            return;
        }

        File[] files = playersFolder.listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            String uuidString = (dot == -1) ? name : name.substring(0, dot);

            try {
                UUID uuid = UUID.fromString(uuidString);

                YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
                SavedState st = new SavedState();
                st.currentIndex = data.getInt("current-index", 0);
                st.remainingSeconds = data.getInt("remaining-seconds", 0);
                st.firstStepsIntroShown = data.getBoolean("first-steps-intro-shown", false);
                st.adminSpeedMultiplier = data.getDouble("admin-speed-multiplier", 1.0);

                List<String> unlockedList = data.getStringList("unlocked-entries");
                Set<String> filtered = new HashSet<>();
                for (String id : unlockedList) {
                    for (ProgressEntry e : allEntries) {
                        if (e.getId().equals(id)) {
                            filtered.add(id);
                            break;
                        }
                    }
                }
                st.unlocked = filtered;

                int maxIndex = allEntries.size();
                if (st.currentIndex < 0) st.currentIndex = 0;
                if (st.currentIndex > maxIndex) st.currentIndex = maxIndex;

                savedStates.put(uuid, st);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid UUID in player data file: " + name);
            }
        }
    }

    public void saveAll() {
        if (!playersFolder.exists() && !playersFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create Players data folder at " + playersFolder.getPath());
            return;
        }

        for (Map.Entry<UUID, PlayerProgress> e : progressMap.entrySet()) {
            UUID uuid = e.getKey();
            PlayerProgress p = e.getValue();

            File file = new File(playersFolder, uuid.toString() + ".yml");
            YamlConfiguration data = new YamlConfiguration();

            data.set("current-index", p.getCurrentIndex());
            data.set("remaining-seconds", p.getRemainingSeconds());
            data.set("unlocked-entries", new ArrayList<>(p.getUnlockedEntryIds()));
            data.set("first-steps-intro-shown", p.isFirstStepsIntroShown());
            data.set("admin-speed-multiplier", p.getAdminSpeedMultiplier());

            try {
                data.save(file);
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not save player progress for " + uuid + ": " + ex.getMessage());
            }
        }
    }

    public boolean isCustomUnlocked(UUID uuid, String customKey) {
        return getOrCreateProgress(uuid).isCustomUnlocked(customKey);
    }

    public ProgressEntry getFirstCustomEntry(String customKey) {
        if (customKey == null || customKey.isEmpty()) return null;
        String keyLower = customKey.toLowerCase();
        for (ProgressEntry e : allEntries) {
            EntryKind kind = e.getKind();
            if (kind != EntryKind.CUSTOM_ITEM && kind != EntryKind.MECHANIC) continue;
            String ck = e.getCustomKey();
            if (ck != null && ck.equalsIgnoreCase(keyLower)) {
                return e;
            }
        }
        return null;
    }

    public SectionDefinition getSectionById(String sectionId) {
        if (sectionId == null) return null;
        for (SectionDefinition s : sections) {
            if (s.getId().equalsIgnoreCase(sectionId)) {
                return s;
            }
        }
        return null;
    }


    // ------------------------------------------------------------------------
    // Player progress & bossbars
    // ------------------------------------------------------------------------

    public List<SectionDefinition> getSections() {
        return sections;
    }

    public List<ProgressEntry> getAllEntries() {
        return allEntries;
    }

    public double getGlobalSpeedMultiplier() {
        return globalSpeedMultiplier;
    }

    public void setGlobalSpeedMultiplier(double globalSpeedMultiplier) {
        if (globalSpeedMultiplier < 0) {
            globalSpeedMultiplier = 0;
        }
        this.globalSpeedMultiplier = globalSpeedMultiplier;
    }

    public PlayerProgress getOrCreateProgress(UUID uuid) {
        return progressMap.computeIfAbsent(uuid, u -> {
            BossBar bar = Bukkit.createBossBar("Progress", BarColor.BLUE, BarStyle.SOLID);
            bossBars.put(u, bar);
            PlayerProgress prog = new PlayerProgress(u, allEntries, sections, bar);

            SavedState st = savedStates.remove(u);
            if (st != null) {
                prog.loadState(st.currentIndex, st.remainingSeconds, st.unlocked, st.adminSpeedMultiplier);
                prog.setFirstStepsIntroShown(st.firstStepsIntroShown);
            }
            return prog;
        });
    }

    public boolean jumpPlayerToSection(UUID uuid, String sectionId) {
        if (sectionId == null) return false;
        int targetIndex = -1;
        for (int i = 0; i < allEntries.size(); i++) {
            ProgressEntry e = allEntries.get(i);
            if (e.getSectionId().equalsIgnoreCase(sectionId)) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) return false;

        PlayerProgress prog = getOrCreateProgress(uuid);
        prog.setProgressIndex(targetIndex);
        return true;
    }

    public boolean jumpPlayerToEntry(UUID uuid, String entryId) {
        if (entryId == null) return false;
        int targetIndex = -1;
        for (int i = 0; i < allEntries.size(); i++) {
            ProgressEntry e = allEntries.get(i);
            if (e.getId().equalsIgnoreCase(entryId)) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) return false;

        PlayerProgress prog = getOrCreateProgress(uuid);
        prog.setProgressIndex(targetIndex);
        return true;
    }

    public void setPlayerSpeed(UUID uuid, double multiplier) {
        PlayerProgress prog = getOrCreateProgress(uuid);
        prog.setAdminSpeedMultiplier(multiplier);
        prog.updateBossBar();
    }
    

    public void ensureBossBar(Player player) {
        getOrCreateProgress(player.getUniqueId()).updateBossBar();
    }

    public void clearBossBars() {
        for (BossBar bar : bossBars.values()) {
            bar.removeAll();
        }
        bossBars.clear();
    }

    public void removeBossBar(UUID uuid) {
        BossBar bar = bossBars.remove(uuid);
        if (bar != null) {
            bar.removeAll();
        }
    }

    public void markActive(UUID uuid) {
        lastActivity.put(uuid, System.currentTimeMillis());
    }

    public void tickPlayer(Player player) {
        UUID uuid = player.getUniqueId();
        Long last = lastActivity.get(uuid);
        if (last == null) return;
        if (System.currentTimeMillis() - last > 2 * 60 * 1000L) return;

        PlayerProgress prog = getOrCreateProgress(uuid);
        prog.tick(1.0 * globalSpeedMultiplier);
    }

    // ------------------------------------------------------------------------
    // Related material handling
    // ------------------------------------------------------------------------
    public void sanitizeEquipment(Player player) {
        PlayerInventory inv = player.getInventory();

        // Armor slots
        ItemStack helmet = inv.getHelmet();
        if (helmet != null && helmet.getType() != Material.AIR && !canUseItem(player, helmet)) {
            inv.setHelmet(null);
            dropOrStore(player, helmet);
            player.sendMessage("§cYou have not unlocked that armor yet.");
        }

        ItemStack chest = inv.getChestplate();
        if (chest != null && chest.getType() != Material.AIR && !canUseItem(player, chest)) {
            inv.setChestplate(null);
            dropOrStore(player, chest);
            player.sendMessage("§cYou have not unlocked that armor yet.");
        }

        ItemStack leggings = inv.getLeggings();
        if (leggings != null && leggings.getType() != Material.AIR && !canUseItem(player, leggings)) {
            inv.setLeggings(null);
            dropOrStore(player, leggings);
            player.sendMessage("§cYou have not unlocked that armor yet.");
        }

        ItemStack boots = inv.getBoots();
        if (boots != null && boots.getType() != Material.AIR && !canUseItem(player, boots)) {
            inv.setBoots(null);
            dropOrStore(player, boots);
            player.sendMessage("§cYou have not unlocked that armor yet.");
        }

        // Offhand – they are not allowed to put disallowed items there
        ItemStack off = inv.getItemInOffHand();
        if (off != null && off.getType() != Material.AIR && !canUseItem(player, off)) {
            inv.setItemInOffHand(null);
            dropOrStore(player, off);
            player.sendMessage("§cYou cannot hold that item in your offhand yet.");
        }
    }

    private void dropOrStore(Player player, ItemStack item) {
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        if (!leftovers.isEmpty()) {
            for (ItemStack leftover : leftovers.values()) {
                if (leftover == null || leftover.getType() == Material.AIR) continue;
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    public void handleRelatedBlockBroken(Player player, org.bukkit.block.Block block, boolean natural) {
        UUID uuid = player.getUniqueId();
        PlayerProgress prog = getOrCreateProgress(uuid);

        // Build candidate materials: the block type AND its drops
        Set<Material> candidates = new HashSet<>();
        Material blockType = block.getType();
        candidates.add(blockType);

        ItemStack tool = player.getInventory().getItemInMainHand();
        Collection<ItemStack> drops;
        if (tool != null && tool.getType() != Material.AIR) {
            drops = block.getDrops(tool);
        } else {
            drops = block.getDrops();
        }
        for (ItemStack drop : drops) {
            if (drop == null) continue;
            candidates.add(drop.getType());
        }

        SectionDefinition currentSection = prog.getCurrentSection();

        // PRIORITY 1: current section, if any of its related materials match
        SectionDefinition firstMatch = null;
        Material matchedMaterial = null;
        if (currentSection != null) {
            for (Material cand : candidates) {
                if (currentSection.getRelatedMaterials().contains(cand)) {
                    firstMatch = currentSection;
                    matchedMaterial = cand;
                    break;
                }
            }
        }

        // PRIORITY 2: some other section (used only for "locked section" warnings)
        if (firstMatch == null) {
            outer:
            for (SectionDefinition s : sections) {
                for (Material cand : candidates) {
                    if (s.getRelatedMaterials().contains(cand)) {
                        firstMatch = s;
                        matchedMaterial = cand;
                        break outer;
                    }
                }
            }
        }

        if (firstMatch == null) return;

        // If the block was NOT natural, just warn once if this is a future/locked section
        if (!natural) {
            boolean isCurrent = currentSection != null && currentSection.getId().equals(firstMatch.getId());

            // Only warn if the section is not completed AND not the current section
            if (!isCurrent && !prog.isSectionCompleted(firstMatch.getId())) {
                prog.notifyLockedSectionOnce(firstMatch.getId(), firstMatch.getDisplayName());
            }
            return;
        }

        // For natural blocks, only give benefits if it's the CURRENT section;
        // otherwise warn only if the section isn't completed yet.
        if (currentSection == null || !currentSection.getId().equals(firstMatch.getId())) {
            if (!prog.isSectionCompleted(firstMatch.getId())) {
                prog.notifyLockedSectionOnce(firstMatch.getId(), firstMatch.getDisplayName());
            }
            return;
        }

        // At this point: natural block, matching related material, and CURRENT section
        prog.applyRelatedMaterialBonus(
                firstMatch.getId(),
                matchedMaterial != null ? matchedMaterial.name() : blockType.name()
        );

        RelatedResourceTimeSkipEvent ev = new RelatedResourceTimeSkipEvent(
                player,
                firstMatch,
                matchedMaterial != null ? matchedMaterial : blockType
        );
        Bukkit.getPluginManager().callEvent(ev);
    }



    // ------------------------------------------------------------------------
    // Usage / crafting / enchant gating
    // ------------------------------------------------------------------------

    public void maybeShowFirstStepsIntro(Player player) {
        UUID uuid = player.getUniqueId();
        PlayerProgress prog = getOrCreateProgress(uuid);

        // If they've already seen the intro or already unlocked something, don't nag
        if (prog.isFirstStepsIntroShown()) return;
        if (prog.hasAnyUnlocked()) {
            prog.setFirstStepsIntroShown(true);
            return;
        }

        SectionDefinition sec = prog.getCurrentSection();
        if (sec == null) return;
        if (!"first_steps".equalsIgnoreCase(sec.getId())) return;

        Map<String, String> ph = new HashMap<>();
        ph.put("section", sec.getDisplayName());
        String msg = MessageConfig.format("first-steps-intro", ph);
        player.sendMessage(msg);
        prog.setFirstStepsIntroShown(true);
    }

    
    private boolean isGoldItem(ItemStack item) {
        if (item == null) return false;
        Material m = item.getType();
        String n = m.name();
        return n.startsWith("GOLDEN_");
    }

    public void handlePrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getInventory() instanceof CraftingInventory)) return;
        ItemStack result = event.getInventory().getResult();
        if (result == null) return;
        if (!(event.getView().getPlayer() instanceof Player)) return;
        Player player = (Player) event.getView().getPlayer();

        if (isGoldItem(result)) return;

        PlayerProgress prog = getOrCreateProgress(player.getUniqueId());
        if (!prog.isItemUnlocked(result.getType())) {
            event.getInventory().setResult(null);
        }
    }

    public void handleInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Allow moving/discarding items freely.
        // We only enforce equipment/offhand via a post-click sanitize.
        Bukkit.getScheduler().runTask(plugin, () -> sanitizeEquipment(player));
    }

    public boolean canUseItem(Player player, ItemStack item) {
        if (item == null) return true;
        if (item.getType() == Material.AIR) return true;
        if (isGoldItem(item)) return true;

        PlayerProgress prog = getOrCreateProgress(player.getUniqueId());

        if (!prog.isItemUnlocked(item.getType())) return false;

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasEnchants()) {
                for (Map.Entry<Enchantment, Integer> en : meta.getEnchants().entrySet()) {
                    if (!prog.isEnchantUnlocked(en.getKey(), en.getValue())) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public void handleEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) return;
        Player player = (Player) event.getDamager();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!canUseItem(player, item)) {
            event.setCancelled(true);
            player.sendMessage(MessageConfig.get("cannot-use-item"));

        } else {
            markActive(player.getUniqueId());
        }
    }

    public void handleEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        ItemStack item = event.getItem();
        if (isGoldItem(item)) return;

        PlayerProgress prog = getOrCreateProgress(player.getUniqueId());

        Map<Enchantment, Integer> original = new HashMap<>(event.getEnchantsToAdd());
        Map<Enchantment, Integer> allowed = new HashMap<>();

        for (Map.Entry<Enchantment, Integer> en : original.entrySet()) {
            Enchantment e = en.getKey();
            int level = en.getValue();

            int best = 0;
            for (int lvl = level; lvl >= 1; lvl--) {
                if (prog.isEnchantUnlocked(e, lvl)) {
                    best = lvl;
                    break;
                }
            }
            if (best > 0) {
                allowed.put(e, best);
            }
        }

        if (allowed.isEmpty()) {
            event.setCancelled(true);
            player.sendMessage(MessageConfig.get("enchant-cancelled"));

        } else {
            event.getEnchantsToAdd().clear();
            event.getEnchantsToAdd().putAll(allowed);
        }
    }

    public void handlePrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack result = event.getResult();
        if (result == null) return;

        if (!(event.getView().getPlayer() instanceof Player)) return;
        Player player = (Player) event.getView().getPlayer();
        if (isGoldItem(result)) return;

        PlayerProgress prog = getOrCreateProgress(player.getUniqueId());
        ItemMeta meta = result.getItemMeta();
        if (meta == null || !meta.hasEnchants()) return;

        Map<Enchantment, Integer> newEnchants = new HashMap<>(meta.getEnchants());
        boolean changed = false;

        Iterator<Map.Entry<Enchantment, Integer>> it = newEnchants.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Enchantment, Integer> en = it.next();
            if (!prog.isEnchantUnlocked(en.getKey(), en.getValue())) {
                it.remove();
                changed = true;
            }
        }

        if (!changed) return;

        if (newEnchants.isEmpty()) {
            event.setResult(null);
            return;
        }

        ItemMeta newMeta = result.getItemMeta();
        newMeta.getEnchants().forEach((e, l) -> newMeta.removeEnchant(e));
        for (Map.Entry<Enchantment, Integer> en : newEnchants.entrySet()) {
            newMeta.addEnchant(en.getKey(), en.getValue(), true);
        }
        result.setItemMeta(newMeta);
        event.setResult(result);
    }

    public void handleEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity().getKiller() instanceof Player)) return;
        Player killer = event.getEntity().getKiller();
        markActive(killer.getUniqueId());

        Collection<ItemStack> drops = event.getDrops();
        if (drops == null || drops.isEmpty()) return;

        handleRelatedDropsFromMob(killer, drops);
    }

    private void handleRelatedDropsFromMob(Player player, Collection<ItemStack> drops) {
        UUID uuid = player.getUniqueId();
        PlayerProgress prog = getOrCreateProgress(uuid);

        // Unique materials from the drops
        Set<Material> mats = new HashSet<>();
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() == Material.AIR) continue;
            mats.add(drop.getType());
        }
        if (mats.isEmpty()) return;

        SectionDefinition currentSection = prog.getCurrentSection();

        for (Material mat : mats) {
            SectionDefinition firstMatch = null;

            // PRIORITY 1: current section
            if (currentSection != null && currentSection.getRelatedMaterials().contains(mat)) {
                firstMatch = currentSection;
            }

            // PRIORITY 2: some other section (for "locked section" warnings)
            if (firstMatch == null) {
                for (SectionDefinition s : sections) {
                    if (s.getRelatedMaterials().contains(mat)) {
                        firstMatch = s;
                        break;
                    }
                }
            }

            if (firstMatch == null) continue;

            if (currentSection == null || !currentSection.getId().equals(firstMatch.getId())) {
                // Mob drops also respect "future section" warning semantics
                if (!prog.isSectionCompleted(firstMatch.getId())) {
                    prog.notifyLockedSectionOnce(firstMatch.getId(), firstMatch.getDisplayName());
                }
                continue;
            }

            // Natural mob drops always count as "natural" for the CURRENT section
            prog.applyRelatedMaterialBonus(firstMatch.getId(), mat.name());

            RelatedResourceTimeSkipEvent ev = new RelatedResourceTimeSkipEvent(
                    player,
                    firstMatch,
                    mat
            );
            Bukkit.getPluginManager().callEvent(ev);
        }
    }


    // === API helpers for hook plugins (SBCPSpecials etc.) ===

    public void setRelatedBonus(UUID uuid, double bonusPercent, int skipSeconds) {
        PlayerProgress prog = getOrCreateProgress(uuid);
        prog.setRelatedBonus(bonusPercent, skipSeconds);
    }

    public void applyExternalTimeSkip(UUID uuid, int skipSeconds, double percentSpeedIncrease, String sourceDescription) {
        PlayerProgress prog = getOrCreateProgress(uuid);
        prog.applyExternalTimeSkip(skipSeconds, percentSpeedIncrease, sourceDescription);
    }

    public void completeCurrentSection(UUID uuid) {
        PlayerProgress prog = progressMap.get(uuid);
        if (prog != null) {
            prog.completeCurrentSection();
        }
    }

    /**
     * Returns the ID of the player's current section, or null if none.
     * This keeps hook plugins decoupled from internal SectionDefinition.
     */
    public String getCurrentSectionId(UUID uuid) {
        PlayerProgress prog = progressMap.get(uuid);
        if (prog == null) return null;
        SectionDefinition sec = prog.getCurrentSection();
        return (sec != null) ? sec.getId() : null;
    }

    public boolean isSectionCompleted(UUID uuid, String sectionId) {
        PlayerProgress prog = progressMap.get(uuid);
        return prog != null && prog.isSectionCompleted(sectionId);
    }


    // ------------------------------------------------------------------------
    // Info command
    // ------------------------------------------------------------------------

    public void sendCurrentTimeSkipInfo(Player player) {
        PlayerProgress prog = getOrCreateProgress(player.getUniqueId());
        SectionDefinition sec = prog.getCurrentSection();
        if (sec == null) {
            player.sendMessage(MessageConfig.get("all-sections-complete"));
            return;
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("section", sec.getDisplayName());
        player.sendMessage(MessageConfig.format("current-section-header", ph));
        player.sendMessage(MessageConfig.get("current-section-related-header"));

        for (Material m : sec.getRelatedMaterials()) {
            Map<String, String> phMat = new HashMap<>();
            phMat.put("material", m.name());
            player.sendMessage(MessageConfig.format("current-section-related-entry", phMat));
        }

        if (sec.getSpecialInfo() != null && !sec.getSpecialInfo().isEmpty()) {
            Map<String, String> phSpec = new HashMap<>();
            phSpec.put("special", sec.getSpecialInfo());
            player.sendMessage(MessageConfig.format("current-section-special", phSpec));
        } else {
            player.sendMessage(MessageConfig.get("current-section-no-special"));
        }

    }
}
