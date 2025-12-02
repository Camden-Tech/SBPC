package me.BaddCamden.SBPC;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;

import me.BaddCamden.SBPC.commands.SbpcCommand;
import me.BaddCamden.SBPC.config.MessageConfig;
import me.BaddCamden.SBPC.commands.CurrentTimeSkipCommand;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
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
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import me.BaddCamden.SessionLibrary.SessionManager;
import me.BaddCamden.SessionLibrary.events.SessionEndEvent;
import me.BaddCamden.SessionLibrary.events.SessionStartEvent;
import me.BaddCamden.SessionLibrary.events.SessionTickEvent;
import me.BaddCamden.SBPC.api.SbpcAPI;
import me.BaddCamden.SBPC.progress.ProgressManager;

public class SBPCPlugin extends JavaPlugin implements Listener {

    public static final String BLOCK_PLACED_META = "sbpc_placed";

    private static SBPCPlugin instance;

    private DayOfWeek sessionDay;
    private LocalTime sessionStart;
    private LocalTime sessionEnd;
    private ZoneId sessionZone;

    private boolean windowOpen = false;
    private BukkitTask windowTask;
    private BukkitTask tickTask;

    private ProgressManager progressManager;

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
        loadConfigValues();

        this.progressManager = new ProgressManager(this);
        SbpcAPI.init(this.progressManager);

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

        windowTask = Bukkit.getScheduler().runTaskTimer(this, this::checkSessionWindow, 20L, 20L * 20);
        tickTask = Bukkit.getScheduler().runTaskTimer(this, this::tickPlayers, 20L, 20L);

        getLogger().info("SBPC enabled.");
    }

    @Override
    public void onDisable() {
        if (windowTask != null) windowTask.cancel();
        if (tickTask != null) tickTask.cancel();
        if (progressManager != null) {
            progressManager.saveAll();
            progressManager.clearBossBars();
        }
        instance = null;
    }

    public void loadConfigValues() {
        FileConfiguration cfg = getConfig();
        String day = cfg.getString("session.window-day", "WEDNESDAY").toUpperCase();
        String start = cfg.getString("session.start-time", "19:00");
        String end = cfg.getString("session.end-time", "22:00");
        String zone = cfg.getString("session.timezone", "America/Chicago");

        try {
            sessionDay = DayOfWeek.valueOf(day);
        } catch (IllegalArgumentException ex) {
            sessionDay = DayOfWeek.WEDNESDAY;
        }

        sessionStart = LocalTime.parse(start);
        sessionEnd = LocalTime.parse(end);
        sessionZone = ZoneId.of(zone);
        MessageConfig.load(cfg);
    }


    // ------------------------------------------------------------------------
    // Session window & SessionManager integration
    // ------------------------------------------------------------------------

    private void checkSessionWindow() {
        ZonedDateTime now = ZonedDateTime.now(sessionZone);
        boolean inWindow = isWithinWindow(now);

        if (inWindow && !windowOpen) {
            windowOpen = true;
            getLogger().info("SBPC session window opened.");

            // Auto-start a SessionManager session for this window if one isn't already running
            if (!SessionManager.hasActiveSession()) {
                // Compute session length as exact difference between configured start and end times
                ZonedDateTime windowStart = now
                        .withHour(sessionStart.getHour())
                        .withMinute(sessionStart.getMinute())
                        .withSecond(sessionStart.getSecond())
                        .withNano(0);

                ZonedDateTime windowEnd = now
                        .withHour(sessionEnd.getHour())
                        .withMinute(sessionEnd.getMinute())
                        .withSecond(sessionEnd.getSecond())
                        .withNano(0);

                // If end is not after start, treat it as next day (handles windows that cross midnight)
                if (!windowEnd.isAfter(windowStart)) {
                    windowEnd = windowEnd.plusDays(1);
                }

                long durationSeconds = java.time.Duration.between(windowStart, windowEnd).getSeconds();
                int duration = (int) Math.max(1L, durationSeconds);

                try {
                    SessionManager.startNewSession(duration, true);
                } catch (Throwable t) {
                    getLogger().warning("Could not start SessionManager session: " + t.getMessage());
                }
            }
        } else if (!inWindow && windowOpen) {
            windowOpen = false;
            getLogger().info("SBPC session window closed.");
            // When the window closes, end any active session
            if (SessionManager.hasActiveSession()) {
                SessionManager.endSession();
            }
        }
    }



    private boolean isWithinWindow(ZonedDateTime now) {
        if (now.getDayOfWeek() != sessionDay) return false;
        LocalTime t = now.toLocalTime();
        if (sessionEnd.isAfter(sessionStart)) {
            return !t.isBefore(sessionStart) && t.isBefore(sessionEnd);
        } else {
            return !t.isBefore(sessionStart) || t.isBefore(sessionEnd);
        }
    }

    private boolean isWithinWindowNow() {
        return isWithinWindow(ZonedDateTime.now(sessionZone));
    }

    private void tickPlayers() {
        if (!SessionManager.hasActiveSession()) return;
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

        // Players who are not opped cannot join unless a SessionManager session has started
        if (!SessionManager.hasActiveSession() && !player.isOp()) {
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
    // SessionManager events
    // ------------------------------------------------------------------------

    @EventHandler
    public void onSessionStart(SessionStartEvent event) {
        getLogger().info("SessionManager session started – SBPC timers ticking while players are active.");

        // NEW: when a session starts, show the First Steps intro for players
        for (Player p : Bukkit.getOnlinePlayers()) {
            progressManager.maybeShowFirstStepsIntro(p);
        }
    }

    @EventHandler
    public void onSessionEnd(SessionEndEvent event) {
        getLogger().info("SessionManager session ended – kicking all non-opped players.");

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isOp()) {
                p.kickPlayer(MessageConfig.get("server-closed-join"));
            }
        }
    }


    @EventHandler
    public void onSessionTick(SessionTickEvent event) {
        // Optional hook point
    }

 
}
