package org.itxtech.nemisys.network.synlib;

/**
 * SynapseContextException
 * ===============
 * author: boybook
 * Nemisys Project
 * ===============
 */
@SuppressWarnings("serial")
public class SynapseContextException extends Exception {

    public SynapseContextException() {
        super();
        this.setStackTrace(new StackTraceElement[0]);
    }

    public SynapseContextException(String message) {
        super(message);
        this.setStackTrace(new StackTraceElement[0]);
    }

    public SynapseContextException(String message, Throwable cause) {
        super(message, cause);
        this.setStackTrace(new StackTraceElement[0]);
    }

    public SynapseContextException(Throwable cause) {
        super(cause);
        this.setStackTrace(new StackTraceElement[0]);
    }

    @Override
    public String toString() {
        return getLocalizedMessage();
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }
}
