/*
 * UdpSocketTest, MIT (c) 2021 miktim@mail.ru
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
    static final String MULTICAST_ADDRESS = "224.0.1.191"; // unused global
// delay for starting the receivers and completely receiving packets before closing
    static final int RECEIVER_DELAY = 100;
    static final int SEND_DELAY = 300; // send datagram delay
// test closure timeout
    static final int TEST_TIMEOUT = 10000;

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

    static void log(String s) {
        System.out.println(s);
    }

    static String socketId(UdpSocket s) {
        String sid = "Socket" + s.getId() + s.getInetAddress();
        return sid;
    }

    static int sent = 0;
    static int received = 0;
    static int errors = 0;

    static void testSocket(UdpSocket socket) throws IOException  {
        log(socket.toString());
        socket.receive(handler);
        received = 0;
        errors = 0;
        byte[] payload = new byte[308];
        for (sent = 0; sent < 5; sent++) {
            try {
                UdpSocket.send(payload, payload.length, socket.getPort(), socket.getInetAddress());
                log(String.format("snd: %d %s:%d",
                        payload.length,
                        socket.getInetAddress().toString(),
                        socket.getPort()));
                Thread.sleep(RECEIVER_DELAY);
            } catch (IOException ex) {
                errors++;
                log("err: " + ex.getMessage());
            } catch (InterruptedException ex) {
            }
        }
        log(String.format("Packets sent: %d received: %d Errors: %d\r\n", sent, received, errors));
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
//        InetAddress iam = InetAddress.getByName("224.0.0.1");
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

//        socket1 = new UdpSocket(UDP_PORT, iar, iah);
//        socket1.setBroadcast(true);/??? send unicast thru broadcast
// broadcast 
        testSocket(new UdpSocket(UDP_PORT));
        testSocket(new UdpSocket(UDP_PORT, iab, iah));

// send to loopback
        testSocket(new UdpSocket(UDP_PORT, ial));

// send to myself        
        testSocket(new UdpSocket(UDP_PORT,iah)); 
// remote
        testSocket(new UdpSocket(UDP_PORT,iar,iah));
// send to multicast
        UdpSocket socket = new UdpSocket(UDP_PORT,iam,iah);
        // enable loopback
        ((MulticastSocket)socket.getDatagramSocket()).setLoopbackMode(true); // true - disable
        testSocket(socket);
        
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                timer.cancel();
                System.exit(0);
            }
        }, TEST_TIMEOUT);
    }
}
