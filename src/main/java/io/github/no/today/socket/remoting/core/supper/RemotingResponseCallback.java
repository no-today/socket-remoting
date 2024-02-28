package io.github.no.today.socket.remoting.core.supper;

import io.github.no.today.socket.remoting.protocol.RemotingCommand;

/**
 * @author no-today
 * @date 2022/07/01 11:36
 */
public interface RemotingResponseCallback {

    void callback(RemotingCommand response);
}
