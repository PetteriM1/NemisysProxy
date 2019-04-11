package org.itxtech.nemisys.command.defaults;

import org.itxtech.nemisys.Player;
import org.itxtech.nemisys.command.CommandSender;
import org.itxtech.nemisys.event.TranslationContainer;
import org.itxtech.nemisys.utils.TextFormat;

/**
 * Created on 2015/11/11 by xtypr.
 * Package org.itxtech.nemisys.command.defaults in project Nukkit.
 */
public class KickCommand extends VanillaCommand {

    public KickCommand(String name) {
        super(name, "Remove the specified player from the network", "/kick <player> [reason ...]");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(new TranslationContainer("Usage: {%0}", this.usageMessage));
            return false;
        }

        String name = args[0];

        String reason = "Kicked!";
        for (int i = 1; i < args.length; i++) {
            reason += args[i] + " ";
        }

        if (reason.endsWith(" ")) {
            reason = reason.substring(0, reason.length() - 1);
        }

        Player player = sender.getServer().getPlayer(name);
        if (player != null) {
            player.close(reason);
            if (reason.length() >= 1) {
                sender.sendMessage(new TranslationContainer("Kicked {%0} from the game: '{%1}'", new String[]{player.getName(), reason}));
            } else {
                sender.sendMessage(new TranslationContainer("Kicked {%0} from the game", player.getName()));
            }
        } else {
            sender.sendMessage(new TranslationContainer(TextFormat.RED + "That player cannot be found"));
        }

        return true;
    }
}
