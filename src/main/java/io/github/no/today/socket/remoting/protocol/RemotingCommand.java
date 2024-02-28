package io.github.no.today.socket.remoting.protocol;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.DataInputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author no-today
 * @date 2024/02/26 14:56
 */
@Data
@Accessors(chain = true)
public class RemotingCommand implements Serializable {
    private static final long serialVersionUID = 2637522862988253773L;

    private static final AtomicInteger requestId = new AtomicInteger();

    private final static byte REQUEST = 0x00;
    private final static byte RESPONSE = 0x01;
    private final static byte ONEWAY = 0x02;

    /**
     * 请求ID, 用于串联应答
     */
    private int reqId;

    /**
     * 指令类型
     */
    private int type;

    /**
     * 指令编码 or 响应编码
     */
    private int code;

    /**
     * 响应时的文本消息, 通常用于告知异常信息
     */
    private String message;

    /**
     * 二进制内容
     * <p>
     * Any
     */
    private byte[] body;

    /**
     * 扩展字段, 用于透传额外的信息, 例如 traceId
     */
    private Map<String, String> extFields;

    public RemotingCommand markResponseType() {
        this.type = RESPONSE;
        return this;
    }

    public RemotingCommand markOnewayRPC() {
        this.type = ONEWAY;
        return this;
    }

    public boolean isResponse() {
        return RESPONSE == type;
    }

    public boolean isOneway() {
        return ONEWAY == type;
    }

    public boolean isSuccess() {
        if (!isResponse()) return false;
        return RemotingSystemCode.SUCCESS == code;
    }

    public String getExtFieldsOrDefault(String key, String defaultValue) {
        return Optional.ofNullable(this.extFields).map(e -> e.getOrDefault(key, defaultValue)).orElse(null);
    }

    public RemotingCommand putExtFields(String key, String value) {
        if (this.extFields == null) this.extFields = new HashMap<>();
        this.extFields.put(key, value);
        return this;
    }

    // ------------------------------------------------------------

    public static RemotingCommand request(int code, byte[] body, Map<String, String> extFields) {
        return new RemotingCommand().setReqId(requestId.getAndIncrement()).setCode(code).setBody(body).setExtFields(extFields);
    }

    public static RemotingCommand request(int code, byte[] body) {
        return request(code, body, null);
    }

    // ------------------------------------------------------------

    public static RemotingCommand success(byte[] body, Map<String, String> extFields) {
        return new RemotingCommand().markResponseType().setCode(RemotingSystemCode.SUCCESS).setBody(body).setExtFields(extFields);
    }

    public static RemotingCommand success(byte[] body) {
        return success(body, null);
    }

    public static RemotingCommand success() {
        return success(null);
    }

    public static RemotingCommand failure(int reqId, int code, String remark) {
        return new RemotingCommand().markResponseType().setReqId(reqId).setCode(code).setMessage(remark);
    }

    public static RemotingCommand failure(int code, String remark) {
        return failure(0, code, remark);
    }

    // ------------------------------------------------------------

    public byte[] encode() {
        return RemotingSerialization.encode(this);
    }

    public static RemotingCommand decode(DataInputStream input) {
        return RemotingSerialization.decode(input);
    }

    @Override
    public String toString() {
        if (isResponse()) {
            return "Response code: " + code + ", message: " + message + ", bodyLength: " + (body == null ? 0 : body.length) + ", extFields: " + extFields;
        } else {
            return "Request code: " + code + ", message: " + message + ", bodyLength: " + (body == null ? 0 : body.length) + ", extFields: " + extFields;
        }
    }
}
