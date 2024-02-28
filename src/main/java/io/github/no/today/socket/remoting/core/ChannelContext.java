package io.github.no.today.socket.remoting.core;

import io.github.no.today.socket.remoting.core.supper.LogUtil;
import io.github.no.today.socket.remoting.protocol.RemotingCommand;
import lombok.Getter;
import lombok.SneakyThrows;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.no.today.socket.remoting.core.supper.RemotingUtil.exceptionSimpleDesc;

/**
 * @author no-today
 * @date 2024/02/26 15:59
 */
public class ChannelContext implements AutoCloseable {

    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final Socket socket;

    @Getter
    private final String peer;

    @Getter
    private final DataInputStream reader;
    private final DataOutputStream writer;

    @SneakyThrows
    public ChannelContext(Socket socket, String peer) {
        this.socket = socket;
        this.peer = peer;
        this.reader = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.writer = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public void stopped() {
        stopped.set(true);
    }

    public boolean isChannelClosed() {
        return stopped.get();
    }

    public void writeAndFlush(RemotingCommand cmd) {
        try {
            writer.write(cmd.encode());
            writer.flush();
        } catch (IOException e) {
            LogUtil.error("Closed [{}] connect by exception, {}", getPeer(), exceptionSimpleDesc(e));
            stopped();
        }
    }

    @SneakyThrows
    public void close() {
        stopped();
        socket.close();
        reader.close();
        writer.close();
    }
}
