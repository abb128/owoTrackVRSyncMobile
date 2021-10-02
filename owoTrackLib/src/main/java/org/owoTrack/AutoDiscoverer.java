package org.owoTrack;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;


public class AutoDiscoverer {

    public interface ConfigSaver {
        boolean saveAndGetMag(String ip_addr, int port);
    }

    public static boolean discoveryStillNecessary = true;

    static final int port = 35903;
    //static final String mcastAddress = "234.35.90.3";
    // VPNs on Android will cause some issues with multicast, need to just broadcast :(
    static final String mcastAddress = "255.255.255.255";

    Activity act;
    ConfigSaver onConnect;

    public AutoDiscoverer(Activity c, ConfigSaver onYes) {
        act = c;
        onConnect = onYes;
    }

    private void plsDoConnect(InetAddress addr, int port){
        Intent mainIntent = new Intent(act, TrackingService.class);
        mainIntent.putExtra("ipAddrTxt", addr.getHostAddress());
        mainIntent.putExtra("port_no", port);
        mainIntent.putExtra("magnetometer", onConnect.saveAndGetMag(addr.getHostAddress(), port));


        // start service
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            act.startForegroundService(mainIntent);
        }else{
            act.startService(mainIntent);
        }
    }

    public static boolean dialogShown = false;

    private void alert(InetAddress addr, int port, String name){
        dialogShown = true;
        act.runOnUiThread(() -> {
            try {
                AlertDialog.Builder alert = new AlertDialog.Builder(act);
                alert.setTitle("Automatic Discovery");
                alert.setMessage("Connect to " + addr.getHostAddress() + ":" + String.valueOf(port) + " (" + name + ")?");

                alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        plsDoConnect(addr, port);
                    }
                });

                alert.setNegativeButton("No", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {}
                });

                alert.show();
            }catch(WindowManager.BadTokenException ignored){}
        });
    }

    public void try_discover(){
        while(discoveryStillNecessary) {
            try {
                DatagramSocket socket = new DatagramSocket();
                {
                    String handshake = "DISCOVERY";
                    DatagramPacket packet = new DatagramPacket(handshake.getBytes(), handshake.length(),
                            InetAddress.getByName(mcastAddress), port);

                    socket.send(packet);

                    socket.setBroadcast(true);

                    ByteBuffer buff = ByteBuffer.allocate(128);
                    DatagramPacket pkt = new DatagramPacket(buff.array(), buff.capacity());
                    socket.setSoTimeout(5000);
                    socket.receive(pkt);

                    String response = new String(buff.array());


                    for (String line : response.split("\n")) {
                        try {
                            int port = Integer.parseInt(line.split(":")[0]);
                            String name = line.split(":")[1];

                            if (port > 0) {
                                discoveryStillNecessary = false;
                                alert(pkt.getAddress(), port, name);
                                break;
                            }
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            } catch (SocketTimeoutException ignored){
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
