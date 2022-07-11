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

     protected static MainLogger logger;
     private File logFile;
 
     public MainLogger(String logFile) {
         this(logFile, LogLevel.DEFAULT_LEVEL);
     }
 
     public MainLogger(String logFile, LogLevel logLevel) {
         if (logger != null) {
             throw new RuntimeException("MainLogger has been created already");
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
     }
 
     private void waitForMessage() {
         while (logBuffer.isEmpty()) {
             try {
                 synchronized (this) {
                     wait(20000);
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
