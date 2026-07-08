package com.example.aethermesh.ble

import android.app.Activity
import com.example.aethermesh.MainActivity
import no.nordicsemi.android.dfu.DfuBaseService

// Nordic DFU host service: streams a .zip firmware package to the nRF52 (RAK)
// bootloader after the node reboots into DFU mode (OtaControl ENTER_DFU).
// All transfer logic lives in the Nordic library; this subclass only points
// its notification at our activity.
class DfuService : DfuBaseService() {

    override fun getNotificationTarget(): Class<out Activity> = MainActivity::class.java

    override fun isDebug(): Boolean = false
}
