package org.owoTrackVRSync;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.appcompat.app.AlertDialog;

import org.owoTrackVRSync.ui.ConnectFragment;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;

public class AutoDiscoverer {
    static final int port = 35903;
    //static final String mcastAddress = "234.35.90.3";
    // VPNs on Android will cause some issues with multicast, need to just broadcast :(
    static final String mcastAddress = "255.255.255.255";

    MainActivity act;

    public AutoDiscoverer(MainActivity c) {
        act = c;
    }

    private void plsDoConnect(InetAddress addr, int port){
        Intent mainIntent = new Intent(act, TrackingService.class);
        mainIntent.putExtra("ipAddrTxt", addr.getHostAddress());
        mainIntent.putExtra("port_no", port);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){
            act.startForegroundService(mainIntent);
        }else{
            act.startService(mainIntent);
        }

        // save it for future :)
        SharedPreferences prefs = ConnectFragment.get_prefs(act);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putString("ip_address", addr.getHostAddress());
        editor.putInt("port", port);

        editor.apply();


        // go
        act.contr.navigate(R.id.connectFragment);
    }

    private void alert(InetAddress addr, int port, String name){
        act.runOnUiThread(new Runnable() {

            @Override
            public void run() {
                AlertDialog.Builder alert = new AlertDialog.Builder(act);
                alert.setTitle("Automatic Discovery");
                alert.setMessage("Connect to " + addr.getHostAddress() + ":" + String.valueOf(port) + " (" + name + ")?");

                alert.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        plsDoConnect(addr, port);
                    }
                });

                alert.setNegativeButton("No",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                            }
                        });

                alert.show();
            }
        });
    }

    public void try_discover(){
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
                    int port = Integer.valueOf(line.split(":")[0]);
                    String name = line.split(":")[1];

                    if (port > 0) {
                        alert(pkt.getAddress(), port, name);
                        break;
                    }
                }
            }
        } catch (SocketException | UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
