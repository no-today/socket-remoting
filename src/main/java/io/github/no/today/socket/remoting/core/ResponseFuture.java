package io.github.no.today.socket.remoting.core;

import io.github.no.today.socket.remoting.core.supper.ErrorInfo;
import io.github.no.today.socket.remoting.core.supper.ResultCallback;
import io.github.no.today.socket.remoting.core.supper.SemaphoreReleaseOnlyOnce;
import io.github.no.today.socket.remoting.protocol.RemotingCommand;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 与请求匹配, 在未来某个时间点通知
 *
 * @author no-today
 * @date 2022/05/29 15:03
 */
@Setter
@Getter
public class ResponseFuture {

    private final ChannelContext channelContext;
    private final int reqId;
    private final long timeoutMillis;
    private final long requestTimestamp = System.currentTimeMillis();
    private final CountDownLatch countDownLatch = new CountDownLatch(1);
    private final ResultCallback<RemotingCommand> responseCallback;
    private final AtomicBoolean executeResponseCallbackOnlyOnce = new AtomicBoolean(false);
    private final SemaphoreReleaseOnlyOnce semaphoreReleaseOnlyOnce;
    private volatile long responseTimestamp;
    private volatile RemotingCommand responseCommand;
    private volatile boolean sendRequestOk = true;
    private volatile Throwable cause;

    public ResponseFuture(ChannelContext channelContext, int reqId, long timeoutMillis, ResultCallback<RemotingCommand> responseCallback, SemaphoreReleaseOnlyOnce semaphoreReleaseOnlyOnce) {
        this.channelContext = channelContext;
        this.reqId = reqId;
        this.timeoutMillis = timeoutMillis;
        this.responseCallback = responseCallback;
        this.semaphoreReleaseOnlyOnce = semaphoreReleaseOnlyOnce;
    }

    public ResponseFuture(ChannelContext channelContext, int reqId, long timeoutMillis) {
        this(channelContext, reqId, timeoutMillis, null, null);
    }

    public void putResponse(final RemotingCommand responseCommand) {
        this.responseTimestamp = System.currentTimeMillis();
        this.responseCommand = responseCommand;
        this.countDownLatch.countDown(); // 通知前完成赋值
    }

    @SneakyThrows
    public RemotingCommand waitResponse(final long timeoutMillis) {
        this.countDownLatch.await(timeoutMillis, TimeUnit.MILLISECONDS);
        return this.responseCommand;
    }

    /**
     * RTT (RoundTripTime): 创建请求的时间 ~ 返回响应的时候
     * <p>
     * 耗时包括:
     * 1. Write to server(encode)
     * 2. Server read(decode)
     * 3. Server Handler request
     * 4. Server write(encode)
     * 5. Read from server(decode)
     *
     * @return -1 表示请求未发出或者没有响应
     */
    public long getRTT() {
        return Math.max(-1, responseTimestamp - requestTimestamp);
    }

    public void executeCallback() {
        if (this.executeResponseCallbackOnlyOnce.compareAndSet(false, true)) {
            if (this.responseCommand == null) {
                this.responseCallback.callback(null, new ErrorInfo(this.reqId, -2, "timeout", this.cause));
                return;
            }

            if (this.responseCommand.isSuccess()) {
                this.responseCallback.callback(this.responseCommand, null);
            } else {
                this.responseCallback.callback(null, new ErrorInfo(this.reqId, this.responseCommand.getCode(), this.responseCommand.getMessage(), this.cause));
            }
        }
    }

    public void releaseSemaphore() {
        if (null != this.semaphoreReleaseOnlyOnce) {
            this.semaphoreReleaseOnlyOnce.release();
        }
    }

    @Override
    public String toString() {
        return "ResponseFuture{" + "reqId=" + reqId +
                ", timeoutMillis=" + timeoutMillis +
                ", requestTimestamp=" + requestTimestamp +
                '}';
    }
}