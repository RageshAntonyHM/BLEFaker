package com.framework.blefaker

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("MissingPermission")
class MainActivity : ComponentActivity() {
   // private lateinit var bleManager: BLEManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BLEPeripheralApp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
       //bleManager.stopAdvertising()
    }

}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BLEPeripheralApp() {
    var bleManager : BLEManager? = null
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var isAdvertising by remember { mutableStateOf(false) }
    var readText =  remember { "" }
   // val statusText by  remember { mutableStateOf(bleManager?.statusText) }
   // val statusText by bleManager?.statusText?.collectAsState()
    val permissionsState = rememberMultiplePermissionsState(
        permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH
            )
        } else {
            listOf(Manifest.permission.BLUETOOTH)
        }
    )
    LaunchedEffect(Unit) {
        if (!permissionsState.allPermissionsGranted) {
            permissionsState.launchMultiplePermissionRequest()
        }
        else {
            bleManager = BLEManager(context,lifecycle)
        }
    }
    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = {
                    if (isAdvertising) {
                        bleManager?.stopAdvertising()
                    } else {
                        bleManager?.startAdvertising()
                    }
                    isAdvertising = !isAdvertising
                }
            ) {
                Text(if (isAdvertising) "Stop Advertising" else "Start Advertising}")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    bleManager?.characteristicSetted?.setValue("${(10..99).random()}".toByteArray());
                    bleManager?.gattServer?.notifyCharacteristicChanged(bleManager?.connectedDevice, bleManager?.characteristicSetted, false)
                    bleManager?.characteristicSetted?.value?.let {
                        Toast.makeText(context, "Changed to ${String(it)}", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("Send Dummy Text and notify")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    bleManager?.characteristicSetted?.setValue("${(10..99).random()}".toByteArray());
                    bleManager?.characteristicSetted?.value?.let {
                        Toast.makeText(context, "Changed to ${String(it)}", Toast.LENGTH_SHORT).show()
                    }
                }
            ) {
                Text("Change Dummy Text")
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    bleManager?.characteristicSetted?.value?.let { value ->
                        readText = String(value)
                    }
                }
            ) {
                Text("Read Text ${readText}")
            }

           // Text("Status: $statusText")
        }
    }

}


@SuppressLint("MissingPermission")
class BLEManager(private val activity: Context,val lifecycle: LifecycleOwner) {
     lateinit var connectedDevice: BluetoothDevice
    lateinit var characteristicSetted: BluetoothGattCharacteristic
    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeAdvertiser: BluetoothLeAdvertiser
    var gattServer: BluetoothGattServer? = null

    private val SERVICE_UUID = UUID.fromString("240d5183-819a-4627-9ca9-1aa24df29f18")
    private val CHARACTERISTIC_UUID = UUID.fromString("240d5183-819a-4627-9ca9-1aa24df29f18")

    private val statusText = MutableStateFlow("Idle")
   // var statusText: SharedFlow<String> = statusText

    init {
        bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser

    }

    // function to get this Bluetooth Device's Name and Address
    fun getBluetoothDeviceNameAndAddress(): String {
        return run {
            val deviceName = bluetoothAdapter.name
            val deviceAddress = bluetoothAdapter.address
            "Name: $deviceName\nAddress: $deviceAddress"
        }
    }
    fun checkPermissions(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestPermissions(activity: ComponentActivity) {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH
            )
        } else {
            arrayOf(Manifest.permission.BLUETOOTH)
        }
        ActivityCompat.requestPermissions(activity, permissions, 1)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @SuppressLint("MissingPermission")
    private fun setupGattServer() {
        gattServer = bluetoothManager.openGattServer(activity, gattServerCallback)
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        characteristicSetted = BluetoothGattCharacteristic(
            CHARACTERISTIC_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(characteristicSetted)
        gattServer?.addService(service)
        characteristicSetted.setValue("Hello from BLE FAKER!".toByteArray())
        lifecycle.lifecycleScope.launch(Dispatchers.Main) {
            statusText.collectLatest {
                println("Status BLE: $it")
                withContext(Dispatchers.Main) {
                    Toast.makeText(activity, it, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startAdvertising() {
        setupGattServer()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        bluetoothAdapter.name = "BLE FAKER"
        bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopAdvertising() {
        bluetoothLeAdvertiser.stopAdvertising(advertiseCallback)
        gattServer?.close()
        statusText.value = "Advertising stopped"
    }
    @SuppressLint("MissingPermission")
    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.i("BLEPeripheral", "Advertising started successfully")
            statusText.value = "Advertising as 'BLE FAKER'"
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("BLEPeripheral", "Advertising failed to start with error: $errorCode")
            statusText.value = "Failed to start advertising: $errorCode"
        }
    }
    @SuppressLint("MissingPermission")
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BLEPeripheral", "Device connected: ${device.address}")
                statusText.value = "Connected to ${device.address}"
                connectedDevice = device
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BLEPeripheral", "Device disconnected: ${device.address}")
                statusText.value = "Disconnected from ${device.address}"
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            val response = String(characteristic.value)
            val oresp = String(characteristicSetted.value)
            Log.d("TAG", "onCharacteristicReadRequest: call ${response} || ${oresp}")
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, response.toByteArray())
            Log.i("BLEPeripheral", "Read request handled: $response")
            statusText.value = "Read request from ${device.address}: $response"
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic, preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray) {
            val receivedValue = String(value)
            Log.i("BLEPeripheral", "Received write request: $receivedValue")
            statusText.value = "Write request from ${device.address}: $receivedValue"
           // characteristic.setValue("GOT ====> $receivedValue".toByteArray());
          //  gattServer?.notifyCharacteristicChanged(device, characteristic, false)
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, value)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun sendNotification(message: String) {
        val characteristic = gattServer?.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID)
        characteristic?.value = message.toByteArray()
        gattServer?.notifyCharacteristicChanged(null, characteristic, false)
        Log.i("BLEPeripheral", "Notification sent: $message")
        statusText.value = "Notification sent: $message"
    }


}

