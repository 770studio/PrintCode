/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.md

import android.Manifest
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Camera
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.View.OnClickListener
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.android.volley.AuthFailureError
import com.android.volley.Response
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.chip.Chip
import com.google.common.base.Objects
import com.google.mlkit.md.barcodedetection.BarcodeField
import com.google.mlkit.md.barcodedetection.BarcodeProcessor
import com.google.mlkit.md.barcodedetection.BarcodeResultFragment
import com.google.mlkit.md.camera.CameraSource
import com.google.mlkit.md.camera.CameraSourcePreview
import com.google.mlkit.md.camera.GraphicOverlay
import com.google.mlkit.md.camera.WorkflowModel
import com.google.mlkit.md.camera.WorkflowModel.WorkflowState
import com.google.mlkit.md.settings.SettingsActivity
import com.tbruyelle.rxpermissions.RxPermissions
import org.json.JSONException
import org.json.JSONObject
import print.Print
import java.io.IOException
import java.util.*


/** Demonstrates the barcode scanning workflow using camera preview.  */
class MainActivity : AppCompatActivity(), OnClickListener {

    private var cameraSource: CameraSource? = null
    private var preview: CameraSourcePreview? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var settingsButton: View? = null
    private var flashButton: View? = null
    private var promptChip: Chip? = null
    private var promptChipAnimator: AnimatorSet? = null
    private var workflowModel: WorkflowModel? = null
    private var currentWorkflowState: WorkflowState? = null

    private val dBurl = "jdbc:mysql://192.168.0.192:3306/myDB"
    private val dBuser = "hitesh"
    private val dBpass = "1234"
    private val debug = true
    private val serverUrl = "https://770studio.ru/demo/barcode_insert.php"

    private var HPRTPrinter: Print? = Print()
    private var mUsbManager: UsbManager? = null
    private var device: UsbDevice? = null

    private var mPermissionIntent: PendingIntent? = null
    private var thisCon: Context? = null
    private val ACTION_USB_PERMISSION = "com.PRINTSDKSample"



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        thisCon = this.applicationContext

        mPermissionIntent = PendingIntent.getBroadcast(thisCon, 0, Intent(ACTION_USB_PERMISSION), 0)
        val filter = IntentFilter(ACTION_USB_PERMISSION )
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        this.applicationContext.registerReceiver(mUsbReceiver, filter)





        //this.connectUSB()

        setContentView(R.layout.print_button )

        val printButton =  findViewById(R.id.printButton) as Button
        printButton.setOnClickListener(OnClickListener {
                this.doPrint ()
         })







        return


        preview = findViewById(R.id.camera_preview)
        graphicOverlay = findViewById<GraphicOverlay>(R.id.camera_preview_graphic_overlay).apply {
            setOnClickListener(this@MainActivity)
            cameraSource = CameraSource(this)
        }

        promptChip = findViewById(R.id.bottom_prompt_chip)
        promptChipAnimator =
                (AnimatorInflater.loadAnimator(this, R.animator.bottom_prompt_chip_enter) as AnimatorSet).apply {
                    setTarget(promptChip)
                }

/*
        findViewById<View>(R.id.close_button).setOnClickListener(this)
        flashButton = findViewById<View>(R.id.flash_button).apply {
            setOnClickListener(this@MainActivity)
        }
        settingsButton = findViewById<View>(R.id.settings_button).apply {
            setOnClickListener(this@MainActivity)
        }
*/

        setUpWorkflowModel()
    }

    override fun onResume() {
        super.onResume()

        workflowModel?.markCameraFrozen()
        settingsButton?.isEnabled = true
        currentWorkflowState = WorkflowState.NOT_STARTED
        cameraSource?.setFrameProcessor(BarcodeProcessor(graphicOverlay!!, workflowModel!!))
        workflowModel?.setWorkflowState(WorkflowState.DETECTING)
    }

    override fun onPostResume() {
        super.onPostResume()
        BarcodeResultFragment.dismiss(supportFragmentManager)
    }

    override fun onPause() {
        super.onPause()
        currentWorkflowState = WorkflowState.NOT_STARTED
        stopCameraPreview()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraSource?.release()
        cameraSource = null
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.close_button -> onBackPressed()
            R.id.flash_button -> {
                flashButton?.let {
                    if (it.isSelected) {
                        it.isSelected = false
                        cameraSource?.updateFlashMode(Camera.Parameters.FLASH_MODE_OFF)
                    } else {
                        it.isSelected = true
                        cameraSource!!.updateFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
                    }
                }
            }
            R.id.settings_button -> {
                settingsButton?.isEnabled = false
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
    }

    private fun startCameraPreview() {
        val workflowModel = this.workflowModel ?: return
        val cameraSource = this.cameraSource ?: return
        if (!workflowModel.isCameraLive) {
            try {
                workflowModel.markCameraLive()
                preview?.start(cameraSource)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to start camera preview!", e)
                cameraSource.release()
                this.cameraSource = null
            }
        }
    }

    private fun stopCameraPreview() {
        val workflowModel = this.workflowModel ?: return
        if (workflowModel.isCameraLive) {
            workflowModel.markCameraFrozen()
            flashButton?.isSelected = false
            preview?.stop()
        }
    }

    private fun setUpWorkflowModel() {
        workflowModel = ViewModelProviders.of(this).get(WorkflowModel::class.java)

        // Observes the workflow state changes, if happens, update the overlay view indicators and
        // camera preview state.
        workflowModel!!.workflowState.observe(this, Observer { workflowState ->
            if (workflowState == null || Objects.equal(currentWorkflowState, workflowState)) {
                return@Observer
            }

            currentWorkflowState = workflowState
            Log.d(TAG, "Current workflow state: ${currentWorkflowState!!.name}")

            val wasPromptChipGone = promptChip?.visibility == View.GONE

            when (workflowState) {
                WorkflowState.DETECTING -> {
                    promptChip?.visibility = View.VISIBLE
                    promptChip?.setText(R.string.prompt_point_at_a_barcode)
                    startCameraPreview()
                }
                WorkflowState.CONFIRMING -> {
                    promptChip?.visibility = View.VISIBLE
                    promptChip?.setText(R.string.prompt_move_camera_closer)
                    startCameraPreview()
                }
                WorkflowState.SEARCHING -> {
                    promptChip?.visibility = View.VISIBLE
                    promptChip?.setText(R.string.prompt_searching)
                    stopCameraPreview()
                }
                WorkflowState.DETECTED, WorkflowState.SEARCHED -> {
                    promptChip?.visibility = View.GONE
                    stopCameraPreview()
                }
                else -> promptChip?.visibility = View.GONE
            }

            val shouldPlayPromptChipEnteringAnimation = wasPromptChipGone && promptChip?.visibility == View.VISIBLE
            promptChipAnimator?.let {
                if (shouldPlayPromptChipEnteringAnimation && !it.isRunning) it.start()
            }
        })

        workflowModel?.detectedBarcode?.observe(this, Observer { barcode ->
            if (barcode != null) {
                val barcodeFieldList = ArrayList<BarcodeField>()
                barcodeFieldList.add(BarcodeField("Raw Value", barcode.rawValue ?: ""))
                BarcodeResultFragment.show(supportFragmentManager, barcodeFieldList)

                barcode.rawValue?.let { this.sendServerUpdate(it) }
                this.doPrint()
            }
        })
    }



    fun sendServerUpdate(   data: String ) {

        val requestQueue = Volley.newRequestQueue(this)
        var jsonParams: JSONObject? = null
        try {

            jsonParams = JSONObject()
            jsonParams.put("data", data)
        } catch (e: JSONException) {
            e.printStackTrace()
            Alert("A JSONException2 error occured. Please try again later.")
        }
        sendToLog("Params:", jsonParams.toString())
        val jsonObjReq: JsonObjectRequest = object : JsonObjectRequest( Method.POST ,
                 serverUrl, jsonParams,
                Response.Listener { response ->
                    sendToLog("response:", response.toString())
                    var jObject: JSONObject? = null
                    try {
                        jObject = JSONObject(response.toString())
                        sendToLog("server response:", jObject.toString())
                        val data = jObject.getJSONObject("data")

                    } catch (e: JSONException) {
                        e.printStackTrace()
                        Alert("A JSONException1 error occured. Please try again later.")
                    }
                }, Response.ErrorListener { error -> // sendToLog("VolleyError:", error.getMessage() );
            val networkResponse = error.networkResponse
            if (networkResponse?.data != null) {
                val jsonError = String(networkResponse.data)
                // Print Error!
                sendToLog("VolleyError:", jsonError)
            }

        }) {
            /**
             * Passing some request headers
             */
            @Throws(AuthFailureError::class)
            override fun getHeaders(): Map<String, String> {
                val headers = HashMap<String, String>()
             //   headers["Content-Type"] = "application/vnd.api+json"
              //  headers["Accept"] = "application/vnd.api+json"
                return headers
            }
        }

        // Adding request to request queue
        requestQueue.add(jsonObjReq)
    }


    private fun Alert(text: String) {
        val toast = Toast.makeText(this,
                text,
                Toast.LENGTH_LONG)
        toast.setGravity(Gravity.RELATIVE_LAYOUT_DIRECTION, 0, 200)
        toast.show()
    }


    fun sendToLog(title: String, text: String?) {
        if (debug) Log.d("$title:", text)
    }


    private fun PrintByWifi() {


        val rxPermissions = RxPermissions(this)
        rxPermissions.request(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).subscribe { aBoolean ->
            if (aBoolean) {
               // setWifiDialog()


                if (HPRTPrinter != null) {
                    Print.PortClose()
                }

                val strIP: String = "192.168.43.37";
                val strPort: String = "9100"


                try {
                    if (Print.PortOpen(this.applicationContext, "WiFi,$strIP,$strPort") != 0) {
                        HPRTPrinter = null

                        runOnUiThread {



                            Print.PrintText("BC_EAN8:\n")

                            val output = Print.PrintBarCode(Print.BC_EAN8,
                                    "04210009")

                            if(output == -1 ) {
                                Alert("Print failure!")
                            } else  {
                                Alert(output.toString())
                            }



                        }
                    } else {
                        Alert("Can not connect on the port!")
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Alert("Can not connect on the port, reason unknown!")
                    }
                }






            } else {
                Alert("No permissions to use WiFi!")
            }
        }






    }

    private fun doPrint () {

        if( this.connectUSB() ) {


            val output = Print.PrintBarCode(Print.BC_EAN8,
                    "04210009")

            if(output == -1 ) {
                Alert("Print failure!")
            } else  {
                Alert(output.toString())
            }


        }



    }
    private fun connectUSB(): Boolean {
        //USB not need call "iniPort"
        mUsbManager = this.applicationContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val deviceList = mUsbManager!!.deviceList
        val deviceIterator: Iterator<UsbDevice> = deviceList.values.iterator()
        var HavePrinter = false
        while (deviceIterator.hasNext()) {
            device = deviceIterator.next()
            val count = device!!.interfaceCount

            for (i in 0 until count) {
                val intf = device!!.getInterface(i)
                if (intf.interfaceClass == 7) {

                    Log.d("PRINT_TAG", "vendorID--" + device!!.vendorId + "ProductId--" + device!!.productId)

                    //							if (device.getVendorId()==8401&&device.getProductId()==28680){
//								Log.d("PRINT_TAG","123");
                    HavePrinter = true
                    mUsbManager!!.requestPermission(device, mPermissionIntent)





                    //							}
                }
            }
        }
        if (!HavePrinter) Alert( "Please connect usb printer" )


            return HavePrinter

    }


    private val mUsbReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val action = intent.action
                if ( ACTION_USB_PERMISSION == action) {
                    synchronized(this) {
                        device = intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            if (Print.PortOpen(thisCon, device) != 0) {
                                Log.d("PRINTER:", "Connect Error!" )
                                return
                            } else  Log.d("PRINTER:", "Connected!" )
                        } else {
                            return
                        }
                    }
                }
                if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                    device = intent.getParcelableExtra<Parcelable>(UsbManager.EXTRA_DEVICE) as UsbDevice
                    if (device != null) {
                        val count = device!!.interfaceCount
                        for (i in 0 until count) {
                            val intf = device!!.getInterface(i)
                            //Class ID 7代表打印机
                            if (intf.interfaceClass == 7) {
                                Print.PortClose()

                                  Log.d("PRINTER:", "Please connect to printer!" )


                            }
                        }
                    }
                }
            } catch (e: java.lang.Exception) {
                Log.e("HPRTSDKSample", StringBuilder("Activity_Main --> mUsbReceiver ").append(e.message).toString())
            }
        }
    }











    companion object {
        private const val TAG = "LiveBarcodeActivity"
    }
}
