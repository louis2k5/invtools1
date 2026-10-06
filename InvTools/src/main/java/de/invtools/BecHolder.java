package de.invtools;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Kennzeichnet ein Inventar als große Enderchest eines bestimmten Spielers. */
public final class BecHolder implements InventoryHolder {

    private final UUID owner;
    private Inventory inventory;

    public BecHolder(UUID owner) {
        this.owner = owner;
    }

    public UUID owner() {
        return owner;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
