package org.owoTrackVRSync;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class Handshaker {
    public static boolean try_handshake(DatagramSocket socket) throws Exception {
        byte[] buffer = new byte[64];
        DatagramPacket handshake_receive = new DatagramPacket(buffer, 64);

        ByteBuffer handshake_buff = ByteBuffer.allocate(12);
        handshake_buff.putInt(3);
        handshake_buff.putLong(0);

        int tries = 0;
        while(true) {
            tries++;
            if(tries > 12){
                throw new Exception("Handshake timed out. Ensure that the IP address and port are correct, that the server is running and that you're connected to the same wifi network.");
            }
            try {
                socket.send(new DatagramPacket(handshake_buff.array(), 12));

                try {
                    socket.setSoTimeout(250);
                    socket.receive(handshake_receive);

                    if (buffer[0] != 3) {
                        throw new Exception("Handshake failed, the server did not respond correctly. Ensure everything is up-to-date and that the port is correct.");
                    }

                    String result = new String(
                            Arrays.copyOfRange(buffer, 1, 64),
                            "ASCII");

                    result = result.substring(0, result.indexOf(0));

                    if (!result.startsWith("Hey OVR =D")) {
                        throw new Exception("Handshake failed, the server did not respond correctly in the header. Ensure everything is up-to-date and that the port is correct");
                    }

                    int version = Integer.valueOf(result.substring(11));

                    if (version != UDPGyroProviderClient.CURRENT_VERSION) {
                        throw new Exception("Handshake failed, mismatching version"
                            + "\nServer version: " + String.valueOf(version)
                            + "\nClient version: " + String.valueOf(UDPGyroProviderClient.CURRENT_VERSION)
                            + "\nPlease make sure everything is up to date.");
                    }

                    return true;
                } catch (SocketTimeoutException e) {
                    continue;
                }
            } catch (PortUnreachableException e){
                throw new Exception("Port is unreachable. Ensure that you've entered the correct IP and port, that the driver is running and that you're on the same wifi network as the computer.");
            } catch (Exception e) {
                throw new Exception("Connect Handshake: " + e.toString());
            }
        }
    }
}
