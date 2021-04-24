/*
 * UdpSocket, MIT (c) 2019-2021 miktim@mail.ru
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
import java.net.UnknownHostException;

public class UdpSocket extends Thread {

    public interface Handler {

        void onStart(UdpSocket s);

        void onPacket(UdpSocket s, DatagramPacket p);

        void onError(UdpSocket s, Exception e);

        void onClose(UdpSocket s);
    }

    private DatagramSocket socket;
    private int port;                // bind/connect port
    private InetAddress inetAddress; // connect address
    private InetAddress bindAddress; // bind (interface) address
    private int bufferLength = 508; // 508 - IPv4 guaranteed receive packet size by any host
    // SO_RCVBUF size = 106496 (Linux x64)
    private Handler handler;
    private boolean isRunning = false;
//    private InetSocketAddress mcastGroup;
//    private NetworkInterface mcastNetIf;

// port - binding (listening), connection, group port
// inetAddr - connection/multicast group address
// bindAddr - socket binding (interface) address
    public UdpSocket(int port, UdpSocket.Handler handler) throws Exception {
        createSocket(port, null, null, handler);
    }

    public UdpSocket(int port, InetAddress inetAddress, UdpSocket.Handler handler)
            throws Exception {
        createSocket(port, inetAddress, null, handler);
    }

    public UdpSocket(int port, InetAddress inetAddress, InetAddress bindAddr, UdpSocket.Handler handler)
            throws Exception {
        createSocket(port, inetAddress, bindAddr, handler);
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

    public boolean isReceiving() {
        Thread.State state = this.getState();
        return !(state == State.NEW || state == State.TERMINATED);
    }

    final void createSocket(int port, InetAddress inetAddr, InetAddress bindAddr, UdpSocket.Handler handler) throws UnknownHostException, IOException {

        if (handler == null) {
            throw new NullPointerException("No handler");
        }
        this.port = port;
        inetAddress = inetAddr != null ? inetAddr : InetAddress.getByName("0.0.0.0");
        bindAddress = bindAddr == null && inetAddr == null
                ? InetAddress.getByName("0.0.0.0") : bindAddr;
        this.handler = handler;
        if (bindAddr != null && NetworkInterface.getByInetAddress(bindAddr) == null) {
            throw new SocketException("Not interface");
        }
        SocketAddress socketAddr = new InetSocketAddress(bindAddress, port);
        if (inetAddress.isMulticastAddress()) {
            if (bindAddr != null && !NetworkInterface.getByInetAddress(bindAddr).supportsMulticast()) {
                throw new SocketException("Not multicast");
            }
// https://stackoverflow.com/questions/10071107/rebinding-a-port-to-datagram-socket-on-a-difftent-ip
            MulticastSocket mcastSocket = isAndroid()
                    ? new MulticastSocket() : new MulticastSocket(null);
            mcastSocket.setReuseAddress(true);
            mcastSocket.bind(socketAddr);
            if (bindAddr != null) {
                mcastSocket.joinGroup(
                        new InetSocketAddress(inetAddress, port),
                        NetworkInterface.getByInetAddress(bindAddr));
            } else {
                mcastSocket.joinGroup(inetAddress);
            }
            mcastSocket.setLoopbackMode(true);
            mcastSocket.setTimeToLive(1);
            socket = mcastSocket;
        } else {
            socket = isAndroid() 
                    ? new DatagramSocket() : new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(socketAddr);
            if (inetAddr != null) {
                socket.connect(new InetSocketAddress(inetAddress, port));
            } else {
                socket.setBroadcast(true);
            }
        }

        socket.setSoTimeout(500); // !!!
    }

// https://stackoverflow.com/questions/4519556/how-to-determine-if-my-app-is-running-on-android
    private boolean isAndroid() {
        return System.getProperty("java.runtime.name").equals("Android Runtime");
    }

    @Override
    public void run() {
        handler.onStart(this);
        isRunning = true;
        while (isRunning) {
            try {
                DatagramPacket dp
                        = new DatagramPacket(new byte[bufferLength], bufferLength);
                socket.receive(dp);
                handler.onPacket(this, dp);
            } catch (java.net.SocketTimeoutException e) {
// ignore
            } catch (IOException e) {
                if (!isRunning) {
                    break;
                }
                handler.onError(this, e);
            }
        }
        handler.onClose(this);
    }

    @Override
    public String toString() {
//        try {
        String serverType = (socket.isConnected() ? "Unicast"
                : ((inetAddress.isMulticastAddress() ? "Multicast"
                : "Broadcast"))) + " UDP socket";
        String mcGroup = inetAddress.isMulticastAddress()
                ? (" MCgroup "
                + (inetAddress.isMCGlobal() ? "global," : "local,")
                + inetAddress)
                : "";
        String boundTo = socket.isBound()
                ? (" is bound to " + socket.getLocalSocketAddress())
                : "";
        String connectedTo = socket.isConnected()
                ? (" connected to " + socket.getRemoteSocketAddress())
                : "";
        return serverType + mcGroup + connectedTo + boundTo;
//        } catch (IOException e) {
//            return e.toString();
//        }
    }

    public void close() {
        if (!socket.isClosed()) {
            try {
                if (isMulticastSocket()) {
                    if (bindAddress != null) {
                        ((MulticastSocket) socket).leaveGroup(
                                new InetSocketAddress(inetAddress, port),
                                NetworkInterface.getByInetAddress(bindAddress));

                    } else {
                        ((MulticastSocket) socket).leaveGroup(inetAddress);
                    }
                }
                if (socket.isConnected()) {
                    socket.disconnect();
                }
            } catch (IOException e) {
                handler.onError(this, e);
            }
        }
        isRunning = false;
        socket.close();
    }

    public void send(byte[] buf) throws IOException {
        socket.send(new DatagramPacket(buf, buf.length, inetAddress, port));
    }

    public static void send(byte[] buf, InetAddress addr, int port)
            throws IOException {
        if (addr.isMulticastAddress()) {
            (new MulticastSocket())
                    .send(new DatagramPacket(buf, buf.length, addr, port));
        } else {
            (new DatagramSocket())
                    .send(new DatagramPacket(buf, buf.length, addr, port));
        }
    }

}
