/**
 * UdpSocket ReceiverTest, MIT (c) 2026 miktim@mail.ru
 */
package udpsocket;

import java.io.IOException;
import static java.lang.String.format;
import static java.lang.Thread.sleep;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Enumeration;
import org.miktim.udpsocket.UdpSocket;

public class ReceiverTest {

    static final int PORT = 9099;
    static final String INTF = "eth0"; // local interface name;
    static final int DELAY = 500; // millis, sleep time
    static final boolean DISABLE_LOOPBACK = false;
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

    static UdpSocket.Handler handler = new UdpSocket.Handler() {
        InetSocketAddress groupSocket;

        @Override
        public void onStart(UdpSocket us) {
            log("Receiver started\r\n" + us);
            try {
                groupSocket = new InetSocketAddress(
                        InetAddress.getByName("224.0.0.1"), PORT);
                us.joinGroup(groupSocket, null);
                log("Multicast group: " + groupSocket);
            } catch (IOException ex) {
            }
        }

        @Override
        public void onPacket(UdpSocket us, DatagramPacket dp) {
            log(format("received \"%s\" from %s",
                    new String(Arrays.copyOfRange(dp.getData(), dp.getOffset(), dp.getLength())),
                    dp.getSocketAddress().toString()));
        }

        @Override
        public void onError(UdpSocket us, Exception e) {
            log("onError:\n");
            e.printStackTrace();
        }

        @Override
        public void onClose(UdpSocket us) {
            try {
                us.leaveGroup(groupSocket, null);
            } catch (IOException ex) {
            }
            log("Receiver closed");
        }
    };

    public static void main(String[] args) throws IOException, InterruptedException {
        log(format("UdpSocket %s receiver test\r\n", UdpSocket.VERSION));
        if (!UdpSocket.isAvailable(PORT)) {
            log(format("\nPort %d unavailible!\n\n", PORT));
            System.exit(1);
        }
        UdpSocket receiver
                = new UdpSocket(null, PORT, null);
//        receiver.setLoopbackMode(DISABLE_LOOPBACK);
        receiver.receive(handler);
        sleep(DELAY);
        UdpSocket sender
                = new UdpSocket(null, 0, null);
        sender.setLoopbackMode(DISABLE_LOOPBACK);
        log("\r\nSender:\r\n" + sender + "\r\n");
        for (String address : ADDRESSES) {
            InetSocketAddress dest = new InetSocketAddress(
                    InetAddress.getByName(address), PORT);
            byte[] buf = ("to " + dest).getBytes();
            DatagramPacket dp = new DatagramPacket(
                    buf, buf.length, dest);
            sender.send(dp);
            log(format("%d bytes sent to %s", buf.length, dest));
            sleep(DELAY);
        }
        sender.close();
        receiver.close();
        log("\r\nCompleted");
    }
}
