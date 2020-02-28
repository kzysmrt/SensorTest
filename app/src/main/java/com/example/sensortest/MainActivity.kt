package com.example.sensortest

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity(), SensorEventListener {

    //センサマネージャ
    private lateinit var sensorManager: SensorManager
    //センサの値
    private var rotationMatrix= FloatArray(9)
    private var newest_gravity= FloatArray(3)
    private var gravity = FloatArray(3)
    private var newest_geomagnetic= FloatArray(3)
    private var geomagnetic = FloatArray(3)
    private var attitude= FloatArray(3)
    private var nowtime: Long = 0L

    //定数
    private val RAD2DEG: Double = 180 / Math.PI
    private val alpha: Float = 0.8f

    //フラグ
    private var gravity_first: Boolean = true
    private var geomagnetic_first: Boolean = true
    private var timer_on: Boolean = false

    //タイマー用
    private val handler = Handler()
    private val runnable = object: Runnable{
        override fun run(){
            x_attitude.setText((attitude[0] * RAD2DEG).toString())
            y_attitude.setText((attitude[1] * RAD2DEG).toString())
            z_attitude.setText((attitude[2] * RAD2DEG).toString())
            handler.postDelayed(this, 1000)

            //Realmを使ってデータを保存する
            realm.executeTransaction { realm ->
                val obj = realm.createObject(SensorDataObject::class.java, id)
                obj.timestamp = nowtime
                obj.x_attitude = attitude[0] * RAD2DEG.toFloat()
                obj.y_attitude = attitude[1] * RAD2DEG.toFloat()
                obj.z_attitude = attitude[2] * RAD2DEG.toFloat()
                id += 1
                Log.d("TAG", id.toString())
            }

        }
    }

    //Realm
    private lateinit var realm: Realm
    private var id: Int = 0

    //データ保存
    // https://akira-watson.com/android/external-storage-file.html
    //https://wiki.toridge.com/index.php?android-kotlin-ファイルへの保存
    private var filename: String = "savedata.csv"
    private val REQUEST_CODE: Int = 1000
    private fun saveFile(filename: String) {
        //外部ストレージが使用可能であれば保存処理開始
        if (isExternalStorageWriable()){
            var path = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS).toString()
            Log.d("TAG", path)
            //Realmから呼び出す
            var text: String = "id,timestamp,x_attitude,y_attitude,z_attitude" + "\n"
            realm.executeTransaction { realm ->
                var all = realm.where(SensorDataObject::class.java).findAll()
                for (line in all) {
                    text = text + line.id + "," + line.timestamp + "," + line.x_attitude + "," + line.y_attitude + "," + line.z_attitude + "\n"
                }
            }
            val filepath: String = path + "/" + filename
            val writeFile = File(filepath)
            writeFile.writeText(text)
            Log.d("TAG", filepath)
            //実機の場合、sdcard/Android/data/com.example.sensortest/files/Documents/以下に保存される

        }else{
            Log.d("TAG", "外部ストレージが使えない")
        }
    }

    //外部ストレージのチェック
    private fun isExternalStorageWriable(): Boolean{
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    private fun checkPermission(){
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            requestStoragePermission()
        }else{
            Toast.makeText(this, "権限が許可されています", Toast.LENGTH_SHORT).show()
        }

    }

    //https://qiita.com/KIRIN3qiita/items/9ea8317b908d7f9d68c3
    private fun requestStoragePermission() {
        ActivityCompat.requestPermissions(this,
            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode){
            REQUEST_CODE -> if(grantResults[0] == PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this, "パーミッション追加しました", Toast.LENGTH_SHORT).show()
            }else{
                Toast.makeText(this, "パーミッション追加できなかったので終了します。再起動して、パーミッションを許可してください。", Toast.LENGTH_SHORT).show()
                exitThisApprication()
            }
        }
    }

    private fun exitThisApprication(){
        if(Build.VERSION.SDK_INT >= 21) {
            finishAndRemoveTask()
        }else{
            finish()
        }
    }


    //onCreate
    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        setContentView(R.layout.activity_main)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        //パーミッションの処理
        if(Build.VERSION.SDK_INT >= 23){
            checkPermission()
        }

        //ボタンの処理
        start_button.setOnClickListener { startSensing() }
        stop_button.setOnClickListener { stopSensing() }
        save_button.setOnClickListener{ saveData()}

        //Realmの処理
        realm = Realm.getDefaultInstance()
    }

    //onDestroy
    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

    //保存ボタン
    private fun saveData() {
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        saveFile(filename)
        Log.d("TAG", "save.")
    }

    //ストップボタン
    private fun stopSensing() {
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        //センシングストップ
        if(timer_on) {
            handler.removeCallbacks(runnable)
            timer_on = false
        }

        //realmの処理
        realm.executeTransaction { realm ->
            val all = realm.where(SensorDataObject::class.java!!).findAll()
            Log.d("realmresult", all.toString())
        }
        //Log.d("TAG", "stop.")
    }

    //スタートボタン
    private fun startSensing() {
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        //センシングスタート
        if(!timer_on) {
            handler.post(runnable)
            timer_on = true
        }
        Log.d("TAG", "start.")
    }


    override fun onResume() {
        super.onResume()
        //加速度センサ
        val accSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorManager.registerListener(this, accSensor, SensorManager.SENSOR_DELAY_GAME)
        //地磁気センサ
        val magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_GAME)
    }

    override fun onPause() {
        super.onPause()
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun onSensorChanged(event: SensorEvent?) {
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        if (event == null) return

        //timestamp
        nowtime = System.currentTimeMillis()
        timestamptxt.setText(nowtime.toString())

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

            x_pitch.setText(gravity[0].toString())
            y_roll.setText(gravity[1].toString())
            z_yaw.setText(gravity[2].toString())


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

            x_mag.setText(geomagnetic[0].toString())
            y_mag.setText(geomagnetic[1].toString())
            z_mag.setText(geomagnetic[2].toString())
        }

        if(geomagnetic != null && gravity != null){
            SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)
            SensorManager.getOrientation(rotationMatrix, attitude)
            //x_attitude.setText((attitude[0] * RAD2DEG).toString())
            //y_attitude.setText((attitude[1] * RAD2DEG).toString())
            //z_attitude.setText((attitude[2] * RAD2DEG).toString())
        }
    }

}
