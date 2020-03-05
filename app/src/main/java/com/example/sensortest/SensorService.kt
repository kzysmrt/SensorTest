package com.example.sensortest

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat

class SensorService : Service(), SensorEventListener {

    //https://re-engines.com/2018/06/07/android-texttospeechをforeground-serviceで実行する/
    //http://yuki312.blogspot.com/2012/07/androidserviceonstartcommand.html
    //https://akira-watson.com/android/service.html
    //https://qiita.com/naoi/items/03e76d10948fe0d45597 ←一応これと、一番上を基本として作った
    //https://medium.com/location-tracking-tech/位置情報を正確にトラッキングする技術-in-android-第1回-バックグランドでの位置情報トラッキングを可能にするアーキテクチャ-6bb36559a977

    //Bindについては以下
    //https://kojion.com/posts/649

    //Bind用
    inner class MyBinder: Binder(){
        fun getGravity(): FloatArray {
            return newest_gravity
        }

        fun getGeomagnetic(): FloatArray {
            return newest_geomagnetic
        }

        fun getAttitude(): FloatArray{
            return attitude
        }
    }

    private val mBinder = MyBinder()

    //センサ
    private var rotationMatrix= FloatArray(9)
    private var newest_gravity= FloatArray(3)
    private var gravity = FloatArray(3)
    private var newest_geomagnetic= FloatArray(3)
    private var geomagnetic = FloatArray(3)
    private var attitude= FloatArray(3)
    //センサフラグ
    private var gravity_first: Boolean = true
    private var geomagnetic_first: Boolean = true
    //センサ定数
    private val RAD2DEG: Double = 180 / Math.PI
    private val alpha: Float = 0.8f


    override fun onBind(intent: Intent): IBinder {
        //TODO("Return the communication channel to the service.")
        //ここはメインのActivityからbindServiceで呼び出したときに呼ばれるらしい
        //throw UnsupportedOperationException("Not yet implemented")
        return mBinder
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //5秒以内にサービスをユーザに通知する必要があるらしい
        //通知の準備
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val name = "通知のタイトル的情報"
        val id = "sensor_channel"
        val notifyDescription = "この通知の詳細情報を設定"
        if(manager.getNotificationChannel(id) == null){
            val mChannel = NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT)
            mChannel.apply {
                description = notifyDescription
            }
            manager.createNotificationChannel(mChannel)
        }
        //通知
        var pendingIntent = PendingIntent.getActivity(applicationContext, 1, intent, PendingIntent.FLAG_ONE_SHOT)
        val notification = NotificationCompat.Builder(this, id).apply {
            setContentTitle("端末角度取得中")
            setContentText("端末角度取得中です")
            setAutoCancel(true)
            setContentIntent(pendingIntent)
            setSmallIcon(R.drawable.ic_launcher_background)
        }.build()
        //foreground service開始
        startForeground(1, notification)

        //ここに実査に行う処理を書けば良いみたい
        Log.d("BACKGROUND", "バックグラウンド処理開始")
        initSensors()   //センサ利用開始
        Log.d("BACKGROUND", "バックグラウンドセンサースタート")

        //return super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }


    @RequiresApi(Build.VERSION_CODES.N)
    override fun onDestroy() {
        super.onDestroy()
        stopForeground(Service.STOP_FOREGROUND_DETACH)
        stopSelf()
    }


    //センサ用の処理
    private fun initSensors(){
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        //加速度センサ
        val accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_GAME)
        //地磁気センサ
        val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_GAME)
        Log.d("BACKGROUND", "バックグラウンドセンサースタート")
    }

    //センサ用
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //TODO("Not yet implemented")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        //TODO("Not yet implemented")
        if (event == null) return

        //timestamp
        //nowtime = System.currentTimeMillis()
        //timestamptxt.setText(nowtime.toString())

        if(event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, newest_gravity, 0, newest_gravity.size)

            if(gravity_first){
                System.arraycopy(newest_gravity,0, gravity, 0, newest_gravity.size)
                gravity_first = false
            }else{
                gravity[0] = alpha * gravity[0] + (1 - alpha) * newest_gravity[0]
                gravity[1] = alpha * gravity[1] + (1 - alpha) * newest_gravity[1]
                gravity[2] = alpha * gravity[2] + (1 - alpha) * newest_gravity[2]
            }

            //x_pitch.setText(gravity[0].toString())
            //y_roll.setText(gravity[1].toString())
            //z_yaw.setText(gravity[2].toString())


        }else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD){
            System.arraycopy(event.values, 0, newest_geomagnetic, 0, newest_geomagnetic.size)

            if(geomagnetic_first){
                System.arraycopy(newest_geomagnetic,0, geomagnetic, 0, newest_geomagnetic.size)
                geomagnetic_first = false
            }else{
                geomagnetic[0] = alpha * geomagnetic[0] + (1 - alpha) * newest_geomagnetic[0]
                geomagnetic[1] = alpha * geomagnetic[1] + (1 - alpha) * newest_geomagnetic[1]
                geomagnetic[2] = alpha * geomagnetic[2] + (1 - alpha) * newest_geomagnetic[2]
            }

            //x_mag.setText(geomagnetic[0].toString())
            //y_mag.setText(geomagnetic[1].toString())
            //z_mag.setText(geomagnetic[2].toString())
        }

        if(geomagnetic != null && gravity != null){
            SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
            SensorManager.getOrientation(rotationMatrix, attitude)
            //x_attitude.setText((attitude[0] * RAD2DEG).toString())
            //y_attitude.setText((attitude[1] * RAD2DEG).toString())
            //z_attitude.setText((attitude[2] * RAD2DEG).toString())
        }

        //x_pitch.setText(meme_attitude[0].toString())
        //y_roll.setText(meme_attitude[1].toString())
        //z_yaw.setText(meme_attitude[2].toString())

        //Log.d("TAG", "sensors are working...")

    }
}
