package us.talabrek.ultimateskyblock.event;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import us.talabrek.ultimateskyblock.handler.WorldGuardHandler;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.util.LocationUtil;
import us.talabrek.ultimateskyblock.util.MaterialUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Responsible for forming the correct blocks in nether on block-breaks.
 */
@Singleton
public class NetherTerraFormEvents implements Listener {
    private final uSkyBlock plugin;
    private final Map<Material, List<MaterialUtil.MaterialProbability>> terraFormMap = new HashMap<>();
    private final Map<String, Double> toolWeights = new HashMap<>();
    private static final Random RND = new Random();
    private final double maxScan;
    private final double chanceWither;
    private final double chanceSkeleton;
    private final double chanceBlaze;
    private final boolean terraformEnabled;
    private final boolean spawnEnabled;
    private final double minPitch;
    private final double maxPitch;

    @Inject
    public NetherTerraFormEvents(@NotNull uSkyBlock plugin) {
        this.plugin = plugin;
        // TODO: 23/09/2015 - R4zorax: Allow this to be perk-based?
        terraformEnabled = plugin.getConfig().getBoolean("nether.terraform-enabled", true);
        spawnEnabled = plugin.getConfig().getBoolean("nether.spawn-chances.enabled", true);
        minPitch = plugin.getConfig().getDouble("nether.terraform-min-pitch", -70d);
        maxPitch = plugin.getConfig().getDouble("nether.terraform-max-pitch", 90d);
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("nether.terraform");
        if (config != null) {
            for (String key : config.getKeys(false)) {
                Material mat = Material.matchMaterial(key);
                if (mat != null) {
                    terraFormMap.put(mat, MaterialUtil.createProbabilityList(config.getStringList(key)));
                }
            }
        }
        config = plugin.getConfig().getConfigurationSection("nether.terraform-weight");
        if (config != null) {
            for (String tool : config.getKeys(false)) {
                toolWeights.put(tool, config.getDouble(tool, 1d));
            }
        }
        maxScan = plugin.getConfig().getInt("nether.terraform-distance", 7);
        config = plugin.getConfig().getConfigurationSection("nether.spawn-chances");
        if (config != null) {
            chanceBlaze = config.getDouble("blaze", 0.2);
            chanceWither = config.getDouble("wither", 0.4);
            chanceSkeleton = config.getDouble("skeleton", 0.1);
        } else {
            chanceBlaze = 0.2;
            chanceWither = 0.4;
            chanceSkeleton = 0.1;
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event == null || !terraformEnabled) {
            return;
        }
        Block block = event.getBlock();
        Player player = event.getPlayer();
        if (!plugin.getWorldManager().isSkyNether(block.getWorld()) || !plugin.getWorldManager().isSkyNether(player.getWorld())) {
            return; // Bail out, not our problem
        }
        if (player.getGameMode() != GameMode.SURVIVAL) {
            return;
        }
        if (!plugin.playerIsOnIsland(player)) {
            return;
        }
        if (!terraFormMap.containsKey(block.getType())) {
            return; // Not a block we terra-form on.
        }
        ItemStack tool = event.getPlayer().getInventory().getItemInMainHand();
        if (event.getBlock().getDrops(tool).isEmpty()) {
            return; // Only terra-form when stuff is mined correctly
        }
        double toolWeight = getToolWeight(tool);
        Location playerLocation = player.getEyeLocation();
        Location blockLocation = LocationUtil.centerInBlock(block.getLocation());
        Vector v = new Vector(blockLocation.getX(), blockLocation.getY(), blockLocation.getZ());
        v.subtract(new Vector(playerLocation.getX(), playerLocation.getY(), playerLocation.getZ()));
        v.normalize();
        // Disable spawning above the player... enabling the player to clear a region
        if (playerLocation.getPitch() >= minPitch && playerLocation.getPitch() <= maxPitch) {
            ProtectedCuboidRegion islandRegion = WorldGuardHandler.getIslandRegion(playerLocation);
            List<Material> yield = getYield(block.getType(), toolWeight);
            for (Material mat : yield) {
                spawnBlock(mat, blockLocation, v, islandRegion);
            }
        }
    }

    private Double getToolWeight(ItemStack tool) {
        String toolType = MaterialUtil.getToolType(tool.getType());
        Double d = toolWeights.get(toolType);
        return d != null ? d : 0d;
    }

    private void spawnBlock(Material type, Location location, Vector v, ProtectedCuboidRegion islandRegion) {
        Location spawnLoc;
        if (MaterialUtil.isFallingMaterial(type)) {
            spawnLoc = findSolidSpawnLocation(location, v, islandRegion);
        } else {
            spawnLoc = findAirSpawnLocation(location, v, islandRegion);
        }
        if (spawnLoc != null) {
            spawnLoc.getWorld().getBlockAt(spawnLoc).setType(type);
        }
    }

    private Location findAirSpawnLocation(Location location, Vector v, ProtectedCuboidRegion islandRegion) {
        // Searches in a cone for an air block
        Location lookAt = new Location(location.getWorld(),
            Math.round(location.getX() + v.getX()),
            Math.round(location.getY() + v.getY()),
            Math.round(location.getZ() + v.getZ()));
        while (v.length() < maxScan) {
            for (Location loc : getLocationsInPlane(lookAt, v)) {
                if (loc.getBlock().getType() == Material.AIR && isAdjacentToSolid(loc)
                    && isInIslandRegion(islandRegion, loc)) {
                    return loc;
                }
            }
            double n = v.length();
            v.normalize().multiply(n + 1);
        }
        return null;
    }

    private boolean isInIslandRegion(ProtectedCuboidRegion islandRegion, Location loc) {
        return WorldGuardHandler.isInRegion(islandRegion, loc);
    }

    private boolean isAdjacentToSolid(Location loc) {
        for (BlockFace face : Arrays.asList(BlockFace.DOWN, BlockFace.UP, BlockFace.WEST, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH)) {
            if (loc.getBlock().getRelative(face).getType().isSolid()) {
                return true;
            }
        }
        return false;
    }

    private Location findSolidSpawnLocation(Location location, Vector v, ProtectedCuboidRegion islandRegion) {
        // Searches in a cone for an air block
        while (v.length() < maxScan) {
            for (Location loc : getLocationsInPlane(location, v)) {
                if (loc.getBlock().getType() == Material.AIR
                    && loc.getBlock().getRelative(BlockFace.DOWN).getType().isSolid()
                    && isInIslandRegion(islandRegion, loc)) {
                    return loc;
                }
            }
            double n = v.length();
            v.normalize().multiply(n + 1);
        }
        return null;
    }

    private List<Location> getLocationsInPlane(Location location, Vector v) {
        Location lookAt = new Location(location.getWorld(),
            Math.round(location.getX() + v.getX()),
            Math.round(location.getY() + v.getY()),
            Math.round(location.getZ() + v.getZ()));
        List<Location> locs = new ArrayList<>();
        boolean xFixed = Math.abs(v.getX()) > Math.abs(v.getZ());
        for (int r = 1; r <= v.length(); r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dxz = -r; dxz <= r; dxz++) {
                    if (xFixed) {
                        locs.add(lookAt.clone().add(0, dy, dxz));
                    } else {
                        locs.add(lookAt.clone().add(dxz, dy, 0));
                    }
                }
            }
        }
        Collections.shuffle(locs);
        locs = locs.subList(0, locs.size() / 2); // Only try half
        return locs;
    }

    public List<Material> getYield(Material material, double toolWeight) {
        List<Material> copy = new ArrayList<>();
        for (MaterialUtil.MaterialProbability e : terraFormMap.get(material)) {
            if (RND.nextDouble() < e.getProbability() * toolWeight) {
                copy.add(e.getMaterial());
            }
        }
        return copy;
    }

    @EventHandler
    public void onGhastExplode(EntityExplodeEvent event) {
        if (!plugin.getWorldManager().isSkyNether(event.getEntity().getWorld())) {
            return; // Bail out, not our problem
        }
        // TODO: 23/09/2015 - R4zorax: Perhaps enable this when island has a certain level?
        if (event.getEntity() instanceof Fireball fireball) {
            fireball.setIsIncendiary(false);
            fireball.setFireTicks(0);
            event.setCancelled(true);
        }
    }

    /**
     * Comes AFTER the {@link SpawnEvents#onCreatureSpawn(CreatureSpawnEvent)} - so cancelled will have effect
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (!spawnEnabled || e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NATURAL) {
            return;
        }
        if (!plugin.getWorldManager().isSkyNether(e.getLocation().getWorld())) {
            return;
        }
        if (e.getLocation().getBlockY() > plugin.getWorldManager().getNetherWorld().getMaxHeight()) {
            // Block spawning above nether...
            e.setCancelled(true);
            return;
        }
        if (e.getEntity() instanceof PigZombie) {
            Block block = e.getLocation().getBlock().getRelative(BlockFace.DOWN);
            if (isNetherFortressWalkway(block)) {
                e.setCancelled(true);
                double p = RND.nextDouble();
                if (p <= chanceWither && block.getRelative(BlockFace.UP, 3).getType() == Material.AIR) {
                    WitherSkeleton mob = (WitherSkeleton) e.getLocation().getWorld().spawnEntity(
                        e.getLocation(), EntityType.WITHER_SKELETON);
                    mob.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_SWORD, 1));
                } else if (p <= chanceWither + chanceBlaze) {
                    e.getLocation().getWorld().spawnEntity(e.getLocation(), EntityType.BLAZE);
                } else if (p <= chanceWither + chanceBlaze + chanceSkeleton) {
                    e.getLocation().getWorld().spawnEntity(e.getLocation(), EntityType.SKELETON);
                } else {
                    e.setCancelled(false); // Spawn PigZombie
                }
            }
        }
    }

    private boolean isNetherFortressWalkway(Block block) {
        return block.getType() == Material.NETHER_BRICKS;
    }
}
