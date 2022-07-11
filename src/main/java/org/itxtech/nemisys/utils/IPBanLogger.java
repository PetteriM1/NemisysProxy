package org.itxtech.nemisys.utils;

import org.itxtech.nemisys.Nemisys;

import java.io.*;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentLinkedQueue;

public class IPBanLogger extends Thread {

    private static File logFile;
    public static boolean shutdown;
    public static ConcurrentLinkedQueue<InetAddress> log;

    @Override
    public void run() {
        do {
            waitForMessage();
            flushBuffer(logFile);
        } while (!shutdown);
        flushBuffer(logFile);
        synchronized (this) {
            notify();
        }
    }

    public void initialize() {
        log = new ConcurrentLinkedQueue<>();
        logFile = new File("blocked-connections.log");
        if (!logFile.exists()) {
            try {
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            File oldLogs = new File(Nemisys.DATA_PATH, "logs");
            if (!oldLogs.exists()) {
                oldLogs.mkdirs();
            }
            String newName = "blocked-connections " + new SimpleDateFormat("y-M-d HH.mm.ss").format(new Date(logFile.lastModified())) + ".log";
            logFile.renameTo(new File(oldLogs, newName));
            logFile = new File("blocked-connections.log");
            if (!logFile.exists()) {
                try {
                    logFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        start();
    }

    private void waitForMessage() {
        while (log.isEmpty()) {
            try {
                synchronized (this) {
                    wait(20000);
                }
                Thread.sleep(5);
            } catch (InterruptedException ignore) {
            }
        }
    }

    private void flushBuffer(File logFile) {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8), 1024)) {
            String fileDateFormat = new SimpleDateFormat("y-M-d HH:mm:ss ").format(new Date());
            while (!log.isEmpty()) {
                InetAddress address = log.poll();
                if (address != null) {
                    writer.write(fileDateFormat);
                    writer.write(String.valueOf(address));
                    writer.write("\r\n");
                }
            }
            writer.flush();
        } catch (Exception ignored) {
        }
    }
}
