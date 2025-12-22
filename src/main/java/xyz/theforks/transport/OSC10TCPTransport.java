// SPDX-FileCopyrightText: 2024 Tracy Scott
// SPDX-License-Identifier: BSD-3-Clause

package xyz.theforks.transport;

import com.illposed.osc.ByteArrayListBytesReceiver;
import com.illposed.osc.OSCPacket;
import com.illposed.osc.OSCParseException;
import com.illposed.osc.OSCParser;
import com.illposed.osc.OSCSerializeException;
import com.illposed.osc.OSCSerializer;
import com.illposed.osc.OSCSerializerAndParserBuilder;
import com.illposed.osc.transport.Transport;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * A {@link Transport} implementation for sending and receiving OSC packets over
 * TCP using OSC 1.0 size-prefix framing (4-byte big-endian length header).
 *
 * This is compatible with TouchOSC and python-osc's mode=1.0 setting.
 */
public class OSC10TCPTransport implements Transport {

    private final InetSocketAddress local;
    private final InetSocketAddress remote;
    private final OSCParser parser;
    private final ByteArrayListBytesReceiver serializationBuffer;
    private final OSCSerializer serializer;
    private Socket clientSocket;
    private ServerSocket serverSocket;
    private Socket acceptedSocket;  // Keep accepted socket open for multiple messages

    public OSC10TCPTransport(
            final InetSocketAddress local,
            final InetSocketAddress remote)
            throws IOException
    {
        this(local, remote, new OSCSerializerAndParserBuilder());
    }

    public OSC10TCPTransport(
            final InetSocketAddress local,
            final InetSocketAddress remote,
            final OSCSerializerAndParserBuilder builder)
            throws IOException
    {
        this.local = local;
        this.remote = remote;
        this.parser = builder.buildParser();
        this.serializationBuffer = new ByteArrayListBytesReceiver();
        this.serializer = builder.buildSerializer(serializationBuffer);
        this.clientSocket = null;
        this.serverSocket = null;
        this.acceptedSocket = null;
    }

    private Socket getClientSocket() throws IOException {
        if ((clientSocket == null) || clientSocket.isClosed()) {
            clientSocket = new Socket();
        }
        return clientSocket;
    }

    private ServerSocket getServerSocket() throws IOException {
        if ((serverSocket == null) || serverSocket.isClosed()) {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(local);
        }
        return serverSocket;
    }

    @Override
    public void connect() throws IOException {
        // Create server socket for receiving
        getServerSocket();
    }

    @Override
    public void disconnect() throws IOException {
        // Close accepted socket if open
        if (acceptedSocket != null && !acceptedSocket.isClosed()) {
            acceptedSocket.close();
            acceptedSocket = null;
        }
    }

    @Override
    public boolean isConnected() {
        return acceptedSocket != null && !acceptedSocket.isClosed();
    }

    public boolean isListening() throws IOException {
        boolean listening;
        try {
            new Socket(local.getAddress(), local.getPort()).close();
            listening = true;
        } catch (ConnectException cex) {
            listening = false;
        }
        return listening;
    }

    @Override
    public void close() throws IOException {
        if (clientSocket != null) {
            clientSocket.close();
        }
        if (acceptedSocket != null) {
            acceptedSocket.close();
        }
        if (serverSocket != null) {
            serverSocket.close();
        }
    }

    @Override
    public void send(final OSCPacket packet)
            throws IOException, OSCSerializeException
    {
        serializer.write(packet);

        final Socket clientSock = getClientSocket();
        if (!clientSock.isConnected()) {
            clientSock.connect(remote);
        }

        // Write with OSC 1.0 size-prefix framing: 4-byte big-endian length + data
        DataOutputStream out = new DataOutputStream(clientSock.getOutputStream());
        byte[] data = serializationBuffer.toByteArray();
        out.writeInt(data.length);  // 4-byte big-endian length
        out.write(data);
        out.flush();

        serializationBuffer.clear();
    }

    @Override
    public OSCPacket receive() throws IOException, OSCParseException {
        final ServerSocket serverSock = getServerSocket();

        while (true) {
            // Accept new connection if we don't have one or it's closed
            if (acceptedSocket == null || acceptedSocket.isClosed()) {
                acceptedSocket = serverSock.accept();
                System.out.println("OSC10TCPTransport: Accepted connection from " +
                    acceptedSocket.getRemoteSocketAddress());
            }

            try {
                DataInputStream in = new DataInputStream(acceptedSocket.getInputStream());

                // Read 4-byte big-endian length prefix
                int length = in.readInt();

                if (length <= 0 || length > 65536) {
                    // Invalid length - likely connection issue or protocol mismatch
                    System.err.println("OSC10TCPTransport: Invalid message length: " + length);
                    acceptedSocket.close();
                    acceptedSocket = null;
                    continue;
                }

                // Read the OSC packet data
                byte[] data = new byte[length];
                in.readFully(data);

                System.out.println("OSC10TCPTransport: Received " + length + " bytes");
                return parser.convert(ByteBuffer.wrap(data));

            } catch (java.io.EOFException eof) {
                // Client disconnected
                System.out.println("OSC10TCPTransport: Client disconnected");
                acceptedSocket.close();
                acceptedSocket = null;
                // Continue to accept new connections
            }
        }
    }

    @Override
    public boolean isBlocking() {
        return true;
    }

    @Override
    public String toString() {
        return String.format(
            "%s: local=%s, remote=%s", getClass().getSimpleName(), local, remote
        );
    }
}
