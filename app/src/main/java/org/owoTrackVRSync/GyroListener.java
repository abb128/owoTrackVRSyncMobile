package org.owoTrackVRSync;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import org.owoTrackVRSync.math.Quaternion;

public class GyroListener implements SensorEventListener {
    private SensorManager sensorManager;

    private Sensor RotationSensor;
    private Sensor AccelSensor;
    private Sensor GyroSensor;


    private float[] rotation_quat;
    private float[] gyro_vec;

    final static private boolean use_geomagnetic = true;
    private int ROTATION_SENSOR_TYPE;

    final static private String sensor_type = use_geomagnetic ? "Geomagnetic Rotation Sensor": "Game Rotation Sensor";

    UDPGyroProviderClient udpClient;

    private void set_sensor_type(boolean geomagnetic){
        ROTATION_SENSOR_TYPE = use_geomagnetic ? Sensor.TYPE_ROTATION_VECTOR : Sensor.TYPE_GAME_ROTATION_VECTOR;
    }

    GyroListener(SensorManager manager, UDPGyroProviderClient udpClient_v, AppStatus logger) throws Exception {
        sensorManager = manager;

        set_sensor_type(use_geomagnetic);


        RotationSensor = sensorManager.getDefaultSensor(ROTATION_SENSOR_TYPE);
        if(RotationSensor == null){
            logger.update("Could not find " + sensor_type + ", falling back to alternative.");
            set_sensor_type(!use_geomagnetic);
            RotationSensor = sensorManager.getDefaultSensor(ROTATION_SENSOR_TYPE);
            if(RotationSensor == null){
                logger.update("Could not find a suitable rotation sensor!!!");
                throw new Exception("FALLBACK FAILED");
            }else if(use_geomagnetic){
                logger.update("NOTE: You may experience yaw drift!!!");
            }
        }

        AccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        if(AccelSensor == null)
            logger.update("Linear Acceleration sensor could not be found, this data will be unavailable.");

        GyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if(GyroSensor == null)
            logger.update("Gyroscope sensor could not be found, this data will be unavailable.");

        rotation_quat = new float[4];
        gyro_vec = new float[3];

        udpClient = udpClient_v;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    public void register_listeners() {
        sensorManager.registerListener(this,RotationSensor, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this,AccelSensor, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this,GyroSensor, SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void stop(){
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == ROTATION_SENSOR_TYPE) {
            float[] quat = new float[4];
            SensorManager.getQuaternionFromVector(quat, event.values);
            Quaternion quaternion = new Quaternion(quat[1], quat[2], quat[3], quat[0]);

            quat[0] = (float) quaternion.getX();
            quat[1] = (float) quaternion.getY();
            quat[2] = (float) quaternion.getZ();
            quat[3] = (float) quaternion.getW();

            udpClient.provide_rot(quat);
        }else if(event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION){
            udpClient.provide_accel(event.values);
        }else if(event.sensor.getType() == Sensor.TYPE_GYROSCOPE){
            udpClient.provide_gyro(event.values);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
