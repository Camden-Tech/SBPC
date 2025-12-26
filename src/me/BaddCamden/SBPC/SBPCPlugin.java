package me.BaddCamden.SBPC;

import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import me.BaddCamden.SBPC.commands.SbpcCommand;
import me.BaddCamden.SBPC.config.MessageConfig;
import me.BaddCamden.SBPC.commands.CurrentTimeSkipCommand;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import me.BaddCamden.SBPC.api.SbpcAPI;
import me.BaddCamden.SBPC.progress.ProgressManager;

/**
 * Main plugin entry point for SBPC. Coordinates progression tracking, commands, and listeners.
 */
public class SBPCPlugin extends JavaPlugin implements Listener {

    public static final String BLOCK_PLACED_META = "sbpc_placed";

    private static SBPCPlugin instance;

    private BukkitTask tickTask;

    private ProgressManager progressManager;
    private Listener sessionLibraryListener;
    private boolean sessionLibraryIntegrationEnabled = false;
    private boolean sessionActive = false;

    /**
     * @return globally accessible plugin instance set during onEnable.
     */
    public static SBPCPlugin getInstance() {
        return instance;
    }

    /**
     * @return the progression manager responsible for player unlock state.
     */
    public ProgressManager getProgressManager() {
        return progressManager;
    }

    @Override
    /**
     * Bootstrap plugin state, commands, listeners, and ticking tasks.
     */
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        this.progressManager = new ProgressManager(this);
        SbpcAPI.init(this.progressManager);

        loadConfigValues();

        // NEW: register command executors and tab completers
        SbpcCommand sbpcCmd = new SbpcCommand(this);
        if (getCommand("sbpc") != null) {
            getCommand("sbpc").setExecutor(sbpcCmd);
            getCommand("sbpc").setTabCompleter(sbpcCmd);
        }

        CurrentTimeSkipCommand ctsCmd = new CurrentTimeSkipCommand(this);
        if (getCommand("currenttimeskip") != null) {
            getCommand("currenttimeskip").setExecutor(ctsCmd);
            getCommand("currenttimeskip").setTabCompleter(ctsCmd);
        }

        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(progressManager, this);

        tickTask = Bukkit.getScheduler().runTaskTimer(this, this::tickPlayers, 20L, 20L);

        getLogger().info("SBPC enabled.");
    }

    @Override
    /**
     * Persist state, unregister listeners, and clean up boss bars when the plugin stops.
     */
    public void onDisable() {
        if (tickTask != null) tickTask.cancel();
        if (progressManager != null) {
            progressManager.saveAll();
            progressManager.clearBossBars();
            HandlerList.unregisterAll(progressManager);
        }

        HandlerList.unregisterAll((Plugin) this);

        if (sessionLibraryListener != null) {
            HandlerList.unregisterAll(sessionLibraryListener);
        }
        instance = null;
    }

    /**
     * Reads configuration and applies global speed/bonus settings as well as optional integrations.
     */
    public void loadConfigValues() {
        FileConfiguration cfg = getConfig();
        MessageConfig.load(cfg);
        double globalSpeed = cfg.getDouble("progression.global-speed-multiplier", 1.0);
        progressManager.setGlobalSpeedMultiplier(globalSpeed);
        ConfigurationSection relatedCfg = cfg.getConfigurationSection("progression.related-bonus");
        double relatedPercent = 3.0;
        int relatedSkipSeconds = 10;
        if (relatedCfg != null) {
            relatedPercent = relatedCfg.getDouble("percent-speed-increase", relatedPercent);
            relatedSkipSeconds = relatedCfg.getInt("skip-seconds", relatedSkipSeconds);
        }
        progressManager.setRelatedBonusDefaults(relatedPercent, relatedSkipSeconds);
        configureSessionLibraryIntegration();
    }

    /**
     * Enables or disables SessionLibrary integration depending on availability and config.
     */
    private void configureSessionLibraryIntegration() {
        boolean integrationEnabledInConfig = getConfig().getBoolean("session.session-library-enabled", true);
        Plugin sessionLib = Bukkit.getPluginManager().getPlugin("SessionLibrary");

        boolean wasEnabled = sessionLibraryIntegrationEnabled;

        if (!integrationEnabledInConfig) {
            disableSessionLibraryIntegration("SessionLibrary integration disabled in config; sessions will always be active.", false);
            return;
        }

        if (sessionLib == null) {
            disableSessionLibraryIntegration("SessionLibrary plugin not found; running without session gating.", true);
            return;
        }

        if (!isSessionLibraryClassesPresent()) {
            disableSessionLibraryIntegration("SessionLibrary classes not available; running without session gating.", true);
            return;
        }

        if (!sessionLibraryIntegrationEnabled) {
            sessionLibraryListener = new SessionLibraryListener(this);
            Bukkit.getPluginManager().registerEvents(sessionLibraryListener, this);
            getLogger().info("SessionLibrary detected; session-based progression gating enabled.");
        }

        sessionLibraryIntegrationEnabled = true;
        if (!wasEnabled) {
            sessionActive = false;
        }
    }

    /**
     * Detects whether SessionLibrary event classes are on the classpath.
     */
    private boolean isSessionLibraryClassesPresent() {
        try {
            Class.forName("me.BaddCamden.SessionLibrary.events.SessionStartEvent");
            Class.forName("me.BaddCamden.SessionLibrary.events.SessionEndEvent");
            Class.forName("me.BaddCamden.SessionLibrary.events.SessionTickEvent");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Cleanly tears down SessionLibrary integration and optionally logs why.
     */
    private void disableSessionLibraryIntegration(String reason, boolean warn) {
        if (sessionLibraryListener != null) {
            HandlerList.unregisterAll(sessionLibraryListener);
            sessionLibraryListener = null;
        }
        sessionLibraryIntegrationEnabled = false;
        sessionActive = true;

        if (reason != null && !reason.isEmpty()) {
            if (warn) {
                getLogger().warning(reason);
            } else {
                getLogger().info(reason);
            }
        }
    }

    /**
     * Per-second tick task that advances active players' progression timers.
     */
    private void tickPlayers() {
        if (!sessionActive) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            progressManager.tickPlayer(p);
        }
    }

    // ------------------------------------------------------------------------
    // Join blocking when “closed”
    // ------------------------------------------------------------------------

    /**
     * Pre-login hook to prepare for session-closed kicks on join.
     */
    @EventHandler
    public void onAsyncLogin(AsyncPlayerPreLoginEvent event) {
        // Actual kick is handled on PlayerJoin (needs Player for op check)
    }

    /**
     * Handles join-time gating and sets up boss bars/intro messaging.
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Players who are not opped cannot join unless a session has started
        if (!sessionActive && !player.isOp()) {
            player.kickPlayer(MessageConfig.get("server-closed-join"));
            return;
        }

        progressManager.ensureBossBar(player);
        progressManager.maybeShowFirstStepsIntro(player);
    }




    // ------------------------------------------------------------------------
    // Activity tracking & natural vs placed blocks
    // ------------------------------------------------------------------------

    /**
     * Marks players as active when they move to keep their timers ticking.
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() != null && !event.getTo().toVector().equals(event.getFrom().toVector())) {
            progressManager.markActive(event.getPlayer().getUniqueId());
        }
    }

    /**
     * Blocks breaking with locked items and routes natural/placed block handling.
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack inHand = player.getInventory().getItemInMainHand();

        // You cannot break blocks with disallowed items
        if (!progressManager.canUseItem(player, inHand)) {
            event.setCancelled(true);
            player.sendMessage("§cYou have not unlocked that item yet.");
            return;
        }

        progressManager.markActive(player.getUniqueId());

        // Only count if *not* player-placed
        if (event.getBlock().hasMetadata(BLOCK_PLACED_META)) {
            progressManager.handleRelatedBlockBroken(player, event.getBlock(), false);
        } else {
            progressManager.handleRelatedBlockBroken(player, event.getBlock(), true);
        }
    }


    /**
     * Marks placed blocks so later breaks do not count as natural resources.
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack inHand = event.getItemInHand();

        // You cannot place blocks with disallowed items/blocks
        if (!progressManager.canUseItem(player, inHand)) {
            event.setCancelled(true);
            player.sendMessage("§cYou have not unlocked that item yet.");
            return;
        }

        // Mark block as player-placed so it doesn't count as “natural”
        event.getBlockPlaced().setMetadata(BLOCK_PLACED_META, new FixedMetadataValue(this, true));
    }
    /**
     * Prevents picking up items the player has not unlocked yet.
     */
    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        ItemStack stack = event.getItem().getItemStack();

        // You cannot pick up disallowed items, but you can still drop them
        if (!progressManager.canUseItem(player, stack)) {
            event.setCancelled(true);
            // Optional message: comment out if too spammy
            // player.sendMessage("§cYou cannot pick up that item yet.");
        }
    }

    /**
     * Prevents using buckets the player has not unlocked when emptying.
     */
    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        ItemStack bucket = event.getItemStack();
        if (!progressManager.canUseItem(player, bucket)) {
            event.setCancelled(true);
            player.sendMessage("§cYou have not unlocked that bucket yet.");
        }
    }

    /**
     * Prevents filling buckets the player has not unlocked.
     */
    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        ItemStack bucket = event.getItemStack();
        if (!progressManager.canUseItem(player, bucket)) {
            event.setCancelled(true);
            player.sendMessage("§cYou have not unlocked that bucket yet.");
        }
    }

    /**
     * Cancels interactions with items that are not yet unlocked (blocks, shields, etc.).
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        EquipmentSlot hand = event.getHand();
        if (hand == null) return;

        ItemStack used;
        if (hand == EquipmentSlot.HAND) {
            used = player.getInventory().getItemInMainHand();
        } else {
            used = player.getInventory().getItemInOffHand();
        }

        if (used == null || used.getType() == Material.AIR) return;

        // Disallowed items are not used at all (no block place, no shield raise, no item use)
        if (!progressManager.canUseItem(player, used)) {
            event.setCancelled(true);
            player.sendMessage("§cYou have not unlocked that item yet.");
        }
    }

    // ------------------------------------------------------------------------
    // Item & enchant blocking hooks
    // ------------------------------------------------------------------------

    /**
     * Strips craft results for items the player has not unlocked.
     */
    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        progressManager.handlePrepareCraft(event);
    }

    /**
     * Sanitises player equipment after inventory interactions.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        progressManager.handleInventoryClick(event);
    }

    /**
     * Filters enchant rolls based on unlocked enchantments.
     */
    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        progressManager.handleEnchant(event);
    }

    /**
     * Removes disallowed enchantments from anvil outputs.
     */
    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        progressManager.handlePrepareAnvil(event);
    }

    /**
     * Ensures players cannot deal damage with locked items or enchants.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        progressManager.handleEntityDamage(event);
    }

    /**
     * Applies related resource bonuses from mob drops.
     */
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        progressManager.handleEntityDeath(event);
    }

    /**
     * Cleans up boss bars when players quit.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        progressManager.removeBossBar(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------------
    // SessionManager integration callbacks
    // ------------------------------------------------------------------------

    /**
     * Invoked when SessionLibrary reports a session start.
     */
    void handleSessionStart() {
        sessionActive = true;
        getLogger().info("SessionManager session started – SBPC timers ticking while players are active.");

        for (Player p : Bukkit.getOnlinePlayers()) {
            progressManager.maybeShowFirstStepsIntro(p);
        }
    }

    /**
     * Invoked when SessionLibrary reports a session end and kicks non-opped players.
     */
    void handleSessionEnd() {
        sessionActive = false;
        getLogger().info("SessionManager session ended – kicking all non-opped players.");

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isOp()) {
                p.kickPlayer(MessageConfig.get("server-closed-join"));
            }
        }
    }

    /**
     * Placeholder for potential periodic work while a session is active.
     */
    void handleSessionTick() {
        // Optional hook point
    }

}
