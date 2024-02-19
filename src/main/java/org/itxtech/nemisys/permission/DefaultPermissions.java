package org.itxtech.nemisys.permission;

import org.itxtech.nemisys.Server;

/**
 * @author CreeperFace
 */
public abstract class DefaultPermissions {

    public static final String ROOT = "nemisys";

    public static Permission registerPermission(Permission perm) {
        return registerPermission(perm, null);
    }

    public static Permission registerPermission(Permission perm, Permission parent) {
        if (parent != null) {
            parent.getChildren().put(perm.getName(), true);
        }
        Server.getInstance().getPluginManager().addPermission(perm);

        return Server.getInstance().getPluginManager().getPermission(perm.getName());
    }

    public static void registerCorePermissions() {
        Permission parent = registerPermission(new Permission(ROOT, "Allows using all Nemisys commands and utilities"));

        Permission broadcasts = registerPermission(new Permission("nemisys.broadcast", "Allows the user to receive all broadcast messages"), parent);

        registerPermission(new Permission("nemisys.broadcast.admin", "Allows the user to receive administrative broadcasts", Permission.DEFAULT_OP), broadcasts);
        registerPermission(new Permission("nemisys.broadcast.user", "Allows the user to receive user broadcasts", Permission.DEFAULT_TRUE), broadcasts);

        broadcasts.recalculatePermissibles();

        Permission commands = registerPermission(new Permission("nemisys.command", "Allows using all Nemisys commands"), parent);

        registerPermission(new Permission("nemisys.command.kick", "Allows the user to kick players", Permission.DEFAULT_OP), commands);
        registerPermission(new Permission("nemisys.command.stop", "Allows the user to stop the server", Permission.DEFAULT_OP), commands);
        registerPermission(new Permission("nemisys.command.list", "Allows the user to list all online players", Permission.DEFAULT_OP), commands);
        registerPermission(new Permission("nemisys.command.help", "Allows the user to view the help menu", Permission.DEFAULT_TRUE), commands);
        registerPermission(new Permission("nemisys.command.plugins", "Allows the user to view the list of plugins", Permission.DEFAULT_OP), commands);
        registerPermission(new Permission("nemisys.command.status", "Allows the user to view the server performance", Permission.DEFAULT_OP), commands);

        commands.recalculatePermissibles();

        parent.recalculatePermissibles();
    }
}
