/*
 * UdpServer, MIT (c) 2019-2021 miktim@mail.ru
 * UDP broadcast/unicast/multicast sender/receiver
 */
package org.miktim.udpserver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.UnknownHostException;

public class UdpServer extends Thread {

    public interface Handler {

        void onStart(UdpServer s);

        void onDatagram(UdpServer s, DatagramPacket p);

        void onError(UdpServer s, Exception e);

        void onShutdown(UdpServer s);
    }

    private DatagramSocket socket;
    private int port;                // bind/connect port
    private InetAddress inetAddress; // connect address
    private InetAddress bindAddress; // bind (interface) address
    private boolean isIntfAddress = false; // inetAddress is local interface
    private int bufferLength = 508; // 508 - IPv4 guaranteed receive packet size by any host
    // SO_RCVBUF size = 106496 (Linux x64)
    private Handler handler;

// port - binding (listening), connection, group port
// inetAddr - connection/multicast group address
// bindAddr - socket binding (interface) address
    public UdpServer(int port, UdpServer.Handler handler) throws Exception {
        createServer(port, null, null, handler);
    }

    public UdpServer(int port, InetAddress inetAddress, UdpServer.Handler handler)
            throws Exception {
        createServer(port, inetAddress, null, handler);
    }

    public UdpServer(int port, InetAddress inetAddress, InetAddress bindAddr, UdpServer.Handler handler)
            throws Exception {
        createServer(port, inetAddress, bindAddr, handler);
    }

    public boolean isMulticastSocket() {
        return inetAddress.isMulticastAddress();
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public void setDatagramLength(int length) throws IllegalArgumentException {
        if (length <= 0) {
            throw new IllegalArgumentException();
        }
        bufferLength = length;
    }

    public int getDatagramLength() {
        return bufferLength;
    }

    public boolean isStarted() {
        Thread.State state = this.getState();
        return !(state == State.NEW || state == State.TERMINATED);
    }

    final void createServer(int port, InetAddress inetAddr, InetAddress bindAddr, UdpServer.Handler handler) throws UnknownHostException, IOException {

        this.port = port;
        inetAddress = inetAddr != null ? inetAddr : InetAddress.getByName("0.0.0.0");
        isIntfAddress = NetworkInterface.getByInetAddress(inetAddress) != null;
        bindAddress = bindAddr;
        if (bindAddr == null && isIntfAddress) {
            bindAddress = inetAddress;
        }
        this.handler = handler;

        if (inetAddress.isMulticastAddress()) {
            if (bindAddress != null) {
                socket = new MulticastSocket();
                ((MulticastSocket) socket).joinGroup(
                        new InetSocketAddress(inetAddr, port),
                        NetworkInterface.getByInetAddress(bindAddr)
                );
            } else {
                socket = new MulticastSocket(port);
                ((MulticastSocket) socket).joinGroup(inetAddress);
            }
        } else {
            if (bindAddress != null) {
                socket = new DatagramSocket(port, bindAddress);
            } else {
                socket = new DatagramSocket(port);
            }
            if (!isIntfAddress) { // 
                socket.connect(inetAddress, port);
            }
        }
// See note:
//   https://docs.oracle.com/javase/7/docs/api/java/net/DatagramSocket.html#setReuseAddress(boolean)
        socket.setReuseAddress(true);
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
        handler.onStart(this);
        while (!socket.isClosed()) {
            try {
                DatagramPacket dp
                        = new DatagramPacket(new byte[bufferLength], bufferLength);
                socket.receive(dp);
                handler.onDatagram(this, dp);
            } catch (java.net.SocketTimeoutException e) {
// ignore
            } catch (IOException e) {
                if (socket.isClosed()) {
                    break;
                }
                handler.onError(this, e);
            }
        }
        handler.onShutdown(this);
    }

    @Override
    public String toString() {
        try {
            String serverType = (socket.getBroadcast() ? "Broadcast"
                    : ((inetAddress.isMulticastAddress() ? "Multicast"
                    : "Unicast"))) + " UDP socket";
            String mcGroup = inetAddress.isMulticastAddress()
                    ? (" MCgroup ("
                    + (inetAddress.isMCGlobal() ? "global," : "local,")
                    + inetAddress
                    + (bindAddress != null ? ":" + port : "")
                    + ")")
                    : "";
            String boundTo = " is bound to "
                    + socket.getLocalSocketAddress()
                    + "";
            String connectedTo = socket.isConnected()
                    ? " connected to " + socket.getRemoteSocketAddress()
                    : "";
            return serverType + mcGroup + boundTo + connectedTo;
        } catch (IOException e) {
            return e.toString();
        }
    }

    public void shutdown() {
        if (!socket.isClosed()) {
            try { // ???
                if (isMulticastSocket()) {
                    if (bindAddress != null) {
                        ((MulticastSocket) socket).leaveGroup(
                                new InetSocketAddress(inetAddress, port),
                                NetworkInterface.getByInetAddress(bindAddress)
                        );
                    } else {
                        ((MulticastSocket) socket).leaveGroup(inetAddress);
                    }
                }
                if (socket.isConnected()) {
                    socket.disconnect();
                }
            } catch (IOException e) {
//                e.printStackTrace();
            }
            socket.close();
            this.interrupt(); // ???
        }
    }

    public void send(byte[] datagram) throws IOException {
        socket.send(new DatagramPacket(datagram, datagram.length, inetAddress, port));
    }

    public void send(byte[] datagram, InetAddress inetAddr) throws IOException {
        socket.send(new DatagramPacket(datagram, datagram.length, inetAddr, port));
    }

}
