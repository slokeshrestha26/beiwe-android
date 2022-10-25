package org.beiwe.app;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.provider.Settings;

import org.beiwe.app.storage.EncryptionEngine;

/**This is a class that NEEDS to be instantiated in the main service. In order to get the Android ID, the class needs
 * Context. Once instantiated, the class assigns two variables for AndroidID and BluetoothMAC. Once they are instantiated,
 * they can be called from different classes to be used. They are hashed when they are called.
 * 
 * The class is used to grab unique ID data, and pass it to the server. The data is used while authenticating users
 * 
 * @author Dor Samet, Eli Jones */  

public class DeviceInfo {

	private static String androidID;
	private static String bluetoothMAC;

	/** grab the Android ID and the Bluetooth's MAC address */
	@SuppressLint("HardwareIds")
	public static void initialize(Context appContext) {
		androidID = Settings.Secure.getString( appContext.getContentResolver(), Settings.Secure.ANDROID_ID ); // android ID appears to be a 64 bit string
		
		/* If the BluetoothAdapter is null, or if the BluetoothAdapter.getAddress() returns null,
		 * record an empty string for the Bluetooth MAC Address.
		 * The Bluetooth MAC Address is always empty in Android 8.0 and above, because the app needs
		 * the LOCAL_MAC_ADDRESS permission, which is a system permission that it's not allowed to
		 * have:
		 * https://android-developers.googleblog.com/2017/04/changes-to-device-identifiers-in.html
		 * The Bluetooth MAC Address is also sometimes empty on Android 7 and lower. */
		if ( android.os.Build.VERSION.SDK_INT >= 23) { //This will not work on all devices: http://stackoverflow.com/questions/33377982/get-bluetooth-local-mac-address-in-marshmallow
			String bluetoothAddress = Settings.Secure.getString(appContext.getContentResolver(), "bluetooth_address");
			if (bluetoothAddress == null) { bluetoothAddress = ""; }
			bluetoothMAC = EncryptionEngine.hashMAC(bluetoothAddress); }
		else { //Android before version 6
			BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();	
			if ( bluetoothAdapter == null || bluetoothAdapter.getAddress() == null ) { bluetoothMAC = ""; }
			else { bluetoothMAC = bluetoothAdapter.getAddress(); }
		}
	}
	
	public static String getBeiweVersion() {
		return BuildConfig.FLAVOR + "-" + BuildConfig.VERSION_NAME;
	}
	public static String getAndroidVersion() { return android.os.Build.VERSION.RELEASE; }
	public static String getProduct() { return android.os.Build.PRODUCT; }
	public static String getBrand() { return android.os.Build.BRAND; }
	public static String getHardwareId() { return android.os.Build.HARDWARE; }
	public static String getManufacturer() { return android.os.Build.MANUFACTURER; }
	public static String getModel() { return android.os.Build.MODEL; }
	public static String getAndroidID() { return EncryptionEngine.safeHash(androidID); }
	public static String getBluetoothMAC() { return EncryptionEngine.hashMAC(bluetoothMAC); }
}