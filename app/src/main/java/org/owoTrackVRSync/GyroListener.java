package org.owoTrackVRSync;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.RequiresApi;

import org.owoTrackVRSync.math.Quaternion;

import java.util.List;

public class GyroListener implements SensorEventListener {
    private SensorManager sensorManager;

    private Sensor RotationSensor;
    private Sensor AccelSensor;
    private Sensor GyroSensor;


    private float[] rotation_quat;
    private float[] gyro_vec;

    private int magnetometerAccuracy = -1;
    public int get_magnetometer_accuracy() { return magnetometerAccuracy; }



    private boolean use_geomagnetic = true;
    private boolean using_geomagnetic = false;

    private int ROTATION_SENSOR_TYPE;

    private String sensor_type = "";

    UDPGyroProviderClient udpClient;

    private void set_sensor_type(boolean geomagnetic){
        sensor_type = use_geomagnetic ? "Magnetometer, Gyroscope, Accelerometer": "Gyroscope, Accelerometer (no magnetometer)";
        ROTATION_SENSOR_TYPE = use_geomagnetic ? Sensor.TYPE_ROTATION_VECTOR : Sensor.TYPE_GAME_ROTATION_VECTOR;
        using_geomagnetic = geomagnetic;
    }

    GyroListener(SensorManager manager, UDPGyroProviderClient udpClient_v, AppStatus logger, boolean mag) throws Exception {
        sensorManager = manager;

        use_geomagnetic = mag;

        set_sensor_type(use_geomagnetic);
        logger.update("Using " + sensor_type);

        magnetometerAccuracy = -1;

        RotationSensor = sensorManager.getDefaultSensor(ROTATION_SENSOR_TYPE);
        if(RotationSensor == null){
            logger.update("Could not find " + sensor_type + ", falling back to alternative.");
            set_sensor_type(!use_geomagnetic);
            RotationSensor = sensorManager.getDefaultSensor(ROTATION_SENSOR_TYPE);
            if(RotationSensor == null){
                logger.update("Could not find any suitable rotation sensor!");
                throw new Exception("Failed to find sensors, see log for more details");
            }
        }

        if(!using_geomagnetic){
            logger.update("You may experience yaw drift.");
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
        udpClient.provide_mag_enabled(using_geomagnetic);
        udpClient.set_listener(this);
    }

    private Handler mHandler;
    public void register_listeners() {
        mHandler = new Handler();
        sensorManager.registerListener(this,RotationSensor, SensorManager.SENSOR_DELAY_FASTEST, mHandler);
        sensorManager.registerListener(this,AccelSensor, SensorManager.SENSOR_DELAY_FASTEST, mHandler);
        sensorManager.registerListener(this,GyroSensor, SensorManager.SENSOR_DELAY_FASTEST, mHandler);
    }

    public void change_realtime_geomagnetic(boolean geomagnetic){
        magnetometerAccuracy = -1;

        sensorManager.unregisterListener(this);
        set_sensor_type(geomagnetic);
        register_listeners();
        udpClient.provide_mag_enabled(geomagnetic);
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
        //magnetometerAccuracy = accuracy;
        //if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
        //    System.out.println("@@@ Accuracy for " + sensor.getStringType() + " : " + String.valueOf(accuracy));
        //}

        //udpClient.provide_accuracy(accuracy);
    }
}
