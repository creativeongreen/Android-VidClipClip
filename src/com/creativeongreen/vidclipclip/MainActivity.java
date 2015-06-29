/*
 * Copyright (C) 2015 creativeongreen
 *
 * Licensed either under the Apache License, Version 2.0, or (at your option)
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation (subject to the "Classpath" exception),
 * either version 2, or any later version (collectively, the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *     http://www.gnu.org/licenses/
 *     http://www.gnu.org/software/classpath/license.html
 *
 * or as provided in the LICENSE.txt file that accompanied this code.
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.creativeongreen.vidclipclip;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.media.MediaRecorder.OnInfoListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.preference.PreferenceManager;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.util.Log;
import android.util.Pair;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

/**
*
* @author creativeongreen
* 
* MainActivity
* 
*/
@SuppressLint("NewApi")
public class MainActivity extends Activity {

	private static final String LOG_TAG = "VCC_MainActivity";
	private static final String VCC_MEDIA_STORAGE_DIR = "VidClipClip";

	private static final int INTENT_REQUEST_SETTINGS_UPDATED = 1;
	private static final int INTENT_REQUEST_MEDIA_PLAYER_ON_COMPLETION = 2;

	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;

	private Camera mCamera;
	private CameraSurfaceView mCameraSurfaceView;
	private FrameLayout cameraPreview;
	private MediaRecorder mMediaRecorder;
	private WakeLock mWakeLock;
	private Handler hRecordingTimer;
	private TextView tvTimer, tvInfo;
	private ImageButton btnSettings, btnPlay;
	private ListView lvVideo;
	private boolean bOnRecording = false;
	private boolean isSystemEnableMediaRecorderStop = false;
	private boolean bIsBtnPlayPressed = false;
	private double dTimeLapseCount = 0L; // unit in second
	private File mediaOutputFile;
	private String itemMediaSelectedFile;

	private double dFrameCaptureRate = 10; // 10 seconds
	private int iMaxRecordDuration = 0; // unlimited

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// Log.d(LOG_TAG, "onCreate()");
		super.onCreate(savedInstanceState);

		// requestFeature() must be called before adding content
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.activity_main);

		// set orientation on manifest to prevent
		// setRequestedOrientation() from re-initing MainActivity instance
		// setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

		// check if device support camera
		if (!isCameraExist(this)) {
			popNotification(getString(R.string.app_name), getString(R.string.msg_no_camera_support));
			finish();
		}

		cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);
		tvTimer = (TextView) findViewById(R.id.text_view_timer);
		btnPlay = (ImageButton) findViewById(R.id.button_play);
		// btnPlay.setVisibility(View.GONE);
		lvVideo = (ListView) findViewById(R.id.list_view);
		lvVideo.setVisibility(View.GONE);

		tvInfo = (TextView) findViewById(R.id.text_view_info);
		btnSettings = (ImageButton) findViewById(R.id.button_settings);
		getSettings();

		// declare a timer to display timelapse if on recording
		hRecordingTimer = new Handler();
	}

	// Important: Call release() to release the camera for use by other
	// applications. Applications should release the camera immediately
	// during onPause() and re-open() it during onResume()).

	@Override
	protected void onResume() {
		// Log.d(LOG_TAG, "onResume()");
		super.onResume();

		// re-initialize camera and preview due to mediaplayer surface view is
		// released
		if (mCamera == null) {
			// Create an instance of Camera
			mCamera = getCameraInstance();
			if (mCamera == null) {
				return;
			}

			// Create our Preview view and set it as the content of our
			// activity.
			mCameraSurfaceView = new CameraSurfaceView(this, mCamera);
			cameraPreview.addView(mCameraSurfaceView);
		}
	}

	@Override
	protected void onPause() {
		// Log.d(LOG_TAG, "onPause()");

		// please follow the sequence below, or the app will crash and
		// MediaRecorder will not be stopped and can not be start again.
		// step.1- if you are using MediaRecorder, release it first
		// step.2- release the camera immediately on pause event
		if (bOnRecording) {
			findViewById(R.id.button_start_recorder).performClick();
		} else {
			releaseMediaRecorder();
		}
		releaseCamera();

		// remove view to prevent fail once screen is off
		cameraPreview.removeView(mCameraSurfaceView);

		// place super pause at last
		super.onPause();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Log.d(LOG_TAG, "onCreateOptionsMenu()");
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	/**
	 * Event Handling for Individual menu item selected Identify single menu item by it's id
	 * */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_settings:
			Intent i = new Intent(this, UserSettingActivity.class);
			startActivityForResult(i, INTENT_REQUEST_SETTINGS_UPDATED);
			break;

		case R.id.menu_action_about:
			showAbout();
			break;

		default:
			return super.onOptionsItemSelected(item);
		}

		return true;
	}

	// to solve no menu hard key issue
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		return onOptionsItemSelected(item);
	}

	// Detect when the back button is pressed
	@Override
	public void onBackPressed() {
		// Log.d(LOG_TAG, "onBackPressed()");
		if (bIsBtnPlayPressed) {
			bIsBtnPlayPressed = false;
			lvVideo.setVisibility(View.GONE);
		} else {
			// Let the system handle the back button
			super.onBackPressed();
		}
	}

	// Listen for results.
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		// Log.d(LOG_TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" +
		// resultCode);
		// super.onActivityResult(requestCode, resultCode, data);

		// See which child activity is calling us back.
		switch (requestCode) {
		case INTENT_REQUEST_MEDIA_PLAYER_ON_COMPLETION:
			if (resultCode == RESULT_OK) { // RESULT_OK == -1
			} else if (resultCode == 0) {
				// user break while media is playing
				Toast.makeText(this, getString(R.string.msg_break), Toast.LENGTH_SHORT).show();
			}
			onClickVideoPlay(lvVideo);
			break;

		case INTENT_REQUEST_SETTINGS_UPDATED:
			getSettings();
			break;

		default:
			break;
		}
	}

	public void onClickSettings(View view) {
		registerForContextMenu(btnSettings);
		openContextMenu(btnSettings);
	}

	public void onClickVideoPlay(View view) {
		/*
		 * File videoFile = new File (videoPath+"/today_special.mp4"); if (videoFile.exists()) { Uri
		 * fileUri = Uri.fromFile(videoFile); Intent intent = new Intent();
		 * intent.setAction(Intent.ACTION_VIEW); intent.setDataAndType(fileUri,
		 * URLConnection.guessContentTypeFromName(fileUri.toString())); startActivity(intent); }
		 * else { Toast.makeText(this, "Video file does not exist", Toast.LENGTH_LONG).show(); }
		 */
		if (bOnRecording) {
			Toast.makeText(this, getString(R.string.msg_on_recording), Toast.LENGTH_SHORT).show();
			return;
		}

		// getMediaFile();
		ArrayList<String> mediaListData = new ArrayList<String>();

		String filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
				.toString();
		final File mediaStorageDir = new File(filePath, VCC_MEDIA_STORAGE_DIR);
		if (!mediaStorageDir.exists()) {
			Toast.makeText(this, getString(R.string.msg_no_media_file), Toast.LENGTH_SHORT).show();
			return;
		}

		File mediaListFiles[] = mediaStorageDir.listFiles(new FileExtensionFilter());
		// sort by date-descending order
		// Arrays.sort(mediaListFiles, Collections.reverseOrder());
		Arrays.sort(mediaListFiles, new Comparator<File>() {
			public int compare(File f1, File f2) {
				return Long.compare(f2.lastModified(), f1.lastModified());
			}
		});

		if (mediaListFiles.length == 0) {
			Toast.makeText(this, getString(R.string.msg_no_media_file), Toast.LENGTH_SHORT).show();
			return;
		}

		for (int i = 0; i < mediaListFiles.length; i++) {
			String fileSize = new DecimalFormat("#,##0.#")
					.format(mediaListFiles[i].length() / 1024) + " KB";
			mediaListData.add(mediaListFiles[i].getName() + "\t ~" + fileSize);
		}

		lvVideo.setVisibility(View.VISIBLE);

		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, mediaListData);
		// selecting single ListView item
		lvVideo.setAdapter(adapter);

		// listening to single listitem click
		lvVideo.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// getting listitem index
				itemMediaSelectedFile = (String) parent.getItemAtPosition(position);

				lvVideo.setVisibility(View.GONE);

				cameraPreview.removeView(mCameraSurfaceView);

				Intent intent = new Intent(MainActivity.this, MyMediaPlayer.class);
				// /mnt/sdcard/DCIM/
				// itemMediaSelectedFile:
				// "VidClipClip/VID_2015-01-02 03.04.05.mp4\t ~xxx KB"
				intent.putExtra("file", mediaStorageDir.getPath() + File.separator
						+ itemMediaSelectedFile.split("\t")[0]);

				try {
					// startActivity(intent);
					startActivityForResult(intent, INTENT_REQUEST_MEDIA_PLAYER_ON_COMPLETION);
				} catch (ActivityNotFoundException e) {
					Log.w(LOG_TAG,
							"onClickVideoPlay/startActivity/ActivityNotFoundException: "
									+ e.getMessage());
					// Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
				}
			}

		});

		bIsBtnPlayPressed = true;

	}

	public void onClickRecorder(View view) {
		bOnRecording = ((ToggleButton) view).isChecked();

		if (bOnRecording) {
			if (prepareVideoRecorder()) {
				// let settings & play button icon disappear
				btnSettings.setVisibility(View.GONE);
				btnPlay.setVisibility(View.GONE);

				dTimeLapseCount = 0L;

				// force the screen and/or keyboard to turn on immediately
				if (mWakeLock == null) {
					PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
					mWakeLock = powerManager.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK
							| PowerManager.ACQUIRE_CAUSES_WAKEUP, LOG_TAG);
					mWakeLock.acquire();
				}

				// start media recorder
				try {
					mMediaRecorder.start();
				} catch (Exception e) {
					releaseMediaRecorder();
					Log.w("LOG_TAG", "onClickRecorder: Fail to start media recorder.");
					Toast.makeText(this, getString(R.string.msg_fail_to_start_recorder),
							Toast.LENGTH_LONG).show();
					return;
				}

				// launch recording timer
				hRecordingTimer.postDelayed(rTimelapseTask, 0);

			} else {
				Log.w(LOG_TAG, "onClickRecorder: Fail to prepare media recorder");
				Toast.makeText(this, getString(R.string.msg_fail_to_prepare_recorder),
						Toast.LENGTH_LONG).show();
			}
		} // /if bOnRecording == true
		else {
			stopRecording();
		}
	}

	// initialize video camera
	private boolean prepareVideoRecorder() {

		mMediaRecorder = new MediaRecorder();
		// Log.d(LOG_TAG, "prepareVideoRecorder: new a MediaRecorder");

		mMediaRecorder.setOnInfoListener(mediaRecorderOnInfoEventHandler);

		// Step 1: Unlock and set camera to MediaRecorder
		mCamera.stopPreview();
		mCamera.unlock();
		mMediaRecorder.setCamera(mCamera);

		// Step 2: Set audio & video sources
		// If a time lapse CamcorderProfile is used, audio related source or recording parameters
		// are ignored.
		// mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
		mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

		// Step 3: Set output format and encoding
		// Set a CamcorderProfile (requires API Level 8 or higher)
		// Use CamcorderProfile.get() to get a profile instance
		CamcorderProfile camcorderProfile;
		if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_TIME_LAPSE_HIGH)) {
			camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_HIGH);
		} else {
			camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
		}
		// using try/catch for setProfile if time lapse CamcorderProfile is NOT used
		try {
			mMediaRecorder.setProfile(camcorderProfile);
		} catch (RuntimeException e) {
			// the exception return message "OK", just skip it.
		}

		// Step 4: Set output file
		mediaOutputFile = getOutputMediaFile(MEDIA_TYPE_VIDEO);
		mMediaRecorder.setOutputFile(mediaOutputFile.toString());

		// set MaxDuration & setMaxFileSize
		// in ms, 30000: 30 seconds / 0 or negative: disable the duration limit
		// note: there will be taken effect if a time lapse CamcorderProfile is used
		mMediaRecorder.setMaxDuration(iMaxRecordDuration * 1000);

		// mMediaRecorder.setMaxFileSize(5000000); // Approximately 5 megabytes
		isSystemEnableMediaRecorderStop = false;

		// Step 5: Set the preview output
		mMediaRecorder.setPreviewDisplay(mCameraSurfaceView.getHolder().getSurface());

		// Step 5.5: Set the video capture rate: setCaptureRate(double fps)
		// 0.1 = capture a frame every 10 seconds, 0.5 = 2 sec,
		// capture_rate=1/seconds
		mMediaRecorder.setCaptureRate((double) 1.0 / dFrameCaptureRate);

		// Step 6: Prepare configured MediaRecorder
		try {
			mMediaRecorder.prepare();
		} catch (IllegalStateException e) {
			Log.w(LOG_TAG, "MediaRecorder/prepare/IllegalStateException: " + e.getMessage());
			releaseMediaRecorder();
			return false;
		} catch (IOException e) {
			// invalid preview surface
			Log.w(LOG_TAG, "MediaRecorder/prepare/IOException: " + e.getMessage());
			releaseMediaRecorder();
			return false;
		}

		return true;
	}

	private MediaRecorder.OnInfoListener mediaRecorderOnInfoEventHandler = new MediaRecorder.OnInfoListener() {
		@Override
		public void onInfo(MediaRecorder mr, int what, int extra) {
			if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
				isSystemEnableMediaRecorderStop = true;
				findViewById(R.id.button_start_recorder).performClick();
				// mr.stop();
				// Log.d(LOG_TAG, "*** stop on max. duration reached");
			} else if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
				isSystemEnableMediaRecorderStop = true;
				findViewById(R.id.button_start_recorder).performClick();
				// Log.d(LOG_TAG, "*** stop on max. filesize reached");
			} else
				Log.w(LOG_TAG, "MediaRecorder.OnInfoListener/onInfo: Error= " + what);
		}
	};

	public void stopRecording() {
		// Log.d(LOG_TAG, "stopRecording()");
		btnSettings.setVisibility(View.VISIBLE);
		btnPlay.setVisibility(View.VISIBLE);

		// allow the device to go to sleep on its own
		if (mWakeLock != null) {
			mWakeLock.release();
			mWakeLock = null;
		}

		hRecordingTimer.removeCallbacks(rTimelapseTask);

		// stop recording and release MediaRecorder object
		// stop recording will bring MediaRecorder to the initial stage, and
		// can't be resumed by start() action
		releaseMediaRecorder();

		bOnRecording = false;

		Toast.makeText(getApplicationContext(), getString(R.string.msg_stop_recording),
				Toast.LENGTH_SHORT).show();
	}

	// class MyTimerTask extends TimerTask {
	private Runnable rTimelapseTask = new Runnable() {
		public void run() {

			int hrs = (int) (dTimeLapseCount / 3600);
			int secs = (int) (dTimeLapseCount % 3600);

			tvTimer.setText(String.format("%02d", hrs) + ":" + String.format("%02d", secs / 60)
					+ ":" + String.format("%02d", secs % 60));

			dTimeLapseCount += dFrameCaptureRate;

			// check if max. recording duration reach
			if (iMaxRecordDuration != 0 && dTimeLapseCount > iMaxRecordDuration) {
				// trigger the start_recorder button, this will also change the icon automatically
				findViewById(R.id.button_start_recorder).performClick();
			} else {
				hRecordingTimer.postDelayed(rTimelapseTask, (long) (dFrameCaptureRate * 1000));
			}

		} // /run()
	};

	/** Check if this device has a camera */
	private boolean isCameraExist(Context context) {
		if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
			// this device has a camera
			return true;
		} else {
			// no camera on this device
			return false;
		}
	}

	/** A safe way to get an instance of the Camera object. */
	public Camera getCameraInstance() {
		Camera c = null;

		try {
			c = Camera.open(); // attempt to get a Camera instance
			// Log.d(LOG_TAG, "getCameraInstance: Open camera");
		} catch (Exception e) {
			// Camera is not available (in use or does not exist)
			Log.w(LOG_TAG, "getCameraInstance/Exception: " + e.getMessage());
			popNotification(getString(R.string.app_name), e.getMessage());
		}

		return c; // returns null if camera is unavailable
	}

	private void releaseCamera() {
		if (mCamera != null) {
			// release the camera for other applications
			try {
				mCamera.release();
				// Log.d(LOG_TAG, "releaseCamera");
			} catch (Exception e) {
				Log.w(LOG_TAG, "releaseCamera/Exception: " + e.getMessage());
				popNotification(getString(R.string.app_name), e.getMessage());
			}

			mCamera = null;
		}
	}

	private void releaseMediaRecorder() {
		if (mMediaRecorder != null) {
			// Call the following methods in order
			// 1. stop recording video
			try {
				// see if media recorder stop by system, if yes, do not do stop() command
				if (!isSystemEnableMediaRecorderStop) {
					mMediaRecorder.stop();
				}
			} catch (RuntimeException e) {
				// delete media file due to this is defective and can not be played.
				mediaOutputFile.delete();
				Log.w(LOG_TAG, "releaseMediaRecorder/RuntimeException: " + e.getMessage());

				// notify error, this would happen if between start and stop are too short.
				popNotification(getString(R.string.msg_dialog_title_stop_failed),
						getString(R.string.msg_stop_failed));

			} finally {
				// 2. remove the configuration settings from the recorder
				mMediaRecorder.reset();
				// 3. release the recorder
				mMediaRecorder.release();
				mMediaRecorder = null;
			}

			// 4. lock camera for later use
			mCamera.lock();
			// Log.d(LOG_TAG, "releaseMediaRecorder");
		}
	}

	private void showAbout() {
		final StringBuilder about_string = new StringBuilder();
		about_string.append(getString(R.string.app_name));
		String version_name = "UNKNOWN_VERSION";
		int version_code = -1;
		try {
			PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			version_name = pInfo.versionName;
			version_code = pInfo.versionCode;
		} catch (NameNotFoundException e) {
			Log.w(LOG_TAG, "showAbout/NameNotFoundException: " + e.getMessage());
			e.printStackTrace();
		}
		about_string.append(" v" + version_name + "\n");
		about_string.append(getString(R.string.version_code) + ": " + version_code + "\n");
		about_string.append(getString(R.string.about_desc));

		popNotification(getString(R.string.action_about), about_string.toString());
	}

	private void popNotification(String title, String message) {
		//new AlertDialog.Builder(this).setTitle(title).setMessage(message).create().show();
		AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		dialog.setNegativeButton("OK", new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialogInterface, int i) {
			}
		});

		dialog.setTitle(title);
		dialog.setMessage(message);
		dialog.show();
	}

	/** Create a file Uri for saving an image or video */
	private static Uri getOutputMediaFileUri(int type) {
		return Uri.fromFile(getOutputMediaFile(type));
	}

	/** Create a File for saving an image or video */
	private static File getOutputMediaFile(int type) {
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		// String filePath = Environment.getExternalStorageDirectory().getPath().toString();
		String filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
				.toString();
		File mediaStorageDir = new File(filePath, VCC_MEDIA_STORAGE_DIR);
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				// Log.w(LOG_TAG, "File/mkdirs: Failed to create directory");
				return null;
			}
		}

		String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss").format(new Date());

		// Create a media file
		File mediaFile;

		if (type == MEDIA_TYPE_IMAGE) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp
					+ ".jpg");
		} else if (type == MEDIA_TYPE_VIDEO) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp
					+ ".mp4");
		} else {
			return null;
		}

		return mediaFile;
	}

	public void getSettings() {
		SharedPreferences settingsPreferences;

		settingsPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		// dFrameCaptureRate =
		// Double.longBitsToDouble(Long.parseLong(settingsPreferences.getString("prefFrameCaptureRate",
		// "10")));
		dFrameCaptureRate = Float.parseFloat(settingsPreferences.getString("prefFrameCaptureRate",
				"10"));
		// sFrameCaptureRate = settingsPreferences.getString("prefFrameCaptureRate", "10");
		// sFrameCaptureRate = String.valueOf(dFrameCaptureRate) + " seconds";

		iMaxRecordDuration = Integer.parseInt(settingsPreferences.getString(
				"prefMaxRecordDuration", "0"));
		// sMaxRecordDuration = settingsPreferences.getString("prefMaxRecordDuration", "");
		// sMaxRecordDuration = String.valueOf(iMaxRecordDuration) + " seconds";

		tvInfo.setText("Frame Capture Rates: "
				+ (dFrameCaptureRate < 1 ? String.format("%.1f", dFrameCaptureRate) + " second"
						: +dFrameCaptureRate == 1 ? "1 second"
								: dFrameCaptureRate < 60 ? dFrameCaptureRate + " seconds"
										: dFrameCaptureRate == 60 ? "1 minute" : dFrameCaptureRate
												/ 60 + " minutes")
				+ "\nMax. Duration: "
				+ (iMaxRecordDuration == 0 ? "unlimited"
						: iMaxRecordDuration < 60 ? iMaxRecordDuration + " seconds"
								: iMaxRecordDuration == 60 ? "1 minute"
										: iMaxRecordDuration < 3600 ? iMaxRecordDuration / 60
												+ " minutes"
												: iMaxRecordDuration == 3600 ? " 1 hour"
														: iMaxRecordDuration / 3600 + " hour"));

		if (iMaxRecordDuration != 0 && dFrameCaptureRate >= iMaxRecordDuration) {
			popNotification(getString(R.string.msg_dialog_title_settings),
					getString(R.string.msg_stop_failed_suggestion));
		}
	}

	/**
	 * Class to filter files which are having .mp3 extension
	 * */
	class FileExtensionFilter implements FilenameFilter {
		public boolean accept(File dir, String name) {
			return (name.endsWith(".mp4") || name.endsWith(".MP4"));
		}
	}
}
