package com.smartcardpoc

import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.abc.terminalfactory.UsbSmartCard

class MainActivity : ReactActivity() {

  override fun getMainComponentName(): String = "SmartcardPOC"

  override fun createReactActivityDelegate(): ReactActivityDelegate =
      DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

  override fun onResume() {
    super.onResume()
    UsbSmartCard.getInstance(applicationContext).onResume()
  }

  override fun onStop() {
    super.onStop()
    UsbSmartCard.getInstance(applicationContext).onStop()
  }
}