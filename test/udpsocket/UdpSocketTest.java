/*
 * UdpSocketTest, MIT (c) 2021 miktim@mail.ru
 */
//package udpsocket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Timer;
import java.util.TimerTask;
import org.miktim.udpsocket.UdpSocket;

public class UdpSocketTest {

    static final int UDP_PORT = 9099; // IANA registry: unused
    static final String REMOTE_ADDRESS = "192.168.1.107";
    static final String MULTICAST_ADDRESS = "224.0.1.191"; // unused global
// delay for starting the receivers and completely receiving packets before closing
    static final int RECEIVER_DELAY = 100;
    static final int SENDER_DELAY = 300; // send datagram delay
// timeouted sockets closure
    static final int CLOSE_TIMEOUT = 10000;

 //   static boolean udpPortAccessible = false;

    static void log(String s) {
        System.out.println(s);
    }

    static String socketId(UdpSocket s) {
        String sid = "Socket" + s.getId() + s.getInetAddress();
        return sid;
    }

    public static void main(String[] args) throws Exception {

        UdpSocket.Handler handler = new UdpSocket.Handler() {
            @Override
            public void onStart(UdpSocket socket) {
                log(socketId(socket) + " started. " + socket.toString());
            }

            @Override
            public void onClose(UdpSocket socket) {
                log(socketId(socket) + " Socket closed.");
            }

            @Override
            public void onPacket(UdpSocket socket, DatagramPacket packet) {
//                if (UdpSocket.isBroadcast(socket.getInetAddress())) {
//                    udpPortAccessible = true;
//                }
                log(socketId(socket) + " onPacket: " + packet.getLength()
                        + packet.getAddress() + ":" + packet.getPort());
            }

            @Override
            public void onError(UdpSocket socket, Exception e) {
                log(socketId(socket) + " onError: " + e);
            }

        };

        InetAddress iab = InetAddress.getByName("255.255.255.255");
//        InetAddress iah = InetAddress.getLocalHost();
        InetAddress iah = InetAddress.getByName("localhost");
        InetAddress ial = InetAddress.getByName("localhost");
        InetAddress iam = InetAddress.getByName(MULTICAST_ADDRESS);
        InetAddress iar = InetAddress.getByName(REMOTE_ADDRESS);

        String osName = System.getProperty("os.name");
        log("UdpSocket test. " + osName);
//        log("");

        UdpSocket.setReuseAddress(true);
        try {
            UdpSocket socket1 = new UdpSocket(UDP_PORT, iar, iah);
            socket1.close();
        } catch (IOException e) {
            UdpSocket.setReuseAddress(false);
        }
        
        if (!UdpSocket.isAccessible(UDP_PORT, null)) {
            log("\r\nUDP port " + UDP_PORT + " is not accessible! Exit.");
            System.exit(1);
        } else {
            log("\r\nUDP port " + UDP_PORT + " is accessible.");
        }
// broadcast        
        final UdpSocket socket0 = new UdpSocket(UDP_PORT);
        socket0.receive(handler);
//        try {
//            Thread.sleep(RECEIVER_DELAY); // delay for starting receiver
//            UdpSocket.send(new byte[socket.getBufLength() / 2], ia0, UDP_PORT);
//            socket0.send(new byte[socket0.getBufLength()]);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        Thread.sleep(RECEIVER_DELAY); // closing delay for receiving packets

//        if (!udpPortAccessible) {
// check reuse address 
        try {
            UdpSocket socket1 = new UdpSocket(UDP_PORT, iar, iah);
            socket1.close();
            log("Reuse address is enabled.\r\n");
        } catch (IOException e) {
            UdpSocket.setReuseAddress(false);
            log("Failed to reuse address!\r\n");
        }
        if (!UdpSocket.getReuseAddress()) {
            socket0.close();
        }

// unicast
// bind to port connect to remote address
        final UdpSocket socket2 = new UdpSocket(UDP_PORT, iar);
        socket2.receive(handler);
        Thread.sleep(RECEIVER_DELAY); // delay for starting receivers
        try {
//            UdpSocket.send(new byte[socket2.getBufLength() / 3], ia1, UDP_PORT);
            socket2.send(new byte[socket2.getBufLength() * 2]);
        } catch (IOException e) {
            e.printStackTrace();
            socket2.close();
        }
        Thread.sleep(RECEIVER_DELAY); // closing delay for receiving packets
        if (!UdpSocket.getReuseAddress()) {
            socket2.close();
        }

// multicast, bind to port
        final UdpSocket socket3 = new UdpSocket(UDP_PORT, iam);
        socket3.receive(handler);
// multicast, bind to interface
        final UdpSocket socket4 = new UdpSocket(UDP_PORT, iam, iah);
        socket4.receive(handler);
        Thread.sleep(RECEIVER_DELAY); // delay for starting receivers

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                socket3.close();
                socket4.close();
                socket0.close();
                socket2.close();
                timer.cancel();
            }
        }, CLOSE_TIMEOUT);

        int count = 0;
        while (socket3.isReceiving()) {// && socket4.isReceiving()) {
//            UdpSocket.send(new byte[15], ia3, UDP_PORT);
            socket3.send(new byte[socket3.getBufLength() / 2]); // multicast
            socket4.send(new byte[socket4.getBufLength()]); // multicast
            if (socket0.isOpen()) {
                socket0.send(new byte[15]); // broadcast
            }
            if (socket2.isOpen()) {
                socket2.send(new byte[30]); // unicast, connected
            }
            Thread.sleep(SENDER_DELAY); // delay sending
            if (++count == 5) {
                log("\r\nMulticast sockets loopback enabled.\r\n");
                ((MulticastSocket) (socket3.getDatagramSocket())).setLoopbackMode(false);
                ((MulticastSocket) (socket4.getDatagramSocket())).setLoopbackMode(false);
            }
        }
    }
}
