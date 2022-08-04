package org.itxtech.nemisys.command;

import jline.console.ConsoleReader;
import jline.console.CursorBuffer;
import org.itxtech.nemisys.InterruptibleThread;
import org.itxtech.nemisys.Server;
import org.itxtech.nemisys.event.server.ServerCommandEvent;

import java.io.IOException;

/**
 * @author MagicDroidX
 * Nukkit
 */
public class CommandReader extends Thread implements InterruptibleThread {

    private static CommandReader instance;
    private ConsoleReader reader;
    private CursorBuffer stashed;
    private volatile boolean running = true;

    public static CommandReader getInstance() {
        return instance;
    }

    public void shutdown() {
        running = false;
        interrupt();
    }

    public CommandReader() {
        if (instance != null) {
            throw new RuntimeException("CommandReader is already initialized!");
        }
        try {
            this.reader = new ConsoleReader();
            reader.setPrompt("> ");
            instance = this;
        } catch (IOException e) {
            Server.getInstance().getLogger().error("Unable to start ConsoleReader", e);
        }
        this.setName("Console");
    }

    public String readLine() {
        try {
            return reader.readLine();
        } catch (IOException e) {
            Server.getInstance().getLogger().logException(e);
        }
        return null;
    }

    public void run() {
        while (running) {
            long lastLine = System.currentTimeMillis();
            String line;

            try {
                while ((line = reader.readLine()) != null) {
                    if (Server.getInstance().getConsoleSender() == null || Server.getInstance().getPluginManager() == null) {
                        continue;
                    }

                    if (!line.trim().isEmpty()) {
                        try {
                            ServerCommandEvent event = new ServerCommandEvent(Server.getInstance().getConsoleSender(), line);
                            Server.getInstance().getPluginManager().callEvent(event);
                            if (!event.isCancelled()) {
                                Server.getInstance().getScheduler().scheduleTask(() -> Server.getInstance().dispatchCommand(event.getSender(), event.getCommand()));
                            }
                        } catch (Exception e) {
                            Server.getInstance().getLogger().logException(e);
                        }

                    } else if (System.currentTimeMillis() - lastLine <= 1) {
                        try {
                            sleep(250);
                        } catch (InterruptedException e) {
                            Server.getInstance().getLogger().logException(e);
                        }
                    }
                    lastLine = System.currentTimeMillis();
                }
            } catch (IOException e) {
                Server.getInstance().getLogger().logException(e);
            }
        }
    }

    public synchronized void stashLine() {
        this.stashed = reader.getCursorBuffer().copy();
        try {
            reader.getOutput().write("\u001b[1G\u001b[K");
            reader.flush();
        } catch (IOException ignored) {
        }
    }

    public synchronized void unstashLine() {
        try {
            reader.resetPromptLine("> ", this.stashed.toString(), this.stashed.cursor);
        } catch (IOException ignored) {
        }
    }

    public void removePromptLine() {
        try {
            reader.resetPromptLine("", "", 0);
        } catch (IOException e) {
            Server.getInstance().getLogger().logException(e);
        }
    }
}
