package us.talabrek.ultimateskyblock.island.task;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.util.HashSet;
import java.util.Set;

public class RecalculateRunnable extends BukkitRunnable {
    private final uSkyBlock plugin;

    public RecalculateRunnable(uSkyBlock plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        Set<String> recalcIslands = new HashSet<>();
        for (Player player : plugin.getWorldManager().getWorld().getPlayers()) {
            if (player.isOnline() && plugin.playerIsOnIsland(player)) {
                recalcIslands.add(plugin.getPlayerInfo(player).locationForParty());
            }
        }
        if (!recalcIslands.isEmpty()) {
            RecalculateTopTen runnable = new RecalculateTopTen(plugin, plugin.getScheduler(), recalcIslands);
            runnable.runTaskAsynchronously(plugin);
        }
    }
}
