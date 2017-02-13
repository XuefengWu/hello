package com.github.wuxuefeng.hello.model;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.Map;
import java.util.function.Function;

/**
 * Created by xfwu on 12/02/2017.
 */
public class HTTPSession {

    private final SocketChannel channel;
    private Charset charset = Charset.forName("UTF-8");
    private CharsetEncoder encoder = charset.newEncoder();
    private final ByteBuffer buffer = ByteBuffer.allocate(2048);
    private final StringBuilder readLines = new StringBuilder();
    private int mark = 0;

    public HTTPSession(SocketChannel channel) {
        this.channel = channel;
    }

    /**
     * Try to read a line.
     */
    private String readLine() throws IOException {
        StringBuilder sb = new StringBuilder();
        int l = -1;
        while (buffer.hasRemaining()) {
            char c = (char) buffer.get();
            sb.append(c);
            if (c == '\n' && l == '\r') {
                // mark our position
                mark = buffer.position();
                // append to the total
                readLines.append(sb);
                // return with no line separators
                return sb.substring(0, sb.length() - 2);
            }
            l = c;
        }
        return null;
    }

    /**
     * Get more data from the stream.
     */
    private void readData() throws IOException {
        buffer.limit(buffer.capacity());
        int read = channel.read(buffer);
        if (read == -1) {
            throw new IOException("End of stream");
        }
        buffer.flip();
        buffer.position(mark);
    }

    private void writeLine(String line) throws IOException {
        channel.write(encoder.encode(CharBuffer.wrap(line + "\r\n")));
    }

    private HTTPRequest getRequest() {
        return new HTTPRequest(readLines.toString());
    }

    private void sendResponse(HTTPResponse response) {
        try {
            writeLine(response.getSummery());
            for (Map.Entry<String, String> header : response.getHeads()) {
                writeLine(header.getKey() + ": " + header.getValue());
            }
            writeLine("");
            channel.write(ByteBuffer.wrap(response.getBody()));
        } catch (IOException ex) {
            // slow silently
        }
    }

    public void processConnection(Function<HTTPRequest,HTTPResponse> handle) throws IOException {
        // get more data
        readData();
        // decode the message
        String line;
        while ((line = readLine()) != null) {
            // check if we have got everything
            if (line.isEmpty()) {
                HTTPResponse response = handle.apply(getRequest());
                sendResponse(response);
                close();
            }
        }
    }


    public void close() {
        try {
            channel.close();
        } catch (IOException ex) {
        }
    }

}
