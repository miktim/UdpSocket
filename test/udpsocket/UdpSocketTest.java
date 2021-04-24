/*
 * UdpSocketTest, MIT (c) 2021 miktim@mail.ru
 */
//package udpsocket;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.Timer;
import java.util.TimerTask;
import org.miktim.udpsocket.UdpSocket;

public class UdpSocketTest {

    static final int UDP_PORT = 9090;
    static final int RECEIVER_TIMEOUT = 30000;
    static final int RECEIVER_DELAY = 100;
    
    static void log(String s) {
        System.out.println(s);
    }
    static String receiverId() {
        return "Receiver"+Thread.currentThread().getId();
    }
    public static void main(String[] args) throws Exception {

        UdpSocket.Handler handler = new UdpSocket.Handler() {
            @Override
            public void onStart(UdpSocket socket) {
                log(receiverId() + " started. " + socket.toString());
            }

            @Override
            public void onClose(UdpSocket socket) {
                log(receiverId() + " Socket closed.");
            }

            @Override
            public void onPacket(UdpSocket socket, DatagramPacket packet) {
                log(receiverId() +" onPacket: size " + packet.getLength()
                        + " to: " + packet.getAddress());
            }

            @Override
            public void onError(UdpSocket socket, Exception e) {
                log(receiverId()+ " onError: " + e);
            }

        };

        InetAddress ia0 = InetAddress.getByName("0.0.0.0");
        InetAddress ia1 = InetAddress.getLocalHost();
        InetAddress ia2 = InetAddress.getByName("localhost");
        InetAddress ia3 = InetAddress.getByName("224.0.0.1"); // all systems in this subnet

// broadcast        
        UdpSocket socket = new UdpSocket(UDP_PORT, handler);
        socket.start();
        Thread.sleep(RECEIVER_DELAY); // delay for starting receiver
        UdpSocket.send(new byte[12], ia0, UDP_PORT);
        socket.send(new byte[socket.getDatagramLength()]);
        Thread.sleep(RECEIVER_DELAY); // closing delay for receiving packets
        socket.close();

// unicast
        UdpSocket socket1 = new UdpSocket(UDP_PORT, ia1, ia2, handler);
        UdpSocket socket2 = new UdpSocket(UDP_PORT, ia2, ia1, handler);
        socket2.start();
        socket1.start();
        Thread.sleep(RECEIVER_DELAY); // delay for starting receivers

        socket1.send(new byte[socket2.getDatagramLength()]);
        socket1.send(new byte[socket2.getDatagramLength() * 2]);
        UdpSocket.send(new byte[13], ia2, UDP_PORT);
        UdpSocket.send(new byte[14], ia1, UDP_PORT);
        socket1.setDatagramLength(socket2.getDatagramLength() * 2);
        socket2.send(new byte[socket2.getDatagramLength()]);
        socket2.send(new byte[socket2.getDatagramLength() * 2]);

        Thread.sleep(RECEIVER_DELAY); // closing delay for receiving packets
        socket1.close();
        socket2.close();

// multicast, bound to port
        final UdpSocket socket3 = new UdpSocket(UDP_PORT, ia3, null, handler);
        socket3.start();
// multicast, bound to interface
        final UdpSocket socket4 = new UdpSocket(UDP_PORT, ia3, ia1, handler);
        socket4.start();
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
        while (socket3.isReceiving() && socket4.isReceiving()) {
            UdpSocket.send(new byte[15], ia3, UDP_PORT);
            socket3.send(new byte[socket.getDatagramLength()/2]);
            socket4.send(new byte[socket.getDatagramLength()]);
            Thread.sleep(2000); // sending delay
            if (++count == 10) {
                log("UdpSocket loopback enabled!");
                ((MulticastSocket) (socket3.getSocket())).setLoopbackMode(false);
                ((MulticastSocket) (socket4.getSocket())).setLoopbackMode(false);
            }
        }
    }
}
