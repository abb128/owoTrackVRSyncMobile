package org.owoTrack;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;

class HandshakeSuccessWithWarningException extends Exception {
    public HandshakeSuccessWithWarningException(String s) {
        super(s);
    }
}

class HandshakeFailException extends Exception {
    public HandshakeFailException(String s){
        super(s);
    }
}

public class Handshaker {

    // Android doesn't allow getting MAC address anymore, so we fake one
    private static byte[] pseudo_mac = new byte[]{0, 69, 0, 0, 0, 0};

    public static void setMac(long from){
        ByteBuffer from_b = ByteBuffer.allocate(Long.BYTES);
        from_b.putLong(from);

        byte[] bytes = from_b.array();

        for(int i=0; i<Math.min(bytes.length, pseudo_mac.length); i++){
            pseudo_mac[i] = bytes[i];
        }
    }


    private static byte[] getMac(){
        return pseudo_mac;
    }

    private static void insert_slime_info(ByteBuffer buff) {
        final int boardType = 0;
        final int imuType = 0;
        final int mcuType = 0;

        final int[] imuInfo = {0, 0, 0};

        final int firmwareBuild = 8;

        final byte[] firmware = {'o', 'w', 'o', 'T', 'r', 'a', 'c', 'k', '8'}; // 9 bytes

        buff.putInt(boardType); // 4 bytes
        buff.putInt(imuType);   // 4 bytes
        buff.putInt(mcuType);   // 4 bytes

        // 4 * 3 = 12 bytes
        for(int info : imuInfo) buff.putInt(info);

        buff.putInt(firmwareBuild); // 4 bytes

        buff.put((byte)firmware.length); // 1 bytes
        buff.put(firmware);

        byte[] mac = getMac();
        assert(mac.length == 6);
        buff.put(mac); // 6 bytes

        buff.put((byte)0xFF); // top it off
    }

    public static boolean try_handshake(DatagramSocket socket, boolean strict, InetAddress ip, int port) throws HandshakeSuccessWithWarningException, HandshakeFailException {
        //if(ip.toString().endsWith(".255")) return true;

        byte[] buffer = new byte[64];
        DatagramPacket handshake_receive = new DatagramPacket(buffer, 64);


        int tries = 0;
        while(true) {
            int len = 12;

            // if the user is running an old version of owoTrackVR driver,
            // recvfrom() will fail as the max packet length the old driver
            // supported was around 28 bytes. to maintain backwards
            // compatibility the slime extensions are not sent after a
            // certain number of failures
            boolean sendSlimeExtensions = (tries < 7);
            if(sendSlimeExtensions) len += 36 + 9;

            ByteBuffer handshake_buff = ByteBuffer.allocate(len);
            handshake_buff.putInt(3);
            handshake_buff.putLong(0);

            if(sendSlimeExtensions) {
                try {
                    insert_slime_info(handshake_buff); // 36 extra bytes
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            tries++;
            if(tries > 12){
                throw new HandshakeFailException("Connection timed out. Ensure IP and port are correct, that the server is running and not blocked by Windows Firewall (try changing your network type to private in Windows) or blocked by router, and that you're connected to the same network (you may need to disable Mobile Data)");
            }
            try {
                if(socket == null) throw new HandshakeFailException("Socket is null!");
                socket.send(new DatagramPacket(handshake_buff.array(), len, ip, port));

                try {
                    socket.setSoTimeout(250);
                    socket.receive(handshake_receive);

                    if(!strict) return true;

                    if (buffer[0] != 3) {
                        throw new HandshakeFailException("Handshake failed, the server did not respond correctly. Ensure everything is up-to-date and that the port is correct.");
                    }

                    String result = new String(
                            Arrays.copyOfRange(buffer, 1, 64),
                            "ASCII");

                    result = result.substring(0, result.indexOf(0));

                    if (!result.startsWith("Hey OVR =D")) {
                        throw new HandshakeFailException("Handshake failed, the server did not respond correctly in the header. Ensure everything is up-to-date and that the port is correct");
                    }

                    int version = -1;
                    try{
                        version = Integer.parseInt(result.substring(11));
                    }catch(NumberFormatException e){
                        throw new HandshakeFailException("Handshake failed, server did not send an int");
                    }

                    if (version != UDPGyroProviderClient.CURRENT_VERSION) {
                        throw new HandshakeFailException("Handshake failed, mismatching version"
                            + "\nServer version: " + String.valueOf(version)
                            + "\nClient version: " + String.valueOf(UDPGyroProviderClient.CURRENT_VERSION)
                            + "\nPlease make sure everything is up to date.");
                    }

                    if(!sendSlimeExtensions){
                        throw new HandshakeSuccessWithWarningException("Your server appears out-of-date with no non-fatal support for longer packet lengths, please update it");
                    }

                    return true;
                } catch (SocketTimeoutException e) {
                    continue;
                }
            } catch (PortUnreachableException e){
                throw new HandshakeFailException("Port is unreachable. Ensure that you've entered the correct IP and port, that the server is running and that you're on the same wifi network as the computer.");
            } catch (IOException e){
                throw new HandshakeFailException("Handshake IO exception: " + e.toString());
            }
        }
    }
}
