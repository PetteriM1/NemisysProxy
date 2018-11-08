package org.itxtech.nemisys;

import org.itxtech.nemisys.command.CommandReader;
import org.itxtech.nemisys.network.protocol.mcpe.ProtocolInfo;
import org.itxtech.nemisys.utils.MainLogger;
import org.itxtech.nemisys.utils.ServerKiller;

public class Nemisys {

    public final static String VERSION = "";
    public final static String API_VERSION = "";
    public final static String CODENAME = "";

    public final static String MINECRAFT_VERSION = ProtocolInfo.MINECRAFT_VERSION;
    public final static String MINECRAFT_VERSION_NETWORK = ProtocolInfo.MINECRAFT_VERSION_NETWORK;

    public final static String PATH = System.getProperty("user.dir") + "/";
    public final static String DATA_PATH = System.getProperty("user.dir") + "/";
    public final static String PLUGIN_PATH = DATA_PATH + "plugins";
    public static final long START_TIME = System.currentTimeMillis();
    public static boolean ANSI = true;
    public static int DEBUG = 1;

    public static void main(String[] args) {

        MainLogger logger = new MainLogger(DATA_PATH + "server.log");

        try {
            if (ANSI) {
                System.out.print((char) 0x1b + "]0;Starting Nemisys..." + (char) 0x07);
            }
            new Server(logger, PATH, DATA_PATH, PLUGIN_PATH);
        } catch (Exception e) {
            logger.logException(e);
        }

        if (ANSI) {
            System.out.print((char) 0x1b + "]0;Stopping Nemisys..." + (char) 0x07);
        }

        logger.debug("Stopping other threads...");

        for (Thread thread : java.lang.Thread.getAllStackTraces().keySet()) {
            if (!(thread instanceof InterruptibleThread)) {
                continue;
            }

            logger.debug("Stopping " + thread.getClass().getSimpleName() + " thread...");

            if (thread.isAlive()) {
                thread.interrupt();
            }
        }

        ServerKiller killer = new ServerKiller(8);
        killer.start();

        logger.shutdown();
        logger.interrupt();
        CommandReader.getInstance().removePromptLine();

        if (ANSI) {
            System.out.print((char) 0x1b + "]0;Nemisys Stopped" + (char) 0x07);
        }

        System.exit(0);
    }
}
