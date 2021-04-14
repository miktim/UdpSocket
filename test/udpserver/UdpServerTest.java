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
            public void onShutdown(UdpServer server) {
                log("Server stopped.");
            }

            @Override
            public void onDatagram(UdpServer server, DatagramPacket packet) {
                log("Receiver onPacket: size " + packet.getLength()
                + " from: " + packet.getAddress());
            }

            @Override
            public void onError(UdpServer server, Exception e) {
                log("Receiver onError: " + e);
            }

        };
        UdpServer sender = new UdpServer(UDP_PORT, InetAddress.getLocalHost(), handler);
        final UdpServer server = new UdpServer(UDP_PORT, InetAddress.getByName(UDP_ADDRESS), handler);
//        final UdpServer server = new UdpServer(UDP_PORT, null, handler);
        server.start();
        sender.start();
        sender.send(new byte[server.getDatagramLength()],InetAddress.getByName(UDP_ADDRESS));
        sender.send(new byte[server.getDatagramLength() * 2],InetAddress.getByName(UDP_ADDRESS));
        Thread.sleep(50);
        server.setDatagramLength(server.getDatagramLength() * 2);
        server.send(new byte[server.getDatagramLength()]);
        server.send(new byte[server.getDatagramLength() * 2]);

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                server.shutdown();
                sender.shutdown();
                timer.cancel();
            }
        }, SERVER_SHUTDOWN_TIMEOUT);

    }
}
