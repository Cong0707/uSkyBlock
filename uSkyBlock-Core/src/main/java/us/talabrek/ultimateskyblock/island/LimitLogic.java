package us.talabrek.ultimateskyblock.island;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dk.lockfuglsang.minecraft.util.ItemStackUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import us.talabrek.ultimateskyblock.handler.WorldGuardHandler;
import us.talabrek.ultimateskyblock.world.WorldManager;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static dk.lockfuglsang.minecraft.po.I18nUtil.marktr;
import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;

@Singleton
public class LimitLogic {
    public enum CreatureType {UNKNOWN, ANIMAL, MONSTER, VILLAGER, GOLEM}

    static {
        marktr("UNKNOWN");
        marktr("ANIMAL");
        marktr("MONSTER");
        marktr("VILLAGER");
        marktr("GOLEM");
    }

    private final WorldManager worldManager;
    private final BlockLimitLogic blockLimitLogic;

    @Inject
    public LimitLogic(@NotNull WorldManager worldManager, @NotNull BlockLimitLogic blockLimitLogic) {
        this.worldManager = worldManager;
        this.blockLimitLogic = blockLimitLogic;
    }

    public Map<CreatureType, Integer> getCreatureCount(us.talabrek.ultimateskyblock.api.IslandInfo islandInfo) {
        Map<CreatureType, Integer> mapCount = new HashMap<>();
        for (CreatureType type : CreatureType.values()) {
            mapCount.put(type, 0);
        }
        Location islandLocation = islandInfo.getIslandLocation();
        ProtectedRegion islandRegionAt = WorldGuardHandler.getIslandRegionAt(islandLocation);
        if (islandRegionAt != null) {
            // Nether and Overworld regions are more or less equal (same x,z coords)
            List<LivingEntity> creatures = WorldGuardHandler.getCreaturesInRegion(worldManager.getWorld(),
                islandRegionAt);
            World nether = worldManager.getNetherWorld();
            if (nether != null) {
                creatures.addAll(WorldGuardHandler.getCreaturesInRegion(nether, islandRegionAt));
            }
            for (LivingEntity creature : creatures) {
                CreatureType key = getCreatureType(creature);
                if (!mapCount.containsKey(key)) {
                    mapCount.put(key, 0);
                }
                mapCount.put(key, mapCount.get(key) + 1);
            }
        }
        return mapCount;
    }

    public Map<CreatureType, Integer> getCreatureMax(us.talabrek.ultimateskyblock.api.IslandInfo islandInfo) {
        Map<CreatureType, Integer> max = new LinkedHashMap<>();
        for (CreatureType creatureType : CreatureType.values()) {
            max.put(creatureType, getMax(islandInfo, creatureType));
        }
        return max;
    }

    public CreatureType getCreatureType(LivingEntity creature) {
        if (creature instanceof Monster
            || creature instanceof Slime
            || creature instanceof Ghast
            || creature instanceof Shulker) {
            return CreatureType.MONSTER;
        } else if (creature instanceof Animals
            || creature instanceof WaterMob) {
            return CreatureType.ANIMAL;
        } else if (creature instanceof Villager) {
            return CreatureType.VILLAGER;
        } else if (creature instanceof IronGolem
            || creature instanceof Snowman) {
            return CreatureType.GOLEM;
        }
        return CreatureType.UNKNOWN;
    }

    public CreatureType getCreatureType(EntityType entityType) {
        if (Monster.class.isAssignableFrom(entityType.getEntityClass())
            || WaterMob.class.isAssignableFrom(entityType.getEntityClass())
            || Slime.class.isAssignableFrom(entityType.getEntityClass())
            || Ghast.class.isAssignableFrom(entityType.getEntityClass())
        ) {
            return CreatureType.MONSTER;
        } else if (Animals.class.isAssignableFrom(entityType.getEntityClass())) {
            return CreatureType.ANIMAL;
        } else if (Villager.class.isAssignableFrom(entityType.getEntityClass())) {
            return CreatureType.VILLAGER;
        } else if (Golem.class.isAssignableFrom(entityType.getEntityClass())) {
            return CreatureType.GOLEM;
        }
        return CreatureType.UNKNOWN;
    }

    public boolean canSpawn(EntityType entityType, us.talabrek.ultimateskyblock.api.IslandInfo islandInfo) {
        Map<CreatureType, Integer> creatureCount = getCreatureCount(islandInfo);
        CreatureType creatureType = getCreatureType(entityType);
        int max = getMax(islandInfo, creatureType);
        return !creatureCount.containsKey(creatureType) || creatureCount.get(creatureType) < max;
    }

    private int getMax(us.talabrek.ultimateskyblock.api.IslandInfo islandInfo, CreatureType creatureType) {
        switch (creatureType) {
            case ANIMAL:
                return islandInfo.getMaxAnimals();
            case MONSTER:
                return islandInfo.getMaxMonsters();
            case VILLAGER:
                return islandInfo.getMaxVillagers();
            case GOLEM:
                return islandInfo.getMaxGolems();
        }
        return Integer.MAX_VALUE;
    }

    public String getSummary(us.talabrek.ultimateskyblock.api.IslandInfo islandInfo) {
        Map<LimitLogic.CreatureType, Integer> creatureMax = getCreatureMax(islandInfo);
        Map<LimitLogic.CreatureType, Integer> count = getCreatureCount(islandInfo);
        StringBuilder sb = new StringBuilder();
        for (LimitLogic.CreatureType key : creatureMax.keySet()) {
            if (key == CreatureType.UNKNOWN) {
                continue; // Skip
            }
            int cnt = count.containsKey(key) ? count.get(key) : 0;
            int max = creatureMax.get(key);
            sb.append(tr("\u00a77{0}: \u00a7a{1}\u00a77 (max. {2})", tr(key.name()), cnt >= max ? tr("\u00a7c{0}", cnt) : cnt, max)).append("\n");
        }
        Map<Material, Integer> blockLimits = blockLimitLogic.getLimits();
        for (Map.Entry<Material, Integer> entry : blockLimits.entrySet()) {
            int blockCount = blockLimitLogic.getCount(entry.getKey(), islandInfo.getIslandLocation());
            int val = entry.getValue();
            if (entry.getKey() == Material.HOPPER){
                val += islandInfo.getHopperLimit();
            }
            if (blockCount >= 0) {
                sb.append(tr("\u00a77{0}: \u00a7a{1}\u00a77 (max. {2})",
                    ItemStackUtil.getItemName(new ItemStack(entry.getKey())),
                    blockCount >= val ? tr("\u00a7c{0}", blockCount) : blockCount,
                    val)).append("\n");
            } else {
                sb.append(tr("\u00a77{0}: \u00a7a{1}\u00a77 (max. {2})",
                    ItemStackUtil.getItemName(new ItemStack(entry.getKey())),
                    tr("\u00a7c{0}", "?"),
                    val)).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
