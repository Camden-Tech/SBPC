package me.BaddCamden.SBPC;

import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import me.BaddCamden.SBPC.commands.SbpcCommand;
import me.BaddCamden.SBPC.config.MessageConfig;
import me.BaddCamden.SBPC.commands.CurrentTimeSkipCommand;

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

public class SBPCPlugin extends JavaPlugin implements Listener {

    public static final String BLOCK_PLACED_META = "sbpc_placed";

    private static SBPCPlugin instance;

    private BukkitTask tickTask;

    private ProgressManager progressManager;
    private Listener sessionLibraryListener;
    private boolean sessionLibraryIntegrationEnabled = false;
    private boolean sessionActive = false;

    public static SBPCPlugin getInstance() {
        return instance;
    }

    public ProgressManager getProgressManager() {
        return progressManager;
    }

    @Override
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
    public void onDisable() {
        if (tickTask != null) tickTask.cancel();
        if (progressManager != null) {
            progressManager.saveAll();
            progressManager.clearBossBars();
            HandlerList.unregisterAll(progressManager);
        }
        HandlerList.unregisterAll(this);
        if (sessionLibraryListener != null) {
            HandlerList.unregisterAll(sessionLibraryListener);
        }
        instance = null;
    }

    public void loadConfigValues() {
        FileConfiguration cfg = getConfig();
        MessageConfig.load(cfg);
        configureSessionLibraryIntegration();
    }

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

    private void tickPlayers() {
        if (!sessionActive) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            progressManager.tickPlayer(p, 1);
        }
    }

    // ------------------------------------------------------------------------
    // Join blocking when “closed”
    // ------------------------------------------------------------------------

    @EventHandler
    public void onAsyncLogin(AsyncPlayerPreLoginEvent event) {
        // Actual kick is handled on PlayerJoin (needs Player for op check)
    }

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

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() != null && !event.getTo().toVector().equals(event.getFrom().toVector())) {
            progressManager.markActive(event.getPlayer().getUniqueId());
        }
    }

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

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Player player = event.getPlayer();
        ItemStack bucket = event.getItemStack();
        if (!progressManager.canUseItem(player, bucket)) {
            event.setCancelled(true);
            player.sendMessage("§cYou have not unlocked that bucket yet.");
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        Player player = event.getPlayer();
        ItemStack bucket = event.getItemStack();
        if (!progressManager.canUseItem(player, bucket)) {
            event.setCancelled(true);
            player.sendMessage("§cYou have not unlocked that bucket yet.");
        }
    }

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

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        progressManager.handlePrepareCraft(event);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        progressManager.handleInventoryClick(event);
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent event) {
        progressManager.handleEnchant(event);
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        progressManager.handlePrepareAnvil(event);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        progressManager.handleEntityDamage(event);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        progressManager.handleEntityDeath(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        progressManager.removeBossBar(event.getPlayer().getUniqueId());
    }

    // ------------------------------------------------------------------------
    // SessionManager integration callbacks
    // ------------------------------------------------------------------------

    void handleSessionStart() {
        sessionActive = true;
        getLogger().info("SessionManager session started – SBPC timers ticking while players are active.");

        for (Player p : Bukkit.getOnlinePlayers()) {
            progressManager.maybeShowFirstStepsIntro(p);
        }
    }

    void handleSessionEnd() {
        sessionActive = false;
        getLogger().info("SessionManager session ended – kicking all non-opped players.");

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isOp()) {
                p.kickPlayer(MessageConfig.get("server-closed-join"));
            }
        }
    }

    void handleSessionTick() {
        // Optional hook point
    }

}
