package org.itxtech.nemisys;

import org.itxtech.nemisys.command.CommandReader;
import org.itxtech.nemisys.utils.MainLogger;
import org.itxtech.nemisys.utils.ServerKiller;

public class Nemisys {

    public final static String PATH = System.getProperty("user.dir") + "/";
    public final static String DATA_PATH = System.getProperty("user.dir") + "/";
    public final static String PLUGIN_PATH = DATA_PATH + "plugins";
    public static final long START_TIME = System.currentTimeMillis();
    public static boolean ANSI = true;
    public static int DEBUG = 1;

    public static void main(String[] args) {

        System.setProperty("java.net.preferIPv4Stack" , "true");

        MainLogger logger = new MainLogger(DATA_PATH + "server.log");

        try {
            if (ANSI) {
                System.out.print("\u001B]0;Nemisys Proxy\u0007");
            }
            new Server(logger, PATH, DATA_PATH, PLUGIN_PATH);
        } catch (Exception e) {
            logger.logException(e);
        }

        if (ANSI) {
            System.out.print("\u001B]0;Proxy shutting down...\u0007");
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
            System.out.print("\u001B]0;Proxy Stopped\u0007");
        }

        System.exit(0);
    }
}
