package com.example.bt_combine_001

import android.annotation.SuppressLint
import android.bluetooth.*
import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.Exception
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class MainActivity : AppCompatActivity() {
    private lateinit var serverThread : ServerThread
    private lateinit var clientThread : ClientThread
    private var sendReceive : SendReceive? = null

    private lateinit var deamonQueueInspecter : DeamonQueueInspector
    private val deamonQ = ConcurrentLinkedQueue<UserInfo>()

    private var delayTime : Long = 100
    private var direction_info_dealyTime : Long = 6000
    private var SERVER_NAME = "maxspace"
    private var terminalType : String? = ""

    lateinit var btManager : BluetoothManager
    lateinit var btAdapter : BluetoothAdapter
    lateinit var btDeviceSet: Set<BluetoothDevice>

    enum class STATEE{
        LISTENING,CONNECTING,CONNECTED,CONNECTION_FAIL,MSG_RECV,
        STREAM_DONE,UPDATE_DIRECTION_INFO
    }

    private val APP_NAME : String = "BT_CHAT"
    private val MY_UUID : UUID = UUID.fromString("6bb9c936-27e4-4042-b247-3e4566900da1")

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etServName.setText(SERVER_NAME)

        btManager = getSystemService(BluetoothManager::class.java)
        btAdapter = btManager.adapter
        if(btAdapter==null){
            Log.d("info","no support bt")
        }
        else{
            if(!btAdapter.isEnabled) {
                //if bt is closed
                btAdapter.enable()
                Toast.makeText(this,"BT is opening!!",Toast.LENGTH_SHORT).show()
            }

            terminalType = intent.extras?.getString("type")
            if(terminalType == getString(R.string.TYPE_SERVER)){
                //server side
                llllClientSide.visibility = View.GONE
                llllServerSide.visibility = View.VISIBLE

                serverThread = ServerThread()
                serverThread.start()

                deamonQueueInspecter = DeamonQueueInspector()
                deamonQueueInspecter.start()
            }
            else if(terminalType == getString(R.string.TYPE_CLIENT)){
                //client side
                llllClientSide.visibility = View.VISIBLE
                llllServerSide.visibility = View.GONE

                btnRunAsServ.setOnClickListener{
                    btnSend.isEnabled = false
                    btnRunAsServ.isEnabled = false
                    btnCloseServ.isEnabled = true

                    serverThread = ServerThread()
                    serverThread.start()
                }
                btnCloseServ.setOnClickListener {
                    btnSend.isEnabled = true
                    btnRunAsServ.isEnabled = true
                    btnCloseServ.isEnabled = false

                    closeServerThread()
                }
                btnSend.setOnClickListener {
                    SERVER_NAME = etServName.text.toString()

                    btDeviceSet = btAdapter.bondedDevices
                    btDeviceSet.forEach {
                        Log.d("dev",it.name)
                    }
                    var btDevice = btDeviceSet.find{it.name == SERVER_NAME}

                    if(btDevice!=null){
                        try {
                            clientThread = ClientThread(btDevice)
                            clientThread.start()
                        }catch (e : Exception){
                            Log.d("exp",e.toString())
                        }
                    }
                    else{
                        Toast.makeText(this,"There is not a bounded device.",Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    var mainHandler = Handler(Looper.myLooper()!!,Handler.Callback {
        when(it.what){
            STATEE.LISTENING.ordinal        -> tvStat.setText("STATUS:"+"Listening...")
            STATEE.CONNECTING.ordinal       -> tvStat.setText("STATUS:"+"Connecting...")
            STATEE.CONNECTED.ordinal        -> tvStat.setText("STATUS:"+"Connected")
            STATEE.CONNECTION_FAIL.ordinal  -> tvStat.setText("STATUS:"+"Connection Failed")
            STATEE.STREAM_DONE.ordinal      ->{
                var s = etMsg.text.toString()
                sendReceive?.write(s.toByteArray())

                Thread{
                    Thread.sleep(delayTime)
                    try{
                        clientThread.cancel()
                    }catch (e:Exception){
                        Log.d("exp",e.toString())
                    }
                }.start()
            }
            STATEE.MSG_RECV.ordinal         -> {
                var readbuf : ByteArray = it.obj as ByteArray
                var tempMsg = String(readbuf,0,it.arg1)
                val s = tempMsg.split(",")

                /* deal with the incoming data "tempMsg"*/
                try {
                    deamonQ.add(
                        UserInfo(
                            s[0],s[1].toFloat()
                        )
                    )
                }catch (e : Exception){
                    e.printStackTrace()
                }
                /* deal with the incoming data "d"*/

                Thread{
                    // after server recv data
                    // we will close serverThread
                    Thread.sleep(delayTime)
                    try{
                        serverThread.cancel()
                    }catch (e:Exception){
                        Log.d("exp",e.toString())
                    }
                    Thread.sleep(delayTime)
                    serverThread = ServerThread()
                    serverThread.start()
                }.start()
            }
            STATEE.UPDATE_DIRECTION_INFO.ordinal ->{
                if(terminalType == getString(R.string.TYPE_SERVER)){
                    var anim = AnimationUtils.loadAnimation(this,R.anim.slide_up)
                    llllServerSide.startAnimation(anim)

                    // dequeue
                    var q = try{
                        deamonQ.remove()
                    }
                    catch (e : Exception){
                        e.printStackTrace()
                        null
                    }

                    // prevent get the null object
                    if(q != null){
                        tvUsernameField.text = q.name
                        ivArrowField.rotation = q.rotDeg
                        tvStat2.text = "Wating in the Queue:${deamonQ.size}"
                    }
                    else{
                        tvUsernameField.text = "N/A"
                        ivArrowField.rotation = 0.0f
                        tvStat2.text = "Wating in the Queue:0"
                    }

                }
            }
        }
        true
    })

    data class UserInfo(
        val name : String = "",
        val rotDeg : Float = 0.0f
    )
    private inner class DeamonQueueInspector : Thread() {
        private var isRunning = true

        override fun run() {
            super.run()
            while(isRunning){
                if(deamonQ.size > 0){
                    mainHandler.sendMessage(
                        Message().also {
                            it.what = STATEE.UPDATE_DIRECTION_INFO.ordinal
                        }
                    )
                    Thread.sleep(direction_info_dealyTime)
                }
                else{
                    Thread.sleep(1000)
                }
            }
        }

        fun cancel(){
            isRunning = false
        }
    }

    @SuppressLint("MissingPermission")
    private inner class ServerThread() : Thread(){
        //AcceptThread

        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            btAdapter?.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID)
        }

        override fun run(){
            var socket: BluetoothSocket? = null
            // Keep listening until exception occurs or a socket is returned.
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                socket = try {
                    mainHandler.sendMessage(
                        Message().also{it.what = MainActivity.STATEE.CONNECTING.ordinal}
                    )

                    mmServerSocket!!.accept()
                } catch (e: IOException) {
                    Log.e("info", "Socket's accept() method failed", e)
                    mainHandler.sendMessage(
                        Message().also{it.what = STATEE.CONNECTION_FAIL.ordinal}
                    )
                    break
                }

                if (socket != null) {
                    // A connection was accepted. Perform work associated with
                    // the connection in a separate thread.
                    //manageMyConnectedSocket(socket)
                    mainHandler.sendMessage(
                        Message().also{it.what = STATEE.CONNECTED.ordinal}
                    )

                    //recv
                    Log.d("info","communication here")
                    sendReceive = SendReceive(socket)
                    sendReceive?.start()

                    break
                }
            }

        }

        fun cancel() {
            try {
                sendReceive?.cancel()
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e("info", "Could not close the connect socket", e)
            }
        }

    }

    @SuppressLint("MissingPermission")
    private inner class ClientThread(d : BluetoothDevice) : Thread(){
        private var device : BluetoothDevice = d
        private val socket : BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(MY_UUID)
        }

        override fun run(){
            btAdapter.cancelDiscovery()

            try{
                socket?.connect()
                mainHandler.sendMessage(
                    Message().also{it.what = STATEE.CONNECTING.ordinal}
                )

                sendReceive = SendReceive(socket)
                sendReceive?.start()

                mainHandler.sendMessage(
                    Message().also{it.what = STATEE.STREAM_DONE.ordinal}
                )
            }catch (e : IOException){
                e.printStackTrace()
                mainHandler.sendMessage(
                    Message().also{it.what = STATEE.CONNECTION_FAIL.ordinal}
                )
            }
        }

        fun cancel() {
            try {
                sendReceive?.cancel()
                socket?.close()
            } catch (e: IOException) {
                Log.e("info", "Could not close the client socket", e)
            }
        }

    }

    private inner class SendReceive(s : BluetoothSocket?) : Thread(){
        private val socket = s
        private val input : InputStream? = s?.inputStream
        private val output : OutputStream? = s?.outputStream
        private var isRunning = true

        override fun run(){
            var buf = ByteArray(1024)
            var bytes : Int

            while(isRunning){
                try {
                    bytes=input!!.read(buf)
                    mainHandler
                        .obtainMessage(STATEE.MSG_RECV.ordinal,bytes,-1,buf)
                        .sendToTarget()
                }catch (e : IOException){
                    e.printStackTrace()
                }
            }
        }

        public fun write( bytes : ByteArray){
            try{
                output!!.write(bytes)
            }catch (e: IOException){
                e.printStackTrace()
            }
        }

        fun cancel() {
            try {
                input?.close()
                output?.close()
                socket?.close()
                isRunning = false
            } catch (e: IOException) {
                Log.e("info", "Could not close the connect socket", e)
            }
        }
    }


    private fun closeServerThread(){
        if(btnCloseServ.isEnabled && serverThread!=null && serverThread.isAlive==true){
            try {
                serverThread.cancel()
            }catch (e : Exception){
                Log.d("exp",e.toString())
            }
        }
    }

    private fun closeDeamonQueue(){
        if(terminalType == getString(R.string.TYPE_SERVER)){
            deamonQueueInspecter.cancel()
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        closeServerThread()
        closeDeamonQueue()
    }
}