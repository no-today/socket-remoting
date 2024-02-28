package io.github.no.today.socket.remoting;

import io.github.no.today.socket.remoting.core.SocketRemotingClient;

/**
 * @author no-today
 * @date 2024/02/27 16:53
 */
public class ClientStart {

    public static void main(String[] args) throws Exception {
        SocketRemotingClient client = new SocketRemotingClient("127.0.0.1", 20300, true);
        client.connect();
    }
}
