package io.github.no.today.socket.remoting.core;

import io.github.no.today.socket.remoting.RemotingServer;
import io.github.no.today.socket.remoting.core.supper.LogUtil;
import io.github.no.today.socket.remoting.core.supper.RemotingUtil;
import io.github.no.today.socket.remoting.core.supper.ResultCallback;
import io.github.no.today.socket.remoting.protocol.RemotingCommand;
import io.github.no.today.socket.remoting.protocol.RemotingSystemCode;
import lombok.SneakyThrows;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.no.today.socket.remoting.core.supper.RemotingUtil.exceptionSimpleDesc;

/**
 * @author no-today
 * @date 2024/02/26 16:39
 */
public class SocketRemotingServer extends AbstractSocketRemoting implements RemotingServer {

    private static final int DEFAULT_HEARTBEAT_TIMEOUT_SECONDS = 60;

    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private ServerSocket server;
    private final int port;

    /**
     * Heartbeat timeout seconds,
     * If there is no heartbeat for a long time, the server will disconnect
     */
    private final int heartbeatTimeoutSeconds;

    private final Timer timer = new Timer("ServerHouseKeepingService", true);
    private final ExecutorService callbackExecutor;

    private final ConcurrentHashMap<String, ChannelContext> channelTable = new ConcurrentHashMap<>();

    public SocketRemotingServer(int port, int heartbeatTimeoutSeconds, int permitsAsync, int permitsOneway, int callbackExecutorThreads) {
        super(permitsAsync, permitsOneway);
        this.port = port;
        this.heartbeatTimeoutSeconds = heartbeatTimeoutSeconds;
        this.callbackExecutor = Executors.newFixedThreadPool(callbackExecutorThreads, RemotingUtil.newThreadFactory("ServerCallbackExecutor"));
    }

    public SocketRemotingServer(int port) {
        this(port, DEFAULT_HEARTBEAT_TIMEOUT_SECONDS, DEFAULT_PERMITS_ASYNC, DEFAULT_PERMITS_ASYNC, Runtime.getRuntime().availableProcessors());
    }

    @Override
    @SneakyThrows
    public void start() {
        if (stopped.get())
            throw new IllegalStateException("The server already stopped, please create new server instance and use");

        server = new ServerSocket(port);
        LogUtil.info("Server listening [{}:{}]", RemotingUtil.getLocalAddress(), port);

        registerHeartbeatHandler();
        setup();

        while (!stopped.get()) {
            try (Socket client = server.accept();
                 ChannelContext ctx = new ChannelContext(client, RemotingUtil.getAddress(client))) {

                channelTable.put(ctx.getPeer(), ctx);

                LogUtil.info("Client [{}] connected", ctx.getPeer());
                listener(ctx);
            } catch (IOException ignore) {
            }
        }

        LogUtil.info("Server ended");
    }

    private void setup() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    scanResponseTable();
                } catch (Throwable e) {
                    LogUtil.error("ScanResponseTable exception: {}", exceptionSimpleDesc(e));
                }
            }
        }, 1000, 1000);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                scanChannelTable();
            }
        }, 1000, TimeUnit.SECONDS.toMillis(heartbeatTimeoutSeconds));
    }

    /**
     * Scan channel table, close timed out connection
     */
    private void scanChannelTable() {
        List<ChannelContext> toList = new LinkedList<>();
        Iterator<Map.Entry<String, ChannelContext>> it = channelTable.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ChannelContext> next = it.next();
            ChannelContext ctx = next.getValue();

            if (ctx.getLastHeartbeatTime() + TimeUnit.SECONDS.toMillis(heartbeatTimeoutSeconds) <= System.currentTimeMillis()) {
                it.remove();
                toList.add(ctx);

                LogUtil.warn("Close timeout client, [{}]", ctx.getPeer());
            }
        }

        for (ChannelContext ctx : toList) {
            closeClient(ctx);
        }
    }

    private void registerHeartbeatHandler() {
        registerProcessor(RemotingSystemCode.COMMAND_CODE_PINT, (ctx, req) -> {
            ctx.refreshHeartbeat();
            return req;
        });
    }

    @Override
    @SneakyThrows
    public void shutdown() {
        if (stopped.compareAndSet(false, true)) {
            timer.cancel();
            callbackExecutor.shutdown();
            if (server != null) server.close();
        }
    }

    private void listener(ChannelContext ctx) {
        try {
            while (!stopped.get() && !ctx.isChannelClosed()) {
                RemotingCommand command = RemotingCommand.decode(ctx.getReader());
                super.processMessageReceived(ctx, command);
            }
        } catch (Exception e) {
            LogUtil.error("Server closed client [{}] connect by exception, {}", ctx.getPeer(), exceptionSimpleDesc(e));
            closeClient(ctx);
        }
    }

    private void closeClient(ChannelContext ctx) {
        try {
            ctx.stopped();
            channelTable.remove(ctx.getPeer());
            ctx.close();
        } catch (Exception ignore) {
        }
    }

    @Override
    public RemotingCommand syncRequest(String peer, RemotingCommand request, long timeoutMillis) {
        return super.syncRequest(channelTable.get(peer), request, timeoutMillis);
    }

    @Override
    public void asyncRequest(String peer, RemotingCommand request, long timeoutMillis, ResultCallback<RemotingCommand> resultCallback) {
        super.asyncRequest(channelTable.get(peer), request, timeoutMillis, resultCallback);
    }

    @Override
    public void onewayRequest(String peer, RemotingCommand request, long timeoutMillis, ResultCallback<Void> resultCallback) {
        super.onewayRequest(channelTable.get(peer), request, timeoutMillis, resultCallback);
    }

    @Override
    public ExecutorService getCallbackExecutor() {
        return callbackExecutor;
    }

    @Override
    public List<String> getChannels() {
        return Collections.list(channelTable.keys());
    }
}
