/*
 * UdpServerTest, MIT (c) 2021 miktim@mail.ru
 */
package udpserver;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.Timer;
import java.util.TimerTask;
import org.miktim.udpserver.UdpServer;

public class UdpServerTest {

    static final int UDP_PORT = 9090;
    static final String UDP_ADDRESS = "localhost";
    static final int SERVER_SHUTDOWN_TIMEOUT = 2000;

    public static void log(String s) {
        System.out.println(s);
    }

    public static void main(String[] args) throws Exception {

        UdpServer.Handler handler = new UdpServer.Handler() {
            @Override
            public void onStart(UdpServer server) {
                log("Server started. " + server.toString());
            }

            @Override
            public void onStop(UdpServer server) {
                log("Server stopped.");
            }

            @Override
            public void onUdpPacket(UdpServer server, DatagramPacket packet) {
                log("Receiver onPacket: size " + packet.getLength());
            }

            @Override
            public void onUdpError(UdpServer server, Exception e) {
                log("Receiver onError: " + e);
            }

        };
        UdpServer sender = new UdpServer(UDP_PORT, InetAddress.getByName(UDP_ADDRESS), null);
        final UdpServer server = new UdpServer(UDP_PORT, InetAddress.getByName(UDP_ADDRESS), handler);
        server.start();
        sender.send(new byte[server.getDatagramLength()]);
        sender.send(new byte[server.getDatagramLength() * 2]);
        Thread.sleep(50);
        server.setDatagramLength(server.getDatagramLength() * 2);
        sender.send(new byte[server.getDatagramLength()]);
        sender.send(new byte[server.getDatagramLength() * 2]);

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                server.close();
                timer.cancel();
            }
        }, SERVER_SHUTDOWN_TIMEOUT);

    }
}
