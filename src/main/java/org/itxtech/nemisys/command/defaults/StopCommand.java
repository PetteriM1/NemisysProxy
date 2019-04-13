package org.itxtech.nemisys.command.defaults;

import org.itxtech.nemisys.command.CommandSender;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class StopCommand extends VanillaCommand {

    public StopCommand(String name) {
        super(name, "Stop the proxy", "/stop", new String[]{"shutdown"});
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {

        sender.sendMessage("\u00A7cStopping the proxy...");

        sender.getServer().shutdown();

        return true;
    }
}
