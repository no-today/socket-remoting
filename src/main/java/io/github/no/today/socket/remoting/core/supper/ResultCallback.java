package io.github.no.today.socket.remoting.core.supper;

/**
 * @author no-today
 * @date 2022/06/27 17:31
 */
public interface ResultCallback<T> {

    void callback(T response, ErrorInfo error);
}
