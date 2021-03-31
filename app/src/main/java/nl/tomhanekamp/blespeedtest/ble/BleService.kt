package nl.tomhanekamp.blespeedtest.ble

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.ParcelUuid
import no.nordicsemi.android.ble.BleManagerCallbacks
import no.nordicsemi.android.ble.callback.DataReceivedCallback
import no.nordicsemi.android.ble.callback.MtuCallback
import no.nordicsemi.android.ble.data.Data
import no.nordicsemi.android.support.v18.scanner.*
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.*
import kotlin.math.ceil

interface BleServiceCallbacks {
    fun testAborted(reason: String)
    fun mtuDetermined(mtu: Int)
    fun speedDetermined(bytesPerSecond: Int)
    fun testFinished()
}

@ExperimentalStdlibApi
class BleService(private val context: Context, private val transferFileId: Int, private val callbacks: BleServiceCallbacks) {

    companion object {
        private const val SCAN_TIMEOUT: Long = 30000
        private const val MTU_REQUEST_SIZE: Int = 512
    }

    private val bleManager: BleManagerImpl = BleManagerImpl(context)
    private var scanTimer: Timer = Timer()

    private val bleScanLock: Any = Object()
    private val bleConnectionLock: Any = Object()

    private var bleScanInProgress: Boolean = false
    private var bleConnectionInProgress: Boolean = false
    private var bleConnected: Boolean = false

    private var dataToSend: MutableList<ByteArray> = mutableListOf()
    private var totalBytesToSend: Int? = null
    private var startTime: Date? = null

    fun startBleTest() {
        synchronized(bleScanLock) {
            if (bleScanInProgress) {
                Timber.w("BLE scan not started, a scan is already in progress.")
            } else {
                val scanSettings = ScanSettings.Builder()
                    .setLegacy(false)
                    .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                    .setMatchMode(ScanSettings.MATCH_MODE_STICKY)
                    .setUseHardwareBatchingIfSupported(true)
                    .build()

                scanTimer = Timer()
                scanTimer.schedule(object : TimerTask() {
                    override fun run() {
                        BluetoothLeScannerCompat.getScanner().stopScan(scanCallback)
                        callbacks.testAborted("No suitable server found.")
                    }
                }, SCAN_TIMEOUT)

                bleScanInProgress = true

                val scanFilters = listOf(
                    ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(UUID.fromString(BleManagerImpl.SERVICE_UUID)))
                        .build()
                )
                val scanner = BluetoothLeScannerCompat.getScanner()
                scanner.startScan(scanFilters, scanSettings, scanCallback)
            }
        }
    }

    fun abortBleTest() {
        synchronized(bleScanLock) {
            if (bleScanInProgress) {
                bleScanInProgress = false
                BluetoothLeScannerCompat.getScanner().stopScan(scanCallback)
                callbacks.testAborted("BLE test aborted.")
            }
        }

        closeBleConnection("BLE test aborted")
    }

    private val scanCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            synchronized(bleScanLock) {
                scanTimer.cancel()
                bleScanInProgress = false
                BluetoothLeScannerCompat.getScanner().stopScan(this)
                openBleConnection(result.device)
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            synchronized(bleScanLock) {
                scanTimer.cancel()
                bleScanInProgress = false
                results.firstOrNull()?.let {
                    BluetoothLeScannerCompat.getScanner().stopScan(this)
                    openBleConnection(it.device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            synchronized(bleScanLock) {
                bleScanInProgress = false
                BluetoothLeScannerCompat.getScanner().stopScan(this)
                callbacks.testAborted("BLE scan has failed.")
            }
        }
    }

    private fun openBleConnection(device: BluetoothDevice) {
        synchronized(bleConnectionLock) {
            when {
                bleConnectionInProgress -> {
                    Timber.w("BLE connection not started, a connection is already in progress.")
                }
                bleConnected -> {
                    Timber.w("BLE connection not started, a connection is already open.")
                }
                else -> {
                    bleConnectionInProgress = true
                    bleManager.setGattCallbacks(bleManagerCallbacks)
                    bleManager.connect(device)
                        .retry(3, 500)
                        .useAutoConnect(false)
                        .enqueue()
                }
            }
        }
    }

    private fun closeBleConnection(reason: String) {
        synchronized(bleConnectionLock) {
            if (bleConnectionInProgress || bleConnected) {
                bleConnectionInProgress = false
                bleConnected = false
                bleManager.disconnect()
                    .done {
                        callbacks.testAborted(reason)
                    }
                    .enqueue()
            }
        }
    }

    private val bleManagerCallbacks = object : BleManagerCallbacks {
        override fun onDeviceReady(device: BluetoothDevice) {
            synchronized(bleConnectionLock) {
                bleConnectionInProgress = false
                bleConnected = true
            }
            bleManager.setMTU(MTU_REQUEST_SIZE, mtuCallback)
        }

        override fun onDeviceDisconnected(device: BluetoothDevice) {
            closeBleConnection("Device has disconnected")
        }

        override fun onDeviceNotSupported(device: BluetoothDevice) {
            closeBleConnection("Device is not supported")
        }

        override fun onLinkLossOccurred(device: BluetoothDevice) {
            closeBleConnection("Link loss has occurred")
        }

        override fun onError(device: BluetoothDevice, message: String, errorCode: Int) {
            closeBleConnection("An error has occurred in the BLE connection: $errorCode")
        }

        override fun onDeviceDisconnecting(device: BluetoothDevice) {}
        override fun onDeviceConnected(device: BluetoothDevice) {}
        override fun onBondingFailed(device: BluetoothDevice) {}
        override fun onServicesDiscovered(device: BluetoothDevice, optionalServicesFound: Boolean) {}
        override fun onBondingRequired(device: BluetoothDevice) {}
        override fun onBonded(device: BluetoothDevice) {}
        override fun onDeviceConnecting(device: BluetoothDevice) {}
    }

    private val mtuCallback = MtuCallback { _, mtu ->
        callbacks.mtuDetermined(mtu)

        bleManager.initNotifications(notifyCallback)
        readData(mtu)
        startTime = Date()

        if (dataToSend.isNotEmpty()) {
            bleManager.send(dataToSend.removeFirst(), requestCallback)
        }
    }

    private val notifyCallback = DataReceivedCallback { _, data ->
        Timber.i("Received $data from BLE device")
    }

    private val requestCallback = object : BleManagerImpl.RequestCallback {
        override fun onRequestFailed(device: BluetoothDevice, status: Int) {
            closeBleConnection("BLE request has failed")
        }

        override fun onInvalidRequest() {
            closeBleConnection("BLE request is invalid")
        }

        override fun onDataSent(device: BluetoothDevice, data: Data) {
            if (dataToSend.isNotEmpty()) {
                bleManager.send(dataToSend.removeFirst(), this)
            } else {
                startTime?.time?.let { startTimeInMs ->
                    val durationInSeconds = (Date().time - startTimeInMs) / 1000
                    totalBytesToSend?.let { bytesSent ->
                        val bytesPerSecond = bytesSent / durationInSeconds
                        callbacks.speedDetermined(bytesPerSecond.toInt())
                        callbacks.testFinished()
                    }
                }
            }
        }
    }

    private fun readData(chunkSize: Int) {
        val inStream: InputStream = context.resources.openRawResource(transferFileId)
        val baos = ByteArrayOutputStream()
        val buff = ByteArray(10240)
        var i: Int
        while (inStream.read(buff, 0, buff.size).also { i = it } > 0) {
            baos.write(buff, 0, i)
        }
        dataToSend = divideArray(baos.toByteArray(), chunkSize)
    }

    private fun divideArray(source: ByteArray, chunkSize: Int): MutableList<ByteArray> {
        totalBytesToSend = source.size
        val ret = Array(ceil(source.size / chunkSize.toDouble()).toInt()) { ByteArray(chunkSize) }
        var start = 0
        for (i in ret.indices) {
            if (start + chunkSize > source.size) {
                System.arraycopy(source, start, ret[i], 0, source.size - start)
            } else {
                System.arraycopy(source, start, ret[i], 0, chunkSize)
            }
            start += chunkSize
        }
        return ret.asList().toMutableList()
    }
}