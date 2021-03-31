package nl.tomhanekamp.blespeedtest.ble

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.BleManagerCallbacks
import no.nordicsemi.android.ble.callback.*
import timber.log.Timber
import java.util.*

class BleManagerImpl(context: Context): BleManager<BleManagerCallbacks>(context) {

    companion object{
        const val SERVICE_UUID = "5fbfb456-a20b-478f-889c-b3fa3329cd7d"
        const val WRITE_CHARACTERISTIC_UUID = "6bebf1b3-e6fd-47a4-8ed9-b36365c3e654"
        const val NOTIFY_CHARACTERISTIC_UUID = "bc30689f-9105-4789-b2e9-8865637f50a0"
    }

    interface RequestCallback: FailCallback, InvalidRequestCallback, DataSentCallback

    var writeCharacteristic: BluetoothGattCharacteristic? = null
    var readCharacteristic: BluetoothGattCharacteristic? = null

    fun setMTU(value: Int, callback: MtuCallback) {
        requestMtu(value)
            .with(callback)
            .enqueue()
    }

    fun initNotifications(notifyCallback: DataReceivedCallback) {
        Timber.i("Registering notification on read characteristic")
        setNotificationCallback(readCharacteristic).with(notifyCallback)
        enableNotifications(readCharacteristic).enqueue()
    }

    fun send(data: ByteArray, callback: RequestCallback) {
        writeCharacteristic?.let {
            it.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            writeCharacteristic(it, data)
                .fail(callback)
                .invalid(callback)
                .with(callback)
                .enqueue()
        }
    }

    override fun getGattCallback(): BleManagerGattCallback {
        return gattCallback
    }

    private val gattCallback: BleManagerGattCallback = object : BleManagerGattCallback() {
        override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
            val service = gatt.getService(UUID.fromString(SERVICE_UUID))
            return if (service == null) {
                false
            } else {
                writeCharacteristic = service.getCharacteristic(UUID.fromString(WRITE_CHARACTERISTIC_UUID))
                readCharacteristic = service.getCharacteristic(UUID.fromString(NOTIFY_CHARACTERISTIC_UUID))
                writeCharacteristic != null && readCharacteristic != null
            }
        }

        override fun onDeviceDisconnected() {
            Timber.i("onDeviceDisconnected")
        }
    }
}

