package com.u3coding.shaver.device

import android.bluetooth.BluetoothAdapter

class ChangeBlueTooth {

    fun open(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return adapter.isEnabled || adapter.enable()
    }

    fun close(): Boolean {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
        return !adapter.isEnabled || adapter.disable()
    }
}
