package org.itxtech.nemisys.command.defaults;

import org.itxtech.nemisys.Player;
import org.itxtech.nemisys.command.CommandSender;
import org.itxtech.nemisys.event.TranslationContainer;

/**
 * Created on 2015/11/11 by xtypr.
 * Package org.itxtech.nemisys.command.defaults in project Nukkit.
 */
public class ListCommand extends VanillaCommand {

    public ListCommand(String name) {
        super(name, "Get a list of all online players", "/list", new String[]{"players"});
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        StringBuilder online = new StringBuilder();
        int onlineCount = 0;
        for (Player player : sender.getServer().getOnlinePlayers().values()) {
            online.append(player.getName()).append(", ");
            ++onlineCount;
        }

        if (online.length() > 0) {
            online = new StringBuilder(online.substring(0, online.length() - 2));
        }

        sender.sendMessage(new TranslationContainer("There are {%0}/{%1} players online:",
                new String[]{String.valueOf(onlineCount), String.valueOf(sender.getServer().getMaxPlayers())}));
        sender.sendMessage(online.toString());
        return true;
    }
}
