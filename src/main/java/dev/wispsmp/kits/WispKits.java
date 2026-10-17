package dev.wispsmp.kits;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class WispKits extends JavaPlugin implements Listener {
    private static final String TITLE = ChatColor.DARK_PURPLE + "Wisp SMP Kits";
    private final Map<Integer, String> menuSlots = new HashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("kit")).setTabCompleter((sender, command, alias, args) -> {
            if (args.length == 1) return List.of("claim", "create", "edit", "save", "delete", "icon", "list").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
            return List.of();
        });
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("This command must be used in-game."); return true; }
        if (args.length == 0) { openMenu(player); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 1 && !Set.of("claim", "create", "edit", "save", "delete", "icon", "list").contains(sub)) {
            claim(player, args[0]); return true;
        }
        if (sub.equals("list")) {
            Set<String> kits = kitNames();
            player.sendMessage(color("&dKits (&f" + kits.size() + "&d): &f" + (kits.isEmpty() ? "none yet" : String.join(", ", kits)))); return true;
        }
        if (args.length < 2) { help(player); return true; }
        String name = cleanName(args[1]);
        if (name.isBlank()) { player.sendMessage(color("&cInvalid kit name.")); return true; }
        switch (sub) {
            case "claim" -> claim(player, name);
            case "create" -> {
                if (!admin(player)) return true;
                saveKit(name, player.getInventory().getContents(), player.getInventory().getItemInMainHand());
                player.sendMessage(color("&aSaved kit &f" + name + "&a from your inventory. Use &f/kit " + name + "&a to claim it."));
            }
            case "edit" -> {
                if (!admin(player)) return true;
                if (!exists(name)) { player.sendMessage(color("&cThat kit doesn't exist.")); return true; }
                player.getInventory().setContents(getKitContents(name));
                player.sendMessage(color("&eKit loaded into your inventory. Make changes, then run &f/kit save " + name + "&e."));
            }
            case "save" -> {
                if (!admin(player)) return true;
                if (!exists(name)) { player.sendMessage(color("&cThat kit doesn't exist. Use /kit create " + name + " first.")); return true; }
                saveKit(name, player.getInventory().getContents(), getIcon(name));
                player.sendMessage(color("&aUpdated kit &f" + name + "&a."));
            }
            case "delete" -> {
                if (!admin(player)) return true;
                if (!exists(name)) { player.sendMessage(color("&cThat kit doesn't exist.")); return true; }
                getConfig().set("kits." + name, null); saveConfig(); player.sendMessage(color("&aDeleted kit &f" + name + "&a."));
            }
            case "icon" -> {
                if (!admin(player)) return true;
                if (!exists(name)) { player.sendMessage(color("&cThat kit doesn't exist.")); return true; }
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType().isAir()) { player.sendMessage(color("&cHold the item you want to use as the icon.")); return true; }
                getConfig().set("kits." + name + ".icon", hand.clone()); saveConfig(); player.sendMessage(color("&aUpdated icon for &f" + name + "&a."));
            }
            default -> help(player);
        }
        return true;
    }

    private void help(Player p) {
        p.sendMessage(color("&d/kit &7- open kit menu"));
        p.sendMessage(color("&d/kit <name> &7or &d/kit claim <name> &7- claim a kit"));
        if (admin(p)) {
            p.sendMessage(color("&d/kit create <name> &7- save your inventory as a kit"));
            p.sendMessage(color("&d/kit edit <name> &7then /kit save <name> &7- edit a kit"));
            p.sendMessage(color("&d/kit icon <name> &7- set icon to held item; &d/kit delete <name>"));
        }
    }
    private boolean admin(Player p) { if (p.hasPermission("wispkits.admin")) return true; p.sendMessage(color("&cYou don't have permission.")); return false; }
    private String cleanName(String name) { return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", ""); }
    private Set<String> kitNames() { ConfigurationSection s = getConfig().getConfigurationSection("kits"); return s == null ? new TreeSet<>() : new TreeSet<>(s.getKeys(false)); }
    private boolean exists(String name) { return getConfig().isConfigurationSection("kits." + name); }
    private void saveKit(String name, ItemStack[] contents, ItemStack icon) {
        getConfig().set("kits." + name + ".contents", Arrays.asList(contents));
        if (icon != null && !icon.getType().isAir()) getConfig().set("kits." + name + ".icon", icon.clone());
        else if (!getConfig().contains("kits." + name + ".icon")) getConfig().set("kits." + name + ".icon", new ItemStack(Material.CHEST));
        getConfig().set("kits." + name + ".display", name);
        saveConfig();
    }
    private ItemStack[] getKitContents(String name) {
        List<?> list = getConfig().getList("kits." + name + ".contents");
        ItemStack[] result = new ItemStack[41];
        if (list != null) for (int i = 0; i < Math.min(list.size(), result.length); i++)
            if (list.get(i) instanceof ItemStack item) result[i] = item.clone();
        return result;
    }
    private ItemStack getIcon(String name) { ItemStack i = getConfig().getItemStack("kits." + name + ".icon"); return i == null ? new ItemStack(Material.CHEST) : i.clone(); }
    private void claim(Player p, String raw) {
        if (!p.hasPermission("wispkits.claim")) { p.sendMessage(color("&cYou don't have permission to claim kits.")); return; }
        String name = cleanName(raw);
        if (!exists(name)) { p.sendMessage(color("&cKit not found: &f" + raw + "&c. Use &f/kit &cto view available kits.")); return; }
        ItemStack[] items = getKitContents(name);
        for (int slot = 0; slot < Math.min(36, items.length); slot++) {
            ItemStack item = items[slot];
            if (item == null || item.getType().isAir()) continue;
            Map<Integer, ItemStack> leftovers = p.getInventory().addItem(item.clone());
            leftovers.values().forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
        }
        if (items.length >= 40) p.getInventory().setArmorContents(new ItemStack[]{cloneOrNull(items[36]), cloneOrNull(items[37]), cloneOrNull(items[38]), cloneOrNull(items[39])});
        if (items.length >= 41) p.getInventory().setItemInOffHand(cloneOrNull(items[40]));
        p.sendMessage(color("&aClaimed kit &f" + name + "&a."));
    }
    private ItemStack cloneOrNull(ItemStack item) { return item == null || item.getType().isAir() ? null : item.clone(); }

    private void openMenu(Player p) {
        Set<String> kits = kitNames();
        int size = Math.max(27, Math.min(54, ((kits.size() + 8) / 9) * 9));
        Inventory inv = Bukkit.createInventory(null, size, TITLE);
        menuSlots.clear(); int slot = 0;
        for (String name : kits) {
            if (slot >= size) break;
            ItemStack icon = getIcon(name); ItemMeta meta = icon.getItemMeta();
            if (meta != null) { meta.setDisplayName(color("&d" + getConfig().getString("kits." + name + ".display", name))); meta.setLore(List.of(color("&7Click to claim"), color("&8Kit: " + name))); icon.setItemMeta(meta); }
            inv.setItem(slot, icon); menuSlots.put(slot, name); slot++;
        }
        if (kits.isEmpty()) { ItemStack info = new ItemStack(Material.BOOK); ItemMeta m = info.getItemMeta(); if (m != null) { m.setDisplayName(color("&eNo kits created yet")); m.setLore(List.of(color("&7Admins: /kit create <name>"))); info.setItemMeta(m); } inv.setItem(13, info); }
        p.openInventory(inv);
    }
    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !TITLE.equals(e.getView().getTitle())) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        String name = menuSlots.get(e.getRawSlot()); if (name != null) { p.closeInventory(); claim(p, name); }
    }
    @EventHandler public void onDrag(InventoryDragEvent e) { if (TITLE.equals(e.getView().getTitle())) e.setCancelled(true); }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
}