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

    static final int UDP_PORT = 9099;
// delay for starting the receivers and completely receiving packets before closing
    static final int RECEIVER_DELAY = 100;
// timeouted multicast receiving/sending
    static final int RECEIVER_TIMEOUT = 30000;
    static final String EXTERNAL_ADDRESS = "192.168.1.105"; // not used

    static void log(String s) {
        System.out.println(s);
    }

    static String socketId(UdpSocket s) {
        String sid = "Socket" + s.getId();
        try {
            sid += ":" + InetAddress.getLocalHost();
        } catch (IOException e) {
        }
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
                log(socketId(socket) + " onPacket: size " + packet.getLength()
                        + " to: " + packet.getAddress());
            }

            @Override
            public void onError(UdpSocket socket, Exception e) {
                log(socketId(socket) + " onError: " + e);
            }

        };

        InetAddress ia0 = InetAddress.getByName("0.0.0.0");
        InetAddress ia1 = InetAddress.getLocalHost();
        InetAddress ia2 = InetAddress.getByName("localhost");
        InetAddress ia3 = InetAddress.getByName("224.0.0.1"); // all systems in this subnet
        InetAddress iae = InetAddress.getByName(EXTERNAL_ADDRESS);

        log("UdpSocket test. " + System.getProperty("os.name"));
        log("");

// broadcast        
        UdpSocket socket = new UdpSocket(UDP_PORT);
        socket.receive(handler);
        Thread.sleep(RECEIVER_DELAY); // delay for starting receiver
        try {
            UdpSocket.send(new byte[socket.getBufferLength()/2], ia0, UDP_PORT);
            socket.send(new byte[socket.getBufferLength()]);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Thread.sleep(RECEIVER_DELAY); // closing delay for receiving packets
        socket.close();

// unicast
        UdpSocket socket1 = new UdpSocket(UDP_PORT, ia1, ia2); // bind to localhost connect to host
        socket1.close(); // check reopen socket
        socket1 = new UdpSocket(UDP_PORT, ia1, ia2);
        UdpSocket socket2 = new UdpSocket(UDP_PORT, ia2, ia1); // bind to host connect to localhost
        socket2.receive(handler);
        socket1.receive(handler);
        Thread.sleep(RECEIVER_DELAY); // delay for starting receivers
        try {
            UdpSocket.send(new byte[socket1.getBufferLength() / 3], ia1, UDP_PORT);
            UdpSocket.send(new byte[socket1.getBufferLength() / 3], ia2, UDP_PORT);
            socket1.send(new byte[socket1.getBufferLength() / 2]);
            socket1.send(new byte[socket1.getBufferLength() / 2]);
            socket2.send(new byte[socket2.getBufferLength() * 2]);
            socket1.setBufferLength(socket1.getBufferLength() * 2);
            socket2.send(new byte[socket2.getBufferLength() * 2]);
        } catch (IOException e) {
            e.printStackTrace();
        }
        Thread.sleep(RECEIVER_DELAY); // closing delay for receiving packets
        socket1.close();
        socket2.close();

// multicast, bind to port
        final UdpSocket socket3 = new UdpSocket(UDP_PORT, ia3);
        socket3.receive(handler);
// multicast, bind to interface
        final UdpSocket socket4 = new UdpSocket(UDP_PORT, ia3, ia1);
        socket4.receive(handler);
        Thread.sleep(RECEIVER_DELAY); // delay for starting receivers

        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                socket3.close();
                socket4.close();
                timer.cancel();
            }
        }, RECEIVER_TIMEOUT);

        int count = 0;
        while (socket3.isReceiving()) {// && socket4.isReceiving()) {
            UdpSocket.send(new byte[15], ia3, UDP_PORT);
            socket3.send(new byte[socket3.getBufferLength() / 2]);
            socket4.send(new byte[socket4.getBufferLength()]);
            Thread.sleep(2000); // sending delay
            if (++count == 10) {
                log("Multicast socket loopback enabled!");
                ((MulticastSocket) (socket3.getSocket())).setLoopbackMode(false);
                ((MulticastSocket) (socket4.getSocket())).setLoopbackMode(false);
            }
        }
    }
}
