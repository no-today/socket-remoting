package io.github.no.today.socket.remoting.protocol;

/**
 * @author no-today
 * @date 2023/09/22 10:52
 */
public class RemotingSystemCode {

    public static final int COMMAND_CODE_PINT = -5;

    /**
     * Usually a client network error
     */
    public static final int REQUEST_FAILED = -1;

    public static final int SUCCESS = 0;

    /**
     * Server common error coding
     */
    public static final int SYSTEM_ERROR = 1;

    /**
     * Server load is too high
     */
    public static final int SYSTEM_BUSY = 2;

    /**
     * Command is not available now
     */
    public static final int COMMAND_NOT_AVAILABLE_NOW = 3;

    /**
     * Request code not supported
     */
    public static final int REQUEST_CODE_NOT_SUPPORTED = 4;
}
