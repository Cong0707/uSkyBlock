package us.talabrek.ultimateskyblock.handler;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.function.mask.RegionMask;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import dk.lockfuglsang.minecraft.util.Timer;
import org.apache.commons.lang3.Validate;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import us.talabrek.ultimateskyblock.Settings;
import us.talabrek.ultimateskyblock.handler.task.WorldEditClear;
import us.talabrek.ultimateskyblock.player.PlayerPerk;
import us.talabrek.ultimateskyblock.uSkyBlock;
import us.talabrek.ultimateskyblock.util.LogUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WorldEditHandler {
    private static final Logger log = Logger.getLogger(WorldEditHandler.class.getName());

    public static void loadIslandSchematic(final File file, final Location origin, PlayerPerk playerPerk) {
        log.finer("Trying to load schematic " + file);
        if (file == null || !file.exists() || !file.canRead()) {
            LogUtil.log(Level.WARNING, "Unable to load schematic " + file);
        }
        boolean noAir = false;
        BlockVector3 to = BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());

        try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
            .world(new BukkitWorld(origin.getWorld())).build()) {
            editSession.setSideEffectApplier(SideEffectSet.none());
            ProtectedRegion region = WorldGuardHandler.getIslandRegionAt(origin);
            if (region != null) {
                editSession.setMask(new RegionMask(getRegion(origin.getWorld(), region)));
            }
            ClipboardFormat clipboardFormat = ClipboardFormats.findByFile(file);
            try (InputStream in = new FileInputStream(file)) {
                Clipboard clipboard = clipboardFormat.getReader(in).read();
                Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(to)
                    .ignoreAirBlocks(noAir)
                    .build();
                Operations.completeBlindly(operation);
            }
        } catch (IOException e) {
            log.log(Level.INFO, "Unable to paste schematic " + file, e);
        }
    }

    /**
     * Returns all the chunks that are fully contained within the region.
     */
    public static Set<BlockVector2> getInnerChunks(Region region) {
        Set<BlockVector2> chunks = new HashSet<>();
        int minX = region.getMinimumPoint().getBlockX();
        int minZ = region.getMinimumPoint().getBlockZ();
        int maxX = region.getMaximumPoint().getBlockX();
        int maxZ = region.getMaximumPoint().getBlockZ();
        int cx = minX & 0xF;
        int cz = minZ & 0xF;
        minX = cx != 0 ? minX - cx + 16 : minX;
        minZ = cz != 0 ? minZ - cz + 16 : minZ;
        cx = maxX & 0xF;
        cz = maxZ & 0xF;
        maxX = cx != 15 ? maxX - cx : maxX;
        maxZ = cz != 15 ? maxZ - cz : maxZ;
        for (int x = minX; x < maxX; x += 16) {
            for (int z = minZ; z < maxZ; z += 16) {
                chunks.add(BlockVector2.at(x >> 4, z >> 4));
            }
        }
        return chunks;
    }

    public static Set<BlockVector2> getOuterChunks(Region region) {
        Set<BlockVector2> chunks = new HashSet<>();
        int minX = region.getMinimumPoint().getBlockX();
        int minZ = region.getMinimumPoint().getBlockZ();
        int maxX = region.getMaximumPoint().getBlockX();
        int maxZ = region.getMaximumPoint().getBlockZ();
        int cx = minX & 0xF;
        int cz = minZ & 0xF;
        minX = minX - cx;
        minZ = minZ - cz;
        cx = maxX & 0xF;
        cz = maxZ & 0xF;
        maxX = cx != 15 ? maxX - cx + 16 : maxX;
        maxZ = cz != 15 ? maxZ - cz + 16 : maxZ;
        for (int x = minX; x < maxX; x += 16) {
            for (int z = minZ; z < maxZ; z += 16) {
                chunks.add(BlockVector2.at(x >> 4, z >> 4));
            }
        }
        return chunks;
    }

    public static Set<BlockVector2> getChunks(Region region) {
        Set<BlockVector2> chunks = new HashSet<>();
        int minX = region.getMinimumPoint().getBlockX();
        int minZ = region.getMinimumPoint().getBlockZ();
        int maxX = region.getMaximumPoint().getBlockX();
        int maxZ = region.getMaximumPoint().getBlockZ();
        int cx = minX & 0xF;
        int cz = minZ & 0xF;
        minX = (minX - cx) >> 4;
        minZ = (minZ - cz) >> 4;
        maxX = (maxX - (maxX & 0xF) + 16) >> 4;
        maxZ = (maxZ - (maxZ & 0xF) + 16) >> 4;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                chunks.add(BlockVector2.at(x, z));
            }
        }
        return chunks;
    }

    /**
     * Returns a collection of regions covering the borders of the original region.
     * <b>Note:</b> complements the #getInnerChunks
     *
     * <pre>
     *     ^
     *     |           +---+---+---+---+---+---+
     *     |           |   |   |   |   |   |   |
     *     |           |   |   |   |   |   |   |
     *     |           +---+---+---+---+---+---+
     *     |         l |   | D=======C |   |   |
     *     |           |   | I |   | I |   |   |
     *     |         k +---+-I-R---Q-I-+---+---+
     *     |           |   | I |   | I |   |   |
     *     |           |   | I |   | I |   |   |
     *     |         j +---+-I-O---P-I-+---+---+
     *     |           |   | I |   | I |   |   |
     *     |         i |   | A=======B |   |   |
     *     |           +---+---+---+---+---+---+
     *     |                 a b   c d
     *     +----------------------------------------->
     *
     * Points:
     *     A = (a,i)
     *     B = (d,i)
     *     C = (d,l)
     *     D = (a,l)
     *
     *     M(x) = X mod 16, i.e. Mc = C mod 16.
     *
     * Borders:
     *     {@code O = A + 16 - Ma   | A > 0}
     *     {@code   = A - Ma        | A <= 0}
     *
     *     {@code Q = C - Mc - 1    | C > 0 && Mc != 15}
     *     {@code   = C + Mc - 16   | C < 0 && Mc != -1}
     * </pre>
     */
    public static Set<Region> getBorderRegions(Region region) {
        Set<Region> borders = new HashSet<>();
        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();
        int minY = min.getBlockY();
        int maxY = max.getBlockY();
        int minX = min.getBlockX();
        int maxX = max.getBlockX();
        int minZ = min.getBlockZ();
        int maxZ = max.getBlockZ();

        int minModX = minX % 16;
        int maxModX = maxX % 16;
        int minModZ = minZ % 16;
        int maxModZ = maxZ % 16;
        // Negative values are aligned differently than positive values
        int minChunkX = minModX > 0 ? minX + 16 - minModX : minX - minModX;
        int maxChunkX = maxModX >= 0 && maxModX != 15 ? maxX - maxModX - 1 : maxModX < 0 && maxModX != -1 ? maxX - 16 + maxModX : maxX;
        int minChunkZ = minModZ > 0 ? minZ + 16 - minModZ : minZ - minModZ;
        int maxChunkZ = maxModZ >= 0 && maxModZ != 15 ? maxZ - maxModZ - 1 : maxModZ < 0 && maxModZ != -1 ? maxZ - 16 + maxModZ : maxZ;
        // min < minChunk < maxChunk < max
        if (minModX != 0) {
            borders.add(new CuboidRegion(region.getWorld(),
                BlockVector3.at(minX, minY, minZ),
                BlockVector3.at(minChunkX - 1, maxY, maxZ)));
        }
        if (maxModZ != 15 && maxModZ != -1) {
            borders.add(new CuboidRegion(region.getWorld(),
                BlockVector3.at(minChunkX, minY, maxChunkZ + 1),
                BlockVector3.at(maxChunkX, maxY, maxZ)));
        }
        if (maxModX != 15 && maxModX != -1) {
            borders.add(new CuboidRegion(region.getWorld(),
                BlockVector3.at(maxChunkX + 1, minY, minZ),
                BlockVector3.at(maxX, maxY, maxZ)));
        }
        if (minModZ != 0) {
            borders.add(new CuboidRegion(region.getWorld(),
                BlockVector3.at(minChunkX, minY, minZ),
                BlockVector3.at(maxChunkX, maxY, minChunkZ - 1)));
        }
        return borders;
    }

    public static void clearIsland(@NotNull final World islandWorld, @NotNull final ProtectedRegion region,
                                   @Nullable final Runnable afterDeletion) {
        Validate.notNull(islandWorld, "IslandWorld cannot be null");
        Validate.notNull(region, "Region cannot be null");

        log.finer("Clearing island " + region);
        uSkyBlock plugin = uSkyBlock.getInstance();
        final Timer timer = Timer.start();
        final Region cube = getRegion(islandWorld, region);
        Runnable onCompletion = () -> {
            Duration elapsed = timer.elapsed();
            LogUtil.log(Level.INFO, String.format("Cleared island on %s in %d.%03d seconds",
                islandWorld.getName(), elapsed.toSeconds(), elapsed.toMillisPart()));
            if (afterDeletion != null) {
                afterDeletion.run();
            }
        };
        Set<BlockVector2> innerChunks;
        Set<Region> borderRegions = new HashSet<>();
        if (isOuterPossible()) {
            if (Settings.island_protectionRange == Settings.island_distance) {
                innerChunks = getInnerChunks(cube);
            } else {
                innerChunks = getOuterChunks(cube);
            }
        } else {
            innerChunks = getInnerChunks(cube);
            borderRegions = getBorderRegions(cube);
        }
        List<Chunk> chunkList = new ArrayList<>();
        for (BlockVector2 vector : innerChunks) {
            chunkList.add(islandWorld.getChunkAt(vector.getBlockX(), vector.getBlockZ()));
        }
        WorldEditClear weClear = new WorldEditClear(plugin, islandWorld, borderRegions, onCompletion);
        plugin.getWorldManager().getChunkRegenerator(islandWorld).regenerateChunks(chunkList, weClear);
    }

    public static Region getRegion(World skyWorld, ProtectedRegion region) {
        return new CuboidRegion(new BukkitWorld(skyWorld), region.getMinimumPoint(), region.getMaximumPoint());
    }

    public static boolean isOuterPossible() {
        return Settings.island_distance >= Settings.island_protectionRange &&
            ((Settings.island_distance % 32) == 0 || (Settings.island_distance - Settings.island_protectionRange) > 32);
    }

    public static void loadRegion(Location location) {
        ProtectedRegion region = WorldGuardHandler.getIslandRegionAt(location);
        World world = location.getWorld();
        Region cube = getRegion(world, region);
        for (BlockVector2 chunk : cube.getChunks()) {
            world.unloadChunk(chunk.getBlockX(), chunk.getBlockZ(), true);
            world.loadChunk(chunk.getBlockX(), chunk.getBlockZ(), false);
        }
    }

    public static void unloadRegion(Location location) {
        ProtectedRegion region = WorldGuardHandler.getIslandRegionAt(location);
        World world = location.getWorld();
        Region cube = getRegion(world, region);
        for (BlockVector2 chunk : cube.getChunks()) {
            world.unloadChunk(chunk.getBlockX(), chunk.getBlockZ(), true);
        }
    }

    public static void refreshRegion(Location location) {
        ProtectedRegion region = WorldGuardHandler.getIslandRegionAt(location);
        World world = location.getWorld();
        Region cube = getRegion(world, region);
        for (BlockVector2 chunk : cube.getChunks()) {
            world.refreshChunk(chunk.getBlockX(), chunk.getBlockZ());
        }
    }

    public static EditSession createEditSession(com.sk89q.worldedit.world.World bukkitWorld, int maxBlocks) {
        return WorldEdit.getInstance().newEditSessionBuilder().world(bukkitWorld).maxBlocks(maxBlocks).build();
    }
}
