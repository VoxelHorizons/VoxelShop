package org.voxelhorizons.shop.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.voxelhorizons.shop.market.MarketQuote;

import java.math.BigDecimal;
import java.util.Objects;

/** Fired synchronously before ANY journal, wallet or inventory changes. */
public final class VoxelShopTradePreEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String shopId, offerId, currency;
    private final MarketQuote.Side side;
    private final int quantity;
    private final BigDecimal total;
    private boolean cancelled;

    public VoxelShopTradePreEvent(Player player, String shopId, String offerId,
                                  MarketQuote.Side side, int quantity, String currency, BigDecimal total) {
        this.player = Objects.requireNonNull(player);
        this.shopId = Objects.requireNonNull(shopId);
        this.offerId = Objects.requireNonNull(offerId);
        this.side = Objects.requireNonNull(side);
        this.quantity = quantity;
        this.currency = Objects.requireNonNull(currency);
        this.total = Objects.requireNonNull(total);
    }

    public Player getPlayer() { return player; }
    public String getShopId() { return shopId; }
    public String getOfferId() { return offerId; }
    public MarketQuote.Side getSide() { return side; }
    public int getQuantity() { return quantity; }
    public String getCurrency() { return currency; }
    public BigDecimal getTotal() { return total; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { cancelled = cancel; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
