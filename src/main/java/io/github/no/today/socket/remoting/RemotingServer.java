package io.github.no.today.socket.remoting;

import io.github.no.today.socket.remoting.core.supper.ResultCallback;
import io.github.no.today.socket.remoting.protocol.RemotingCommand;

import java.util.List;

/**
 * @author no-today
 * @date 2024/02/26 14:53
 */
public interface RemotingServer extends RemotingProcessable {

    void start();

    void shutdown();

    /**
     * 同步调用
     *
     * @param peer          目标客户端
     * @param request       请求指令
     * @param timeoutMillis 超时时间
     * @return 响应指令
     */
    RemotingCommand syncRequest(final String peer, final RemotingCommand request, final long timeoutMillis);

    /**
     * 异步调用
     *
     * @param peer           目标客户端
     * @param request        请求指令
     * @param timeoutMillis  超时时间
     * @param resultCallback 结果回调
     */
    void asyncRequest(final String peer, final RemotingCommand request, final long timeoutMillis, final ResultCallback<RemotingCommand> resultCallback);

    /**
     * 单向调用(发送消息，但不需要响应)
     *
     * @param peer           目标客户端
     * @param request        请求指令
     * @param timeoutMillis  发送超时时间
     * @param resultCallback 结果回调
     */
    void onewayRequest(final String peer, final RemotingCommand request, final long timeoutMillis, final ResultCallback<Void> resultCallback);

    /**
     * 获取全部连接名
     */
    List<String> getChannels();

    /**
     * 检查是否连接了
     *
     * @param peer 目标客户端
     */
    boolean isConnected(String peer);
}
