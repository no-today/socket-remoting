package io.github.no.today.socket.remoting;

import io.github.no.today.socket.remoting.core.SocketRemotingServer;

/**
 * @author no-today
 * @date 2024/02/27 16:53
 */
public class ServerStart {

    public static void main(String[] args) {
        SocketRemotingServer server = new SocketRemotingServer(20300);
        server.start();
    }
}
