package io.github.no.today.socket.remoting.protocol;

import lombok.SneakyThrows;

import java.io.DataInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author no-today
 * @date 2024/02/28 08:51
 */
public class RemotingSerialization {

    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    public static byte[] encode(RemotingCommand cmd) {

        /*
         * | reqId[4] | type[4] | code[4] | message_length[4] | message[message_length] | body_length[4] | body[body_length] | extFieldsLength[4] | extFieldsJoinStr[extFieldsLength]
         */

        byte[] reqIdBytes = intToBytes(cmd.getReqId());
        byte[] typeBytes = intToBytes(cmd.getType());
        byte[] codeBytes = intToBytes(cmd.getCode());
        byte[] messageBytes = stringToBytes(cmd.getMessage());
        byte[] messageLengthBytes = intToBytes(messageBytes.length);
        byte[] bodyBytes = Optional.ofNullable(cmd.getBody()).orElse(EMPTY_BYTES);
        byte[] bodyLengthBytes = intToBytes(bodyBytes.length);
        byte[] extFieldsBytes = mapToBytes(cmd.getExtFields());
        byte[] extFieldsLength = intToBytes(extFieldsBytes.length);

        int offset = 0;
        int length = reqIdBytes.length + typeBytes.length + codeBytes.length + messageBytes.length + messageLengthBytes.length + bodyBytes.length + bodyLengthBytes.length + extFieldsBytes.length + extFieldsLength.length;
        byte[] result = new byte[length];

        offset = append(reqIdBytes, result, offset);
        offset = append(typeBytes, result, offset);
        offset = append(codeBytes, result, offset);
        offset = append(messageLengthBytes, result, offset);
        offset = append(messageBytes, result, offset);
        offset = append(bodyLengthBytes, result, offset);
        offset = append(bodyBytes, result, offset);
        offset = append(extFieldsLength, result, offset);
        offset = append(extFieldsBytes, result, offset);

        return result;
    }

    @SneakyThrows
    public static RemotingCommand decode(DataInputStream input) {
        int reqId = input.readInt();
        int type = input.readInt();
        int code = input.readInt();
        int messageLength = input.readInt();

        byte[] messageBytes = new byte[messageLength];
        input.readFully(messageBytes);

        int bodyLength = input.readInt();
        byte[] bodyBytes = new byte[bodyLength];
        input.readFully(bodyBytes);

        int extFieldsLength = input.readInt();
        byte[] extFieldsBytes = new byte[extFieldsLength];
        input.readFully(extFieldsBytes);

        return new RemotingCommand().setReqId(reqId).setType(type).setCode(code).setMessage(bytesToString(messageBytes)).setBody(bodyBytes).setExtFields(bytesToMap(extFieldsBytes));
    }

    private static int append(byte[] data, byte[] target, int offset) {
        System.arraycopy(data, 0, target, offset, data.length);
        return offset + data.length;
    }

    private static byte[] intToBytes(int value) {
        byte[] bytes = new byte[4];
        bytes[0] = (byte) (value >> 24);
        bytes[1] = (byte) (value >> 16);
        bytes[2] = (byte) (value >> 8);
        bytes[3] = (byte) value;
        return bytes;
    }

    private static byte[] stringToBytes(String str) {
        if (str == null) return EMPTY_BYTES;
        return str.getBytes(CHARSET);
    }

    private static String bytesToString(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        return new String(bytes, CHARSET);
    }

    private static byte[] mapToBytes(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return intToBytes(0);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            sb.append(entry.getKey());
            sb.append("\t");
            sb.append(entry.getValue());
            sb.append("\t");
        }
        return stringToBytes(sb.toString());
    }

    private static Map<String, String> bytesToMap(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        Map<String, String> extFields = new HashMap<>();

        String[] entries = bytesToString(bytes).split("\t");
        for (int i = 0; i < entries.length - 1; i += 2) {
            extFields.put(entries[i], entries[i + 1]);
        }

        return extFields;
    }
}
