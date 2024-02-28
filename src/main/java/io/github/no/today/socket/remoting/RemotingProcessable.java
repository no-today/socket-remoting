package io.github.no.today.socket.remoting;

import java.util.concurrent.ExecutorService;

/**
 * @author no-today
 * @date 2024/02/26 15:37
 */
public interface RemotingProcessable {

    /**
     * 注册默认请求处理器
     * <p>
     * 当根据请求码匹配不到处理器时, 会使用该处理器
     *
     * @param processor 默认处理器
     * @param executor  执行线程池
     */
    void registerDefaultProcessor(ExecutorService executor, RemotingRequestProcessor processor);

    void registerDefaultProcessor(RemotingRequestProcessor processor);


    /**
     * 注册请求处理器
     *
     * @param requestCode 请求编码
     * @param executor    执行线程池
     * @param processor   处理器
     */
    void registerProcessor(int requestCode, ExecutorService executor, RemotingRequestProcessor processor);

    void registerProcessor(int requestCode, RemotingRequestProcessor processor);
}
