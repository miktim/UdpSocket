/*
 * UdpServer, MIT (c) 2019-2021 miktim@mail.ru
 * UDP unicast/multicast sender/receiver
 */
package org.miktim.udpserver;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayDeque;

public class UdpServer extends Thread {

    public interface Handler {

        void onUdpPacket(UdpServer server, DatagramPacket packet);

        void onUdpError(UdpServer server, Exception e);
    }

    private DatagramSocket ds;
    private MulticastSocket ms = null;
    private int bufferLength = 256;
    private Handler handler;

    private final ArrayDeque<DatagramPacket> packetQueue = new ArrayDeque();

    private class PacketDequer extends Thread {

        UdpServer server;
        DatagramPacket dp;

        PacketDequer(UdpServer server) {
            this.server = server;
        }

        @Override
        public void run() {

            while (!server.ds.isClosed()) {
                synchronized (server.packetQueue) {
                    if (!server.packetQueue.isEmpty()) {
                        dp = server.packetQueue.pollFirst();
                        server.handler.onUdpPacket(server, dp);
                    } else {
                        try {
                            server.packetQueue.wait();
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }
    }

    public UdpServer(int port, Handler handler) throws IOException {
        create(port, null, handler);
    }

    public UdpServer(int port, InetAddress inetAddress, Handler handler) throws IOException {
        create(port, inetAddress, handler);
    }

    private void create(int port, InetAddress inetAddress, Handler handler) throws IOException {
        InetAddress ia = inetAddress != null ? inetAddress : InetAddress.getByName("0.0.0.0");
        if (ia.isMulticastAddress()) {
            ms = new MulticastSocket(port);
            ms.joinGroup(ia);
            ms.setLoopbackMode(false);
            if (ia.isMCLinkLocal()) {
                ms.setTimeToLive(1);
            }
            ds = ms;
        } else {
            ds = new DatagramSocket(port, ia);
            if (ds.getInetAddress().isAnyLocalAddress()) {
                ds.setBroadcast(true);
            }
        }
        this.handler = handler;
    }

    public boolean isMulticast() {
        return ms != null;
    }
    
//    public MulticastSocket getMulticastSocket() {
//        return ms;
//    }

    public DatagramSocket getDatagramSocket() {
        return ds;
    }

    public void send(byte[] packet) throws IOException {
            DatagramPacket dp = new DatagramPacket(packet, packet.length);
            ds.send(dp);
    }

    /*
    public void send(byte[] packet, InetAddress inetAddress) {
        try {
            DatagramPacket dp = new DatagramPacket(packet, packet.length);
            dp.setAddress(inetAddress);
            ds.send(dp);
        } catch (IOException e) {
            listener.onUdpError(e);
        }
    }
     */
    public void setBufferLength(int length) {
        bufferLength = length;
    }

    public int getBufferLength() {
        return bufferLength;
    }

    @Override
    public void run() {
        new PacketDequer(this).start();
        while (!ds.isClosed()) {
            try {
                DatagramPacket dp
                        = new DatagramPacket(new byte[bufferLength], bufferLength);
                ds.receive(dp);
                synchronized (packetQueue) {
                    packetQueue.addLast(dp);
                    packetQueue.notify();
                }
            } catch (IOException e) {
                handler.onUdpError(this, e);
            }
        }
    }
    public boolean isListening() {
        Thread.State state = this.getState();
        return !(state == State.NEW || state == State.TERMINATED);
    }
    
    public void close() {
        if (ds != null) {
            ds.close();
        }
        try {
            if (ms != null) {
                ms.leaveGroup(ms.getInetAddress());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        packetQueue.clear();
    }
}
