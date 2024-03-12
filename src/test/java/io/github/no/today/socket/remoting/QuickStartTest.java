package io.github.no.today.socket.remoting;

import io.github.no.today.socket.remoting.core.SocketRemotingClient;
import io.github.no.today.socket.remoting.core.SocketRemotingServer;
import io.github.no.today.socket.remoting.core.supper.LogUtil;
import io.github.no.today.socket.remoting.protocol.RemotingCommand;
import io.github.no.today.socket.remoting.protocol.RemotingSystemCode;
import junit.framework.TestCase;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertArrayEquals;

/**
 * @author no-today
 * @date 2024/02/27 14:46
 */
public class QuickStartTest extends TestCase {
    public static final int PORT = 20300;

    public void test() throws Exception {
        // Set logging level
        LogUtil.setLevel(LogUtil.Level.DEBUG);

        // 1. Create server instance
        RemotingServer server = new SocketRemotingServer(PORT);

        // 2. Add a processor to the server, cmd code is 1024, this handler will return the request unchanged, you can also add the processor after the service is started
        int serverCmd = 1024;
        server.registerProcessor(serverCmd, (ctx, req) -> req.setCode(RemotingSystemCode.SUCCESS));

        // 3. When starting the server, there will be an infinite loop inside to receive client connections, so it needs to be executed asynchronously.
        new Thread(server::start).start();

        // 4. Create client instance, waiting 100 milliseconds is to allow the server to start first
        TimeUnit.MILLISECONDS.sleep(100);
        SocketRemotingClient client = new SocketRemotingClient("127.0.0.1", PORT);

        // 5. When the client connects to the server, it will read data in an infinite loop internally, so it needs to be executed asynchronously.
        new Thread(client::connect).start();

        // 6. Client call to server
        clientCallServer(client, serverCmd);

        // 7. The client can also process instructions issued by the server, which is bidirectional.
        int clientCmd = 2048;
        client.registerProcessor(clientCmd, (ctx, req) -> req.setCode(RemotingSystemCode.SUCCESS));

        // 8. Server call to server
        serverCallClient(server, clientCmd, server.getChannels().get(0));

        TimeUnit.SECONDS.sleep(2);

        // 10. End release resources
        server.shutdown();
        client.disconnect();
    }

    private static void serverCallClient(RemotingServer server, int cmd, String client) {
        // Build request cmd
        RemotingCommand request = RemotingCommand.request(cmd, UUID.randomUUID().toString().getBytes(), Map.of("traceId", UUID.randomUUID().toString()));

        // Sync request to client
        RemotingCommand response = server.syncRequest(client, request, 3000);
        assertArrayEquals(request.getBody(), response.getBody());
        assertEquals(request.getExtFields(), response.getExtFields());

        // Async request to client
        server.asyncRequest(client, request, 3000, (res, err) -> {
            if (err != null) {
                // failure
                System.out.println("Async request failure: " + err);
            } else {
                // success
                assertArrayEquals(request.getBody(), res.getBody());
                assertEquals(request.getExtFields(), res.getExtFields());
            }
        });

        // Oneway request to client, no response required
        server.onewayRequest(client, request, 3000, (res, err) -> {
            if (err != null) {
                // failure
                System.out.println("Async request failure: " + err);
            }
        });
    }

    private static void clientCallServer(SocketRemotingClient client, int cmd) {
        // Build request cmd
        RemotingCommand request = RemotingCommand.request(cmd, UUID.randomUUID().toString().getBytes(), Map.of("traceId", UUID.randomUUID().toString()));

        // Sync request to server
        RemotingCommand response = client.syncRequest(request, 3000);
        assertArrayEquals(request.getBody(), response.getBody());
        assertEquals(request.getExtFields(), response.getExtFields());

        // Async request to server
        client.asyncRequest(request, 3000, (res, err) -> {
            if (err != null) {
                // failure
                System.out.println("Async request failure: " + err);
            } else {
                // success
                assertArrayEquals(request.getBody(), res.getBody());
                assertEquals(request.getExtFields(), res.getExtFields());
            }
        });

        // Oneway request to server, no response required
        client.onewayRequest(request, 3000, (res, err) -> {
            if (err != null) {
                // failure
                System.out.println("Async request failure: " + err);
            }
        });
    }
}
