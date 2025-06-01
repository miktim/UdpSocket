/**
 * UdpSocket BasicTest, MIT (c) 2025 miktim@mail.ru
 */

package udpsocket;

import java.io.IOException;
import static java.lang.Thread.sleep;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Arrays;
import org.miktim.udpsocket.UdpSocket;

public class BasicTest {

    static final int PORT = 9099;
    static final String INTF = "eth1"; // local interface name;

    static final String HT_ADDRESS = "192.168.0.104"; // host
    static final String MS_ADDRESS = "224.0.1.191"; // iana unassigned multicast
//    static final String MC_ADDRESS = "224.0.0.1"; // iana All Systems on this Subnet
    static final String MC_ADDRESS = "FF09::114"; // iana private experiment
//    static final String MS_ADDRESS = "232.0.1.199"; //iana source-specific

    InetSocketAddress loopSoc; // loopback 127.0.0.1 socket
    InetSocketAddress hostSoc;
    InetSocketAddress bcastSoc; // 225.225.225.225 
    InetSocketAddress mcastSoc; // MC_ADDRESS socket
    InetSocketAddress specmcSoc; // MS_ADDRES socket
    InetSocketAddress[] sockets;

    BasicTest() throws UnknownHostException {

        loopSoc = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT);
        bcastSoc = new InetSocketAddress(InetAddress.getByName("255.255.255.255"), PORT);
        mcastSoc = new InetSocketAddress(InetAddress.getByName(MC_ADDRESS), PORT);
        specmcSoc = new InetSocketAddress(InetAddress.getByName(MS_ADDRESS), PORT);
        hostSoc = new InetSocketAddress(InetAddress.getByName(HT_ADDRESS), PORT);
        sockets = new InetSocketAddress[]{loopSoc, hostSoc,bcastSoc, mcastSoc, specmcSoc};
    }

    void log(Object msg) {
        System.out.println(String.valueOf(msg));
    }
    UdpSocket.Handler handler = new UdpSocket.Handler() {
        @Override
        public void onStart(UdpSocket us) {
            log("onStart");
        }

        @Override
        public void onPacket(UdpSocket us, DatagramPacket dp) {
            log("onPacket: " + new String(Arrays.copyOfRange(dp.getData(),dp.getOffset(),dp.getLength())));
        }

        @Override
        public void onError(UdpSocket uc, Exception e) {
            log("onError:");
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
        if (!UdpSocket.isAvailable(PORT)) {
            log("Port unavailible: " + PORT);
            System.exit(1);
        }
        UdpSocket us;
        for (InetSocketAddress remote : sockets) {
            us = new UdpSocket(remote, NetworkInterface.getByName(INTF));
            if(us.isMulticast()) {
                us.setLoopbackMode(false);//.join();
 //               us.setTimeToLive(10);
            }
            log("\n"+us);
            try {
                us.receive(handler);
                us.send("Send/receive OK".getBytes());
                sleep(200);
            } catch (IOException e) {
                log(us.getRemote().getAddress()+" "+ e.getClass());
            }
            us.close();
        }
    }
}
