package com.example.sensortest

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.jins_jp.meme.*
import io.realm.Realm
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File


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
    //WakeLockの処理
    private var wakelock: PowerManager.WakeLock? = null
    private var powerManager: PowerManager? = null

    //定数
    private val RAD2DEG: Double = 180 / Math.PI
    private val alpha: Float = 0.8f

    //フラグ
    private var gravity_first: Boolean = true
    private var geomagnetic_first: Boolean = true
    private var timer_on: Boolean = false
    private var scanning: Boolean = false
    private var meme_connected: Boolean = false
    private var meme_listening: Boolean = false

    //タイマー用
    private val handler = Handler()
    private val runnable = object: Runnable{
        override fun run(){
            //端末角度
            x_attitude.setText((attitude[0] * RAD2DEG).toString())
            y_attitude.setText((attitude[1] * RAD2DEG).toString())
            z_attitude.setText((attitude[2] * RAD2DEG).toString())
            //修正角度
            meme_edited.setText(meme_attitude[0].toString())
            device_edited.setText((attitude[1] * RAD2DEG.toFloat() + 90).toString())

            handler.postDelayed(this, 1000)

            //Realmを使ってデータを保存する
            realm.executeTransaction { realm ->
                val obj = realm.createObject(SensorDataObject::class.java, id)
                obj.timestamp = nowtime
                obj.x_attitude = attitude[0] * RAD2DEG.toFloat()
                obj.y_attitude = attitude[1] * RAD2DEG.toFloat()    //これが端末の角度
                obj.z_attitude = attitude[2] * RAD2DEG.toFloat()
                obj.x_meme_attitide = meme_attitude[0]  //ここがmemeの角度
                obj.y_meme_attitude = meme_attitude[1]
                obj.z_meme_attitude = meme_attitude[2]
                id += 1
                Log.d("TAG", (attitude[1] * RAD2DEG.toFloat()).toString())
            }

        }
    }

    //JINS MEWME関連
    // https://www.yaz.co.jp/tec-blog/スマートデバイス/496
    var appClientId: String? = "676271552413885"
    var appClientSecret = "mihk65jnr756l9c8pfwa9vsa3fq50ffw"
    private lateinit var memeLib: MemeLib
    private var device_address: String = ""
    private var meme_attitude = FloatArray(3)

    private val uiHandler =  Handler()
    private val uirunnable = object: Runnable{
        override fun run(){
            memeStart_button.setEnabled(true)
        }
    }
    //リスナーを設定する必要がある
    private val memeConnectListener = object: MemeConnectListener{
        override fun memeConnectCallback(p0: Boolean) {
            //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            //接続時のコールバック
            Log.d("TAG", "MEMEの接続コールバックが呼ばれた")
            meme_connected = true
            uiHandler.post(uirunnable)
        }

        override fun memeDisconnectCallback() {
            //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            //切断時のコールバック
            Log.d("TAG", "MEMEの切断コールバックが呼ばれた")
            meme_connected = false
        }
    }

    private fun memestart() {
        //TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
        memeStart_button.setEnabled(true)
    }

    private val memeRealTimeListener: MemeRealtimeListener = MemeRealtimeListener { memeRealtimeData ->
        //リアルタイムデータが随時通知される
        //Log.d("MEMEDATA", memeRealtimeData.toString())
        meme_attitude[0] = memeRealtimeData.pitch
        meme_attitude[1] = memeRealtimeData.roll
        meme_attitude[2] = memeRealtimeData.yaw
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
        if (isExternalStorageWritable()){
            var path = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS).toString()
            Log.d("TAG", path)
            //Realmから呼び出す
            var text: String = "id,timestamp,x_attitude,y_attitude,z_attitude,x_meme_attitude,y_meme_attitude,z_meme_attitude" + "\n"
            realm.executeTransaction { realm ->
                var all = realm.where(SensorDataObject::class.java).findAll()
                for (line in all) {
                    text = text + line.id + "," + line.timestamp + "," + line.x_attitude + "," + line.y_attitude + "," + line.z_attitude + "," + line.x_meme_attitide + "," + line.y_meme_attitude + "," + line.z_meme_attitude+ "\n"
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
    private fun isExternalStorageWritable(): Boolean{
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    private fun checkStoragePermission(){
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

    //Locationパーミッション（JINS MEME用）
    private fun checkLocationPermission(){
        if(ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
            //パーミッション要求
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE)
            return
        }
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
            checkStoragePermission()    //外部ストレージ用
            checkLocationPermission()   //JINS MEMEで必要

        }

        //ボタンの処理
        start_button.setOnClickListener { startSensing() }
        stop_button.setOnClickListener { stopSensing() }
        save_button.setOnClickListener{ saveData()}
        memeScan_button.setOnClickListener{ scanStart() }
        memeConnect_button.setOnClickListener{ memeConnect() }
        memeConnect_button.setEnabled(false)
        memeStart_button.setOnClickListener{ memeReportStart() }
        memeStart_button.setEnabled(false)

        //Realmの処理
        realm = Realm.getDefaultInstance()

        //MemeLibの処理
        MemeLib.setAppClientID(applicationContext, appClientId, appClientSecret)
        memeLib = MemeLib.getInstance()
        memeLib.setVerbose(true)
        memeLib?.setMemeConnectListener(memeConnectListener)

        //WakeLockの処理
        //https://qiita.com/KoheiKanagu/items/20243f9f8e777818c74e
        //https://developer.android.com/training/scheduling/wakelock?hl=ja
        //http://uchida001tmhr.hatenablog.com/entry/2017/12/17/002553
        if(Build.VERSION.SDK_INT >= 23) {
            //startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            powerManager = getSystemService(PowerManager::class.java)
            if (!powerManager!!.isIgnoringBatteryOptimizations(getPackageName())){
                var intent: Intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.setData(Uri.parse("package: " + getPackageName()))
                startActivity(intent)
                Log.d("TAG", "start doze taisaku.")
            }
        }
        wakelock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag").apply {
                acquire()
                Log.d("TAG", "start wake lock.")
            }
        }

    }


    //onDestroy
    override fun onDestroy() {
        super.onDestroy()
        wakelock?.release()
        realm.close()
    }

    //MEMEスキャンボタン
    private fun scanStart(){
        Log.d("TAG", "scan start.")
        scanning = true
        var status = memeLib?.startScan(MemeScanListener { address: String ->
            //スキャンできたらアドレスが通知されるらしい
            device_address = address
            meme_status.setText(device_address)
            memeConnect_button.setEnabled(true)
        })
        if(status != MemeStatus.MEME_OK){
            //スキャンに失敗した時の処理
            Log.d("TAG", "scan failed.")
            scanning = false
            memeLib?.stopScan()
        }
    }

    //MEME接続ボタン
    private fun memeConnect(){
        if(scanning && device_address != ""){
            memeLib?.connect(device_address)
            scanning = false
            //memeStart_button.setEnabled(true)
        }else{
            //スキャンしていないときは押しても何もしない
            Toast.makeText(this, "デバイスに接続できません", Toast.LENGTH_SHORT).show()
        }
    }

    //MEME開始ボタン
    private fun memeReportStart(){
        if(memeLib?.isConnected) {
            if (!meme_listening){
                memeLib?.startDataReport(memeRealTimeListener)
                memeStart_button.setText(R.string.memestop_text)
                meme_listening = true
            }else{
                memeLib?.stopDataReport()
                memeStart_button.setText(R.string.memestart_text)
                meme_listening = false
            }
        }else{
            Toast.makeText(this, "デバイスの準備が終わっていません", Toast.LENGTH_SHORT).show()
        }
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

        x_pitch.setText(meme_attitude[0].toString())
        y_roll.setText(meme_attitude[1].toString())
        z_yaw.setText(meme_attitude[2].toString())

        Log.d("TAG", "sensors are working...")

    }

}
