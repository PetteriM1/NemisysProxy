package org.itxtech.nemisys.command.defaults;

import com.nukkitx.network.raknet.RakNetServer;
import org.itxtech.nemisys.command.CommandSender;

public class ReloadBanlistCommand extends VanillaCommand {

    public ReloadBanlistCommand(String name) {
        super(name, "Reload banned-ips.txt", "/reloadbanlist");
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        RakNetServer.loadIpBans();
        sender.sendMessage("banned-ips.txt reloaded");
        return true;
    }
}
