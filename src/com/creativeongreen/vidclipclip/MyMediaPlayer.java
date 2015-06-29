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

import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

/**
*
* @author creativeongreen
* 
* MyMediaPlayer activity
* 
*/
public class MyMediaPlayer extends Activity implements OnBufferingUpdateListener,
		OnCompletionListener, OnPreparedListener, SurfaceHolder.Callback {

	private static final String LOG_TAG = "VCC_MyMediaPlayer";
	private SurfaceView mPreview;
	private SurfaceHolder mHolder;
	private MediaPlayer mMediaPlayer;
	private String mediaFile;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_my_mediaplayer);
		mPreview = (SurfaceView) findViewById(R.id.video_player_view);
		mHolder = mPreview.getHolder();
		mHolder.addCallback(this);
		// mHolder.setType(SurfacemHolder.SURFACE_TYPE_PUSH_BUFFERS);
		mediaFile = getIntent().getExtras().getString("file");
		// Log.d(LOG_TAG, "onCreate: " + mediaFile);
	}

	@Override
	protected void onPause() {
		// Log.d(LOG_TAG, "onPause");
		super.onPause();
		releaseMediaPlayer();
	}

	@Override
	protected void onDestroy() {
		// Log.d(LOG_TAG, "onDestroy");
		super.onDestroy();
		releaseMediaPlayer();
	}

	@Override
	public void onPrepared(MediaPlayer mediaplayer) {
		// Log.d(LOG_TAG, "onPrepared");
		// Toast.makeText(this, "MediaPlayerVideo- onPrepared",
		// Toast.LENGTH_SHORT).show();
		mMediaPlayer.start();
		// double finalTime = mMediaPlayer.getDuration();
		// double startTime = mMediaPlayer.getCurrentPosition();
	}

	@Override
	public void onCompletion(MediaPlayer arg0) {
		// Log.d(LOG_TAG, "onCompletion");
		Toast.makeText(this, "Press BACK to close.", Toast.LENGTH_SHORT).show();

		Intent intent = new Intent();
		setResult(Activity.RESULT_OK, intent);
	}

	@Override
	public void onBufferingUpdate(MediaPlayer arg0, int percent) {
		// Log.d(LOG_TAG, "onBufferingUpdate/percent:" + percent);
	}

	@Override
	public void surfaceCreated(SurfaceHolder mHolder) {
		// Log.d(LOG_TAG, "surfaceCreated");
		// prepareVideo() when surface created
		prepareVideo();
	}

	@Override
	public void surfaceChanged(SurfaceHolder surfacemHolder, int i, int j, int k) {
		// Log.d(LOG_TAG, "surfaceChanged: i=" + i + ", j=" + j + ", k=" + k);
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder surfacemHolder) {
		// Log.d(LOG_TAG, "surfaceDestroyed");
	}

	private void releaseMediaPlayer() {
		if (mMediaPlayer != null) {
			mMediaPlayer.release();
			mMediaPlayer = null;
			// Log.d(LOG_TAG, "releaseMediaPlayer");
		}
	}

	private void prepareVideo() {
		// Log.d(LOG_TAG, "prepareVideo: new a MediaPlayer");
		mMediaPlayer = new MediaPlayer();

		mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mMediaPlayer.setDisplay(mHolder);
		Uri myUri = Uri.parse("file://" + mediaFile);

		try {
			mMediaPlayer.setDataSource(getApplicationContext(), myUri);
			mMediaPlayer.prepare();
		} catch (IllegalArgumentException e) {
			Log.w(LOG_TAG, "prepareVideo/IllegalArgumentException: " + e.getMessage());
		} catch (SecurityException e) {
			Log.w(LOG_TAG, "prepareVideo/SecurityException: " + e.getMessage());
		} catch (IllegalStateException e) {
			Log.w(LOG_TAG, "prepareVideo/IllegalStateException: " + e.getMessage());
		} catch (IOException e) {
			Log.w(LOG_TAG, "prepareVideo/IOException: " + e.getMessage());
		}

		mMediaPlayer.setOnBufferingUpdateListener(this);
		mMediaPlayer.setOnCompletionListener(this);
		mMediaPlayer.setOnPreparedListener(this);
	}
}
