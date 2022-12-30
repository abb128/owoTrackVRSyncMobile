package org.owoTrack;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;

class HandshakeFailException extends Exception {
    public HandshakeFailException(String s){
        super(s);
    }
}

public class Handshaker {
    public static class HandshakeResult {
        public boolean success;

        public boolean had_to_rediscover;
        public InetAddress server_address;
        public int port;

        HandshakeResult(){}

        public static Handshaker.HandshakeResult none(){
            Handshaker.HandshakeResult result = new Handshaker.HandshakeResult();
            result.success = false;

            return result;
        }

        public static Handshaker.HandshakeResult some(InetAddress srv, int port, boolean had_to_rediscover){
            Handshaker.HandshakeResult result = new Handshaker.HandshakeResult();
            result.success = true;
            result.server_address = srv;
            result.port = port;
            result.had_to_rediscover = had_to_rediscover;

            return result;
        }
    }

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

    public static void insert_slime_info(ByteBuffer buff) {
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

    public static HandshakeResult try_handshake(DatagramSocket socket, InetAddress ip, int port) throws HandshakeFailException {
        //if(ip.toString().endsWith(".255")) return true;

        byte[] buffer = new byte[64];
        DatagramPacket handshake_receive = new DatagramPacket(buffer, 64);


        int tries = 0;
        boolean tryBroadcast = false;
        while(true) {
            int len = 12;

            // if the user is running an old version of owoTrackVR driver,
            // recvfrom() will fail as the max packet length the old driver
            // supported was around 28 bytes. to maintain backwards
            // compatibility the slime extensions are not sent after a
            // certain number of failures
            boolean sendSlimeExtensions = (tries < 10);
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
            tryBroadcast = tryBroadcast || (tries > 4);
            if(tries > 12){
                throw new HandshakeFailException("Connection failed. Ensure IP and port are correct, that the server is running and not blocked by Windows Firewall (try changing your network type to private in Windows) or blocked by router, and that you're connected to the same network (you may need to disable Mobile Data)");
            }

            try {
                if(socket == null) throw new HandshakeFailException("Socket is null!");

                if(!tryBroadcast) {
                    socket.send(new DatagramPacket(handshake_buff.array(), len, ip, port));
                }else{
                    socket.disconnect();
                    socket.setBroadcast(true);
                    InetAddress broadcast_address = InetAddress.getByName("255.255.255.255");
                    socket.send(new DatagramPacket(handshake_buff.array(), len, broadcast_address, 6969));
                }

                try {
                    socket.setSoTimeout(250);
                    socket.receive(handshake_receive);

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

                    boolean had_to_rediscover = (handshake_receive.getAddress() != ip) || (handshake_receive.getPort() != port);
                    if(tryBroadcast && had_to_rediscover) {
                        socket.connect(handshake_receive.getAddress(), handshake_receive.getPort());
                        socket.setBroadcast(false);
                    }
                    return HandshakeResult.some(handshake_receive.getAddress(), handshake_receive.getPort(), had_to_rediscover);
                } catch (SocketTimeoutException e) {
                    continue;
                }
            } catch (PortUnreachableException e){
                //throw new HandshakeFailException("Port is unreachable. Ensure that you've entered the correct IP and port, that the server is running and that you're on the same wifi network as the computer.");
                tryBroadcast = true;
                continue;
            } catch (IOException e){
                throw new HandshakeFailException("Handshake IO exception: " + e.toString());
            }
        }
    }
}
