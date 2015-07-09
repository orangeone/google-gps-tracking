package jp.orangeone.googlegpstracking;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.DatePicker;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.TwoLineListItem;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks;
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionApi;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import panda.android.location.LocationChecker;
import panda.lang.time.DateTimes;
import panda.log.Log;
import panda.log.Logs;

public class MainActivity extends Activity implements OnClickListener, ResultCallback<Status> {
	private static final Log log = Logs.getLog(MainActivity.class);

	private Calendar mDate;

	private ActivityRecognitionApi _recClient; // 行動認識のメインクラス

	private GoogleApiClient mGoogleApiClient;

	private LocationChecker locationChecker = new LocationChecker();

	private Handler mHandler;

	private Geocoder mGeocoder;

	private List<LocationListener> locationListeners = new ArrayList<LocationListener>();

	private class MyLocationListener implements LocationListener {
		@Override
		public void onLocationChanged(Location location) {
			if (!locationChecker.isFineLocation(location)) {
				log.debug("SKIP BAD " + location);
				return;
			}

			GPSHelper.addLocation(location, mGeocoder);
			updateList();
		}
	};

	private static class MyAdapter extends BaseAdapter {
		private Context context;
		private List<TrackingData> items;

		public MyAdapter(Context context, List<TrackingData> items) {
			this.context = context;
			this.items = items;
		}

		public void setItems(List<TrackingData> items) {
			this.items = items;
		}

		@Override
		public int getCount() {
			return items.size();
		}

		@Override
		public Object getItem(int position) {
			return items.get(getCount() - 1 - position);
		}

		@Override
		public long getItemId(int position) {
			return 0;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			TwoLineListItem twoLineListItem;

			if (convertView == null) {
				LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				twoLineListItem = (TwoLineListItem)inflater.inflate(android.R.layout.simple_list_item_2, null);
				twoLineListItem.getText1().setTextSize(14);
				twoLineListItem.getText2().setTextSize(12);
			}
			else {
				twoLineListItem = (TwoLineListItem)convertView;
			}

			TextView text1 = twoLineListItem.getText1();
			TextView text2 = twoLineListItem.getText2();

			TrackingData td = (TrackingData)getItem(position);
			text1.setText(td.toMain());
			text2.setText(td.toSub());

			return twoLineListItem;
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mDate = Calendar.getInstance();

		findViewById(R.id.btnStart).setOnClickListener(this);
		findViewById(R.id.btnStop).setOnClickListener(this);
		TextView txtDate = (TextView)findViewById(R.id.txtDate);
		txtDate.setText(DateTimes.dateFormat().format(mDate));
		txtDate.setOnClickListener(this);

		buttonVisible(false);

		mHandler = new Handler(getMainLooper());

		mGeocoder = new Geocoder(this, Locale.getDefault());

		MyAdapter adapter = new MyAdapter(this, GPSHelper.getTrackings());
		ListView listView = (ListView)findViewById(R.id.listTracking);
		listView.setAdapter(adapter);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// ListView listView = (ListView)parent;

				// TrackingData item = (TrackingData)listView.getItemAtPosition(position);

				if (position % 2 == 0) {
					startActivity(new Intent(MainActivity.this, MapLineActivity.class));
				}
				else {
					startActivity(new Intent(MainActivity.this, MapCircleActivity.class));
				}
			}
		});

		mGoogleApiClient = new GoogleApiClient.Builder(this).addApi(LocationServices.API)
				.addApi(ActivityRecognition.API).addOnConnectionFailedListener(new OnConnectionFailedListener() {
					@Override
					public void onConnectionFailed(ConnectionResult result) {
						String msg = "Connection failed: ConnectionResult.getErrorCode() = " + result.getErrorCode();
						Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
						log.error(msg);
					}
				}).addConnectionCallbacks(new ConnectionCallbacks() {
					@Override
					public void onConnected(Bundle connectionHint) {
						String msg = "Connected to GoogleApiClient";
						Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
						log.info(msg);
					}

					@Override
					public void onConnectionSuspended(int cause) {
						log.info("Connection suspended");
						mGoogleApiClient.connect();
					}
				}).build();
		mGoogleApiClient.connect();

		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				GPSHelper.loadTrackings(mDate);
				updateList();
			}
		}, 100);
	}

	public void onStart() {
		super.onStart();
	}

	public void onResume() {
		super.onResume();
		buttonVisible(!locationListeners.isEmpty());
	}

	public void onDestory() {
		log.debug("onDestroy()");
		super.onDestroy();
	}

	public void onStop() {
		super.onStop();
	}

	public void onPause() {
		super.onPause();
	}

	public void onBackPressed() {
		if (isTracking()) {
			this.moveTaskToBack(true);
			return;
		}
		super.onBackPressed();
	}


	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.btnStart:
			startTrackingService();
			break;
		case R.id.btnStop:
			stopTrackingService();
			break;
		case R.id.txtDate:
			showDatepicker();
			break;
		}
	}

	private boolean isTracking() {
		return !locationListeners.isEmpty();
	}
	private void showDatepicker() {
		if (isTracking()) {
			return;
		}

		final DatePickerDialog datePickerDialog = new DatePickerDialog(this, new DatePickerDialog.OnDateSetListener() {
			@Override
			public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
				Calendar c = Calendar.getInstance();
				c.set(year, monthOfYear, dayOfMonth);

				if (DateTimes.isSameDay(mDate, c)) {
					return;
				}

				mDate.set(year, monthOfYear, dayOfMonth);

				final TextView txtDate = (TextView)findViewById(R.id.txtDate);
				txtDate.setText(DateTimes.dateFormat().format(mDate));

				GPSHelper.loadTrackings(mDate);
				updateList();
			}
		}, mDate.get(Calendar.YEAR), mDate.get(Calendar.MONTH), mDate.get(Calendar.DAY_OF_MONTH));

		datePickerDialog.show();
	}

	private void stopTrackingService() {
		if (!mGoogleApiClient.isConnected()) {
			Toast.makeText(this, "GoogleApiClient not connected!", Toast.LENGTH_SHORT).show();
			return;
		}

		// Remove all activity updates for the PendingIntent that was used to request activity
		// updates.
		ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(mGoogleApiClient,
			getActivityDetectionPendingIntent()).setResultCallback(this);
		
		for (LocationListener ll : locationListeners) {
			LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, ll);
		}
		locationListeners.clear();
		buttonVisible(false);
	}

	/**
	 * Used when requesting or removing activity detection updates.
	 */
	private PendingIntent mActivityDetectionPendingIntent;

	/**
	 * Gets a PendingIntent to be sent for each activity detection.
	 */
	private PendingIntent getActivityDetectionPendingIntent() {
		// Reuse the PendingIntent if we already have it.
		if (mActivityDetectionPendingIntent == null) {
			Intent intent = new Intent(this, DetectedActivitiesIntentService.class);

			// We use FLAG_UPDATE_CURRENT so that we get the same pending intent back when calling
			// requestActivityUpdates() and removeActivityUpdates().
			mActivityDetectionPendingIntent = PendingIntent.getService(this, 0, intent,
				PendingIntent.FLAG_UPDATE_CURRENT);
		}
		return mActivityDetectionPendingIntent;
	}

	private void startTrackingService() {
		if (!mGoogleApiClient.isConnected()) {
			Toast.makeText(this, "GoogleApiClient not connected!", Toast.LENGTH_SHORT).show();
			return;
		}

		ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(mGoogleApiClient, 0,
			getActivityDetectionPendingIntent()).setResultCallback(this);

		if (!DateTimes.isSameDay(mDate, Calendar.getInstance())) {
			mDate = Calendar.getInstance();

			TextView txtDate = (TextView)findViewById(R.id.txtDate);
			txtDate.setText(DateTimes.dateFormat().format(mDate));

			GPSHelper.loadTrackings(mDate);
			updateList();
		}

		LocationListener ll = new MyLocationListener();
		LocationRequest lr = new LocationRequest();
		lr.setInterval(60000);
		lr.setFastestInterval(5000);
		lr.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
		LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, lr, ll);
		locationListeners.add(ll);

		buttonVisible(true);
	}

	/**
	 * Runs when the result of calling requestActivityUpdates() and removeActivityUpdates() becomes
	 * available. Either method can complete successfully or with an error.
	 * 
	 * @param status The Status returned through a PendingIntent when requestActivityUpdates() or
	 *            removeActivityUpdates() are called.
	 */
	public void onResult(Status status) {
		Toast.makeText(this, status.getStatusMessage(), Toast.LENGTH_SHORT).show();
	}

	private void buttonVisible(boolean stop) {
		findViewById(R.id.btnStart).setEnabled(!stop);
		findViewById(R.id.btnStop).setEnabled(stop);
	}

	private void updateList() {
		ListView listView = (ListView)findViewById(R.id.listTracking);
		((MyAdapter)listView.getAdapter()).notifyDataSetChanged();
	}
}
