package org.voxelhorizons.shop;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.inventory.ClickType;
import java.util.Locale;

/** UI click map; every action remains subject to server-side trade checks. */
public final class ShopClickActions {
    private ShopClickActions() {}
    public enum Action { BUY, SELL, SELL_ALL, BUY_GUI, SELL_GUI, NONE }
    public static Action action(FileConfiguration config, ClickType click) {
        String fallback = switch (click) {
            case LEFT -> "BUY";
            case RIGHT -> "SELL";
            case SHIFT_LEFT -> "BUY_GUI";
            case SHIFT_RIGHT, MIDDLE -> "SELL_ALL";
            default -> "NONE";
        };
        String value = config.getString("gui.click-actions." + click.name(), fallback);
        if (value == null) return Action.NONE;
        try { return Action.valueOf(value.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException invalid) { return Action.NONE; }
    }
}
