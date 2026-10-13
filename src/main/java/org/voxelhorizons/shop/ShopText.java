package org.voxelhorizons.shop;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Optional PlaceholderAPI + VoxelCore authored UI glyph resolution, without
 * depending on their compile-time classes. GUI titles are permitted to use
 * VoxelCore's negative-spacing glyph expansion.
 */
public final class ShopText {
    public String render(Player player, String input, boolean guiTitle) {
        String resolved = input == null ? "" : input;
        Plugin placeholders = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (placeholders != null && placeholders.isEnabled()) {
            try {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true,
                        placeholders.getClass().getClassLoader());
                Method parse = api.getMethod("setPlaceholders", Player.class, String.class);
                resolved = (String) parse.invoke(null, player, resolved);
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                // Optional dependency may not expose the standard PlaceholderAPI API.
            }
        }
        Plugin core = Bukkit.getPluginManager().getPlugin("VoxelCore");
        if (core != null && core.isEnabled()) {
            try {
                Object service = core.getClass().getMethod("getTextPlaceholderService").invoke(core);
                resolved = (String) service.getClass()
                        .getMethod("resolve", String.class, boolean.class, boolean.class)
                        .invoke(service, resolved, true, guiTitle);
            } catch (ReflectiveOperationException | ClassCastException ignored) {
                // Never interrupt GUI opening solely due to optional text plugins.
            }
        }
        return ChatColor.translateAlternateColorCodes('&', resolved);
    }
}
