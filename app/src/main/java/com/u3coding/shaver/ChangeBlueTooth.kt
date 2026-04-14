package com.u3coding.shaver

import android.bluetooth.BluetoothAdapter

class ChangeBlueTooth {

    fun open() {
        BluetoothAdapter.getDefaultAdapter()?.enable()
    }

    fun close() {
        BluetoothAdapter.getDefaultAdapter()?.disable()
    }
}
