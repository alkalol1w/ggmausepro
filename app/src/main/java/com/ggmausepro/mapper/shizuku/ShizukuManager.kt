package com.ggmausepro.mapper.shizuku

import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    const val SHIZUKU_REQUEST_CODE = 1001

    private val _isShizukuAvailable = MutableStateFlow(false)
    val isShizukuAvailable: StateFlow<Boolean> = _isShizukuAvailable.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        checkStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        _isShizukuAvailable.value = false
        _hasPermission.value = false
        ShizukuInputInjector.onBinderDead()
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST_CODE) {
                val granted = grantResult == PackageManager.PERMISSION_GRANTED
                _hasPermission.value = granted
                if (granted) {
                    ShizukuInputInjector.initInjector()
                }
            }
        }

    fun init() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
            checkStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing ShizukuManager", e)
        }
    }

    fun checkStatus() {
        val pingSuccess = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
        _isShizukuAvailable.value = pingSuccess

        if (pingSuccess) {
            val hasPerm = try {
                if (Shizuku.isPreV11()) {
                    false
                } else {
                    Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                }
            } catch (e: Exception) {
                false
            }
            _hasPermission.value = hasPerm
            if (hasPerm) {
                ShizukuInputInjector.initInjector()
            }
        } else {
            _hasPermission.value = false
        }
    }

    fun requestPermission() {
        if (!Shizuku.pingBinder()) {
            return
        }
        if (Shizuku.isPreV11()) {
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            _hasPermission.value = true
            ShizukuInputInjector.initInjector()
            return
        }
        Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
    }

    fun getInputServiceBinder(): IBinder? {
        return try {
            val binder = SystemServiceHelper.getSystemService("input")
            if (binder != null) ShizukuBinderWrapper(binder) else null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get input service binder through Shizuku", e)
            null
        }
    }
}
