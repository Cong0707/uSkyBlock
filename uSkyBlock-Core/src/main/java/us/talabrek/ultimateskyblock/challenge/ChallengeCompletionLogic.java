package us.talabrek.ultimateskyblock.challenge;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import dk.lockfuglsang.minecraft.file.FileUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import us.talabrek.ultimateskyblock.island.IslandInfo;
import us.talabrek.ultimateskyblock.player.PlayerInfo;
import us.talabrek.ultimateskyblock.uSkyBlock;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

/**
 * Responsible for handling ChallengeCompletions
 */
public class ChallengeCompletionLogic {

    private final uSkyBlock plugin;
    private final File storageFolder;
    private final boolean storeOnIsland;
    private final LoadingCache<String, Map<String, ChallengeCompletion>> completionCache;

    public ChallengeCompletionLogic(uSkyBlock plugin, FileConfiguration config) {
        this.plugin = plugin;
        storeOnIsland = config.getString("challengeSharing", "island").equalsIgnoreCase("island");
        completionCache = CacheBuilder
            .from(plugin.getConfig().getString("options.advanced.completionCache", "maximumSize=200,expireAfterWrite=15m,expireAfterAccess=10m"))
            .removalListener((RemovalListener<String, Map<String, ChallengeCompletion>>) removal -> saveToFile(removal.getKey(), removal.getValue()))
            .build(new CacheLoader<>() {
                       @Override
                       public @NotNull Map<String, ChallengeCompletion> load(@NotNull String id) {
                           return loadFromFile(id);
                       }
                   }
            );
        storageFolder = new File(plugin.getDataFolder(), "completion");
        if (!storageFolder.exists() || !storageFolder.isDirectory()) {
            storageFolder.mkdirs();
        }
    }

    private void saveToFile(String id, Map<String, ChallengeCompletion> map) {
        File configFile = new File(storageFolder, id + ".yml");
        FileConfiguration fileConfiguration = new YamlConfiguration();
        saveToConfiguration(fileConfiguration, map);
        try {
            fileConfiguration.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Unable to store challenge-completion to " + configFile, e);
        }
    }

    private void saveToConfiguration(FileConfiguration configuration, Map<String, ChallengeCompletion> map) {
        for (Map.Entry<String, ChallengeCompletion> entry : map.entrySet()) {
            String challengeName = entry.getKey();
            ChallengeCompletion completion = entry.getValue();
            ConfigurationSection section = configuration.createSection(challengeName);
            Instant cooldownUntil = completion.cooldownUntil();
            Long cooldown = cooldownUntil != null ? cooldownUntil.toEpochMilli() : null;
            section.set("firstCompleted", cooldown);
            section.set("timesCompleted", completion.getTimesCompleted());
            section.set("timesCompletedSinceTimer", completion.getTimesCompletedInCooldown());
        }
    }

    private Map<String, ChallengeCompletion> loadFromFile(String id) {
        File configFile = new File(storageFolder, id + ".yml");
        if (!configFile.exists() && storeOnIsland) {
            IslandInfo islandInfo = plugin.getIslandInfo(id);
            if (islandInfo != null && islandInfo.getLeader() != null && islandInfo.getLeaderUniqueId() != null) {
                File leaderFile = new File(storageFolder, islandInfo.getLeaderUniqueId().toString() + ".yml");
                if (leaderFile.exists()) {
                    leaderFile.renameTo(configFile);
                }
            }
        }
        if (configFile.exists()) {
            FileConfiguration fileConfiguration = new YamlConfiguration();
            FileUtil.readConfig(fileConfiguration, configFile);
            if (fileConfiguration.getRoot() != null) {
                return loadFromConfiguration(fileConfiguration.getRoot());
            }
        }
        return new ConcurrentHashMap<>();
    }

    private Map<String, ChallengeCompletion> loadFromConfiguration(ConfigurationSection root) {
        Map<String, ChallengeCompletion> challengeMap = new ConcurrentHashMap<>();
        plugin.getChallengeLogic().populateChallenges(challengeMap);
        if (root != null) {
            for (String challengeName : challengeMap.keySet()) {
                long firstCompleted = root.getLong(challengeName + ".firstCompleted", 0);
                Instant firstCompletedDuration = firstCompleted > 0 ? Instant.ofEpochMilli(firstCompleted) : null;
                challengeMap.put(challengeName, new ChallengeCompletion(
                    challengeName,
                    firstCompletedDuration,
                    root.getInt(challengeName + ".timesCompleted", 0),
                    root.getInt(challengeName + ".timesCompletedSinceTimer", 0)
                ));
            }
        }
        return challengeMap;
    }

    public Map<String, ChallengeCompletion> getIslandChallenges(String islandName) {
        if (storeOnIsland && islandName != null) {
            try {
                return completionCache.get(islandName);
            } catch (ExecutionException e) {
                plugin.getLogger().log(Level.WARNING, "Error fetching challenge-completion for id " + islandName);
            }
        }
        return new ConcurrentHashMap<>();
    }

    public Map<String, ChallengeCompletion> getChallenges(PlayerInfo playerInfo) {
        if (playerInfo == null || !playerInfo.getHasIsland() || playerInfo.locationForParty() == null) {
            return new ConcurrentHashMap<>();
        }
        String id = getCacheId(playerInfo);
        Map<String, ChallengeCompletion> challengeMap = new ConcurrentHashMap<>();
        try {
            challengeMap = completionCache.get(id);
        } catch (ExecutionException e) {
            plugin.getLogger().log(Level.WARNING, "Error fetching challenge-completion for id " + id);
        }
        if (challengeMap.isEmpty()) {
            // Fetch from the player-yml file
            challengeMap = loadFromConfiguration(playerInfo.getConfig().getConfigurationSection("player.challenges"));
            if (!challengeMap.isEmpty()) {
                completionCache.put(id, challengeMap);
            }
            // Wipe it
            playerInfo.getConfig().set("player.challenges", null);
            playerInfo.save();
        }
        return challengeMap;
    }

    private String getCacheId(PlayerInfo playerInfo) {
        return storeOnIsland ? playerInfo.locationForParty() : playerInfo.getUniqueId().toString();
    }

    public void completeChallenge(PlayerInfo playerInfo, String challengeName) {
        Map<String, ChallengeCompletion> challenges = getChallenges(playerInfo);
        if (challenges.containsKey(challengeName)) {
            ChallengeCompletion completion = challenges.get(challengeName);
            if (!completion.isOnCooldown()) {
                Duration resetDuration = plugin.getChallengeLogic().getResetDuration(challengeName);
                if (resetDuration.isPositive()) {
                    Instant now = Instant.now();
                    completion.setCooldownUntil(now.plus(resetDuration));
                } else {
                    completion.setCooldownUntil(null);
                }
            }
            completion.addTimesCompleted();
        }
    }

    public void resetChallenge(PlayerInfo playerInfo, String challenge) {
        Map<String, ChallengeCompletion> challenges = getChallenges(playerInfo);
        if (challenges.containsKey(challenge)) {
            challenges.get(challenge).setTimesCompleted(0);
            challenges.get(challenge).setCooldownUntil(null);
        }
    }

    public int checkChallenge(PlayerInfo playerInfo, String challengeName) {
        Map<String, ChallengeCompletion> challenges = getChallenges(playerInfo);
        if (challenges.containsKey(challengeName)) {
            return challenges.get(challengeName).getTimesCompleted();
        }
        return 0;
    }

    public ChallengeCompletion getChallenge(PlayerInfo playerInfo, String challenge) {
        Map<String, ChallengeCompletion> challenges = getChallenges(playerInfo);
        return challenges.get(challenge);
    }

    public void resetAllChallenges(PlayerInfo playerInfo) {
        Map<String, ChallengeCompletion> challengeMap = new ConcurrentHashMap<>();
        plugin.getChallengeLogic().populateChallenges(challengeMap);
        completionCache.put(getCacheId(playerInfo), challengeMap);
    }

    public void shutdown() {
        flushCache();
    }

    public long flushCache() {
        long size = completionCache.size();
        completionCache.invalidateAll();
        return size;
    }

    public boolean isIslandSharing() {
        return storeOnIsland;
    }

}
