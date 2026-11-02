\package com.ryden.dbsmp;

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
        Inventory inv = Bukkit.createInventory(null, 54, shopTitle);
        Material[] mats = {Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.BOW,
            Material.ARROW, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.IRON_CHESTPLATE,
            Material.DIAMOND_CHESTPLATE, Material.IRON_HELMET, Material.DIAMOND_HELMET,
            Material.IRON_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.IRON_BOOTS,
            Material.DIAMOND_BOOTS, Material.ENCHANTED_BOOK, Material.EXP_BOTTLE, Material.GOLDEN_APPLE,
            Material.ENDER_PEARL, Material.ARROW};
        double[] prices = {250, 400, 1200, 800, 8, 700, 2500, 1100, 5000, 650, 2200,
            950, 4200, 600, 2000, 1800, 30, 600, 350, 8};
        for (int i = 0; i < mats.length; i++) {
            ItemStack it = new ItemStack(mats[i]);
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName(ChatColor.YELLOW + mats[i].toString().replace('_', ' '));
            meta.setLore(Arrays.asList(ChatColor.GRAY + "Price: $" + money(prices[i]), ChatColor.GREEN + "Click to buy"));
            it.setItemMeta(meta);
            inv.setItem(i, it);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onShopClick(InventoryClickEvent e) {
        if (e.getView() == null || !shopTitle.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player) || e.getRawSlot() < 0 || e.getRawSlot() >= 20) return;
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();
        Material[] mats = {Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.BOW,
            Material.ARROW, Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.IRON_CHESTPLATE,
            Material.DIAMOND_CHESTPLATE, Material.IRON_HELMET, Material.DIAMOND_HELMET,
            Material.IRON_LEGGINGS, Material.DIAMOND_LEGGINGS, Material.IRON_BOOTS,
            Material.DIAMOND_BOOTS, Material.ENCHANTED_BOOK, Material.EXP_BOTTLE, Material.GOLDEN_APPLE,
            Material.ENDER_PEARL, Material.ARROW};
        double[] prices = {250, 400, 1200, 800, 8, 700, 2500, 1100, 5000, 650, 2200,
            950, 4200, 600, 2000, 1800, 30, 600, 350, 8};
        if (slot >= mats.length) return;
        load(p.getUniqueId());
        if (balances.get(p.getUniqueId()) < prices[slot]) {
            msg(p, "You need $" + money(prices[slot]) + ".");
            return;
        }
        ItemStack item = new ItemStack(mats[slot]);
        if (p.getInventory().firstEmpty() == -1) {
            msg(p, "Your inventory is full.");
            return;
        }
        balances.put(p.getUniqueId(), balances.get(p.getUniqueId()) - prices[slot]);
        p.getInventory().addItem(item);
        save(p.getUniqueId());
        msg(p, "Bought " + mats[slot].toString().replace('_', ' ') + " for $" + money(prices[slot]) + ".");
    }

    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String cmd = c.getName().toLowerCase();

        if (cmd.equals("baltop")) {
            int page = 1;
            if (a.length > 0) {
                try {
                    page = Integer.parseInt(a[0]);
                    if (page < 1) {
                        msg(s, "Page must be 1 or higher.");
                        return true;
                    }
                } catch (NumberFormatException e) {
                    msg(s, "Usage: /baltop [page]");
                    return true;
                }
            }
            showBaltop(s, page);
            return true;
        }

        if (cmd.equals("stats")) {
            if (!(s instanceof Player)) {
                msg(s, "Players only.");
                return true;
            }
            Player p = (Player) s;
            UUID id = p.getUniqueId();
            load(id);
            msg(p, "===== YOUR DBSMP STATS =====");
            msg(p, "Balance: $" + money(balances.get(id)));
            msg(p, "Total earned: $" + totalEarned.get(id));
            msg(p, "Items sold: " + itemsSold.get(id));
            msg(p, "Trades completed: " + tradesCompleted.get(id));
            return true;
        }

        if (cmd.equals("eco")) {
            if (!s.hasPermission("dbsmp.admin")) { msg(s, "No permission."); return true; }
            if (a.length != 3 || !(a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("take") || a[0].equalsIgnoreCase("set")) || !amount(a[2])) {
                msg(s, "Usage: /eco <give|take|set> <player> <amount>");
                return true;
            }
            OfflinePlayer op = Bukkit.getOfflinePlayer(a[1]);
            UUID id = op.getUniqueId();
            load(id);
            double v = Double.parseDouble(a[2]), b = balances.get(id);
            balances.put(id, a[0].equalsIgnoreCase("give") ? b + v : a[0].equalsIgnoreCase("take") ? Math.max(0, b - v) : v);
            save(id);
            msg(s, "Balance for " + a[1] + " is now $" + money(balances.get(id)));
            return true;
        }

        if (cmd.equals("bal")) {
            if (a.length > 0 && s.hasPermission("dbsmp.admin")) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(a[0]);
                load(op.getUniqueId());
                msg(s, a[0] + " has $" + money(balances.get(op.getUniqueId())));
                return true;
            }
            if (!(s instanceof Player)) { msg(s, "Players only."); return true; }
            Player p = (Player) s;
            load(p.getUniqueId());
            msg(p, "Balance: $" + money(balances.get(p.getUniqueId())));
            return true;
        }

        if (cmd.equals("pay")) {
            if (!(s instanceof Player) || a.length != 2 || !amount(a[1])) {
                msg(s, "Usage: /pay <player> <amount>");
                return true;
            }
            Player p = (Player) s, t = online(a[0]);
            if (t == null || t.equals(p)) { msg(p, "That player must be online and cannot be you."); return true; }
            double v = Double.parseDouble(a[1]);
            load(p.getUniqueId());
            load(t.getUniqueId());
            if (balances.get(p.getUniqueId()) < v) { msg(p, "Not enough money."); return true; }
            balances.put(p.getUniqueId(), balances.get(p.getUniqueId()) - v);
            balances.put(t.getUniqueId(), balances.get(t.getUniqueId()) + v);
            save(p.getUniqueId());
            save(t.getUniqueId());
            msg(p, "Sent $" + money(v) + " to " + t.getName() + ".");
            msg(t, "You received $" + money(v) + " from " + p.getName() + ".");
            return true;
        }

        if (cmd.equals("shop")) {
            if (s instanceof Player) openShop((Player) s);
            else msg(s, "Players only.");
            return true;
        }

        if (cmd.equals("sell")) {
            if (!(s instanceof Player)) { msg(s, "Players only."); return true; }
            Player p = (Player) s;
            ItemStack hand = p.getItemInHand();
            if (hand == null || hand.getType() == Material.AIR) { msg(p, "Hold an item in your hand first."); return true; }
            double unit = priceFor(hand.getType());
            int count = hand.getAmount();
            double total = unit * count;
            if (unit <= 0) { msg(p, "The shop does not buy that item."); return true; }
            Material soldType = hand.getType();
            p.setItemInHand(null);
            load(p.getUniqueId());
            balances.put(p.getUniqueId(), balances.get(p.getUniqueId()) + total);
            itemsSold.put(p.getUniqueId(), itemsSold.get(p.getUniqueId()) + count);
            totalEarned.put(p.getUniqueId(), totalEarned.get(p.getUniqueId()) + (int) total);
            save(p.getUniqueId());
            msg(p, "Sold " + count + " " + soldType.toString().replace('_', ' ') + " for $" + money(total) + ".");
            return true;
        }

        if (cmd.equals("friend") || cmd.equals("friends")) return friendCommand(s, a);
        if (cmd.equals("trade")) return tradeCommand(s, a);
        return false;
    }

    private void showBaltop(CommandSender sender, int page) {
        File[] files = dataDir.listFiles();
        if (files == null) {
            msg(sender, "Could not read player data.");
            return;
        }

        List<LeaderEntry> entries = new ArrayList<LeaderEntry>();
        for (File f : files) {
            if (!f.isFile() || !f.getName().toLowerCase().endsWith(".yml")) continue;
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            String rawId = y.getString("uuid");
            String name = y.getString("last-known-name");
            if (name == null || name.trim().isEmpty()) name = "Unknown";

            // Include only valid player data files with a stored UUID.
            if (rawId == null) continue;
            try {
                UUID.fromString(rawId);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            entries.add(new LeaderEntry(name, y.getDouble("balance", 30000.0)));
        }

        Collections.sort(entries, new Comparator<LeaderEntry>() {
            public int compare(LeaderEntry first, LeaderEntry second) {
                return Double.compare(second.balance, first.balance);
            }
        });

        int perPage = 10;
        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) perPage));
        if (page > totalPages) {
            msg(sender, "There are only " + totalPages + " leaderboard page(s).");
            page = totalPages;
        }

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, entries.size());

        msg(sender, "===== DBSMP RICHEST PLAYERS =====");
        msg(sender, "Page " + page + " of " + totalPages);

        if (entries.isEmpty()) {
            msg(sender, "No player data found yet.");
            return;
        }

        for (int i = start; i < end; i++) {
            LeaderEntry entry = entries.get(i);
            sender.sendMessage(ChatColor.GOLD + "#" + (i + 1) + " " +
                ChatColor.YELLOW + entry.name + ChatColor.GRAY + " - " +
                ChatColor.GREEN + "$" + money(entry.balance));
        }

        if (page < totalPages) msg(sender, "Next page: /baltop " + (page + 1));
        if (page > 1) msg(sender, "Previous page: /baltop " + (page - 1));
    }

    private class LeaderEntry {
        private final String name;
        private final double balance;

        private LeaderEntry(String name, double balance) {
            this.name = name;
            this.balance = balance;
        }
    }

    private double priceFor(Material m) {
        switch (m) {
            case IRON_INGOT: return 100;
            case GOLD_INGOT: return 160;
            case DIAMOND: return 500;
            case BOW: return 250;
            case ARROW: return 3;
            case IRON_SWORD: return 200;
            case DIAMOND_SWORD: return 900;
            case IRON_CHESTPLATE: return 350;
            case DIAMOND_CHESTPLATE: return 1800;
            case IRON_HELMET: return 200;
            case DIAMOND_HELMET: return 800;
            case IRON_LEGGINGS: return 300;
            case DIAMOND_LEGGINGS: return 1500;
            case IRON_BOOTS: return 180;
            case DIAMOND_BOOTS: return 700;
            case ENCHANTED_BOOK: return 600;
            case EXP_BOTTLE: return 5;
            case GOLDEN_APPLE: return 200;
            case ENDER_PEARL: return 100;
            default: return 0;
        }
    }

    private boolean friendCommand(CommandSender s, String[] a) {
        if (!(s instanceof Player)) { msg(s, "Players only."); return true; }
        Player p = (Player) s;
        UUID id = p.getUniqueId();
        load(id);
        if (a.length == 0 || a[0].equalsIgnoreCase("list")) {
            msg(p, "Friends: " + (friends.get(id).isEmpty() ? "none" : names(friends.get(id))));
            return true;
        }
        if (a[0].equalsIgnoreCase("accept")) {
            UUID from = friendRequests.remove(id);
            if (from == null) { msg(p, "No pending friend request."); return true; }
            load(from);
            friends.get(id).add(from);
            friends.get(from).add(id);
            save(id);
            save(from);
            Player other = Bukkit.getPlayer(from);
            msg(p, "You are now friends with " + (other == null ? Bukkit.getOfflinePlayer(from).getName() : other.getName()) + ".");
            if (other != null) msg(other, p.getName() + " accepted your friend request.");
            return true;
        }
        Player t = online(a[0]);
        if (t == null || t.equals(p)) { msg(p, "That player must be online and cannot be you."); return true; }
        load(t.getUniqueId());
        if (friends.get(id).contains(t.getUniqueId())) { msg(p, "You are already friends."); return true; }
        friendRequests.put(t.getUniqueId(), id);
        msg(p, "Friend request sent to " + t.getName() + ".");
        msg(t, p.getName() + " sent you a friend request. Use /friend accept.");
        return true;
    }

    private String names(Set<UUID> ids) {
        List<String> out = new ArrayList<String>();
        for (UUID id : ids) {
            String n = Bukkit.getOfflinePlayer(id).getName();
            out.add(n == null ? id.toString().substring(0, 8) : n);
        }
        return out.toString();
    }

    private boolean tradeCommand(CommandSender s, String[] a) {
        if (!(s instanceof Player)) { msg(s, "Players only."); return true; }
        Player p = (Player) s;
        UUID id = p.getUniqueId();
        load(id);
        if (a.length == 0) {
            msg(p, "/trade <player>, /trade accept, /trade item, /trade money <amount>, /trade confirm, /trade cancel");
            return true;
        }
        if (a[0].equalsIgnoreCase("accept")) {
            UUID from = tradeRequests.remove(id);
            if (from == null) { msg(p, "No pending trade request."); return true; }
            Player other = Bukkit.getPlayer(from);
            if (other == null) { msg(p, "That player is offline."); return true; }
            Trade t = new Trade(from, id);
            trades.put(from, t);
            trades.put(id, t);
            tradeItems.put(from, new ArrayList<ItemStack>());
            tradeItems.put(id, new ArrayList<ItemStack>());
            tradeMoney.put(from, 0.0);
            tradeMoney.put(id, 0.0);
            msg(p, "Trade started with " + other.getName() + ". Use /trade item while holding an item, /trade money <amount>, then both use /trade confirm.");
            msg(other, "Trade started with " + p.getName() + ". Use /trade item while holding an item, /trade money <amount>, then both use /trade confirm.");
            return true;
        }

        Trade t = trades.get(id);
        if (a[0].equalsIgnoreCase("cancel")) {
            if (t != null) cancelTrade(t, "Trade cancelled.");
            else msg(p, "No active trade.");
            return true;
        }
        if (t == null) {
            if (a[0].equalsIgnoreCase("confirm") || a[0].equalsIgnoreCase("item") || a[0].equalsIgnoreCase("money")) {
                msg(p, "You have no active trade.");
                return true;
            }
            Player target = online(a[0]);
            if (target == null || target.equals(p)) { msg(p, "That player must be online and cannot be you."); return true; }
            tradeRequests.put(target.getUniqueId(), id);
            msg(p, "Trade request sent to " + target.getName() + ".");
            msg(target, p.getName() + " wants to trade. Use /trade accept.");
            return true;
        }

        if (a[0].equalsIgnoreCase("item")) {
            ItemStack hand = p.getItemInHand();
            if (hand == null || hand.getType() == Material.AIR) { msg(p, "Hold the item you want to offer."); return true; }
            tradeItems.get(id).add(hand.clone());
            p.setItemInHand(null);
            t.confirmed.clear();
            tellTrade(t, "Item added to " + p.getName() + "'s offer. Both players must confirm again.");
            return true;
        }

        if (a[0].equalsIgnoreCase("money")) {
            if (a.length != 2 || !amount(a[1])) { msg(p, "Usage: /trade money <amount>"); return true; }
            double v = Double.parseDouble(a[1]);
            if (balances.get(id) < v) { msg(p, "Not enough money."); return true; }
            tradeMoney.put(id, v);
            t.confirmed.clear();
            tellTrade(t, p.getName() + " offered $" + money(v) + ". Both players must confirm again.");
            return true;
        }

        if (a[0].equalsIgnoreCase("confirm")) {
            t.confirmed.add(id);
            UUID other = t.other(id);
            Player op = Bukkit.getPlayer(other);
            msg(p, "Trade confirmed. Waiting for the other player.");
            if (t.confirmed.size() == 2) {
                if (op == null) { cancelTrade(t, "Trade cancelled because a player went offline."); return true; }
                if (balances.get(id) < tradeMoney.get(id) || balances.get(other) < tradeMoney.get(other)) {
                    cancelTrade(t, "Trade cancelled: one player no longer has enough money.");
                    return true;
                }
                for (UUID who : Arrays.asList(id, other)) {
                    Player recipient = Bukkit.getPlayer(who);
                    UUID from = who.equals(id) ? other : id;
                    for (ItemStack item : tradeItems.get(from)) {
                        Map<Integer, ItemStack> leftover = recipient.getInventory().addItem(item.clone());
                        for (ItemStack rem : leftover.values()) recipient.getWorld().dropItemNaturally(recipient.getLocation(), rem);
                    }
                    balances.put(who, balances.get(who) - tradeMoney.get(who) + tradeMoney.get(from));
                    tradesCompleted.put(who, tradesCompleted.get(who) + 1);
                    save(who);
                }
                tellTrade(t, "Trade completed successfully.");
                clearTrade(t);
            }
            return true;
        }

        msg(p, "Unknown trade option.");
        return true;
    }

    private void tellTrade(Trade t, String m) {
        Player a = Bukkit.getPlayer(t.a), b = Bukkit.getPlayer(t.b);
        if (a != null) msg(a, m);
        if (b != null) msg(b, m);
    }

    private void clearTrade(Trade t) {
        trades.remove(t.a);
        trades.remove(t.b);
        tradeItems.remove(t.a);
        tradeItems.remove(t.b);
        tradeMoney.remove(t.a);
        tradeMoney.remove(t.b);
        t.confirmed.clear();
    }

    private void cancelTrade(Trade t, String reason) {
        for (UUID id : Arrays.asList(t.a, t.b)) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                List<ItemStack> its = tradeItems.get(id);
                if (its != null) {
                    for (ItemStack it : its) {
                        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
                        for (ItemStack rem : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rem);
                    }
                }
            }
        }
        tellTrade(t, reason);
        clearTrade(t);
    }

    private class Trade {
        UUID a, b;
        Set<UUID> confirmed = new HashSet<UUID>();
        Trade(UUID x, UUID y) { a = x; b = y; }
        UUID other(UUID id) { return id.equals(a) ? b : a; }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        load(e.getPlayer().getUniqueId());
        save(e.getPlayer().getUniqueId());
    }
}
