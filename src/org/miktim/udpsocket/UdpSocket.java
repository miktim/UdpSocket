/*
 * UdpSocket, MIT (c) 2019-2025 miktim@mail.ru
 * UDP broadcast/unicast/multicast sender/receiver
 */
package org.miktim.udpsocket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;

public class UdpSocket extends Thread {

    public static final String version = "2.0.0";
    
    public interface Handler {

        void onStart(UdpSocket s);

        void onPacket(UdpSocket s, DatagramPacket p);

        void onError(UdpSocket s, Exception e);

        void onClose(UdpSocket s); // called before closing datagram socket
    }

    private static boolean reuseAddressEnabled = true;

    public static void setReuseAddress(boolean on) {
        reuseAddressEnabled = on;
    }

    public static boolean getReuseAddress() {
        return reuseAddressEnabled;
    }

    public static void send(byte[] buf, int len, int port, InetAddress addr) throws IOException {
        send(buf, len, port, addr, null);
    }

    public static void send(byte[] buf, int len, int port, InetAddress addr, InetAddress localAddr)
            throws IOException {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(UdpSocket.isBroadcast(addr));
            if (localAddr != null) {
                socket.bind(new InetSocketAddress(localAddr, 0));
            }
            socket.send(new DatagramPacket(buf, len, addr, port));
        }
    }

    private DatagramSocket socket;
    private int port;                // bind/connect/group port
    private InetAddress inetAddress; // broadcast/connect/group address
    private int bufLength = 1024;
    // 508 bytes is guaranteed receive packet size by any IPv4 host 
    // For any case: SO_RCVBUF size = 106496 (Linux x64)
    private Handler handler;
    private boolean isRunning = false;
    private static final int SOCKET_SO_TIMEOUT = 300;
    
    public UdpSocket(int port) throws IOException {
        createSocket(port, null, null);
    }

    public UdpSocket(int port, InetAddress inetAddr) throws IOException {
        createSocket(port, inetAddr, null);
    }

    public UdpSocket(int port, InetAddress inetAddr, InetAddress localAddr)
            throws IOException {
        createSocket(port, inetAddr, localAddr);
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

// any ipv4 address ending in .255 (in fact, the subnet mask may be different from /24)
    public static boolean isBroadcast(InetAddress addr) {
        if (addr.isMulticastAddress()) {
            return false;
        }
        byte[] b = addr.getAddress();
        return b.length == 4 && b[3] == (byte) 255;
    }

    public boolean isBroadcast() {
        return isBroadcast(inetAddress);
    }

    public DatagramSocket getDatagramSocket() {
        return socket;
    }

    public void setBufLength(int length) throws IllegalArgumentException {
        if (length <= 0) {
            throw new IllegalArgumentException();
        }
        bufLength = length;
    }

    public int getBufLength() {
        return bufLength;
    }

    public InetAddress getInetAddress() {
        return inetAddress;
    }

    public boolean isReceiving() {
//        Thread.State state = this.getState();
//        return !(state == State.NEW || state == State.TERMINATED);
        return isRunning;
    }

    public boolean isOpen() {
        return !socket.isClosed();
    }

    final void createSocket(int port, InetAddress inetAddr, InetAddress bindAddr)
            throws IOException {
        this.port = port;
        inetAddress = inetAddr != null
                ? inetAddr : InetAddress.getByName("255.255.255.255");
        if (bindAddr != null && NetworkInterface.getByInetAddress(bindAddr) == null) {
            throw new SocketException("Not interface");
        }
        SocketAddress socketAddr = new InetSocketAddress(bindAddr, port);

        if (inetAddress.isMulticastAddress()) {
//            if (bindAddr != null && !NetworkInterface.getByInetAddress(bindAddr).supportsMulticast()) {
//                throw new SocketException("Interface not supports multicast");
//            }
            MulticastSocket mcastSocket;
            if (reuseAddressEnabled) {
// https://stackoverflow.com/questions/10071107/rebinding-a-port-to-datagram-socket-on-a-difftent-ip
                mcastSocket = new MulticastSocket(null);
                mcastSocket.setReuseAddress(true);
                mcastSocket.bind(socketAddr); //
            } else {
                mcastSocket = new MulticastSocket(socketAddr);
            }
            mcastSocket.joinGroup(inetAddress);
            mcastSocket.setLoopbackMode(true); // disable loopback
            mcastSocket.setTimeToLive(1);
            socket = mcastSocket;
        } else {
            if (reuseAddressEnabled) {
                socket = new DatagramSocket(null);
                socket.setReuseAddress(true);
                socket.bind(socketAddr);
            } else {
                socket = new DatagramSocket(socketAddr);
            }
            if (isBroadcast(inetAddress)) {
                socket.setBroadcast(true);
//            } else if (!inetAddress.isAnyLocalAddress()) { //! isMulticast()
//                socket.connect(new InetSocketAddress(inetAddress, port));
            }
        }
        socket.setSoTimeout(SOCKET_SO_TIMEOUT); // !!! DO NOT disable
    }

    public void receive(UdpSocket.Handler handler) {
        this.handler = handler;
        this.start();
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
                        = new DatagramPacket(new byte[bufLength], bufLength);
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
            }
        }
        isRunning = false;
        handler.onClose(this);
    }

    @Override
    public String toString() {
        String serverType = (socket.isConnected() ? "Unicast"
                : ((inetAddress.isMulticastAddress() ? "Multicast"
                : "Broadcast"))) + " UDP socket";
        String mcGroup = inetAddress.isMulticastAddress()
                ? " MCgroup "
                + (inetAddress.isMCGlobal() ? "global" : "local")
                + inetAddress + ":" + port
                : "";
        String boundTo = socket.isBound()
                ? (" bound to " + socket.getLocalSocketAddress())
                : "";
        String connectedTo = socket.isConnected()
                ? " connected to " + socket.getRemoteSocketAddress()
                : "";
        String broadcast = isBroadcast(inetAddress) ? " " + inetAddress + ":" + port
                : "";
        return serverType + broadcast + mcGroup + connectedTo + boundTo;
    }

    public void close() {
        if (isRunning) {
            isRunning = false;
            try {
                this.join();
            } catch (InterruptedException e) {
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
//                e.printStackTrace();
            }
        }
        socket.close();
    }

}
