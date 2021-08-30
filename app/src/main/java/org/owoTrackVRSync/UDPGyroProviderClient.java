package org.owoTrackVRSync;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;


class UDPPackets {
    static final int HEARTBEAT = 0;
    static final int ROTATION = 1;
    static final int GYRO = 2;
    static final int HANDSHAKE = 3;
    static final int ACCEL = 4;
    static final int MAG = 5;
    static final int RAW_CALIBRATION_DATA = 6;
    static final int CALIBRATION_FINISHED = 7;
    static final int CONFIG = 8;
    static final int RAW_MAGENTOMETER = 9;
    static final int PING_PONG = 10;
    static final int SERIAL = 11;
    static final int BATTERY_LEVEL = 12;
    static final int TAP = 13;
    static final int RESET_REASON = 14;
    static final int SENSOR_INFO = 15;
    static final int ROTATION_2 = 16;
    static final int ROTATION_DATA = 17;
    static final int MAGENTOMETER_ACCURACY = 18;

    static final int BUTTON_PUSHED = 60;
    static final int SEND_MAG_STATUS = 61;
    static final int CHANGE_MAG_STATUS = 62;


    static final int RECIEVE_HEARTBEAT = 1;
    static final int RECIEVE_VIBRATE = 2;
    static final int RECIEVE_HANDSHAKE = 3;
    static final int RECIEVE_COMMAND = 4;


}

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
            did_handshake_succeed = Handshaker.try_handshake(socket, handshake_required, ip_addr, port_v);
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

    private boolean isBroadcasting(){
        return ip_addr.toString().endsWith(".255");
    }

    private boolean isConnected = false;

    private Runnable on_connection_death;

    Thread listening_thread = null;
    Thread sending_thread = null;
    public boolean connect(Runnable on_death){
        if(socket == null) return false;

        on_connection_death = on_death;
        socket.disconnect();
        packet_id = 0;
        did_handshake_succeed = false;
        if(ip_addr == null){
            return false;
        }

        if(!isBroadcasting()) {
            status.update("Attempting connection...");
            try {
                socket.connect(new InetSocketAddress(ip_addr, port_v));
            } catch (Exception e) {
                status.update("Connect: " + e.toString());
                e.printStackTrace();
                return false;
            }
        }

        if(!try_handshake()){
            return false;
        }
        did_handshake_succeed = true;
        isConnected = isBroadcasting() ? true : socket.isConnected();
        if(isConnected){
            last_heartbeat_time = System.currentTimeMillis();
            last_packetsend_time = last_heartbeat_time;
            status.update("Connection succeeded!");
            AutoDiscoverer.discoveryStillNecessary = false;

            if(listening_thread != null) listening_thread.interrupt();
            try {
                listening_thread = new Thread(listen_task);
                listening_thread.start();
            }catch(OutOfMemoryError e){
                status.update("Ran out of memory trying to spawn listening_thread");
                isConnected = false;
            }

            if(sending_thread != null) sending_thread.interrupt();
            try {
                sending_thread = new Thread(send_task);
                sending_thread.start();
            }catch(OutOfMemoryError e){
                status.update("Ran out of memory trying to spawn sending_thread");
                isConnected = false;
            }
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
        if(sending_thread != null) sending_thread.interrupt();

        if(unexpected){
            synchronized (retry) {
                if (is_retrying) return;
                is_retrying = true;
            }

            try {
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

                            if (!isConnected) {
                                status.update("Failed to reconnect after 10 tries");
                                on_connection_death.run();
                            }
                        }
                    }
                });

                retry_thread.start();
            }catch(OutOfMemoryError ignored){}
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


    private boolean big_endian = true;
    private void parse_packet(int msg_type, int msg_len, ByteBuffer buff, boolean recursive) {
        switch (msg_type) {
            case UDPPackets.RECIEVE_HEARTBEAT: {
                // just heartbeat
                // check if our sensors are working
                long lastSent = (last_packetsend_time - last_heartbeat_time);
                if ((lastSent > (3_000)) && (num_packetsend < 32)) {
                    status.update(String.format("Android OS is not providing rotational data. " +
                                    "It has been %d ms with only %d rotations provided. " +
                                    "If you're using a custom ROM it's likely that your ROM doesn't implement registerListener Sensor.TYPE_ROTATION_VECTOR",
                            (int) lastSent, (int) num_packetsend));
                    killConnection(false);
                    return;
                }
                break;
            }

            case UDPPackets.RECIEVE_VIBRATE: {
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
                break;
            }

            case UDPPackets.CHANGE_MAG_STATUS: {
                char c = buff.getChar();
                if (listener != null)
                    listener.change_realtime_geomagnetic(c == 'y');
                break;
            }

            case UDPPackets.PING_PONG: {
                sendPacket(buff, msg_len);
                break;
            }

            default: {
                // owoTrack server sends packets in little-endian for simplicity,
                // but SlimeVR server sends them in big-endian, so we need to switch
                // endianness on the fly if the packet ID is extremely big
                if (msg_type >= 2048) {
                    big_endian = !big_endian;
                    if(recursive) {
                        // oops! looks like that wasnt it, return to avoid infinite recursion
                        System.out.println("STILL unknown! " + msg_type);
                        return;
                    }
                    System.out.println("Flipping endianness..");
                    buff.order(big_endian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
                    buff.rewind();
                    parse_packet(buff.getInt(), msg_len, buff, true);
                }
            }
        }
    }

    private boolean flush_packet(int timeout){
        DatagramPacket packet = null;
        try {
            if(timeout > 0) {
                packet = packets.poll(timeout, TimeUnit.MILLISECONDS);
            }else{
                packet = packets.poll();
            }
        } catch (InterruptedException e) {
            return false;
        }
        if(Thread.currentThread().isInterrupted()) return false;
        if(packet != null) {
            try {
                socket.send(packet);
                failed_in_series = 0;
            } catch (Exception e) {
                e.printStackTrace();
                // Log it if enough time has passed since previous log
                long curr_time = System.currentTimeMillis();
                if (curr_time - last_error > 1000) {
                    status.update("Packet Send: " + e.toString());
                    last_error = curr_time;
                }

                // Kill connection if we failed too many times
                failed_in_series++;
                if (failed_in_series > 10) {
                    status.update("Packet sends are continuously failing, treating connection as dead");
                    killConnection(true);
                }
            }
            return true;
        }
        return false;
    }

    private ArrayBlockingQueue<DatagramPacket> packets = new ArrayBlockingQueue<DatagramPacket>(64);

    private long packet_id = 0;

    int failed_in_series = 0;
    long last_error = 0;
    boolean magMsgWaiting = false;
    boolean magMsgContents = false;

    Runnable send_task = () -> {
        while (isConnected && !Thread.currentThread().isInterrupted()) {
            flush_packet(250);
        }
    };

    Runnable listen_task = () -> {
            byte[] buffer = new byte[256];
            while (isConnected && !Thread.currentThread().isInterrupted()) {
                if(magMsgWaiting){
                    provide_mag_enabled(magMsgContents);
                    magMsgWaiting = false;
                }
                try {
                    socket.setSoTimeout(250);
                    DatagramPacket p = new DatagramPacket(buffer, 256);
                    try {
                        socket.receive(p);
                    } catch (SocketTimeoutException e) {
                        continue;
                    }

                    last_heartbeat_time = System.currentTimeMillis();

                    ByteBuffer buff = ByteBuffer.wrap(buffer, 0, p.getLength());
                    buff.order(big_endian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
                    buff.position(0);
                    int msg_type = buff.getInt();

                    if ((last_heartbeat_time - last_batterysend_time) > 10000) {
                        last_batterysend_time = last_heartbeat_time;
                        provide_battery();
                    }

                    parse_packet(msg_type, p.getLength(), buff, false);
                    while(flush_packet(0));
                } catch (Exception e) {
                    if(Thread.currentThread().isInterrupted()) return;

                    status.update("Consume: " + e.toString());
                    e.printStackTrace();
                    continue;
                }
            }
    };

    private boolean sendPacket(ByteBuffer buff, int len){
        return packets.offer(new DatagramPacket(buff.array(), len, ip_addr, port_v));
    }

    private void provide_battery(){
        if(!isConnected()) return;

        int battery_level = getBatteryPercentage(service);

        int len = 12 + 4;

        ByteBuffer buff = ByteBuffer.allocate(len);
        buff.putInt(UDPPackets.BATTERY_LEVEL);
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
        buff.putInt(UDPPackets.SEND_MAG_STATUS);
        buff.putLong(packet_id++);
        buff.putChar(enabled ? 'y' : 'n');

        sendPacket(buff, len);

    }

    public void button_pushed(){
        int len = 12 + 1;
        ByteBuffer buff = ByteBuffer.allocate(len);
        buff.putInt(UDPPackets.BUTTON_PUSHED);
        buff.putLong(packet_id++);

        sendPacket(buff, len);
    }

    public void provide_gyro(float[] gyro_v){
        provide_floats(gyro_v,  3, UDPPackets.GYRO);
    }

    public void provide_rot(float[] rot_q){
        provide_floats(rot_q,  4, UDPPackets.ROTATION);
        num_packetsend++;
        last_packetsend_time = System.currentTimeMillis();
    }

    public void provide_accel(float[] accel){
        provide_floats(accel,  3, UDPPackets.ACCEL);
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
