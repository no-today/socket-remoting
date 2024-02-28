package io.github.no.today.socket.remoting.core;

import io.github.no.today.socket.remoting.protocol.RemotingCommand;
import lombok.Getter;

/**
 * @author no-today
 * @date 2022/05/31 16:01
 */
@Getter
public class RequestTask implements Runnable {

    private final Runnable runnable;
    private final RemotingCommand request;
    private final ChannelContext channelContext;
    private final long createTimestamp = System.currentTimeMillis();

    public RequestTask(Runnable runnable, RemotingCommand request, ChannelContext channelContext) {
        this.runnable = runnable;
        this.request = request;
        this.channelContext = channelContext;
    }

    @Override
    public void run() {
        if (channelContext.isChannelClosed()) return;
        runnable.run();
    }
}
