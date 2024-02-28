package io.github.no.today.socket.remoting;

import io.github.no.today.socket.remoting.core.supper.ResultCallback;
import io.github.no.today.socket.remoting.protocol.RemotingCommand;

/**
 * @author no-today
 * @date 2024/02/26 14:51
 */
public interface RemotingClient extends RemotingProcessable {

    /**
     * 连接
     */
    void connect();

    /**
     * 断开连接
     */
    void disconnect();

    /**
     * 同步调用
     *
     * @param request       请求指令
     * @param timeoutMillis 超时时间
     * @return 响应指令
     */
    RemotingCommand syncRequest(final RemotingCommand request, final long timeoutMillis);

    /**
     * 异步调用
     *
     * @param request        请求指令
     * @param timeoutMillis  超时时间
     * @param resultCallback 结果回调
     */
    void asyncRequest(final RemotingCommand request, final long timeoutMillis, final ResultCallback<RemotingCommand> resultCallback);

    /**
     * 单向调用(发送消息，但不需要响应)
     *
     * @param request        请求指令
     * @param timeoutMillis  发送超时时间
     * @param resultCallback 结果回调
     */
    void onewayRequest(final RemotingCommand request, final long timeoutMillis, final ResultCallback<Void> resultCallback);
}
