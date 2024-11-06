package org.itxtech.nemisys.command;

import org.itxtech.nemisys.plugin.Plugin;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class PluginCommand<T extends Plugin> extends Command implements PluginIdentifiableCommand {

    private final T owningPlugin;

    private CommandExecutor executor;

    public PluginCommand(String name, T owner) {
        super(name);
        this.owningPlugin = owner;
        this.executor = owner;
        this.usageMessage = "";
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.owningPlugin.isEnabled()) {
            return false;
        }

        boolean success = this.executor.onCommand(sender, this, commandLabel, args);

        if (!success && !this.usageMessage.isEmpty()) {
            sender.sendMessage("Usage: " + this.usageMessage);
        }

        return success;
    }

    public CommandExecutor getExecutor() {
        return executor;
    }

    public void setExecutor(CommandExecutor executor) {
        this.executor = (executor != null) ? executor : this.owningPlugin;
    }

    @Override
    public T getPlugin() {
        return this.owningPlugin;
    }
}
