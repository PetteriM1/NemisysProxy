package org.itxtech.nemisys.utils;

import org.fusesource.jansi.AnsiConsole;
import org.itxtech.nemisys.Nemisys;
import org.itxtech.nemisys.command.CommandReader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author MagicDroidX
 * Nukkit
 */
 public class MainLogger extends ThreadedLogger {
 
     protected final String logPath;
     protected final ConcurrentLinkedQueue<String> logBuffer = new ConcurrentLinkedQueue<>();
     protected AtomicBoolean shutdown = new AtomicBoolean(false);
     protected AtomicBoolean isShutdown = new AtomicBoolean(false);
     protected LogLevel logLevel = LogLevel.DEFAULT_LEVEL;
     //private final Map<TextFormat, String> replacements = new EnumMap<>(TextFormat.class);
     //private final TextFormat[] colors = TextFormat.values();

     protected static MainLogger logger;
     private File logFile;
 
     public MainLogger(String logFile) {
         this(logFile, LogLevel.DEFAULT_LEVEL);
     }
 
     public MainLogger(String logFile, LogLevel logLevel) {
 
         if (logger != null) {
             throw new RuntimeException("MainLogger has been already created");
         }
         logger = this;
         this.logPath = logFile;
         this.setName("MainLogger");
         this.initialize();
         this.start();
     }
 
     public MainLogger(String logFile, boolean logDebug) {
         this(logFile, logDebug ? LogLevel.DEBUG : LogLevel.INFO);
     }
 
     public static MainLogger getLogger() {
         return logger;
     }
 
     @Override
     public void emergency(String message) {
         if (LogLevel.EMERGENCY.getLevel() <= logLevel.getLevel())
             this.send(/*TextFormat.RED +*/ "[EMERGENCY] " + message);
     }
 
     @Override
     public void alert(String message) {
         if (LogLevel.ALERT.getLevel() <= logLevel.getLevel())
             this.send(/*TextFormat.RED +*/ "[ALERT] " + message);
     }
 
     @Override
     public void critical(String message) {
         if (LogLevel.CRITICAL.getLevel() <= logLevel.getLevel())
             this.send(/*TextFormat.RED +*/ "[CRITICAL] " + message);
     }
 
     @Override
     public void error(String message) {
         if (LogLevel.ERROR.getLevel() <= logLevel.getLevel())
             this.send(/*TextFormat.DARK_RED +*/ "[ERROR] " + message);
     }
 
     @Override
     public void warning(String message) {
         if (LogLevel.WARNING.getLevel() <= logLevel.getLevel())
             this.send(/*TextFormat.YELLOW +*/ "[WARNING] " + message);
     }
 
     @Override
     public void notice(String message) {
         if (LogLevel.NOTICE.getLevel() <= logLevel.getLevel())
             this.send(/*TextFormat.AQUA +*/ "[NOTICE] " + message);
     }
 
     @Override
     public void info(String message) {
         if (LogLevel.INFO.getLevel() <= logLevel.getLevel())
             this.send(/*TextFormat.WHITE +*/ "[INFO] " + message);
     }
 
     @Override
     public void debug(String message) {
         if (LogLevel.DEBUG.getLevel() <= logLevel.getLevel())
             this.send(/*TextFormat.GRAY +*/ "[DEBUG] " + message);
     }
 
     public void setLogDebug(Boolean logDebug) {
         this.logLevel = logDebug ? LogLevel.DEBUG : LogLevel.INFO;
     }
 
     public void logException(Exception e) {
         this.alert(Utils.getExceptionMessage(e));
     }
 
     @Override
     public void log(LogLevel level, String message) {
         level.log(this, message);
     }
 
     public void shutdown() {
         if (shutdown.compareAndSet(false, true)) {
            while (!isShutdown.get()) {
                 try {
                     Thread.sleep(1000);
                 } catch (InterruptedException ignored) {}
             }
         }
     }
 
     protected void send(String message) {
         this.send(message, -1);
         synchronized (this) {
             this.notify();
         }
     }
 
     protected void send(String message, int level) {
         logBuffer.add(message);
     }
 
    /*private String colorize(String string) {
         if (string.indexOf(TextFormat.ESCAPE) < 0) {
             return string;
         } else if (Nemisys.ANSI) {
             for (TextFormat color : colors) {
                 if (replacements.containsKey(color)) {
                     string = string.replaceAll("(?i)" + color, replacements.get(color));
                 } else {
                     string = string.replaceAll("(?i)" + color, "");
                 }
             }
         } else {
             return TextFormat.clean(string);
         }
         return string + Ansi.ansi().reset();
     }*/
 
     @Override
     public void run() {
         do {
             waitForMessage();
             flushBuffer(logFile);
         } while (!shutdown.get());
 
         if (!isShutdown.compareAndSet(false, true)) {
            throw new IllegalStateException("MainLogger has already shutdown");
         }
     }
 
     private void initialize() {
         AnsiConsole.systemInstall();
         logFile = new File(logPath);
         if (!logFile.exists()) {
             try {
                 logFile.createNewFile();
             } catch (IOException e) {
                 this.logException(e);
             }
         } else {
             File oldLogs = new File(Nemisys.DATA_PATH, "logs");
             if (!oldLogs.exists()) {
                 oldLogs.mkdirs();
             }
             String newName = new SimpleDateFormat("y-M-d HH.mm.ss").format(new Date(logFile.lastModified())) + ".log";
             logFile.renameTo(new File(oldLogs, newName));
             logFile = new File(logPath);
             if (!logFile.exists()) {
                 try {
                     logFile.createNewFile();
                 } catch (IOException e) {
                     this.logException(e);
                 }
             }
         }
         /*replacements.put(TextFormat.BLACK, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.BLACK).boldOff().toString());
         replacements.put(TextFormat.DARK_BLUE, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.BLUE).boldOff().toString());
         replacements.put(TextFormat.DARK_GREEN, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.GREEN).boldOff().toString());
         replacements.put(TextFormat.DARK_AQUA, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.CYAN).boldOff().toString());
         replacements.put(TextFormat.DARK_RED, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.RED).boldOff().toString());
         replacements.put(TextFormat.DARK_PURPLE, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.MAGENTA).boldOff().toString());
         replacements.put(TextFormat.GOLD, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.YELLOW).boldOff().toString());
         replacements.put(TextFormat.GRAY, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.WHITE).boldOff().toString());
         replacements.put(TextFormat.DARK_GRAY, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.BLACK).bold().toString());
         replacements.put(TextFormat.BLUE, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.BLUE).bold().toString());
         replacements.put(TextFormat.GREEN, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.GREEN).bold().toString());
         replacements.put(TextFormat.AQUA, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.CYAN).bold().toString());
         replacements.put(TextFormat.RED, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.RED).bold().toString());
         replacements.put(TextFormat.LIGHT_PURPLE, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.MAGENTA).bold().toString());
         replacements.put(TextFormat.YELLOW, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.YELLOW).bold().toString());
         replacements.put(TextFormat.WHITE, Ansi.ansi().a(Ansi.Attribute.RESET).fg(Ansi.Color.WHITE).bold().toString());
         replacements.put(TextFormat.BOLD, Ansi.ansi().a(Ansi.Attribute.UNDERLINE_DOUBLE).toString());
         replacements.put(TextFormat.STRIKETHROUGH, Ansi.ansi().a(Ansi.Attribute.STRIKETHROUGH_ON).toString());
         replacements.put(TextFormat.UNDERLINE, Ansi.ansi().a(Ansi.Attribute.UNDERLINE).toString());
         replacements.put(TextFormat.ITALIC, Ansi.ansi().a(Ansi.Attribute.ITALIC).toString());
         replacements.put(TextFormat.RESET, Ansi.ansi().a(Ansi.Attribute.RESET).toString());*/
     }
 
     private void waitForMessage() {
         while (logBuffer.isEmpty()) {
             try {
                 synchronized (this) {
                     wait(25000);
                 }
                 Thread.sleep(5);
             } catch (InterruptedException ignore) {}
         }
     }
 
     private synchronized void flushBuffer(File logFile) {
         try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8), 1024)) {
             Date now = new Date();
             String consoleDateFormat = new SimpleDateFormat("HH:mm:ss ").format(now);
             String fileDateFormat = new SimpleDateFormat("y-M-d HH:mm:ss ").format(now);
             while (!logBuffer.isEmpty()) {
                 String message = logBuffer.poll();
                 if (message != null) {
                     writer.write(fileDateFormat);
                     writer.write(TextFormat.clean(message));
                     writer.write("\r\n");
                     CommandReader.getInstance().stashLine();
                     System.out.println(TextFormat.clean(consoleDateFormat + message)/*colorize(TextFormat.AQUA + consoleDateFormat + TextFormat.RESET + message + TextFormat.RESET)*/);
                     CommandReader.getInstance().unstashLine();
                 }
             }
             writer.flush();
         } catch (IOException e) {
             this.logException(e);
         }
     }
 
     @Override
     public void emergency(String message, Throwable t) {
         this.emergency(message + "\r\n" + Utils.getExceptionMessage(t));
     }
 
     @Override
     public void alert(String message, Throwable t) {
         this.alert(message + "\r\n" + Utils.getExceptionMessage(t));
     }
 
     @Override
     public void critical(String message, Throwable t) {
         this.critical(message + "\r\n" + Utils.getExceptionMessage(t));
     }
 
     @Override
     public void error(String message, Throwable t) {
         this.error(message + "\r\n" + Utils.getExceptionMessage(t));
     }
 
     @Override
     public void warning(String message, Throwable t) {
         this.warning(message + "\r\n" + Utils.getExceptionMessage(t));
     }
 
     @Override
     public void notice(String message, Throwable t) {
         this.notice(message + "\r\n" + Utils.getExceptionMessage(t));
     }
 
     @Override
     public void info(String message, Throwable t) {
         this.info(message + "\r\n" + Utils.getExceptionMessage(t));
     }
 
     @Override
     public void debug(String message, Throwable t) {
         this.debug(message + "\r\n" + Utils.getExceptionMessage(t));
     }
 
     @Override
     public void log(LogLevel level, String message, Throwable t) {
         this.log(level, message + "\r\n" + Utils.getExceptionMessage(t));
     }
 }
