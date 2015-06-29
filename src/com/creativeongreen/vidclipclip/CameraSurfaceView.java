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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.Paint.Style;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
*
* @author creativeongreen
* 
* A basic Camera preview class
* android.view.SurfaceHolder.Callback interface, which is used to pass image data from the camera hardware to the application.
* 
*/
public class CameraSurfaceView extends SurfaceView implements SurfaceHolder.Callback,
		Camera.PreviewCallback {

	private static final String LOG_TAG = "VCC_CameraSurfaceView";
	private SurfaceHolder mHolder;
	private Camera mCamera;

	private int[] pixels;

	public CameraSurfaceView(Context context, Camera camera) {
		super(context);

		// Log.d(LOG_TAG, "CameraSurfaceView: init");
		this.mCamera = camera;

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		this.mHolder = this.getHolder();
		this.mHolder.addCallback(this);

		// deprecated setting, but required on Android versions prior to 3.0
		this.mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		// Log.d(LOG_TAG, "surfaceCreated");

		Camera.Parameters cameraParams = mCamera.getParameters();

		Camera.Size previewSize = cameraParams.getPreviewSize();
		// pixels = new int[previewSize.width * previewSize.height];

		// add the following scripts to prevent mediarecorder.stop() fail ???
		// The issue was setting the preview size before setting the actual preview for the camera.
		// The preview size MUST be equal to the selected video size.
		// http://stackoverflow.com/questions/15833694/mediarecorder-stop-stop-failed-1007
		CamcorderProfile camcorderProfile;
		if (CamcorderProfile.hasProfile(CamcorderProfile.QUALITY_TIME_LAPSE_HIGH)) {
			camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_TIME_LAPSE_HIGH);
		} else {
			camcorderProfile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
		}
		cameraParams.setPreviewSize(camcorderProfile.videoFrameWidth,
				camcorderProfile.videoFrameHeight);
		mCamera.setParameters(cameraParams);

		// The Surface has been created, now tell the camera where to draw the preview.
		try {
			mCamera.setPreviewDisplay(holder);
		} catch (IOException e) {
			// error on setting or start camera preview
			Log.w(LOG_TAG, "surfaceCreated/IOException: " + e.getMessage());
		}

		mCamera.startPreview();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		// empty. Take care of releasing the Camera preview in your activity.
		// [ray] release camera preview on stage of parent's onPause()
		// Log.d(LOG_TAG, "surfaceDestroyed");
		// mCamera.setPreviewCallbackWithBuffer(null);
		// mCamera.setPreviewCallback(null);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
		// If your preview can change or rotate, take care of those events here.
		// Make sure to stop the preview before resizing or reformatting it.
		// Log.d(LOG_TAG, "surfaceChanged");

		if (holder.getSurface() == null) {
			// preview surface does not exist
			return;
		}

		// stop preview before making changes
		mCamera.stopPreview();

		// set preview size and make any resize, rotate or
		// reformatting changes here

		try {
			mCamera.setPreviewDisplay(holder);
		} catch (Exception e) {
			// Error on setting or starting camera preview
			Log.w(LOG_TAG, "surfaceChanged/Exception: " + e.getMessage());
		}

		// start preview with new settings
		// mCamera.setOneShotPreviewCallback(this);
		// mCamera.setPreviewCallbackWithBuffer(this);
		// mCamera.setPreviewCallback(this);
		mCamera.startPreview();

	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		// Log.d(LOG_TAG, "onPreviewFrame/data len= " + data.length);

		if (mHolder == null) {
			return;
		}

		try {
			synchronized (mHolder) {
				Camera.Parameters cameraParams = camera.getParameters();
				Camera.Size previewSize = cameraParams.getPreviewSize();

				// --- YUV method ---
				YuvImage yuvImage = new YuvImage(data, cameraParams.getPreviewFormat(),
						previewSize.width, previewSize.height, null);
				Rect rectangle = new Rect(0, 0, previewSize.width, previewSize.height);

				YUVtoGrayScale(pixels, data, previewSize.width, previewSize.height);
				// --- Bitmap method ---
				Bitmap bmPreviewFrame = Bitmap.createBitmap(previewSize.width, previewSize.height,
						Bitmap.Config.ALPHA_8);
				bmPreviewFrame.setPixels(pixels, 0/* offset */, previewSize.width /* stride */, 0, 0,
						previewSize.width, previewSize.height);
				// bmPreviewFrame.copyPixelsFromBuffer(ByteBuffer.wrap(data));

				Canvas canvas = new Canvas(bmPreviewFrame);
				canvas.drawBitmap(bmPreviewFrame, 0f, 0f, null);

				Paint paint = new Paint();
				paint.setTextSize(35);
				paint.setColor(Color.BLUE);
				paint.setStyle(Style.FILL);
				float height = paint.measureText("yY");
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				String dateTime = sdf.format(Calendar.getInstance().getTime());
				canvas.drawText(dateTime, 20f, height + 15f, paint);

				bmPreviewFrame.copyPixelsToBuffer(ByteBuffer.wrap(data));

			}
		} finally {
			// do this in a finally so that if an exception is thrown during the above, we don't
			// leave the Surface in an inconsistent state

			// add buffers back when finish processing the data in them
			mCamera.addCallbackBuffer(data);
		}
	}

	static public void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) {
		final int frameSize = width * height;

		for (int j = 0, yp = 0; j < height; j++) {
			int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
			for (int i = 0; i < width; i++, yp++) {
				int y = (0xff & ((int) yuv420sp[yp])) - 16;
				if (y < 0)
					y = 0;
				if ((i & 1) == 0) {
					v = (0xff & yuv420sp[uvp++]) - 128;
					u = (0xff & yuv420sp[uvp++]) - 128;
				}

				int y1192 = 1192 * y;
				int r = (y1192 + 1634 * v);
				int g = (y1192 - 833 * v - 400 * u);
				int b = (y1192 + 2066 * u);

				if (r < 0)
					r = 0;
				else if (r > 262143)
					r = 262143;
				if (g < 0)
					g = 0;
				else if (g > 262143)
					g = 262143;
				if (b < 0)
					b = 0;
				else if (b > 262143)
					b = 262143;

				rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) | ((g >> 2) & 0xff00)
						| ((b >> 10) & 0xff);
			}
		}
	}

	// YUV Space to Greyscale
	static public void YUVtoGrayScale(int[] rgb, byte[] yuv420sp, int width, int height) {
		final int frameSize = width * height;

		for (int pix = 0; pix < frameSize; pix++) {
			int pixVal = (0xff & ((int) yuv420sp[pix])) - 16;
			if (pixVal < 0)
				pixVal = 0;
			if (pixVal > 255)
				pixVal = 255;

			rgb[pix] = 0xff000000 | (pixVal << 16) | (pixVal << 8) | pixVal;
		}
	}

}