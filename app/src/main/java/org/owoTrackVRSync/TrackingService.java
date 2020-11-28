package org.owoTrackVRSync;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class TrackingService extends Service {
    private UDPGyroProviderClient client;
    private GyroListener listener;
    private PowerManager.WakeLock wakeLock;

    private String ip_address;
    private AppStatus stat;

    @Override
    public void onCreate() {
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Bundle data = intent.getExtras();
        ip_address = data.getString("ipAddrTxt");
        int port_no = data.getInt("port_no");


        stat = new AppStatus((Service)this);
        client = new UDPGyroProviderClient(stat, this);
        listener = new GyroListener((SensorManager)getSystemService(Context.SENSOR_SERVICE), client);
        Runnable on_death = new Runnable() {
            @Override
            public void run() {
                System.out.println("Death!!!");
                stopSelf(startId);
            }
        };

        foregroundstuff();

        Thread thread = new Thread(() -> {
            client.setTgt(ip_address, port_no);
            client.connect(on_death);
            if(!client.isConnected()){
                stopSelf(startId);
            }
        });
        thread.start();

        listener.register_listeners();


        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "owoTrackVRSync::BackgroundTrackingSync");
        wakeLock.acquire();



        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(this).sendBroadcast(new Intent("cya-ded"));
        listener.stop();
        wakeLock.release();

        unregisterReceiver(broadcastReceiver);

        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        v.vibrate(VibrationEffect.createOneShot((long)(1000), (int)(255)));


    }

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            stat.update("Killed by notification");
            stopSelf();
        }
    };

    // stupid foreground stuff
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void foregroundstuff(){
        NotificationManager nm = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("NOTIFICATION_CHANNEL_ID", "namee", NotificationManager.IMPORTANCE_DEFAULT));

        registerReceiver(broadcastReceiver, new IntentFilter("kill-ze-service"));

        Intent intent = new Intent("kill-ze-service");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, intent, 0);

        Notification notification = new NotificationCompat.Builder(this, "NOTIFICATION_CHANNEL_ID")
                .setContentTitle("owoTrackVR")
                .setTicker("owoTrackVR")
                .setContentText("Currently connected to " + ip_address)
                .setSmallIcon(R.mipmap.ic_launcher)
                .addAction(0, "Stop", pendingIntent)
                .setOngoing(true).build();

        startForeground(1001, notification);
    }
}
