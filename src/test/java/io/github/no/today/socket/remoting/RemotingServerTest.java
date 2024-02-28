package io.github.no.today.socket.remoting;

import io.github.no.today.socket.remoting.core.ChannelContext;
import io.github.no.today.socket.remoting.core.SocketRemotingClient;
import io.github.no.today.socket.remoting.core.SocketRemotingServer;
import io.github.no.today.socket.remoting.core.supper.LogUtil;
import io.github.no.today.socket.remoting.core.supper.ResultCallback;
import io.github.no.today.socket.remoting.protocol.RemotingCommand;
import io.github.no.today.socket.remoting.protocol.RemotingSystemCode;
import junit.framework.TestCase;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * @author no-today
 * @date 2024/02/27 14:32
 */
public class RemotingServerTest extends TestCase {

    public static final int PORT = 20300;

    RemotingServer server = null;
    RemotingClient client = null;

    static {
        LogUtil.setLevel(LogUtil.Level.DEBUG);
    }

    public void setUp() throws Exception {
        server = new SocketRemotingServer(PORT);
        client = new SocketRemotingClient("127.0.0.1", PORT);
        new Thread(server::start).start();
        // Wait for the server to start first
        TimeUnit.MILLISECONDS.sleep(100);
        new Thread(client::connect).start();
    }

    public void tearDown() throws Exception {
        if (server != null) server.shutdown();
        if (client != null) client.disconnect();
    }

    public void testDisconnectByClient() throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        client.disconnect();
        TimeUnit.SECONDS.sleep(1);
        server.shutdown();
    }

    public void testDisconnectByServer() throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        server.shutdown();
        TimeUnit.SECONDS.sleep(1);
        client.disconnect();
    }

    public void testRequest() throws Exception {
        int LENGTH = 1024 * 1024;
        assertEquals(RemotingSystemCode.REQUEST_CODE_NOT_SUPPORTED, client.syncRequest(RemotingCommand.request(1024, randomString(LENGTH).getBytes(), Map.of("random", randomString(LENGTH))), 1000).getCode());

        // 原封不动返回请求数据
        AtomicBoolean rejectRequest = new AtomicBoolean(true);
        registerRequestProcessor(server, rejectRequest, null);

        assertEquals(RemotingSystemCode.COMMAND_NOT_AVAILABLE_NOW, client.syncRequest(RemotingCommand.request(1024, randomString(LENGTH).getBytes(), Map.of("random", randomString(LENGTH))), 1000).getCode());
        rejectRequest.set(false);
        assertEquals(RemotingSystemCode.SUCCESS, client.syncRequest(RemotingCommand.request(1024, randomString(LENGTH).getBytes(), Map.of("random", randomString(LENGTH))), 1000).getCode());

        int count = 10000;
        CountDownLatch cd = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            client.asyncRequest(RemotingCommand.request(1024, randomString(10).getBytes(), Map.of("random", randomString(500))), 30000, callback(cd));
        }

        cd.await();
    }

    private String randomString(int count) {
        StringBuilder r = new StringBuilder();
        for (int i = 0; i < count; i++) {
            r.append(UUID.randomUUID());
        }
        return r.toString();
    }

    private void registerRequestProcessor(RemotingProcessable server, AtomicBoolean rejectRequest, Consumer<RemotingCommand> consumer) {
        server.registerDefaultProcessor(new RemotingRequestProcessor() {
            @Override
            public boolean rejectRequest() {
                return rejectRequest.get();
            }

            @Override
            public RemotingCommand processRequest(ChannelContext ctx, RemotingCommand request) {
                if (consumer != null) consumer.accept(request);
                return request.setCode(0);
            }
        });
    }

    private ResultCallback<RemotingCommand> callback(CountDownLatch cd) {
        return (response, error) -> {
            if (error != null) {
                System.err.println(error);
            }
            cd.countDown();
        };
    }
}