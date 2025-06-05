/**
 * UdpSocket BasicTest, MIT (c) 2025 miktim@mail.ru
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
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import org.miktim.udpsocket.UdpSocket;

public class BasicTest {

    static final int PORT = 9099;
    static final String INTF = "eth1"; // local interface name;

    static final String MU_ADDRESS = "224.0.1.191"; // iana unassigned multicast
//    static final String MC_ADDRESS = "224.0.0.1"; // iana All Systems on this Subnet
    static final String MC_ADDRESS = "FF09::114"; // iana private experiment
//    static final String MS_ADDRESS = "232.0.1.199"; //iana source-specific

    InetSocketAddress loopSoc; // loopback 127.0.0.1 socket
    InetSocketAddress hostSoc; // localhost
    InetSocketAddress bcastSoc; // broadcast 225.225.225.225 
    InetSocketAddress mcastSoc; // MC_ADDRESS socket
    InetSocketAddress freemcSoc; // MU_ADDRES socket
    InetSocketAddress wildSoc; // 0.0.0.0

    InetSocketAddress[] sockets;

    BasicTest() throws UnknownHostException, SocketException {

        loopSoc = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT);
        bcastSoc = new InetSocketAddress(InetAddress.getByName("255.255.255.255"), PORT);
        mcastSoc = new InetSocketAddress(InetAddress.getByName(MC_ADDRESS), PORT);
        freemcSoc = new InetSocketAddress(InetAddress.getByName(MU_ADDRESS), PORT);
        InetAddress hostAddr = getInet4Address(NetworkInterface.getByName(INTF));
        hostSoc = new InetSocketAddress(hostAddr, PORT);
        wildSoc = new InetSocketAddress(PORT);

        sockets = new InetSocketAddress[]{loopSoc, bcastSoc, mcastSoc, freemcSoc, hostSoc, wildSoc};
    }

    void log(Object msg) {
        System.out.println(String.valueOf(msg));
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

    UdpSocket.Handler handler = new UdpSocket.Handler() {
        @Override
        public void onStart(UdpSocket us) {
            log("onStart");
        }

        @Override
        public void onPacket(UdpSocket us, DatagramPacket dp) {
            log("onPacket: " + new String(Arrays.copyOfRange(dp.getData(), dp.getOffset(), dp.getLength())));
        }

        @Override
        public void onError(UdpSocket uc, Exception e) {
            log("onError:\n");
            e.printStackTrace();
        }

        @Override
        public void onClose(UdpSocket uc) {
            log("onClose");
        }
    };

    public static void main(String[] args) throws IOException, InterruptedException {
        (new BasicTest()).run();
    }

    void run() throws IOException, InterruptedException {
        log(format("UdpSocket %s basic test", UdpSocket.VERSION));
        if (!UdpSocket.isAvailable(PORT)) {
            log("\nPort unavailible: " + PORT);
            System.exit(1);
        }
        UdpSocket us;

        log("\nSend/receive with null multicast interface");
        us = new UdpSocket(freemcSoc.getAddress(), freemcSoc.getPort(), null);
        us.setLoopbackMode(false);// enable loopback
        log(us);
        us.receive(handler);
        us.send("Send/receive OK".getBytes());
        sleep(200);
        us.close();
        
        for (InetSocketAddress remote : sockets) {
            us = new UdpSocket(remote, NetworkInterface.getByName(INTF));
            us.bind();
            if (us.isMulticast()) {
                us.setLoopbackMode(false);// enable loopback
            }
            log("\n" + us);
            try {
                us.receive(handler);
                us.send("Send/receive OK".getBytes());
                sleep(200);
            } catch (IOException e) {
                log("Send failed:\n");
                e.printStackTrace();
                sleep(200);
            }
            us.close();
        }
        log("\nCompleted");
    }
}
