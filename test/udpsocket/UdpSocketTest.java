/*
 * UdpSocketTest, MIT (c) 2021 miktim@mail.ru
 */

package udpsocket;
 
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import org.miktim.udpsocket.UdpSocket;

public class UdpSocketTest {

    static final int UDP_PORT = 9090;

    public static void log(String s) {
        System.out.println(s);
    }

    public static void send(byte[] buf, InetAddress addr, int port)
            throws IOException {
        if (addr.isMulticastAddress()) {
            (new MulticastSocket())
                    .send(new DatagramPacket(buf, buf.length, addr, port));
        } else {
            (new DatagramSocket())
                    .send(new DatagramPacket(buf, buf.length, addr, port));
        }
    }

    public static void main(String[] args) throws Exception {

        UdpSocket.Handler handler = new UdpSocket.Handler() {
            @Override
            public void onStart(UdpSocket server) {
                log("Receiver started. " + server.toString());
            }

            @Override
            public void onClose(UdpSocket server) {
                log("Socket closed.");
            }

            @Override
            public void onPacket(UdpSocket server, DatagramPacket packet) {
                log("Receiver onPacket: size " + packet.getLength()
                        + " to: " + packet.getAddress());
            }

            @Override
            public void onError(UdpSocket server, Exception e) {
                log("Receiver onError: " + e);
            }

        };
        
        InetAddress ia0 = InetAddress.getByName("0.0.0.0");
        InetAddress ia1 = InetAddress.getLocalHost(); 
        InetAddress ia2 = InetAddress.getByName("localhost");
        InetAddress ia3 = InetAddress.getByName("225.4.5.6");

// broadcast        
        UdpSocket socket = new UdpSocket(UDP_PORT,handler);
        socket.start();
        Thread.sleep(10);
        send(new byte[12], ia0, UDP_PORT);
        socket.send(new byte[socket.getDatagramLength()]);
        Thread.sleep(10);
        socket.close();

// multicast
        socket = new UdpSocket(UDP_PORT, ia3, null, handler);
        socket.start();
//        Thread.sleep(10);
        send(new byte[12], ia3, UDP_PORT);
        socket.send(new byte[socket.getDatagramLength()]);
        Thread.sleep(10);
        socket.close();
        
// multicast bound to interface
        socket = new UdpSocket(UDP_PORT, ia3, ia1, handler);
        socket.start();
//        Thread.sleep(10);
        send(new byte[12], ia3, UDP_PORT);
        socket.send(new byte[socket.getDatagramLength()]);
        Thread.sleep(10);
        socket.close();

// unicast
        UdpSocket socket1 = new UdpSocket(UDP_PORT, ia1, ia2, handler);
        UdpSocket socket2 = new UdpSocket(UDP_PORT, ia2, ia1, handler);
        socket2.start();
        socket1.start();
        socket1.send(new byte[socket2.getDatagramLength()]);
        socket1.send(new byte[socket2.getDatagramLength() * 2]);
        send(new byte[13],ia2,UDP_PORT);
        Thread.sleep(10);
        send(new byte[14],ia1,UDP_PORT);
        socket1.setDatagramLength(socket2.getDatagramLength() * 2);
        socket2.send(new byte[socket2.getDatagramLength()]);
        socket2.send(new byte[socket2.getDatagramLength() * 2]);

        Thread.sleep(10);
        socket1.close();
        socket2.close();
    }
}
