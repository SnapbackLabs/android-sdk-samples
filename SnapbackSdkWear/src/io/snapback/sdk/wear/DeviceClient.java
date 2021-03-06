package io.snapback.sdk.wear;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import io.snapback.sdk.wear.shared.DataMapKeys;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DeviceClient {
	private static final String TAG = DeviceClient.class.getSimpleName();
	public static final int CLIENT_CONNECTION_TIMEOUT = 15000;

	public static DeviceClient instance;
	
	private Context context;
	
	private ArrayList<String> sensorIdList;
	private ArrayList<Integer> sensorTypeList;
	private ArrayList<String> sensorStringTypeList;
	private ArrayList<String> reportingModeList;
	private ArrayList<String> sensorWakeUpList;
	private ArrayList<String> sensorNameList;
	private ArrayList<String> sensorVendorList;
	private ArrayList<Integer> sensorVersionList;
	private ArrayList<String> sensorPowerList;

	public static DeviceClient getInstance(Context context) {
		if (instance == null) {
			instance = new DeviceClient(context.getApplicationContext());
		}

		return instance;
	}

	private GoogleApiClient googleApiClient;
	private ExecutorService executorService;
	private SensorStringTypes sensorStringTypes;

	private DeviceClient(Context context) {
		this.context = context;
		
		googleApiClient = new GoogleApiClient.Builder(context).addApi(Wearable.API).build();

		executorService = Executors.newCachedThreadPool();
		
		sensorStringTypes = new SensorStringTypes();
		
	}
	
	public void sendSensorsList(SensorManager sensorManager) {
		
		sensorIdList = new ArrayList<String>();
		sensorTypeList = new ArrayList<Integer>();
		sensorStringTypeList = new ArrayList<String>();
		reportingModeList = new ArrayList<String>();
		sensorWakeUpList = new ArrayList<String>();
		sensorNameList = new ArrayList<String>();
		sensorVendorList = new ArrayList<String>();
		sensorVersionList = new ArrayList<Integer>();
		sensorPowerList = new ArrayList<String>();
		
		List<Sensor> list = sensorManager.getSensorList(Sensor.TYPE_ALL);
		
		for(Sensor s : list) {
			
			Sensor defSensors = sensorManager.getDefaultSensor(s.getType());
			if(defSensors == null) {
				// sensor from getSensorList(Sensor.TYPE_ALL) could be a raw sensor or a sensor not accessible
				continue;
			}
			
			fillSensorsListData(s);
		}
		
		executorService.submit(new Runnable() {
			@Override
			public void run() {
				sendSensorsListInBackground();
			}
		});
	}
	
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	private void fillSensorsListData(Sensor s) {
		sensorTypeList.add(s.getType());
		
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			sensorStringTypeList.add(s.getStringType());
			reportingModeList.add(Util.getReportingModeString(s));
			sensorWakeUpList.add("" + s.isWakeUpSensor());
			sensorIdList.add(Util.getIdForSensor(s.getType(), s.isWakeUpSensor()));
		}
		else {
			sensorStringTypeList.add(sensorStringTypes.getStringType(s.getType()));
			reportingModeList.add(null);
			sensorWakeUpList.add(null);
			sensorIdList.add(Util.getIdForSensor(s.getType(), (String)null));
		}
		
		sensorNameList.add(s.getName());
		sensorVendorList.add(s.getVendor());
		sensorVersionList.add(s.getVersion());
		sensorPowerList.add(String.valueOf(s.getPower()));
	}
	
	private void sendSensorsListInBackground() {
		PutDataMapRequest dataMap = PutDataMapRequest.create("/list");
		
		// onDataChanged() gets only called when the data really changes
		// make sure that the method gets called once for each write operation
		dataMap.getDataMap().putLong(DataMapKeys.TIMESTAMP, System.currentTimeMillis());
		
		dataMap.getDataMap().putStringArrayList(DataMapKeys.SENSOR_ID_LIST, sensorIdList);
		dataMap.getDataMap().putIntegerArrayList(DataMapKeys.SENSOR_TYPE_LIST, sensorTypeList);
		dataMap.getDataMap().putStringArrayList(DataMapKeys.SENSOR_REPORTING_MODE_LIST, reportingModeList);
		dataMap.getDataMap().putStringArrayList(DataMapKeys.SENSOR_STRING_TYPE_LIST, sensorStringTypeList);
		dataMap.getDataMap().putStringArrayList(DataMapKeys.SENSOR_WAKEUP_LIST, sensorWakeUpList);
		dataMap.getDataMap().putStringArrayList(DataMapKeys.SENSOR_NAME_LIST, sensorNameList);
		dataMap.getDataMap().putStringArrayList(DataMapKeys.SENSOR_VENDOR_LIST, sensorVendorList);
		dataMap.getDataMap().putIntegerArrayList(DataMapKeys.SENSOR_VERSION_LIST, sensorVersionList);
		dataMap.getDataMap().putStringArrayList(DataMapKeys.SENSOR_POWER_LIST, sensorPowerList);
		
		PutDataRequest putDataRequest = dataMap.asPutDataRequest();
		
		Log.d(TAG, "Trying to send sensors list (" + sensorIdList.size() + ")...");
		if(validateConnection()) {
			Wearable.DataApi.putDataItem(googleApiClient, putDataRequest).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
				@Override
				public void onResult(DataApi.DataItemResult dataItemResult) {
					Log.d(TAG, "Sending sensors list: " + dataItemResult.getStatus().isSuccess());
				}
			});
		}
		else {
			Log.d(TAG, "Disconnected");
		}
	}

	public void sendSensorData(final int sensorType, final String stringType, final String reportingMode,final int accuracy, final long timestamp, final float[] values, final String wakeUp, final String name, final String vendor, final int version, final float power) {
		executorService.submit(new Runnable() {
			@Override
			public void run() {
				sendSensorDataInBackground(sensorType, stringType, reportingMode, accuracy, timestamp, values, wakeUp, name, vendor, version, power);
			}
		});
	}

	private void sendSensorDataInBackground(int sensorType, String stringType, String reportingMode, int accuracy, long timestamp, float[] values, String wakeUp, String name, String vendor, int version, float power) {
		PutDataMapRequest dataMap = PutDataMapRequest.create("/sensors/" + sensorType);

		dataMap.getDataMap().putString(DataMapKeys.STRING_TYPE, stringType);
		dataMap.getDataMap().putString(DataMapKeys.REPORTING_MODE, reportingMode);
		
		dataMap.getDataMap().putInt(DataMapKeys.ACCURACY, accuracy);
		dataMap.getDataMap().putLong(DataMapKeys.TIMESTAMP, timestamp);
		dataMap.getDataMap().putFloatArray(DataMapKeys.VALUES, values);
		dataMap.getDataMap().putString(DataMapKeys.WAKEUP, wakeUp);
		
		dataMap.getDataMap().putString(DataMapKeys.NAME, name);
		dataMap.getDataMap().putString(DataMapKeys.VENDOR, vendor);
		dataMap.getDataMap().putInt(DataMapKeys.VERSION, version);
		dataMap.getDataMap().putFloat(DataMapKeys.POWER, power);
		
		PutDataRequest putDataRequest = dataMap.asPutDataRequest();
		
		if (validateConnection()) {
			Wearable.DataApi.putDataItem(googleApiClient, putDataRequest).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
				@Override
				public void onResult(DataApi.DataItemResult dataItemResult) {
					Log.v(TAG, "Sending sensor data: " + dataItemResult.getStatus().isSuccess());
				}
			});
		}
		else {
			Log.d(TAG, "Disconnected");
		}
	}
	
	public void sendTriggerData(final int sensorType, final String stringType, final  String reportingMode, final long timestamp, final float[] values, final String wakeUp, final String name, final String vendor, final int version, final float power) {
		executorService.submit(new Runnable() {
			@Override
			public void run() {
				sendTriggerDataInBackground(sensorType, stringType, reportingMode, timestamp, values, wakeUp, name, vendor, version, power);
			}
		});
	}
	
	private void sendTriggerDataInBackground(final int sensorType, String stringType, String reportingMode, long timestamp, float[] values, final String wakeUp, String name, String vendor, int version, float power) {
		PutDataMapRequest dataMap = PutDataMapRequest.create("/triggers/" + sensorType);

		dataMap.getDataMap().putString(DataMapKeys.STRING_TYPE, stringType);
		dataMap.getDataMap().putString(DataMapKeys.REPORTING_MODE, reportingMode);
		
		dataMap.getDataMap().putLong(DataMapKeys.TIMESTAMP, timestamp);
		dataMap.getDataMap().putFloatArray(DataMapKeys.VALUES, values);
		dataMap.getDataMap().putString(DataMapKeys.WAKEUP, wakeUp);
		
		dataMap.getDataMap().putString(DataMapKeys.NAME, name);
		dataMap.getDataMap().putString(DataMapKeys.VENDOR, vendor);
		dataMap.getDataMap().putInt(DataMapKeys.VERSION, version);
		dataMap.getDataMap().putFloat(DataMapKeys.POWER, power);
		
		PutDataRequest putDataRequest = dataMap.asPutDataRequest();
		
		if (validateConnection()) {
			Wearable.DataApi.putDataItem(googleApiClient, putDataRequest).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
				@Override
				public void onResult(DataApi.DataItemResult dataItemResult) {
					Log.v(TAG, "Sending trigger data: " + dataItemResult.getStatus().isSuccess());
					
					Intent notifySensorTriggered = new Intent(Constants.NOTIFY_SENSOR_TRIGGERED_INTENT);
			    	notifySensorTriggered.putExtra("id", Util.getIdForSensor(sensorType, wakeUp));
			    	context.sendBroadcast(notifySensorTriggered);
				}
			});
		}
		else {
			Log.d(TAG, "Disconnected");
		}
	}

	private boolean validateConnection() {
		if (googleApiClient.isConnected()) {
			return true;
		}
		
		Log.d(TAG, "Connecting to device...");
		ConnectionResult result = googleApiClient.blockingConnect(CLIENT_CONNECTION_TIMEOUT, TimeUnit.MILLISECONDS);

		return result.isSuccess();
	}

	public void sendDeviceName(final String deviceName) {
		executorService.submit(new Runnable() {
			@Override
			public void run() {
				sendDeviceNameInBackground(deviceName);
			}
		});
	}
	
	private void sendDeviceNameInBackground(String deviceName) {
		PutDataMapRequest dataMap = PutDataMapRequest.create("/device");
		
		// onDataChanged() gets only called when the data really changes
		// make sure that the method gets called once for each write operation
		dataMap.getDataMap().putLong(DataMapKeys.TIMESTAMP, System.currentTimeMillis());
		
		dataMap.getDataMap().putString(DataMapKeys.DEVICE_NAME, deviceName);
		
		PutDataRequest putDataRequest = dataMap.asPutDataRequest();
		
		Log.d(TAG, "Trying to send sensors list (" + sensorIdList.size() + ")...");
		if(validateConnection()) {
			Wearable.DataApi.putDataItem(googleApiClient, putDataRequest).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
				@Override
				public void onResult(DataApi.DataItemResult dataItemResult) {
					Log.d(TAG, "Sending device name: " + dataItemResult.getStatus().isSuccess());
				}
			});
		}
		else {
			Log.d(TAG, "Disconnected");
		}
	}
}
