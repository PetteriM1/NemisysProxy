package org.itxtech.nemisys.command.defaults;

import org.itxtech.nemisys.command.CommandSender;
import org.itxtech.nemisys.event.TranslationContainer;
import org.itxtech.nemisys.plugin.Plugin;
import org.itxtech.nemisys.utils.TextFormat;

import java.util.Map;

/**
 * Created on 2015/11/12 by xtypr.
 * Package org.itxtech.nemisys.command.defaults in project Nukkit.
 */
public class PluginsCommand extends VanillaCommand {

    public PluginsCommand(String name) {
        super(name,
                "Get a list of plugins",
                "/plugins",
                new String[]{"pl"}
        );
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        this.sendPluginList(sender);
        return true;
    }

    private void sendPluginList(CommandSender sender) {
        StringBuilder list = new StringBuilder();
        Map<String, Plugin> plugins = sender.getServer().getPluginManager().getPlugins();
        for (Plugin plugin : plugins.values()) {
            if (list.length() > 0) {
                list.append(TextFormat.WHITE + ", ");
            }
            list.append(plugin.isEnabled() ? TextFormat.GREEN : TextFormat.RED);
            list.append(plugin.getDescription().getFullName());
        }

        sender.sendMessage(new TranslationContainer("Plugins ({%0}): {%1}", new String[]{String.valueOf(plugins.size()), list.toString()}));
    }
}
