package dev.codex.sanction;

import com.palmergames.bukkit.towny.TownyAPI;
import com.palmergames.bukkit.towny.object.Nation;
import io.papermc.paper.event.player.PlayerTradeEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EmbargoListener implements Listener {
    private static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.RED + "Sanction"
            + ChatColor.DARK_GRAY + "] " + ChatColor.GRAY;

    private final SanctionPlugin plugin;
    private final SanctionManager sanctions;
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public EmbargoListener(SanctionPlugin plugin, SanctionManager sanctions) {
        this.plugin = plugin;
        this.sanctions = sanctions;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        boolean blockedMerchant = entity instanceof AbstractVillager
                && (entity instanceof org.bukkit.entity.WanderingTrader
                ? plugin.getConfig().getBoolean("embargo.block-wandering-trader-trading", true)
                : plugin.getConfig().getBoolean("embargo.block-villager-trading", true));
        boolean blockedPiglin = entity instanceof Piglin
                && plugin.getConfig().getBoolean("embargo.block-piglin-bartering", true);
        if (!blockedMerchant && !blockedPiglin) {
            return;
        }
        denyIfSanctioned(event.getPlayer(), () -> event.setCancelled(true));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMerchantInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !(event.getInventory().getHolder() instanceof AbstractVillager villager)) {
            return;
        }
        boolean blocked = villager instanceof org.bukkit.entity.WanderingTrader
                ? plugin.getConfig().getBoolean("embargo.block-wandering-trader-trading", true)
                : plugin.getConfig().getBoolean("embargo.block-villager-trading", true);
        if (blocked) {
            denyIfSanctioned(player, () -> event.setCancelled(true));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrade(PlayerTradeEvent event) {
        AbstractVillager villager = event.getVillager();
        boolean blocked = villager instanceof org.bukkit.entity.WanderingTrader
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
        if (droppedGold.getThrower() == null) {
            return;
        }
        Player thrower = Bukkit.getPlayer(droppedGold.getThrower());
        if (thrower != null && thrower.hasPermission("sanction.bypass")) {
            return;
        }
        var resident = TownyAPI.getInstance().getResident(droppedGold.getThrower());
        Nation nation = resident == null ? null : TownyAPI.getInstance().getResidentNationOrNull(resident);
        if (nation == null || !sanctions.hasActiveSanction(nation)) {
            return;
        }

        event.setCancelled(true);
        if (thrower != null) {
            sendEmbargoMessage(thrower, sanctions.forNation(nation).get(0));
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
        sendEmbargoMessage(player, active.get(0));
    }

    private void sendEmbargoMessage(Player player, Sanction sanction) {
        long now = System.currentTimeMillis();
        Long previous = lastNotice.put(player.getUniqueId(), now);
        if (lastNotice.size() > 1024) {
            lastNotice.values().removeIf(timestamp -> now - timestamp > 60_000L);
        }
        if (previous != null && now - previous < 2000L) {
            return;
        }
        player.sendMessage(PREFIX + "Your nation is under a trade embargo issued by "
                + ChatColor.GOLD + sanction.getIssuerName() + ChatColor.GRAY + ". Reason: "
                + ChatColor.WHITE + sanction.getReason());
    }
}
