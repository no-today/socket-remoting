package io.github.no.today.socket.remoting.core;

import io.github.no.today.socket.remoting.RemotingProcessable;
import io.github.no.today.socket.remoting.RemotingRequestProcessor;
import io.github.no.today.socket.remoting.core.supper.*;
import io.github.no.today.socket.remoting.exception.RemotingConnectException;
import io.github.no.today.socket.remoting.exception.RemotingSendRequestException;
import io.github.no.today.socket.remoting.exception.RemotingTimeoutException;
import io.github.no.today.socket.remoting.exception.RemotingTooMuchRequestException;
import io.github.no.today.socket.remoting.protocol.RemotingCommand;
import io.github.no.today.socket.remoting.protocol.RemotingSystemCode;

import java.util.*;
import java.util.concurrent.*;

import static io.github.no.today.socket.remoting.core.supper.RemotingUtil.exceptionSimpleDesc;

/**
 * @author no-today
 * @date 2024/02/26 15:38
 */
public abstract class AbstractSocketRemoting implements RemotingProcessable {
    protected static final int DEFAULT_PERMITS_ASYNC = 65535;

    /**
     * 异步命令信号量, 控制异步调用的并发数量, 从而保护系统内存
     */
    protected final Semaphore semaphoreAsync;

    /**
     * 单向命令信号量, 控制单向调用的并发数量, 从而保护系统内存
     */
    protected final Semaphore semaphoreOneway;

    /**
     * 缓存所有正在进行(未响应)的请求
     */
    protected final ConcurrentMap<Integer /* request id */, ResponseFuture> responseTable = new ConcurrentHashMap<>(Runtime.getRuntime().availableProcessors() * 2);

    /**
     * 通过请求编码找到请求处理器
     */
    protected final Map<Integer /* request code */, Pair<RemotingRequestProcessor, ExecutorService>> processorTable = new HashMap<>(8);

    /**
     * 默认请求处理器
     * 当根据请求编码找不到处理器时使用默认处理器处理请求
     */
    protected Pair<RemotingRequestProcessor, ExecutorService> defaultRequestProcessor;

    private ExecutorService defaultExecutor;

    public AbstractSocketRemoting(int permitsAsync, int permitsOneway) {
        this.semaphoreAsync = new Semaphore(permitsAsync, true);
        this.semaphoreOneway = new Semaphore(permitsOneway, true);
    }

    /**
     * 从子类获取回调执行器
     * 如果为空或者任务已满, 交由当前线程执行回调
     */
    public abstract ExecutorService getCallbackExecutor();

    synchronized
    protected ExecutorService getDefaultExecutor() {
        if (defaultExecutor == null) {
            defaultExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() * 2);
        }

        return defaultExecutor;
    }

    @Override
    public void registerDefaultProcessor(RemotingRequestProcessor processor) {
        this.registerDefaultProcessor(getDefaultExecutor(), processor);
    }

    @Override
    public void registerDefaultProcessor(ExecutorService executor, RemotingRequestProcessor processor) {
        this.defaultRequestProcessor = new Pair<>(processor, executor);
    }

    @Override
    public void registerProcessor(int requestCode, RemotingRequestProcessor processor) {
        this.registerProcessor(requestCode, getDefaultExecutor(), processor);
    }

    @Override
    public void registerProcessor(int requestCode, ExecutorService executor, RemotingRequestProcessor processor) {
        this.processorTable.put(requestCode, new Pair<>(processor, executor));
    }

    // ----------------------------------------------------------------------

    protected void processMessageReceived(ChannelContext ctx, RemotingCommand cmd) {
        if (cmd == null) return;
        if (cmd.isResponse()) {
            processResponse(cmd);
        } else {
            processRequest(ctx, cmd);
        }
    }

    public void processRequest(ChannelContext ctx, RemotingCommand request) {
        Pair<RemotingRequestProcessor, ExecutorService> pair = this.processorTable.getOrDefault(request.getCode(), this.defaultRequestProcessor);

        // 担心在 processor 里被篡改, 安全起见留个副本
        final int reqId = request.getReqId();
        final boolean oneway = request.isOneway();

        if (pair == null) {
            response(ctx, RemotingCommand.failure(reqId, RemotingSystemCode.REQUEST_CODE_NOT_SUPPORTED, "[REQUEST_CODE_NOT_SUPPORTED] request code " + request.getCode() + " not supported"));
            return;
        }

        RemotingRequestProcessor remotingRequestProcessor = pair.getObj1();
        if (remotingRequestProcessor.rejectRequest()) {
            response(ctx, RemotingCommand.failure(reqId, RemotingSystemCode.COMMAND_NOT_AVAILABLE_NOW, "[COMMAND_UNAVAILABLE_NOW] this command is currently unavailable"));
            return;
        }

        RequestTask task = new RequestTask(() -> {
            try {
                // 默认实现实际上还是同步
                remotingRequestProcessor.asyncProcessRequest(ctx, request, response -> {
                    if (!oneway && response != null) {
                        response.setReqId(reqId);
                        response.markResponseType();

                        response(ctx, response);
                    }
                });
            } catch (Throwable e) {
                LogUtil.error("Process request exception: {}", exceptionSimpleDesc(e));
                LogUtil.error("{}", request);

                // 单向消息不需要响应
                if (!oneway) {
                    response(ctx, RemotingCommand.failure(reqId, RemotingSystemCode.SYSTEM_ERROR, e.getMessage()));
                }
            }
        }, request, ctx);

        try {
            pair.getObj2().submit(task);
        } catch (RejectedExecutionException e) {
            // 10s print once log
            if (System.currentTimeMillis() % 10000 == 0) {
                LogUtil.warn("Too many requests and system thread pool busy, RejectedExecutionException");
            }

            // 单向消息不需要响应
            if (!oneway) {
                response(ctx, RemotingCommand.failure(reqId, RemotingSystemCode.SYSTEM_BUSY, "[OVERLOAD] system busy, try later"));
            }
        }
    }

    public void processResponse(RemotingCommand response) {
        int reqId = response.getReqId();
        ResponseFuture future = this.responseTable.remove(reqId);
        if (null != future) {
            future.putResponse(response);
            executionCallback(future);
            future.releaseSemaphore();
        } else {
            LogUtil.warn("Receive response command, but not matched any request, reqId: {}", response.getReqId());
        }
    }

    private void response(ChannelContext ctx, RemotingCommand response) {
        ctx.writeAndFlush(response);
    }

    /**
     * 执行响应回调
     * <p>
     * 先尝试异步执行, 若无法异步则在当前线程执行回调
     */
    protected void executionCallback(final ResponseFuture responseFuture) {
        if (null == responseFuture.getResponseCallback()) return;

        LogUtil.debug("Execute callback, req: {}, cost: {}ms", responseFuture.getReqId(), responseFuture.getRTT());

        boolean runInThisThread = false;
        ExecutorService executor = getCallbackExecutor();
        if (null != executor) {
            try {
                executor.submit(() -> {
                    try {
                        responseFuture.executeCallback();
                    } catch (Exception e) {
                        LogUtil.warn("Execute callback in executor exception, and callback throw", e);
                    } finally {
                        responseFuture.releaseSemaphore();
                    }
                });
            } catch (Exception e) {
                runInThisThread = true;
                LogUtil.warn("Execute callback in executor exception, maybe executor busy", e);
            }
        } else {
            runInThisThread = true;
        }

        if (runInThisThread) {
            try {
                responseFuture.executeCallback();
            } catch (Throwable e) {
                LogUtil.warn("ExecuteCallback Exception", e);
            } finally {
                responseFuture.releaseSemaphore();
            }
        }
    }

    // ----------------------------------------------------------------------

    /**
     * 扫描已经超时的请求, 并进行回调通知
     */
    protected void scanResponseTable() {
        List<ResponseFuture> rfList = new LinkedList<>();
        Iterator<Map.Entry<Integer, ResponseFuture>> it = responseTable.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, ResponseFuture> next = it.next();
            ResponseFuture rep = next.getValue();

            if (rep.getRequestTimestamp() + rep.getTimeoutMillis() <= System.currentTimeMillis()) {
                rep.releaseSemaphore();
                it.remove();
                rfList.add(rep);

                LogUtil.warn("Remove timeout request, {}", rep);
            }
        }

        for (ResponseFuture future : rfList) {
            executionCallback(future);
        }
    }

    protected RemotingCommand syncRequest(final ChannelContext ctx, final RemotingCommand request, final long timeoutMillis) {
        ifChannelUnavailableThrowException(ctx);

        int reqId = request.getReqId();
        ResponseFuture responseFuture = new ResponseFuture(ctx, reqId, timeoutMillis);
        this.responseTable.put(reqId, responseFuture);

        try {
            ctx.writeAndFlush(request);

            responseFuture.setSendRequestOk(true);
        } catch (Exception e) {
            responseFuture.setSendRequestOk(false);
            responseFuture.setCause(e);
            responseFuture.putResponse(null);  // 触发闭锁
        }

        try {
            // 同步请求, 阻塞等待响应
            RemotingCommand response = responseFuture.waitResponse(timeoutMillis);
            if (null == response) {
                // 发送成功未响应则是对端处理超时
                if (responseFuture.isSendRequestOk()) {
                    throw new RemotingTimeoutException(timeoutMillis, responseFuture.getCause());
                } else {
                    throw new RemotingSendRequestException("Failed to send request to channel", responseFuture.getCause());
                }
            }

            LogUtil.debug("Return response, reqId: {}, cost: {}ms", responseFuture.getReqId(), responseFuture.getRTT());

            return response;
        } finally {
            this.responseTable.remove(reqId);
        }
    }

    protected void asyncRequest(final ChannelContext ctx, final RemotingCommand request, final long timeoutMillis, final ResultCallback<RemotingCommand> resultCallback) {
        try {
            ifChannelUnavailableThrowException(ctx);

            if (!this.semaphoreAsync.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) {
                String info = String.format("asyncRequest tryAcquire semaphore timeout, %dms, waiting thread nums: %d semaphoreOnewayValue: %d", timeoutMillis, this.semaphoreAsync.getQueueLength(), this.semaphoreAsync.availablePermits());
                throw new RemotingTooMuchRequestException(info);
            }

            int reqId = request.getReqId();
            ResponseFuture responseFuture = new ResponseFuture(ctx, reqId, timeoutMillis, resultCallback, new SemaphoreReleaseOnlyOnce(this.semaphoreAsync));
            this.responseTable.put(reqId, responseFuture);

            try {
                ctx.writeAndFlush(request);

                responseFuture.setSendRequestOk(true);
            } catch (Exception e) {
                LogUtil.warn("Send a request command to channel failed", e);

                this.responseTable.remove(reqId);
                responseFuture.setSendRequestOk(false);
                responseFuture.setCause(e);
                responseFuture.putResponse(null);
                executionCallback(responseFuture);
            }
        } catch (Throwable e) {
            resultCallback.callback(null, new ErrorInfo(request.getReqId(), -1, e.getMessage(), e));
        }
    }

    protected void onewayRequest(final ChannelContext ctx, final RemotingCommand request, final long timeoutMillis, final ResultCallback<Void> resultCallback) {
        try {
            ifChannelUnavailableThrowException(ctx);

            request.markOnewayRPC();
            if (!this.semaphoreOneway.tryAcquire(timeoutMillis, TimeUnit.MILLISECONDS)) {
                String info = String.format("onewayRequest tryAcquire semaphore timeout, %dms, waiting thread nums: %d semaphoreOnewayValue: %d", timeoutMillis, this.semaphoreOneway.getQueueLength(), this.semaphoreOneway.availablePermits());
                throw new RemotingTooMuchRequestException(info);
            }

            int reqId = request.getReqId();
            ResponseFuture responseFuture = new ResponseFuture(ctx, reqId, timeoutMillis, null, new SemaphoreReleaseOnlyOnce(this.semaphoreOneway));

            try {
                ctx.writeAndFlush(request);

                resultCallback.callback(null, null);
            } catch (Exception e) {
                String message = "Send a request command to channel failed";
                LogUtil.warn(message, e);
                resultCallback.callback(null, new ErrorInfo(reqId, -1, message, e));
            } finally {
                responseFuture.releaseSemaphore();
            }
        } catch (Throwable e) {
            resultCallback.callback(null, new ErrorInfo(request.getReqId(), -1, e.getMessage(), e));
        }
    }

    protected void ifChannelUnavailableThrowException(ChannelContext ctx) throws RemotingConnectException {
        if (ctx == null || ctx.isChannelClosed()) {
            throw new RemotingConnectException("Channel unavailable");
        }
    }
}
