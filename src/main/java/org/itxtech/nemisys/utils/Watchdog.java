package org.itxtech.nemisys.utils;

import org.itxtech.nemisys.Server;

import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.ThreadInfo;

public class Watchdog extends Thread {

    private final Server server;
    private final long time;
    public volatile boolean running;

    public Watchdog(Server server, long time) {
        this.server = server;
        this.time = time;
        this.running = true;
        this.setName("Watchdog");
        this.setDaemon(true);
    }

    public void kill() {
        running = false;
        interrupt();
    }

    @Override
    public void run() {
        while (this.running) {
            long current = server.getNextTick();
            if (current != 0) {
                long diff = System.currentTimeMillis() - current;
                if (diff > time) {
                    MainLogger logger = server.getLogger();
                    logger.emergency("--------- Server stopped responding ---------");
                    logger.emergency("Last response " + Math.round(diff / 1000d) + " seconds ago");
                    logger.emergency("---------------- Main thread ----------------");
                    dumpThread(ManagementFactory.getThreadMXBean().getThreadInfo(server.getPrimaryThread().getId(), Integer.MAX_VALUE), logger);
                    logger.emergency("---------------- All threads ----------------");
                    ThreadInfo[] threads = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
                    for (int i = 0; i < threads.length; i++) {
                        if (i != 0) logger.emergency("------------------------------");
                        dumpThread(threads[i], logger);
                    }
                    logger.emergency("---------------------------------------------");
                    try {
                        sleep(100);
                    } catch (InterruptedException ignore) {
                    }
                    System.exit(1);
                }
            }
            try {
                sleep(Math.max(time >> 2, 1000));
            } catch (InterruptedException ignore) {
                server.getLogger().emergency("The Watchdog thread has been interrupted and is no longer monitoring the server state");
                running = false;
                return;
            }
        }
        server.getLogger().warning("Watchdog has been stopped");
    }

    private static void dumpThread(ThreadInfo thread, Logger logger) {
        logger.emergency("Current Thread: " + thread.getThreadName());
        logger.emergency("\tPID: " + thread.getThreadId() + " | Suspended: " + thread.isSuspended() + " | Native: " + thread.isInNative() + " | State: " + thread.getThreadState());
        if (thread.getLockedMonitors().length != 0) {
            logger.emergency("\tThread is waiting on monitor(s):");
            for (MonitorInfo monitor : thread.getLockedMonitors()) {
                logger.emergency("\t\tLocked on:" + monitor.getLockedStackFrame());
            }
        }
        logger.emergency("\tStack:");
        for (StackTraceElement stack : thread.getStackTrace()) {
            logger.emergency("\t\t" + stack);
        }
    }
}
