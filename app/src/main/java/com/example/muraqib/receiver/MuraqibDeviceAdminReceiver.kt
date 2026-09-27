package com.example.muraqib.receiver

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * مستقبل مسؤول الجهاز لحماية التطبيق ضد إلغاء التثبيت العفوي أو المتعمد
 */
class MuraqibDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        fun getComponentName(context: Context): ComponentName {
            return ComponentName(context, MuraqibDeviceAdminReceiver::class.java)
        }

        fun isDeviceAdminActive(context: Context): Boolean {
            val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as? DevicePolicyManager
            return dpm?.isAdminActive(getComponentName(context)) == true
        }
    }

    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "تم تفعيل حماية التطبيق ضد إلغاء التثبيت", Toast.LENGTH_SHORT).show()
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "تعطيل صلاحية مسؤول الجهاز سيعطل حماية التطبيق والقيود المفروضة. لا تقم بالتعطيل إلا إذا كنت ترغب في إيقاف الحماية تمامًا."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Toast.makeText(context, "تم إلغاء تفعيل مسؤول الجهاز", Toast.LENGTH_SHORT).show()
    }
}
