package us.talabrek.ultimateskyblock.command.challenge;

import com.google.inject.Inject;
import dk.lockfuglsang.minecraft.command.AbstractCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import us.talabrek.ultimateskyblock.challenge.Challenge;
import us.talabrek.ultimateskyblock.challenge.ChallengeCompletion;
import us.talabrek.ultimateskyblock.challenge.ChallengeLogic;
import us.talabrek.ultimateskyblock.player.PlayerInfo;
import us.talabrek.ultimateskyblock.player.PlayerLogic;

import java.util.Map;

import static dk.lockfuglsang.minecraft.po.I18nUtil.marktr;
import static dk.lockfuglsang.minecraft.po.I18nUtil.tr;

/**
 * Shows information about a challenge
 */
public class ChallengeInfoCommand extends AbstractCommand {

    private final ChallengeLogic challengeLogic;
    private final PlayerLogic playerLogic;

    @Inject
    public ChallengeInfoCommand(
        @NotNull ChallengeLogic challengeLogic,
        @NotNull PlayerLogic playerLogic
    ) {
        super("info|i", null, "challenge", marktr("show information about the challenge"));
        this.challengeLogic = challengeLogic;
        this.playerLogic = playerLogic;
    }

    @Override
    public boolean execute(CommandSender sender, String alias, Map<String, Object> data, String... args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(tr("\u00a7cCommand only available for players."));
            return false;
        }
        String challengeName = String.join(" ", args);
        Challenge challenge = challengeLogic.getChallenge(challengeName);
        PlayerInfo playerInfo = playerLogic.getPlayerInfo(player);
        if (challenge != null && challenge.getRank().isAvailable(playerInfo)) {
            player.sendMessage("\u00a7eChallenge Name: " + ChatColor.WHITE + challengeName.toLowerCase());
            if (challengeLogic.getRanks().size() > 1) {
                player.sendMessage(tr("\u00a7eRank: ") + ChatColor.WHITE + challenge.getRank());
            }
            ChallengeCompletion completion = playerInfo.getChallenge(challengeName);
            if (completion.getTimesCompleted() > 0 && !challenge.isRepeatable()) {
                player.sendMessage(tr("\u00a74This Challenge is not repeatable!"));
            }
            ItemStack item = challenge.getDisplayItem(completion, challengeLogic.defaults.enableEconomyPlugin);
            for (String lore : item.getItemMeta().getLore()) {
                if (lore != null && !lore.trim().isEmpty()) {
                    player.sendMessage(lore);
                }
            }
            if (challenge.getType() == Challenge.Type.PLAYER) {
                if (challenge.isTakeItems()) {
                    player.sendMessage(tr("\u00a74You will lose all required items when you complete this challenge!"));
                }
            } else if (challenge.getType() == Challenge.Type.ISLAND) {
                player.sendMessage(tr("\u00a74All required items must be placed on your island, within {0} blocks of you.", challenge.getRadius()));
            }
            player.sendMessage(tr("\u00a7eTo complete this challenge, use \u00a7f/c c {0}", challengeName.toLowerCase()));
        } else {
            player.sendMessage(tr("\u00a74Invalid challenge name! Use /c help for more information"));
        }
        return true;
    }
}
