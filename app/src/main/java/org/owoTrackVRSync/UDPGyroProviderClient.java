package org.owoTrackVRSync;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class UDPGyroProviderClient {
    private int port_v;
    private InetAddress ip_addr;

    private DatagramSocket socket;

    private boolean did_handshake_succeed;

    private AppStatus status;

    Service service;

    final int CURRENT_VERSION = 5;

    long last_heartbeat_time = 0;

    UDPGyroProviderClient(AppStatus status_v, Service s){
        status = status_v;

        service = s;

        try {
            socket = new DatagramSocket();
        } catch (Exception e) {
            status.update("A socket exception has occurred");
            e.printStackTrace();
            return;
        }
    }

    public void setTgt(String ip, int port){
        port_v = port;
        try {
            ip_addr = InetAddress.getByName(ip);
        } catch (Exception e) {
            status.update("Set IP: " + e.toString());
            e.printStackTrace();
            ip_addr = null;
            return;
        }
    }

    public boolean try_handshake(){
        byte[] buffer = new byte[64];
        DatagramPacket handshake_receive = new DatagramPacket(buffer, 64);

        ByteBuffer handshake_buff = ByteBuffer.allocate(12);
        handshake_buff.putInt(3);
        handshake_buff.putLong(0);

        int tries = 0;
        while(true) {
            tries++;
            if(tries > 12){
                status.update("Handshake timed out. Ensure that the IP address and port are correct.");
                return false;
            }
            try {
                socket.send(new DatagramPacket(handshake_buff.array(), 12));

                try {
                    socket.setSoTimeout(250);
                    socket.receive(handshake_receive);

                    if(buffer[0] != 3){
                        status.update("Handshake failed, the server did not respond correctly. Ensure everything is up-to-date and that the port is correct.");
                        return false;
                    }

                    String result = new String(
                            Arrays.copyOfRange(buffer, 1, 64),
                            "ASCII");

                    result = result.substring(0, result.indexOf(0));

                    if(!result.startsWith("Hey OVR =D")){
                        status.update("Handshake failed, the server did not respond correctly in the header. Ensure everything is up-to-date and that the port is correct");
                        return false;
                    }

                    int version = Integer.valueOf(result.substring(11));

                    if(version != CURRENT_VERSION){
                        status.update("Handshake failed, mismatching version");
                        status.update("Server version: " + String.valueOf(version));
                        status.update("Client version: " + String.valueOf(CURRENT_VERSION));
                        status.update("Please make sure everything is up to date.");
                        return false;
                    }

                    did_handshake_succeed = true;
                    return true;
                } catch(SocketTimeoutException e){
                    continue;
                }
            } catch (Exception e) {
                status.update("Connect Handshake: " + e.toString());
                e.printStackTrace();
                return false;
            }
        }
    }

    private boolean isConnected = false;

    private Runnable on_connection_death;

    public boolean connect(Runnable on_death){
        on_connection_death = on_death;
        socket.disconnect();
        packet_id = 0;
        did_handshake_succeed = false;
        if(ip_addr == null){
            return false;
        }

        status.update("Attempting connection...");
        try {
            socket.connect(new InetSocketAddress(ip_addr, port_v));
        } catch (Exception e) {
            status.update("Connect: " + e.toString());
            e.printStackTrace();
            return false;
        }

        if(!try_handshake()){
            return false;
        }
        did_handshake_succeed = true;
        isConnected = socket.isConnected();
        if(isConnected){
            last_heartbeat_time = System.currentTimeMillis();
            status.update("Connection succeeded!");
            Thread task = new Thread(listen_task);
            task.start();
        }
        return isConnected;
    }

    public boolean isConnected(){
        if(!(isConnected && did_handshake_succeed))
            return false;

        long time = System.currentTimeMillis();

        long time_diff = time - last_heartbeat_time;

        if(time_diff > (10*1000)){
            status.update("Connection with the server has been lost!!!");
            on_connection_death.run();
            isConnected = false;
            did_handshake_succeed = false;
            return false;
        }

        return true;
    }


    Runnable listen_task = new Runnable(){
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void run() {
            System.out.println("handle intent");
            byte[] buffer = new byte[64];
            while (true) {
                try {
                    socket.setSoTimeout(1000);
                    DatagramPacket p = new DatagramPacket(buffer, 64);
                    try {
                        socket.receive(p);
                    } catch (SocketTimeoutException e) {
                        continue;
                    }

                    last_heartbeat_time = System.currentTimeMillis();

                    ByteBuffer buff = ByteBuffer.wrap(buffer, 0, 64);
                    buff.order(ByteOrder.LITTLE_ENDIAN);
                    buff.position(0);
                    int msg_type = buff.getInt();

                    if (msg_type == 1) {
                        // just heartbeat
                    }else if(msg_type == 2){
                        // vibrate
                        float duration_s = buff.getFloat();
                        float frequency = buff.getFloat();
                        float amplitude = buff.getFloat();

                        Vibrator v = (Vibrator) service.getSystemService(Context.VIBRATOR_SERVICE);
                        v.vibrate(VibrationEffect.createOneShot((long)(duration_s * 1000), (int)(amplitude * 255)));

                    } else {
                        System.out.printf("Unknown message type %d\n", msg_type);
                    }
                } catch (Exception e) {
                    status.update("Consume: " + e.toString());
                    e.printStackTrace();
                    continue;
                }
            }
        }
    };

    private long packet_id = 0;

    private void provide_floats(float[] floats, int len, int msg_type) {
        if (!isConnected()) {
            return;
        }


        int bytes = 12 + len * 4; // 12b header (int + long)  + floats (4b each)

        ByteBuffer buff = ByteBuffer.allocate(bytes);
        buff.putInt(msg_type);
        buff.putLong(packet_id);

        for (int i = 0; i < len; i++) {
            buff.putFloat(floats[i]);
        }

        DatagramPacket packet = new DatagramPacket(buff.array(), bytes);
        try {
            socket.send(packet);
        } catch (Exception e) {
            status.update("Packet Send: " + e.toString());
            e.printStackTrace();
            did_handshake_succeed = false;
            isConnected = false;
            return;
        }
        packet_id++;

    }

    public void provide_gyro(float[] gyro_v){
        provide_floats(gyro_v,  3, 2);
    }

    public void provide_rot(float[] rot_q){
        provide_floats(rot_q,  4, 1);
    }

    public void provide_accel(float[] accel){
        provide_floats(accel,  3, 4);
    }

}
