package io.github.no.today.socket.remoting;

import io.github.no.today.socket.remoting.core.ChannelContext;
import io.github.no.today.socket.remoting.core.supper.RemotingResponseCallback;
import io.github.no.today.socket.remoting.protocol.RemotingCommand;

/**
 * @author no-today
 * @date 2024/02/26 14:53
 */
public interface RemotingRequestProcessor {

    /**
     * 拒绝请求
     */
    default boolean rejectRequest() {
        return false;
    }

    /**
     * 处理请求
     *
     * @param ctx     peer
     * @param request request
     * @return response
     */
    RemotingCommand processRequest(ChannelContext ctx, RemotingCommand request) throws Exception;

    default void asyncProcessRequest(ChannelContext ctx, RemotingCommand request, RemotingResponseCallback callback) throws Exception {
        RemotingCommand response = processRequest(ctx, request);
        callback.callback(response);
    }
}
