package com.example.sensortest

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class SensorDataObject: RealmObject() {
    @PrimaryKey
    var id: Long = 0
    var timestamp: Long = 0
    var x_attitude: Float = 0f
    var y_attitude: Float = 0f
    var z_attitude: Float = 0f
    var x_meme_attitide: Float = 0f
    var y_meme_attitude: Float = 0f
    var z_meme_attitude: Float = 0f
}