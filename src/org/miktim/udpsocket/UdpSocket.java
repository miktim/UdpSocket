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

        void onClose(UdpSocket s); // called before closing datagram socket
    }

    public static boolean tryReuseAddress = false;
    public static boolean nullAddressRequired = true; // Android - false; Linux,Windows - true 

    private DatagramSocket socket;
    private int port;                // bind/connect/group port
    private InetAddress inetAddress; // broadcast/connect/group address
    private int bufferLength = 508;  // 508 - IPv4 guaranteed receive packet size by any host
    // SO_RCVBUF size = 106496 (Linux x64)
    private Handler handler;
    private boolean isRunning = false;

    public UdpSocket(int port) throws Exception {
        createSocket(port, null, null);
    }

    public UdpSocket(int port, InetAddress inetAddr)
            throws Exception {
        createSocket(port, inetAddr, null);
    }

    public UdpSocket(int port, InetAddress inetAddr, InetAddress bindAddr)
            throws Exception {
        createSocket(port, inetAddr, bindAddr);
    }

    public boolean isMulticast() {
        return inetAddress.isMulticastAddress();
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    public void setBufferLength(int length) throws IllegalArgumentException {
        if (length <= 0) {
            throw new IllegalArgumentException();
        }
        bufferLength = length;
    }

    public int getBufferLength() {
        return bufferLength;
    }

    public InetSocketAddress getGroup() {
        if (isMulticast()) {
            return new InetSocketAddress(inetAddress, port);
        } else {
            return null;
        }
    }

    public boolean isReceiving() {
//        Thread.State state = this.getState();
//        return !(state == State.NEW || state == State.TERMINATED);
        return isRunning;
    }

    public boolean isOpen() {
        return !socket.isClosed();
    }

    final void createSocket(int port, InetAddress inetAddr, InetAddress bindAddr) throws UnknownHostException, IOException {
        this.port = port;
        inetAddress = inetAddr != null
                ? inetAddr : InetAddress.getByName("0.0.0.0");
        if (bindAddr != null && NetworkInterface.getByInetAddress(bindAddr) == null) {
            throw new SocketException("Not interface");
        }
        SocketAddress socketAddr = new InetSocketAddress(bindAddr, port);

        if (inetAddress.isMulticastAddress()) {
            if (bindAddr != null && !NetworkInterface.getByInetAddress(bindAddr).supportsMulticast()) {
                throw new SocketException("Not multicast");
            }
            MulticastSocket mcastSocket;
            if (tryReuseAddress) {
// https://stackoverflow.com/questions/10071107/rebinding-a-port-to-datagram-socket-on-a-difftent-ip
// I'm failed to achieve stable work on different operating systems
                mcastSocket = nullAddressRequired
                        ? new MulticastSocket(null) : new MulticastSocket();
                mcastSocket.setReuseAddress(true);
                mcastSocket.bind(socketAddr); //
            } else {
                mcastSocket = new MulticastSocket(socketAddr);
            }
            if (bindAddr == null) {
                mcastSocket.joinGroup(inetAddress);
            } else {
                mcastSocket.joinGroup(
                        getGroup(),
                        NetworkInterface.getByInetAddress(bindAddr));
            }
            mcastSocket.setLoopbackMode(true); // disable loopback
            mcastSocket.setTimeToLive(1);
            socket = mcastSocket;
        } else {
            if (tryReuseAddress) {
                socket = nullAddressRequired
                        ? new DatagramSocket(null) : new DatagramSocket();
                socket.setReuseAddress(true);
                socket.bind(socketAddr);
            } else {
                socket = new DatagramSocket(socketAddr);
            }
            if (inetAddress.isAnyLocalAddress()) {
                socket.setBroadcast(true);
            } else {
                socket.connect(new InetSocketAddress(inetAddress, port));
            }
        }
        socket.setSoTimeout(500); // !!! DO NOT disable
    }

// https://stackoverflow.com/questions/4519556/how-to-determine-if-my-app-is-running-on-android
//    private boolean nullAddressRequired() {
//        return System.getProperty("os.name").startsWith("Linux");
////        return !System.getProperty("java.runtime.name").equals("Android Runtime");
//    }
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
                        = new DatagramPacket(new byte[bufferLength], bufferLength);
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
        return serverType + mcGroup + connectedTo + boundTo;
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
                    NetworkInterface netIf
                            = ((MulticastSocket) socket).getNetworkInterface();
                    if (netIf == null) {
                        ((MulticastSocket) socket).leaveGroup(inetAddress);
                    } else {
                        ((MulticastSocket) socket).leaveGroup(getGroup(), netIf);
                    }
                }
                if (socket.isConnected()) {
                    socket.disconnect();
                }
            } catch (IOException e) {
//                handler.onError(this, e);
            }
        }
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
