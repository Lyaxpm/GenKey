package com.gen.keymapper

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.view.InputEvent
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

/** Authorized Binder calls run as shell/root, without starting another app_process. */
internal class DirectInputInjector private constructor(
    private val shizukuBinder: IBinder,
    private val inputBinder: IBinder,
    private val manager: Any,
    private val injectMethod: Method,
    val description: String
) {
    fun isAlive() = shizukuBinder === Shizuku.getBinder() &&
        shizukuBinder.isBinderAlive && inputBinder.isBinderAlive

    fun inject(event: InputEvent): Boolean {
        check(isAlive()) { "Sesi Shizuku berubah. Hubungkan kembali." }
        return injectMethod.invoke(manager, event, 0) == true
    }

    companion object {
        @SuppressLint("PrivateApi")
        fun connect(binder: IBinder): DirectInputInjector {
            check(binder === Shizuku.getBinder() && binder.isBinderAlive) { "Shizuku terputus" }
            check(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                "Izinkan GenKey di aplikasi Shizuku."
            }
            check(Shizuku.checkRemotePermission("android.permission.INJECT_EVENTS") == PackageManager.PERMISSION_GRANTED) {
                "Firmware tidak memberi izin injeksi ke Shizuku. Periksa pengaturan debugging keamanan perangkat."
            }
            if (Build.VERSION.SDK_INT >= 28) {
                check(HiddenApiBypass.addHiddenApiExemptions(
                    "Landroid/hardware/input/IInputManager", "Landroid/os/ServiceManager;"
                )) { "API input internal tidak dapat diakses pada Android ini." }
            }
            val inputBinder = checkNotNull(SystemServiceHelper.getSystemService("input")) {
                "Layanan input Android tidak tersedia"
            }
            val api = Class.forName("android.hardware.input.IInputManager")
            val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
            val manager = checkNotNull(stub.getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(inputBinder)))
            val method = api.getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            // Verify the authorized route with a harmless remote call, before marking READY.
            api.getMethod("getInputDeviceIds").invoke(manager)
            check(binder === Shizuku.getBinder() && binder.isBinderAlive) { "Shizuku berubah saat menghubungkan" }
            return DirectInputInjector(binder, inputBinder, manager, method,
                "Binder langsung · Shizuku API ${Shizuku.getVersion()} · UID ${Shizuku.getUid()}")
        }
    }
}
