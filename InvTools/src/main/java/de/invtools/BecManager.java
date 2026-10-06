package de.invtools;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Verwaltet die großen Enderchests (54 Slots) und speichert sie pro Spieler als YAML-Datei. */
public final class BecManager {

    public static final int SIZE = 54;

    private final JavaPlugin plugin;
    private final File dir;
    private final Map<UUID, Inventory> cache = new HashMap<>();
    private final Set<UUID> dirty = new HashSet<>();

    public BecManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "bec");
        if (!dir.exists() && !dir.mkdirs()) {
            plugin.getLogger().warning("Ordner " + dir + " konnte nicht erstellt werden.");
        }
    }

    /** Liefert die (gecachte) große Enderchest. Alle Betrachter teilen sich dasselbe Inventar-Objekt. */
    public Inventory get(UUID owner, String ownerName) {
        Inventory inv = cache.get(owner);
        if (inv != null) {
            return inv;
        }
        BecHolder holder = new BecHolder(owner);
        String name = ownerName != null ? ownerName : owner.toString();
        inv = Bukkit.createInventory(holder, SIZE, Component.text("Große Enderchest: " + name));
        holder.setInventory(inv);
        load(owner, inv);
        cache.put(owner, inv);
        return inv;
    }

    public void markDirty(UUID owner) {
        dirty.add(owner);
    }

    public void saveDirty() {
        for (UUID id : new HashSet<>(dirty)) {
            save(id);
        }
    }

    public void saveAll() {
        for (UUID id : new HashSet<>(cache.keySet())) {
            save(id);
        }
    }

    public void save(UUID owner) {
        Inventory inv = cache.get(owner);
        if (inv == null) {
            return;
        }
        YamlConfiguration cfg = new YamlConfiguration();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }
            cfg.set("slots." + slot, Base64.getEncoder().encodeToString(item.serializeAsBytes()));
        }
        File target = new File(dir, owner + ".yml");
        File tmp = new File(dir, owner + ".yml.tmp");
        try {
            cfg.save(tmp);
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            dirty.remove(owner);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Große Enderchest von " + owner + " konnte nicht gespeichert werden", ex);
        }
    }

    private void load(UUID owner, Inventory inv) {
        File file = new File(dir, owner + ".yml");
        if (!file.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = cfg.getConfigurationSection("slots");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                int slot = Integer.parseInt(key);
                String encoded = section.getString(key);
                if (encoded == null || slot < 0 || slot >= inv.getSize()) {
                    continue;
                }
                inv.setItem(slot, ItemStack.deserializeBytes(Base64.getDecoder().decode(encoded)));
            } catch (RuntimeException ex) {
                plugin.getLogger().log(Level.WARNING,
                        "Slot " + key + " der großen Enderchest von " + owner + " konnte nicht gelesen werden", ex);
            }
        }
    }
}
