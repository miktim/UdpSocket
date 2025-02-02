/*
 * UdpSocketTest, MIT (c) 2021-2025 miktim@mail.ru
 * Enable the UDP_PORT in your firewall
 * Linux: internal, external, public zones
 */
//package udpsocket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import org.miktim.udpsocket.UdpSocket;

public class UdpSocketTest {

    static final int UDP_PORT = 9099; // IANA registry: unused
    static final String REMOTE_ADDRESS = "192.168.0.105";
    static final String MULTICAST_ADDRESS = "224.0.1.191"; // IANA registry: unused global
    static final int SEND_DELAY = 300; // send datagram delay millis
// test closure timeout millis
    static final int TEST_TIMEOUT = 20000;

    static InetAddress getInet4Address(NetworkInterface ni) {
        if (ni == null) {
            return null;
        }
        Enumeration<InetAddress> iaEnum = ni.getInetAddresses();
        while (iaEnum.hasMoreElements()) {
            InetAddress ia = iaEnum.nextElement();
            if (ia instanceof Inet4Address) {
                return ia;
            }
        }
        return null;
    }

    public static InetAddress getLocalHost() {
        try {
            Enumeration<NetworkInterface> niEnum = NetworkInterface.getNetworkInterfaces();
            while (niEnum.hasMoreElements()) {
                NetworkInterface ni = niEnum.nextElement();
                InetAddress ia = getInet4Address(ni);
                if (!(ia == null || ia.isLoopbackAddress())) {
                    return ia;
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    static void log(Object obj) {
        System.out.println(String.valueOf(obj));
    }

    static void logOk(boolean ok) {
        log(ok ? "Ok" : "Something wrong...");
    }

    static int sent = 0;
    static int received = 0;
    static int errors = 0;

    static void testSocket(UdpSocket socket) throws IOException {
        testSocket(socket, 5);
    }

    static void testSocket(UdpSocket socket, int count) throws IOException {
        log("\r\n" + socket.toString());
        socket.receive(handler);
        received = 0;
        errors = 0;
        byte[] payload = new byte[308];
        for (sent = 0; sent < count; sent++) {
            try {
                UdpSocket.send(payload, payload.length, socket.getPort(), socket.getInetAddress());
                log(String.format("snt: %d %s:%d",
                        payload.length,
                        socket.getInetAddress().toString(),
                        socket.getPort()));
                Thread.sleep(SEND_DELAY);
            } catch (IOException ex) {
                errors++;
                log("err: " + ex.getMessage());
            } catch (InterruptedException ex) {
            }
        }
        log(String.format("Packets sent: %d received: %d Errors: %d", sent, received, errors));
        socket.close();
    }

    static UdpSocket.Handler handler = new UdpSocket.Handler() {
        @Override
        public void onStart(UdpSocket socket) {
        }

        @Override
        public void onClose(UdpSocket socket) {
        }

        @Override
        public void onPacket(UdpSocket socket, DatagramPacket packet) {
            received++;
            log(String.format("rcv: %d %s:%d",
                    packet.getLength(),
                    packet.getAddress().toString(),
                    packet.getPort()));
            if (packet.getAddress().equals(REMOTE_ADDRESS)) {
                try { // echo packet
                    socket.send(packet.getData(), packet.getLength());
                } catch (IOException ex) {
                    errors++;
                    log("err: " + ex);
                }
            }
        }

        @Override
        public void onError(UdpSocket socket, Exception e) {
            errors++;
            log("err: " + e);
        }
    };

    public static void main(String[] args) throws Exception {

        InetAddress ias = InetAddress.getByName("0.0.0.0");
        InetAddress iab = InetAddress.getByName("255.255.255.255");
//        InetAddress iah = InetAddress.getLocalHost();
        InetAddress iah = getLocalHost();
        InetAddress ial = InetAddress.getByName("localhost");
        InetAddress iam = InetAddress.getByName(MULTICAST_ADDRESS);
        InetAddress iar = InetAddress.getByName(REMOTE_ADDRESS);

        log("UdpSocket test.");
        log("UDP port: " + UDP_PORT);
        log("loopback: " + ial.toString());
        log("host: " + iah.toString());
        log("broadcast: " + iab.toString());
        log("multicast: " + iam.toString());
        log("remote: " + iar.toString());
        log("special: " + ias.toString());
        log("Test timeout: " + TEST_TIMEOUT);

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                log("Test terminated. Timeout.");
                timer.cancel();
                System.exit(0);
            }
        }, TEST_TIMEOUT);

        if (!UdpSocket.isAvailable(UDP_PORT)) {
            log("\r\nUDP port " + UDP_PORT + " is unavailable! Exit.\r\n");
            System.exit(1);
        } else {
            log("\r\nUDP port " + UDP_PORT + " is available.");
        }

        try {
            UdpSocket socket1 = new UdpSocket(UDP_PORT, iar, iah);
            UdpSocket socket2 = new UdpSocket(UDP_PORT, iar, iah);
            socket1.close();
            socket2.close();
        } catch (IOException e) {
            log("Reused address failed. Exit.\r\n");
            System.exit(1);
        }
        UdpSocket socket1 = new UdpSocket(UDP_PORT, iar, iah);
        try {
            socket1.setReuseAddress(false);
            UdpSocket socket2 = new UdpSocket(UDP_PORT, iar, iah);
            socket1.close();
            socket2.close();
        } catch (IOException e) {
            socket1.close();
            log("Reused address Ok.\r\n");
        }

// test binding socket & autocloseable        
        try (UdpSocket soc = new UdpSocket(UDP_PORT, iam)) {
            if (!(soc.getDatagramSocket().isBound())) {
                log("UdpSocket is not bound! Failed.");
                System.exit(1);
            }
            soc.setReuseAddress(false);
        }
        try {
            socket1 = new UdpSocket(UDP_PORT, iam);
        } catch (IOException ex) {
            log("AutoCloseable failed.");
            System.exit(1);
        }
        socket1.close();

        UdpSocket.send(new byte[1], 1, UDP_PORT, iam);

//        socket1 = new UdpSocket(UDP_PORT, iar, iah);
//        socket1.setBroadcast(true);/??? send unicast thru broadcast
// broadcast 
        UdpSocket socket0 = new UdpSocket(UDP_PORT);
        UdpSocket.send(new byte[64], 64, UDP_PORT, iab);
        testSocket(socket0); // test closes socket
        logOk(sent == 5 && received == 6);

        socket0 = new UdpSocket(UDP_PORT, iab, iah);
        UdpSocket.send(new byte[64], 64, UDP_PORT, iab, iah);
        testSocket(socket0);
        logOk(sent == 5 && received == 0);

// send to loopback
        testSocket(new UdpSocket(UDP_PORT, ial));
        logOk(sent == 5 && received == 5);

// send to myself (echo)       
        testSocket(new UdpSocket(UDP_PORT, iah));
        logOk(sent == 5 && received == 5);
// remote echo
        if (iar.isReachable(200)) {
            log(String.format("\r\nEcho host %s is reachable.", iar.toString()));
            testSocket(new UdpSocket(UDP_PORT, iar, iah));
            logOk(received > 0);
        } else {
            log(String.format("\r\nEcho host %s is unreachable.", iar.toString()));
        }
// send to multicast
        UdpSocket socket = new UdpSocket(UDP_PORT, iam);
        // enable loopback
        ((MulticastSocket) socket.getDatagramSocket()).setLoopbackMode(false); // true - disable
        testSocket(socket, 25);
        log(received > 0?"Ok":"There is no one in the group");
        timer.cancel();
        log("Complete");
    }
}
