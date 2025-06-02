/*
 * UdpSocket package, MIT (c) 2025 miktim@mail.ru
 * Manage UDP via MulticastSocket class
 *
 * Created 2025-06-01
 */
package org.miktim.udpsocket;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;

public class UdpSocket extends MulticastSocket implements Closeable, AutoCloseable {
    
    public static String VERSION = "4.0.2";
    InetSocketAddress remote;

    public UdpSocket(InetSocketAddress remoteSoc, NetworkInterface netIf) throws IOException {
        super(null); // creates unbounded socket
        setReuseAddress(true);
        setLoopbackMode(true); // disable loopback
        remote = remoteSoc;
        setNetworkInterface(netIf);
        if (isMulticast()) {
            joinGroup(remote, netIf);
        }
    }

    public UdpSocket(InetAddress remoteAddr, int remotePort, String netIfName) throws IOException {
        this(new InetSocketAddress(remoteAddr, remotePort), NetworkInterface.getByName(netIfName));
    }

    public interface Handler {

        void onStart(UdpSocket us);

        void onError(UdpSocket us, Exception e);

        void onPacket(UdpSocket us, DatagramPacket dp);

        void onClose(UdpSocket us); // called before closing datagram socket
    }

    class SocketListener extends Thread {

        UdpSocket us;

        SocketListener(UdpSocket socket) {
            us = socket;
        }

        @Override
        public void run() {
            us.isRunning = true;
            us.handler.onStart(us);
            while (us.isReceiving() && !us.isClosed()) {
                try {
                    DatagramPacket dp
                            = new DatagramPacket(new byte[us.payloadSize], us.payloadSize);
                    us.receive(dp);
                    us.handler.onPacket(us, dp);
                } catch (java.net.SocketTimeoutException e) {
                } catch (Exception e) {
                    if (!us.isReceiving() || us.isClosed()) { //
                        break;
                    }
                    try {
                        us.handler.onError(us, e);
                        us.close();
                    } catch (Exception ignore) {
                    }
                }
            }
        }
    }

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

    public UdpSocket bind() throws IOException {
        if (!isBound()) {
            bind(new InetSocketAddress(remote.getPort()));
        }
        return this;
    }

    public InetSocketAddress getRemote() {
        return remote;
    }

    public InetSocketAddress getLocal() throws IOException {
        NetworkInterface ni = getNetworkInterface();
        if (ni == null) {
            return new InetSocketAddress(remote.getPort());
        }
        Class cls = remote.getAddress().getClass();
        for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
            if (ia.getAddress().getClass().equals(cls)) {
                return new InetSocketAddress(ia.getAddress(), remote.getPort());
            }
        }
        throw new SocketException();
    }

    public final boolean isMulticast() {
        return remote.getAddress().isMulticastAddress();
    }
    
    Handler handler;
    boolean isRunning; // receiving in progress
    private int payloadSize = 1500; // maximum length of received datagrams
    
    public boolean isReceiving() {
        return isRunning;
    }

    public UdpSocket setPayloadSize(int size) {
        payloadSize = size;
        return this;
    }

    public int getPayloadSize() {
        return payloadSize;
    }

    public void receive(Handler handler) throws IOException {
        bind();
        if (isReceiving()) {
            throw new IllegalStateException("Already receiving");
        }
        if (handler == null) {
            throw new NullPointerException("No handler");
        }
        this.handler = handler;
        (new SocketListener(this)).start();
    }

    @Override
    public void receive(DatagramPacket dp) throws IOException {
        bind();
        if (dp.getAddress() == null) {
            dp.setSocketAddress(remote);
        }
        super.receive(dp); 
    }

    public void send(byte[] buf) throws IOException {
        send(new DatagramPacket(buf, buf.length));
    }

    @Override
    public void send(DatagramPacket dp) throws IOException {
        bind();
        if (dp.getAddress() == null) {
            dp.setSocketAddress(remote);
        }
        super.send(dp);
    }

    @Override
    public void close() {
        if (isReceiving()) {
            isRunning = false;
            handler.onClose(this);
            handler = null;
        }
        super.close();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append(String.format("UdpSocket remote: %s bound to: %s\n\r",
                     getRemote(), getLocalSocketAddress()));
            sb.append("Options:\r\n");
            sb.append(String.format("SO_SNDBUF: %d SO_RCVBUF: %d SO_REUSEADDR: %b SO_BROADCAST: %b\n\r",
                    getSendBufferSize(),
                    getReceiveBufferSize(),
                    getReuseAddress(),
                    getBroadcast()));
            NetworkInterface intf = getNetworkInterface();
            sb.append(String.format("IP_MULTICAST_IF: %s IP_MULTICAST_TTL: %d IP_MULTICAST_LOOP: %b",
                    intf != null ? intf.getDisplayName() : "null",
                    getTimeToLive(),
                    getLoopbackMode()));
        } catch (IOException e) {
            sb.append(e.getClass().getName());
        }
        return sb.toString();
    }
}
