package com.ryden.dbsmp;

import java.io.*;
import java.util.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class DBSMPEconomy extends JavaPlugin implements Listener {
    private File dataDir;
    private final Map<UUID, Double> balances = new HashMap<UUID, Double>();
    private final Map<UUID, Set<UUID>> friends = new HashMap<UUID, Set<UUID>>();
    private final Map<UUID, UUID> friendRequests = new HashMap<UUID, UUID>();
    private final Map<UUID, Trade> trades = new HashMap<UUID, Trade>();
    private final Map<UUID, UUID> tradeRequests = new HashMap<UUID, UUID>();
    private final Map<UUID, Double> tradeMoney = new HashMap<UUID, Double>();
    private final Map<UUID, List<ItemStack>> tradeItems = new HashMap<UUID, List<ItemStack>>();
    private final Map<UUID, Integer> totalEarned = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> tradesCompleted = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> itemsSold = new HashMap<UUID, Integer>();
    private final String shopTitle = ChatColor.DARK_GREEN + "DBSMP Item Shop";

    public void onEnable() {
        dataDir = new File(getDataFolder(), "players");
        if (!dataDir.exists()) dataDir.mkdirs();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("DBSMP Economy enabled. Player data is saved by UUID and is not reset by bans.");
    }

    public void onDisable() {
        for (UUID id : new HashSet<UUID>(balances.keySet())) save(id);
        for (Trade t : new HashSet<Trade>(trades.values())) {
            if (t != null) cancelTrade(t, "Server restarting; trade cancelled.");
        }
    }

    private File file(UUID id) { return new File(dataDir, id.toString() + ".yml"); }

    private void load(UUID id) {
        if (balances.containsKey(id)) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file(id));
        balances.put(id, y.contains("balance") ? y.getDouble("balance") : 30000.0);
        Set<UUID> fs = new HashSet<UUID>();
        for (String s : y.getStringList("friends")) {
            try { fs.add(UUID.fromString(s)); } catch (Exception ignored) {}
        }
        friends.put(id, fs);
        totalEarned.put(id, y.getInt("stats.total-earned", 0));
        tradesCompleted.put(id, y.getInt("stats.trades-completed", 0));
        itemsSold.put(id, y.getInt("stats.items-sold", 0));
    }

    private void save(UUID id) {
        load(id);
        YamlConfiguration y = new YamlConfiguration();
        y.set("uuid", id.toString());
        y.set("last-known-name", Bukkit.getOfflinePlayer(id).getName());
        y.set("balance", balances.get(id));
        List<String> fs = new ArrayList<String>();
        for (UUID f : friends.get(id)) fs.add(f.toString());
        y.set("friends", fs);
        y.set("stats.total-earned", totalEarned.get(id));
        y.set("stats.trades-completed", tradesCompleted.get(id));
        y.set("stats.items-sold", itemsSold.get(id));
        try {
            y.save(file(id));
        } catch (IOException e) {
            getLogger().warning("Could not save player " + id + ": " + e.getMessage());
        }
    }

    private void msg(CommandSender s, String m) {
        s.sendMessage(ChatColor.GREEN + "[DBSMP] " + ChatColor.GRAY + m);
    }

    private Player online(String name) { return Bukkit.getPlayerExact(name); }

    private boolean amount(String s) {
        try {
            double value = Double.parseDouble(s);
            return value > 0 && !Double.isInfinite(value) && !Double.isNaN(value);
        } catch (Exception e) {
            return false;
        }
    }

    private String money(double n) { return String.format(Locale.US, "%,.2f", n); }

    private void openShop(Player p) {
