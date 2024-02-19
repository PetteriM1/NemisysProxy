package org.itxtech.nemisys.command.defaults;

import org.itxtech.nemisys.command.CommandSender;

import java.util.StringJoiner;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class StopCommand extends VanillaCommand {

    public StopCommand(String name) {
        super(name, "Stop the proxy", "/stop [reason]");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        sender.sendMessage("§cStopping the proxy...");
        if (args.length > 0) {
            StringJoiner reason = new StringJoiner(" ");
            for (String s : args) {
                reason.add(s);
            }
            if (reason.length() > 0) {
                sender.getServer().forceShutdown(reason.toString());
                return true;
            }
        }
        sender.getServer().shutdown();
        return true;
    }
}
