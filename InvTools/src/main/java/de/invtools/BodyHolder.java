package de.invtools;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * Rüstungsansicht eines Spielers (9 Slots):
 * 0 Helm, 1 Brustplatte, 2 Hose, 3 Schuhe, 5 Zweithand, übrige Slots sind gesperrte Platzhalter.
 */
public final class BodyHolder implements InventoryHolder {

    public static final int HELMET = 0;
    public static final int CHEST = 1;
    public static final int LEGS = 2;
    public static final int BOOTS = 3;
    public static final int OFFHAND = 5;

    private final Player target;
    private final Inventory inventory;

    public BodyHolder(Player target) {
        this.target = target;
        this.inventory = Bukkit.createInventory(this, 9, Component.text("Rüstung von " + target.getName()));
        refresh();
    }

    public static boolean isFiller(int slot) {
        return slot == 4 || (slot >= 6 && slot <= 8);
    }

    /** Liest die aktuelle Ausrüstung des Spielers in die Ansicht. */
    public void refresh() {
        PlayerInventory pi = target.getInventory();
        inventory.setItem(HELMET, pi.getHelmet());
        inventory.setItem(CHEST, pi.getChestplate());
        inventory.setItem(LEGS, pi.getLeggings());
        inventory.setItem(BOOTS, pi.getBoots());
        inventory.setItem(OFFHAND, pi.getItemInOffHand());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (isFiller(slot)) {
                inventory.setItem(slot, filler());
            }
        }
    }

    /** Schreibt die Ansicht zurück in die Ausrüstung des Spielers. */
    public void apply() {
        if (!target.isOnline()) {
            return;
        }
        PlayerInventory pi = target.getInventory();
        pi.setHelmet(clean(inventory.getItem(HELMET)));
        pi.setChestplate(clean(inventory.getItem(CHEST)));
        pi.setLeggings(clean(inventory.getItem(LEGS)));
        pi.setBoots(clean(inventory.getItem(BOOTS)));
        pi.setItemInOffHand(clean(inventory.getItem(OFFHAND)));
    }

    private static ItemStack clean(ItemStack item) {
        return (item == null || item.getType().isAir()) ? null : item;
    }

    private static ItemStack filler() {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.displayName(Component.text(" "));
        pane.setItemMeta(meta);
        return pane;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
