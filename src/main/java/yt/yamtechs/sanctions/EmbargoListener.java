package yt.yamtechs.sanctions;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EmbargoListener implements Listener {
    private static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.AQUA + "YamTech"
            + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY;

    private final SanctionsPlugin plugin;
    private final SanctionManager sanctions;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public EmbargoListener(SanctionsPlugin plugin, SanctionManager sanctions) {
        this.plugin = plugin;
        this.sanctions = sanctions;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        boolean blocked = event.getVillager() instanceof WanderingTrader
                ? plugin.getConfig().getBoolean("embargo.block-wandering-trader-trading", true)
                : plugin.getConfig().getBoolean("embargo.block-villager-trading", true);
        if (blocked) {
            denyIfSanctioned(event.getPlayer(), () -> event.setCancelled(true));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPiglinPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Piglin)
                || !plugin.getConfig().getBoolean("embargo.block-piglin-bartering", true)
                || event.getItem().getItemStack().getType() != Material.GOLD_INGOT) {
            return;
        }

        Item droppedGold = event.getItem();
        UUID throwerId = droppedGold.getThrower();
        if (throwerId == null) {
            return;
        }
        Player thrower = Bukkit.getPlayer(throwerId);
        if (thrower != null && thrower.hasPermission("sanction.bypass")) {
            return;
        }
        var resident = TownyAPI.getInstance().getResident(throwerId);
        Nation nation = resident == null ? null : TownyAPI.getInstance().getResidentNationOrNull(resident);
        if (nation == null) {
            return;
        }
        List<Sanction> active = sanctions.forNation(nation);
        if (active.isEmpty()) {
            return;
        }

        event.setCancelled(true);
        if (thrower != null) {
            sendEmbargoMessage(thrower, active);
        }
    }

    private void denyIfSanctioned(Player player, Runnable cancel) {
        if (player.hasPermission("sanction.bypass")) {
            return;
        }
        Nation nation = TownyAPI.getInstance().getNation(player);
        if (nation == null) {
            return;
        }
        List<Sanction> active = sanctions.forNation(nation);
        if (active.isEmpty()) {
            return;
        }
        cancel.run();
        sendEmbargoMessage(player, active);
    }

    private void sendEmbargoMessage(Player player, List<Sanction> active) {
        long now = System.currentTimeMillis();
        Long previous = lastNotice.get(player.getUniqueId());
        if (previous != null && now - previous < 2000L) {
            return;
        }
        lastNotice.put(player.getUniqueId(), now);
        if (lastNotice.size() > 1024) {
            lastNotice.values().removeIf(timestamp -> now - timestamp > 60_000L);
        }
        player.sendMessage(PREFIX + "Your nation is under a trade embargo. Active issuers:");
        for (Sanction sanction : active) {
            player.sendMessage(ChatColor.GRAY + "• " + ChatColor.GOLD + sanction.getIssuerName()
                    + ChatColor.GRAY + " — " + ChatColor.WHITE + sanction.getReason());
        }
    }
}
