/**
 * UdpSocket BasicTest, MIT (c) 2025-2026 miktim@mail.ru
 */
package udpsocket;

import java.io.IOException;
import static java.lang.String.format;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import org.miktim.udpsocket.UdpSocket;

public class BasicTest {

    static final int PORT = 9099;
    static final String INTF = "eth0"; // local interface name;
    static final int DELAY = 500; // millis, sleep time
    static final boolean DISABLE_LOOPBACK = true;
    static final String[] ADDRESSES = new String[]{
        null, // INTF IPv4 address
        "localhost", // host address
        "0.0.0.0", // wild
        "127.0.0.1", // loopback 127.0.0.1
        "225.225.225.225", // broadcast
        "224.0.1.191", // iana unassigned multicast
        "224.0.0.1", // iana All Systems on this Subnet
        "FF09::114", // iana private experiment
        "232.0.1.199", //iana source-specific
        null
    };

    static {
        try {
            ADDRESSES[0] = getInet4Address(NetworkInterface.getByName(INTF))
                    .getHostAddress();
        } catch (SocketException ex) {
        }
    }

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

    static void log(Object msg) {
        System.out.println(msg);
    }

    BasicTest() throws UnknownHostException, SocketException {
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        (new BasicTest()).run();
    }

    void run() throws IOException, InterruptedException {
        log(format("UdpSocket %s basic test", UdpSocket.VERSION));
        if (!UdpSocket.isAvailable(PORT)) {
            log(format("\nPort %d unavailible!\n\n", PORT));
            System.exit(1);
        }
        UdpSocket us;

        for (String address : ADDRESSES) {
            us = new UdpSocket(
                    InetAddress.getByName(address), PORT, INTF);
            us.getLocal();
            us.bind();
            us.setLoopbackMode(DISABLE_LOOPBACK);//
            us.setSoTimeout(DELAY);
            log("\n" + us);
            try {
                us.send("Send/receive OK".getBytes());
                DatagramPacket dp = us.receive();
                log(new String(dp.getData(), 0, dp.getLength()));
                us.close();
            } catch (SocketTimeoutException e) {
                log("Nothing received!");
            } catch (IOException e) {
                log(e);
            }
            us.close();
//            sleep(DELAY);
        }
        log("\nCompleted");
    }
}
