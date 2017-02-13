package com.github.wuxuefeng.hello;

import com.github.wuxuefeng.hello.model.HTTPEvent;
import com.github.wuxuefeng.hello.model.HTTPRequest;
import com.github.wuxuefeng.hello.model.HTTPResponse;
import com.github.wuxuefeng.hello.model.HTTPSession;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Created by xfwu on 12/02/2017.
 */
public class DisruptorWebServer implements Runnable {


    private Selector selector = Selector.open();
    private ServerSocketChannel server = ServerSocketChannel.open();
    private boolean isRunning = true;
    private boolean debug = true;
    private final RingBuffer<HTTPEvent> ringBuffer;

    /**
     * Create a new server and immediately binds it.
     *
     * @param address the address to bind on
     * @throws IOException if there are any errors creating the server.
     */
    protected DisruptorWebServer(InetSocketAddress address, RingBuffer<HTTPEvent> ringBuffer) throws IOException {
        server.socket().bind(address);
        server.configureBlocking(false);
        server.register(selector, SelectionKey.OP_ACCEPT);
        this.ringBuffer = ringBuffer;
    }

    @Override
    public void run() {
        if (isRunning) {
            try {
                selector.selectNow();
                Iterator<SelectionKey> i = selector.selectedKeys().iterator();
                while (i.hasNext()) {
                    SelectionKey key = i.next();
                    i.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    try {
                        // get a new connection
                        if (key.isAcceptable()) {
                            // accept them
                            SocketChannel client = server.accept();
                            // non blocking please
                            client.configureBlocking(false);
                            // show out intentions
                            client.register(selector, SelectionKey.OP_READ);
                            // read from the connection
                        } else if (key.isReadable()) {
                            //  get the client
                            SocketChannel client = (SocketChannel) key.channel();
                            HTTPSession session = (HTTPSession)key.attachment();
                            if (session == null) {
                                session = new HTTPSession(client);
                                key.attach(session);
                            }
                            ringBuffer.publishEvent((event, sequence, _session) -> event.set(_session), session);

                        }
                    } catch (Exception ex) {
                        System.err.println("Error handling client: " + key.channel());
                        if (debug) {
                            ex.printStackTrace();
                        } else {
                            System.err.println(ex);
                            System.err.println("\tat " + ex.getStackTrace()[0]);
                        }
                        if (key.attachment() instanceof HTTPSession) {
                            ((HTTPSession) key.attachment()).close();
                        }
                    }
                }
            } catch (IOException ex) {
                // call it quits
                shutdown();
                // throw it as a runtime exception so that Bukkit can handle it
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * Shutdown this server, preventing it from handling any more requests.
     */
    public final void shutdown() {
        isRunning = false;
        try {
            selector.close();
            server.close();
        } catch (IOException ex) {
            // do nothing, its game over
        }
    }

    public static void main(String[] args) throws Exception {
        RingBuffer<HTTPEvent> ringBuffer = createHttpEventRingBuffer();
        DisruptorWebServer server = new DisruptorWebServer(new InetSocketAddress(8080),ringBuffer);
        System.out.println("WebServer running on 8080");
        while (true) {
            server.run();
            Thread.sleep(100);
        }
    }

    private static RingBuffer<HTTPEvent> createHttpEventRingBuffer() {
        // Executor that will be used to construct new threads for consumers
        ThreadFactory threadFactory = Executors.defaultThreadFactory();

        // Specify the size of the ring buffer, must be power of 2.
        int bufferSize = 1024;

        // Construct the Disruptor
        Disruptor<HTTPEvent> disruptor = new Disruptor<>(HTTPEvent::new, bufferSize,
                threadFactory, ProducerType.SINGLE,new BlockingWaitStrategy());

        // Connect the handler
        disruptor.handleEventsWith((event, sequence, endOfBatch) ->
                event.getSession().processConnection(DisruptorWebServer::handle));

        // Start the Disruptor, starts all threads running
        disruptor.start();

        // Get the ring buffer from the Disruptor to be used for publishing.
        return disruptor.getRingBuffer();
    }


    /**
     * Handle a web request.
     *
     * @param request
     * @return the handled request
     */
    protected static HTTPResponse handle(HTTPRequest request) {
        HTTPResponse response = new HTTPResponse();
        StringBuilder sb = new StringBuilder();
        sb.append(request.getLocation());
        sb.append("   I liek cates");
        response.setContent(sb.toString().getBytes());
        response.addDefaultHeaders();
        return response;
    }

}
