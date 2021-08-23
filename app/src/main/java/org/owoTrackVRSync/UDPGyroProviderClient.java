package org.owoTrackVRSync;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.BatteryManager;
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
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Date;

public class UDPGyroProviderClient {
    private int port_v;
    private InetAddress ip_addr;

    private DatagramSocket socket;

    private boolean did_handshake_succeed;

    private AppStatus status;

    Service service;
    GyroListener listener;

    public final static int CURRENT_VERSION = 5;

    long last_packetsend_time = 0;
    long last_batterysend_time = 0;
    long num_packetsend = 0;

    long last_heartbeat_time = 0;

    static long last_kill_time = 0;

    boolean handshake_required = true;

    // from https://stackoverflow.com/questions/3291655/get-battery-level-and-state-in-android
    public static int getBatteryPercentage(Context context) {
        if (Build.VERSION.SDK_INT >= 21) {
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } else {
            IntentFilter iFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, iFilter);

            int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
            int scale = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;

            double batteryPct = level / (double) scale;

            return (int) (batteryPct * 100);
        }
    }

    UDPGyroProviderClient(AppStatus status_v, Service s){
        status = status_v;

        service = s;

        for(int portn = 9185; portn <= 9190; portn++) {
            try {
                if(portn == 9190){
                    socket = new DatagramSocket();
                    status.update("WARNING: Using randomly assigned port");
                }else{
                    socket = new DatagramSocket(portn);
                }

                break;
            }catch(SocketException e){
                long curr_time = System.currentTimeMillis();
                if((curr_time - last_kill_time) < 1000){
                    status.update("Please wait a bit before trying to reconnect");
                    socket = null;
                    break;
                }

                status.update("Failed to create socket for port " + String.valueOf(portn));
                e.printStackTrace();
                continue;
            }
        }

        if(socket == null){
            status.update("Failed to create datagram socket");
        }
    }

    public void setTgt(String ip, int port){
        handshake_required = true;
        port_v = port;
        try {
            ip_addr = InetAddress.getByName(ip);
        } catch (UnknownHostException e){
            status.update("Invalid IP address. Please enter a valid IP address.");
            ip_addr = null;
            return;
        } catch (Exception e) {
            status.update("Set IP: " + e.toString());
            e.printStackTrace();
            ip_addr = null;
            return;
        }
    }

    public boolean try_handshake() {
        try {
            did_handshake_succeed = Handshaker.try_handshake(socket, handshake_required);
        } catch (HandshakeSuccessWithWarningException f){
            did_handshake_succeed = true;
            handshake_required = false;
            status.update("WARNING: " + f.getMessage());
            return true;
        } catch (HandshakeFailException e) {
            e.printStackTrace();
            did_handshake_succeed = false;
            status.update(e.getMessage());
        }

        handshake_required = !did_handshake_succeed;
        return did_handshake_succeed;
    }

    private boolean isConnected = false;

    private Runnable on_connection_death;

    Thread listening_thread = null;
    public boolean connect(Runnable on_death){
        if(socket == null) return false;

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
            last_packetsend_time = last_heartbeat_time;
            status.update("Connection succeeded!");
            AutoDiscoverer.discoveryStillNecessary = false;

            if(listening_thread != null) listening_thread.interrupt();
            listening_thread = new Thread(listen_task);
            listening_thread.start();
        }

        return isConnected;
    }

    final Object retry = new Object();
    boolean is_retrying = false;
    Thread retry_thread;
    private void killConnection(boolean unexpected){
        if(!isConnected) return;

        if(!unexpected) on_connection_death.run();
        isConnected = false;
        did_handshake_succeed = false;

        if(listening_thread != null) listening_thread.interrupt();

        if(unexpected){
            synchronized (retry) {
                if (is_retrying) return;
                is_retrying = true;
            }

            retry_thread = new Thread(() -> {
                try {
                    for (int i = 0; i < 10; i++) {
                        status.update("Connection died unexpectedly, trying to reconnect...");


                        if (socket == null) return;

                        if (isConnected) break;
                        if (connect(on_connection_death)) break;

                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                        }
                    }
                } finally {
                    synchronized (retry) {
                        is_retrying = false;

                        if(!isConnected) {
                            status.update("Failed to reconnect after 10 tries");
                            on_connection_death.run();
                        }
                    }
                }
            });

            retry_thread.start();
        }
    }

    public boolean isConnected(){
        if(socket == null) return false;

        if(!(isConnected && did_handshake_succeed))
            return false;

        long time = System.currentTimeMillis();

        long time_diff = time - last_heartbeat_time;

        if(time_diff > (10*1000)){
            status.update("Connection with the server has been lost!");
            killConnection(true);
            return false;
        }

        return true;
    }


    boolean magMsgWaiting = false;
    boolean magMsgContents = false;
    Runnable listen_task = () -> {
            byte[] buffer = new byte[64];
            while (isConnected && !Thread.currentThread().isInterrupted()) {
                if(magMsgWaiting){
                    provide_mag_enabled(magMsgContents);
                    magMsgWaiting = false;
                }
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

                    if((last_heartbeat_time - last_batterysend_time) > 10000){
                        last_batterysend_time = last_heartbeat_time;
                        provide_battery();
                    }

                    if (msg_type == 1) {
                        // just heartbeat
                        // check if our sensors are working
                        long lastSent = (last_packetsend_time - last_heartbeat_time);
                        if( (lastSent > (3_000)) && (num_packetsend < 32) ){
                            status.update(String.format("Android OS is not providing rotational data. " +
                                    "It has been %d ms with only %d rotations provided. " +
                                    "If you're using a custom ROM it's likely that your ROM doesn't implement registerListener Sensor.TYPE_ROTATION_VECTOR",
                                    (int)lastSent, (int)num_packetsend));
                            killConnection(false);
                            return;
                        }
                    }else if(msg_type == 2) {
                        // vibrate
                        float duration_s = buff.getFloat();
                        float frequency = buff.getFloat();
                        float amplitude = buff.getFloat();

                        Vibrator v = (Vibrator) service.getSystemService(Context.VIBRATOR_SERVICE);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            v.vibrate(VibrationEffect.createOneShot((long) (duration_s * 1000), (int) (amplitude * 255)));
                        } else {
                            v.vibrate((long) (duration_s * 1000));
                        }
                    }else if(msg_type == 7){
                        // change geomagnetic type
                        char c = buff.getChar();
                        if(listener != null) listener.change_realtime_geomagnetic(c == 'y');
                    }
                } catch (Exception e) {
                    if(Thread.currentThread().isInterrupted()) return;

                    status.update("Consume: " + e.toString());
                    e.printStackTrace();
                    continue;
                }
            }
    };

    private long packet_id = 0;

    int failed_in_series = 0;
    long last_error = 0;
    private boolean sendPacket(ByteBuffer buff, int len){
        DatagramPacket packet = new DatagramPacket(buff.array(), len);
        try {
            socket.send(packet);
            failed_in_series = 0;
        } catch (Exception e) {
            // Log it if enough time has passed since previous log
            long curr_time = System.currentTimeMillis();
            if(curr_time - last_error > 1000) {
                status.update("Packet Send: " + e.toString());
                last_error = curr_time;
            }

            // Kill connection if we failed too many times
            failed_in_series++;
            if(failed_in_series > 10){
                status.update("Packet sends are continuously failing, treating connection as dead");
                killConnection(true);
            }

            return false;
        }
        return true;
    }

    private void provide_battery(){
        if(!isConnected()) return;

        int battery_level = getBatteryPercentage(service);

        int len = 12 + 4;

        ByteBuffer buff = ByteBuffer.allocate(len);
        buff.putInt(12);
        buff.putLong(packet_id++);
        buff.putFloat((float)battery_level);

        sendPacket(buff, len);
    }

    private void provide_floats(float[] floats, int len, int msg_type) {
        if (!isConnected()) return;

        int bytes = 12 + len * 4; // 12b header (int + long)  + floats (4b each)

        ByteBuffer buff = ByteBuffer.allocate(bytes);
        buff.putInt(msg_type);
        buff.putLong(packet_id);

        for (int i = 0; i < len; i++) {
            buff.putFloat(floats[i]);
        }

        if(!sendPacket(buff, bytes)) return;

        packet_id++;

    }


    public void provide_mag_enabled(boolean enabled){
        int len = 12 + 2;
        if(!isConnected()){
            magMsgWaiting = true;
            magMsgContents = enabled;
            return;
        }

        ByteBuffer buff = ByteBuffer.allocate(len);
        buff.putInt(5);
        buff.putLong(packet_id++);
        buff.putChar(enabled ? 'y' : 'n');

        sendPacket(buff, len);

    }

    public void recenter_yaw(){
        int len = 12 + 1;
        ByteBuffer buff = ByteBuffer.allocate(len);
        buff.putInt(6);
        buff.putLong(packet_id++);

        sendPacket(buff, len);
    }

    public void provide_gyro(float[] gyro_v){
        provide_floats(gyro_v,  3, 2);
    }

    public void provide_rot(float[] rot_q){
        provide_floats(rot_q,  4, 1);
        num_packetsend++;
        last_packetsend_time = System.currentTimeMillis();
    }

    public void provide_accel(float[] accel){
        provide_floats(accel,  3, 4);
    }

    public void set_listener(GyroListener gyroListener) {
        listener = gyroListener;
    }

    public void stop() {
        killConnection(false);

        listener = null;

        if(socket != null) {
            socket.close();
            socket = null;
        }

        last_kill_time = System.currentTimeMillis();
    }
}
