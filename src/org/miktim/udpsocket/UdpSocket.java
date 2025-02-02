/*
 * UdpSocket, MIT (c) 2019-2025 miktim@mail.ru
 * UDP broadcast/unicast/multicast sender/receiver
 */
package org.miktim.udpsocket;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.SocketException;

public final class UdpSocket extends Thread implements Closeable, AutoCloseable {

    public static final String VERSION = "3.1.2";

    public interface Handler {

        void onStart(UdpSocket s);

        void onPacket(UdpSocket s, DatagramPacket p);

        void onError(UdpSocket s, Exception e);

        void onClose(UdpSocket s); // called before closing datagram socket
    }

    private DatagramSocket socket;
    private int port;                // bind/connect/group port
    private InetAddress inetAddress; // broadcast/connect/group address
    private int payloadSize = 1500;  // mtu length
    // The maximum safe UDP payload size is ~508 bytes.  
    // For any case: SO_RCVBUF size = 106496 (Linux x64)
    private Handler handler;
    private boolean isRunning = false;
    private static final int SOCKET_SO_TIMEOUT = 1000;

    public static boolean isAvailable(int port) {
// https://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
        DatagramSocket soc;
        try {
            soc = new DatagramSocket(port);
        } catch (SocketException e) {
            return false;
        }
        soc.close();
        return true;
    }

    public static boolean seemsBroadcast(InetAddress addr) {
        if (addr.isMulticastAddress()) {
            return false;
        }
        byte[] b = addr.getAddress();
        return b.length == 4 && (b[3] == (byte) 255);
    }

    public static void send(byte[] buf, int len, int port, InetAddress inetAddr) throws IOException {
//        UdpSocket.send(buf, len, port, inetAddr, InetAddress.getByAddress(new byte[4]));
        try (DatagramSocket soc = createSocket(inetAddr)) {
            soc.send(new DatagramPacket(buf, len, inetAddr, port));
        }
    }

    public static void send(byte[] buf, int len, int port, InetAddress inetAddr, InetAddress localAddr) throws IOException {
        UdpSocket.send(buf, len, port, inetAddr, new InetSocketAddress(localAddr, port));
    }

    public static void send(byte[] buf, int len, int port, InetAddress inetAddr, SocketAddress socketAddr)
            throws IOException {
        try (DatagramSocket soc = UdpSocket.createSocket(inetAddr)) {
            soc.bind(socketAddr);
            soc.send(new DatagramPacket(buf, len, inetAddr, port));
        }
    }

    public UdpSocket(int port) throws IOException {
        udpSocket(port, InetAddress.getByName("255.255.255.255"), new InetSocketAddress(port));
    }

    public UdpSocket(int port, InetAddress inetAddr) throws IOException {
        udpSocket(port, inetAddr, new InetSocketAddress(port));
    }

    public UdpSocket(int port, InetAddress inetAddr, InetAddress localAddr)
            throws IOException {
        udpSocket(port, inetAddr, new InetSocketAddress(localAddr, port));
    }

    public UdpSocket(int port, InetAddress inetAddr, SocketAddress socketAddr)
            throws IOException {
        udpSocket(port, inetAddr, socketAddr);
    }

    void udpSocket(int port, InetAddress inetAddr, SocketAddress socketAddr) throws IOException {
        this.port = port;
        this.inetAddress = inetAddr;
        socket = UdpSocket.createSocket(inetAddr);
        socket.bind(socketAddr);
        if (isMulticast()) {
            ((MulticastSocket) socket).joinGroup(inetAddress);
        }
    }

// creates unbinded socket
    static DatagramSocket createSocket(InetAddress inetAddr)
            throws IOException {

        DatagramSocket soc;

        if (inetAddr.isMulticastAddress()) {
            MulticastSocket mcastSoc = new MulticastSocket(null);
            mcastSoc.setLoopbackMode(true); // disable loopback
            mcastSoc.setTimeToLive(1);
//            mcastSoc.joinGroup(inetAddr);
            soc = mcastSoc;
        } else {
            soc = new DatagramSocket(null);
            soc.setBroadcast(seemsBroadcast(inetAddr));
//            soc.connect(inetAddr, port);            
        }
        soc.setReuseAddress(true);
//        soc.bind(new InetSocketAddress(localAddr, port));
        soc.setSoTimeout(SOCKET_SO_TIMEOUT);// !!! DO NOT disable
        return soc;
    }

    public void send(byte[] buf) throws IOException {
        socket.send(new DatagramPacket(buf, buf.length, inetAddress, port));
    }

    public void send(byte[] buf, int len) throws IOException {
        socket.send(new DatagramPacket(buf, len, inetAddress, port));
    }

    public boolean isMulticast() {
        return inetAddress.isMulticastAddress();
    }

    public boolean isBroadcast() {
        return seemsBroadcast(inetAddress);
    }

    public void setBroadcast(boolean on) throws SocketException {
        socket.setBroadcast(on);
    }

    public boolean getBroadcast() throws SocketException {
        return socket.getBroadcast();
    }

    public void setReuseAddress(boolean on) throws SocketException {
        socket.setReuseAddress(on);
    }

    public boolean getReuseAddress() throws SocketException {
        return socket.getReuseAddress();
    }

    public void connect() {
        // "A socket connected to a multicast address may only be used to send packets."
        if (!isMulticast()) {
            socket.connect(inetAddress, port);
        }
    }

    public void disconnect() {
        socket.disconnect();
    }

    public boolean isConnected() {
        return socket.isConnected();
    }

    public DatagramSocket getDatagramSocket() {
        return socket;
    }

    public void setPayloadSize(int size) throws IllegalArgumentException {
        if (size <= 0) {
            throw new IllegalArgumentException();
        }
        payloadSize = size;
    }

    public int getPayloadSize() {
        return payloadSize;
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }

    public int getPort() {
        return port;
    }

    public InetAddress getLocalAddress() {
        return socket.getLocalAddress();
    }

    public boolean isReceiving() {
        return isRunning;
    }

    public boolean isOpen() {
        return !socket.isClosed();
    }

    public void receive(UdpSocket.Handler handler) {
        this.handler = handler;
        start();
    }

    @Override
    public void start() {
        if (handler == null) {
            throw new NullPointerException("No handler");
        }
        super.start();
    }

    @Override
    public void run() {
        isRunning = true;
        handler.onStart(this);
        while (isRunning && !socket.isClosed()) {
            try {
                DatagramPacket dp
                        = new DatagramPacket(new byte[payloadSize], payloadSize);
                socket.receive(dp);
                handler.onPacket(this, dp);
            } catch (java.net.SocketTimeoutException e) {
// it takes a timeout to close the socket properly.
// DO NOT DISABLE the socket timeout!
            } catch (IOException e) {
                if (!isRunning || socket.isClosed()) {
                    break;
                }
                handler.onError(this, e);
                close();
            }
        }
        isRunning = false;
        handler.onClose(this);
    }

    public void close() {
        if (isRunning) {
            isRunning = false;
            try {
                socket.setSoTimeout(5);
                this.join(); // wait thread
            } catch (InterruptedException | SocketException e) {
            }
        }
        if (!socket.isClosed()) {
            try {
                if (isMulticast()) {
                    ((MulticastSocket) socket).leaveGroup(inetAddress);
                }
                if (socket.isConnected()) {
                    socket.disconnect();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        socket.close();
    }

    public String typeToString() throws IOException {
        if (socket instanceof MulticastSocket) {
            MulticastSocket ms = (MulticastSocket) socket;
            return String.format("Multicast(%d,%s)",
                    ms.getTimeToLive(),
                    "" + ms.getLoopbackMode());
        } else if (socket.getBroadcast()) {
            return "Broadcast";
        }
        return "Unicast";
    }

    @Override
    public String toString() {
        String info = "";
        try {
            info = String.format("%s %s:%d",
                    typeToString(),
                    inetAddress.toString(),
                    port);
            info += socket.isConnected() 
                    ? " connected: " + socket.getLocalSocketAddress()
                    : "";
            info += socket.isBound()
                    ? (" bound: " + socket.getLocalSocketAddress())
                    : "";
            info += socket.isClosed() ? " closed" : "";
        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return info;
    }

}
