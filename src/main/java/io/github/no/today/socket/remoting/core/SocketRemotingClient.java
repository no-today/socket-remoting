package io.github.no.today.socket.remoting.core;

import io.github.no.today.socket.remoting.RemotingClient;
import io.github.no.today.socket.remoting.core.supper.LogUtil;
import io.github.no.today.socket.remoting.core.supper.RemotingUtil;
import io.github.no.today.socket.remoting.core.supper.ResultCallback;
import io.github.no.today.socket.remoting.protocol.RemotingCommand;
import io.github.no.today.socket.remoting.protocol.RemotingSystemCode;
import lombok.SneakyThrows;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.no.today.socket.remoting.core.supper.RemotingUtil.exceptionSimpleDesc;

/**
 * @author no-today
 * @date 2024/02/26 16:38
 */
public class SocketRemotingClient extends AbstractSocketRemoting implements RemotingClient {

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private ChannelContext ctx;
    private final String host;
    private final int port;

    /**
     * Automatically reconnect in case of exception, always stay connect
     * <p>
     * 1. Server is not online
     * 2. Server goes offline during communication
     */
    private final boolean autoReconnect;

    private final Timer timer = new Timer("ClientHouseKeepingService", true);
    private final ExecutorService callbackExecutor;

    public SocketRemotingClient(String host, int port, boolean autoReconnect, int permitsAsync, int permitsOneway, int callbackExecutorThreads) {
        super(permitsAsync, permitsOneway);
        this.host = host;
        this.port = port;
        this.autoReconnect = autoReconnect;
        this.callbackExecutor = Executors.newFixedThreadPool(callbackExecutorThreads, RemotingUtil.newThreadFactory("ClientCallbackExecutor"));
    }

    public SocketRemotingClient(String host, int port, boolean autoReconnect) {
        this(host, port, autoReconnect, 65535, 65535, Runtime.getRuntime().availableProcessors() * 2);
    }

    public SocketRemotingClient(String host, int port) {
        this(host, port, false);
    }

    @Override
    public void connect() {
        if (stopped.get())
            throw new IllegalStateException("The client already stopped");

        setup();

        do {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, port));
                ctx = new ChannelContext(socket, host);
                connected.set(true);

                LogUtil.info("Client connected [{}:{}]", host, port);

                listener(ctx);
            } catch (Exception e) {
                boolean older = connected.getAndSet(false);
                if (older) {
                    LogUtil.error("Service is not available, disconnect");
                } else {
                    LogUtil.error("Service is not available, connection failure");
                }

                if (autoReconnect) {
                    // TODO Can be optimized to an exponential backoff algorithm
                    try {
                        TimeUnit.SECONDS.sleep(3);
                    } catch (InterruptedException ignore) {
                        throw new RuntimeException(e);
                    }
                }
            }
        } while (autoReconnect && !stopped.get());

        LogUtil.info("Client ended");
    }

    private void setup() {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    scanResponseTable();
                } catch (Exception e) {
                    LogUtil.error("ScanResponseTable exception: {}", exceptionSimpleDesc(e));
                }
            }
        }, 1000, 1000);

        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                ping();
            }
        }, 1000, 3000);
    }

    @SneakyThrows
    private void ping() {
        if (!connected.get()) return;
        onewayRequest(RemotingCommand.request(RemotingSystemCode.COMMAND_CODE_PINT, null, null), 3000, (res, err) -> {
        });
    }

    private void listener(ChannelContext ctx) throws Exception {
        while (!stopped.get() && !ctx.isChannelClosed()) {

            RemotingCommand command = RemotingCommand.decode(ctx.getReader());
            super.processMessageReceived(ctx, command);
        }
    }

    @Override
    @SneakyThrows
    public void disconnect() {
        if (stopped.compareAndSet(false, true)) {
            timer.cancel();
            callbackExecutor.shutdown();
            if (ctx != null) ctx.close();
        }
    }

    @Override
    public RemotingCommand syncRequest(RemotingCommand request, long timeoutMillis) {
        return super.syncRequest(ctx, request, timeoutMillis);
    }

    @Override
    public void asyncRequest(RemotingCommand request, long timeoutMillis, ResultCallback<RemotingCommand> resultCallback) {
        super.asyncRequest(ctx, request, timeoutMillis, resultCallback);
    }

    @Override
    public void onewayRequest(RemotingCommand request, long timeoutMillis, ResultCallback<Void> resultCallback) {
        super.onewayRequest(ctx, request, timeoutMillis, resultCallback);
    }

    @Override
    public ExecutorService getCallbackExecutor() {
        return callbackExecutor;
    }
}
