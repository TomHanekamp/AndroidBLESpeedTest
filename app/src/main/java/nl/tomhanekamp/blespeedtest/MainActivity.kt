package nl.tomhanekamp.blespeedtest

import android.Manifest
import android.content.pm.PackageManager
import android.os.AsyncTask
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.amazonaws.auth.CognitoCachingCredentialsProvider
import com.amazonaws.mobileconnectors.lambdainvoker.LambdaFunctionException
import com.amazonaws.mobileconnectors.lambdainvoker.LambdaInvokerFactory
import com.amazonaws.regions.Regions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import nl.tomhanekamp.blespeedtest.ble.BleService
import nl.tomhanekamp.blespeedtest.ble.BleServiceCallbacks
import nl.tomhanekamp.blespeedtest.server.api.MyInterface
import nl.tomhanekamp.blespeedtest.server.model.Request
import nl.tomhanekamp.blespeedtest.server.model.Response
import timber.log.Timber


@ExperimentalStdlibApi
class MainActivity : AppCompatActivity(), BleServiceCallbacks {
    companion object {
        private const val REQUEST_ACCESS_FINE_LOCATION = 1022
    }

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

        executeLambda()

        startButton?.setOnClickListener {
            startButton?.isEnabled = false
            abortButton?.isEnabled = true
            clearValues()

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                CoroutineScope(Dispatchers.IO).launch {
                    bleService?.startBleTest()
                }
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_ACCESS_FINE_LOCATION
                )
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String?>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_ACCESS_FINE_LOCATION) {
            CoroutineScope(Dispatchers.IO).launch {
                bleService?.startBleTest()
            }
        }
    }

    private fun clearValues() {
        mtuValue?.text = ""
        transferSpeedValue?.text = ""
        messages?.text = ""
    }

    override fun mtuDetermined(mtu: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            mtuValue?.text = mtu.toString()
        }
    }

    override fun speedDetermined(bytesPerSecond: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            transferSpeedValue?.text = "$bytesPerSecond B/s"
        }
    }

    override fun testAborted(reason: String) {
        CoroutineScope(Dispatchers.Main).launch {
            messages?.text = "Something has forced the test to abort: $reason"
        }
    }

    override fun testFinished() {
        CoroutineScope(Dispatchers.Main).launch {
            messages?.text = "Test complete"
            startButton?.isEnabled = true
            abortButton?.isEnabled = false
        }
    }

    private fun executeLambda() {
        CoroutineScope(Dispatchers.IO).launch {
            val cognitoProvider = CognitoCachingCredentialsProvider(
                applicationContext, "<identifyPoolId>", Regions.EU_WEST_1
            )
            val factory = LambdaInvokerFactory.builder()
                .context(applicationContext)
                .region(Regions.EU_WEST_1)
                .credentialsProvider(cognitoProvider)
                .build()
            val myInterface = factory.build(MyInterface::class.java)
            val request = Request("Tom", "Hanekamp")
            try {
                val result = myInterface.AndroidBackendLambdaFunction(request)
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(this@MainActivity, result?.greetings, Toast.LENGTH_LONG).show()
                }
            } catch (lfe: LambdaFunctionException) {
                Timber.e(lfe, "Failed to invoke echo")
            }
        }
    }
}