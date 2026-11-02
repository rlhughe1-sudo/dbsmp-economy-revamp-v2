
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
