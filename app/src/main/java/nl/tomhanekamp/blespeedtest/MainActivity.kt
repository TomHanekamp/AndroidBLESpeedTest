package nl.tomhanekamp.blespeedtest

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.tomhanekamp.blespeedtest.ble.BleService
import nl.tomhanekamp.blespeedtest.ble.BleServiceCallbacks

@ExperimentalStdlibApi
class MainActivity : AppCompatActivity(), BleServiceCallbacks {

    private var bleService: BleService? = null

    private var startButton: Button? = null
    private var abortButton: Button? = null
    private var mtuValue: TextView? = null
    private var transferSpeedValue: TextView? = null
    private var messages: TextView? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bleService = BleService(this, R.raw.transfer, this)

        startButton = findViewById(R.id.startButton)
        abortButton = findViewById(R.id.abortButton)
        mtuValue = findViewById(R.id.mtuValue)
        transferSpeedValue = findViewById(R.id.transferSpeedValue)
        messages = findViewById(R.id.messages)
    }

    override fun onResume() {
        super.onResume()

        startButton?.setOnClickListener {
            startButton?.isEnabled = false
            abortButton?.isEnabled = true
            clearValues()

            CoroutineScope(Dispatchers.IO).launch {
                bleService?.startBleTest()
            }
        }

        abortButton?.setOnClickListener {
            startButton?.isEnabled = true
            abortButton?.isEnabled = false
            CoroutineScope(Dispatchers.IO).launch {
                bleService?.abortBleTest()
            }
        }
    }

    private fun clearValues() {
        mtuValue?.text = ""
        transferSpeedValue?.text = ""
        messages?.text = ""
    }

    override fun mtuDetermined(mtu: Int) {
        mtuValue?.text = mtu.toString()
    }

    override fun speedDetermined(bytesPerSecond: Int) {
        transferSpeedValue?.text = "$bytesPerSecond B/s"
    }

    override fun testAborted(reason: String) {
        messages?.text = "Something has forced the test to abort: $reason"
    }

    override fun testFinished() {
        CoroutineScope(Dispatchers.Main).launch {
            messages?.text = "Test complete"
            startButton?.isEnabled = true
            abortButton?.isEnabled = false
        }
    }
}