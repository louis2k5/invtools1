package de.invtools;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class InvTools extends JavaPlugin implements TabExecutor, Listener {

    private static final String PERM_EC = "invtools.ec";
    private static final String PERM_BEC = "invtools.bec";
    private static final String PERM_INVSEE = "invtools.invsee";
    private static final String PERM_BODYSEE = "invtools.bodysee";
    private static final String PERM_CLEAR = "invtools.invclear";
    private static final String PERM_EDIT_OTHERS = "invtools.edit.others";

    private static final List<String> COMMANDS = List.of("ec", "bec", "invsee", "bodysee", "invclear");

    /** Spieler, die gerade ein fremdes Inventar nur ansehen dürfen. */
    private final Set<UUID> readOnly = new HashSet<>();
    private BecManager becs;

    @Override
    public void onEnable() {
        becs = new BecManager(this);
        for (String name : COMMANDS) {
            PluginCommand command = getCommand(name);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().runTaskTimer(this, () -> becs.saveDirty(), 1200L, 1200L);
    }

    @Override
    public void onDisable() {
        if (becs != null) {
            becs.saveAll();
        }
    }

    // ------------------------------------------------------------------ Befehle

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);

        if (name.equals("invclear")) {
            invClear(sender, args);
            return true;
        }
        if (!(sender instanceof Player viewer)) {
            error(sender, "Dieser Befehl ist nur für Spieler.");
            return true;
        }
        switch (name) {
            case "ec" -> enderChest(viewer, args);
            case "bec" -> bigEnderChest(viewer, args);
            case "invsee" -> invSee(viewer, args);
            case "bodysee" -> bodySee(viewer, args);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void enderChest(Player viewer, String[] args) {
        Player target = resolveTarget(viewer, args, PERM_EC);
        if (target == null) {
            return;
        }
        boolean canEdit = canEdit(viewer, target.getUniqueId());
        open(viewer, target.getEnderChest(), canEdit);
        notice(viewer, target.getUniqueId(), "Enderchest von " + target.getName(), canEdit);
    }

    private void bigEnderChest(Player viewer, String[] args) {
        if (!viewer.hasPermission(PERM_BEC)) {
            error(viewer, "Dafür fehlt dir die Berechtigung.");
            return;
        }
        UUID id;
        String name;
        if (args.length == 0 || args[0].equalsIgnoreCase(viewer.getName())) {
            id = viewer.getUniqueId();
            name = viewer.getName();
        } else {
            if (!viewer.hasPermission(PERM_BEC + ".others")) {
                error(viewer, "Dafür fehlt dir die Berechtigung.");
                return;
            }
            Player online = Bukkit.getPlayerExact(args[0]);
            if (online != null) {
                id = online.getUniqueId();
                name = online.getName();
            } else {
                OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(args[0]);
                if (offline == null) {
                    error(viewer, "Spieler \"" + args[0] + "\" ist unbekannt (er muss schon einmal auf dem Server gewesen sein).");
                    return;
                }
                id = offline.getUniqueId();
                name = offline.getName() != null ? offline.getName() : args[0];
            }
        }
        boolean canEdit = canEdit(viewer, id);
        open(viewer, becs.get(id, name), canEdit);
        notice(viewer, id, "große Enderchest von " + name, canEdit);
    }

    private void invSee(Player viewer, String[] args) {
        Player target = resolveTarget(viewer, args, PERM_INVSEE);
        if (target == null) {
            return;
        }
        boolean canEdit = canEdit(viewer, target.getUniqueId());
        open(viewer, target.getInventory(), canEdit);
        notice(viewer, target.getUniqueId(), "Inventar von " + target.getName(), canEdit);
    }

    private void bodySee(Player viewer, String[] args) {
        Player target = resolveTarget(viewer, args, PERM_BODYSEE);
        if (target == null) {
            return;
        }
        boolean canEdit = canEdit(viewer, target.getUniqueId());
        open(viewer, new BodyHolder(target).getInventory(), canEdit);
        send(viewer, "Slots: Helm, Brustplatte, Hose, Schuhe, (Lücke), Zweithand.", NamedTextColor.GRAY);
        notice(viewer, target.getUniqueId(), "Rüstung von " + target.getName(), canEdit);
    }

    private void invClear(CommandSender sender, String[] args) {
        boolean self = sender instanceof Player p && (args.length == 0 || args[0].equalsIgnoreCase(p.getName()));
        Player target;
        if (self) {
            if (!sender.hasPermission(PERM_CLEAR)) {
                error(sender, "Dafür fehlt dir die Berechtigung.");
                return;
            }
            target = (Player) sender;
        } else {
            if (args.length == 0) {
                error(sender, "Verwendung: /invclear <Spieler>");
                return;
            }
            if (!sender.hasPermission(PERM_CLEAR + ".others")) {
                error(sender, "Dafür fehlt dir die Berechtigung.");
                return;
            }
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                error(sender, "Spieler \"" + args[0] + "\" ist nicht online.");
                return;
            }
        }
        // clear() leert Inventar, Rüstung und Zweithand
        target.getInventory().clear();
        target.setItemOnCursor(null);
        if (self) {
            send(sender, "Dein Inventar wurde geleert.", NamedTextColor.GREEN);
        } else {
            send(sender, "Das Inventar von " + target.getName() + " wurde geleert.", NamedTextColor.GREEN);
            send(target, "Dein Inventar wurde von " + sender.getName() + " geleert.", NamedTextColor.YELLOW);
        }
    }

    // ------------------------------------------------------------------ Hilfsmethoden

    /** Prüft Rechte und liefert den Zielspieler (online) oder null, wenn eine Meldung gesendet wurde. */
    private Player resolveTarget(Player viewer, String[] args, String basePerm) {
        if (!viewer.hasPermission(basePerm)) {
            error(viewer, "Dafür fehlt dir die Berechtigung.");
            return null;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase(viewer.getName())) {
            return viewer;
        }
        if (!viewer.hasPermission(basePerm + ".others")) {
            error(viewer, "Du darfst das nicht bei anderen Spielern benutzen.");
            return null;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            error(viewer, "Spieler \"" + args[0] + "\" ist nicht online.");
            return null;
        }
        return target;
    }

    /** Eigenes Zeug darf jeder bearbeiten, fremdes nur mit invtools.edit.others (Standard: OP). */
    private boolean canEdit(Player viewer, UUID owner) {
        return viewer.getUniqueId().equals(owner) || viewer.hasPermission(PERM_EDIT_OTHERS);
    }

    private void open(Player viewer, Inventory inventory, boolean canEdit) {
        InventoryView view = viewer.openInventory(inventory);
        // Erst nach dem Öffnen eintragen: Das Schließen einer vorher offenen Ansicht entfernt den Eintrag wieder.
        if (view != null && !canEdit) {
            readOnly.add(viewer.getUniqueId());
        }
    }

    private void notice(Player viewer, UUID owner, String what, boolean canEdit) {
        if (viewer.getUniqueId().equals(owner)) {
            return;
        }
        if (canEdit) {
            send(viewer, "Du bearbeitest: " + what, NamedTextColor.GRAY);
        } else {
            send(viewer, "Du siehst: " + what + " (nur ansehen, nichts entnehmbar)", NamedTextColor.GRAY);
        }
    }

    private static void send(CommandSender to, String text, NamedTextColor color) {
        to.sendMessage(Component.text("[InvTools] ", NamedTextColor.DARK_GRAY).append(Component.text(text, color)));
    }

    private static void error(CommandSender to, String text) {
        send(to, text, NamedTextColor.RED);
    }

    // ------------------------------------------------------------------ Tab-Vervollständigung

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String perm = switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "ec" -> PERM_EC;
            case "bec" -> PERM_BEC;
            case "invsee" -> PERM_INVSEE;
            case "bodysee" -> PERM_BODYSEE;
            case "invclear" -> PERM_CLEAR;
            default -> null;
        };
        if (perm == null || !sender.hasPermission(perm + ".others")) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(player.getName());
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ Events

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (readOnly.contains(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        if (holder instanceof BodyHolder body) {
            if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
                return;
            }
            if (event.getClickedInventory() == top && BodyHolder.isFiller(event.getSlot())) {
                event.setCancelled(true);
                return;
            }
            getServer().getScheduler().runTask(this, body::apply);
        } else if (holder instanceof BecHolder bec) {
            becs.markDirty(bec.owner());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        if (readOnly.contains(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        Inventory top = event.getView().getTopInventory();
        InventoryHolder holder = top.getHolder();

        if (holder instanceof BodyHolder body) {
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < top.getSize() && BodyHolder.isFiller(rawSlot)) {
                    event.setCancelled(true);
                    return;
                }
            }
            getServer().getScheduler().runTask(this, body::apply);
        } else if (holder instanceof BecHolder bec) {
            becs.markDirty(bec.owner());
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        readOnly.remove(event.getPlayer().getUniqueId());
        if (event.getInventory().getHolder() instanceof BecHolder bec) {
            becs.save(bec.owner());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        readOnly.remove(event.getPlayer().getUniqueId());
    }
}
