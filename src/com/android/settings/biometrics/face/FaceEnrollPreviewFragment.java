/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.settings.biometrics.face;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageView;

import com.android.settings.R;
import com.android.settings.biometrics.BiometricEnrollSidecar;
import com.android.settings.core.InstrumentedPreferenceFragment;

import java.util.Arrays;

/**
 * Fragment that contains the logic for showing and controlling the camera preview, circular
 * overlay, as well as the enrollment animations.
 */
public class FaceEnrollPreviewFragment extends InstrumentedPreferenceFragment
        implements BiometricEnrollSidecar.Listener {

    private static final String TAG = "FaceEnrollPreviewFragment";

    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private Handler mHandler = new Handler(Looper.getMainLooper());
    private CameraManager mCameraManager;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CameraCaptureSession mCaptureSession;
    private CaptureRequest mPreviewRequest;
    private Size mPreviewSize;
    private ParticleCollection.Listener mListener;
    private static final boolean USING_UNISOC_FACEID = true;
    private static final String FACEID_VER_PROP = "persist.sys.cam.faceid.version";
    private static final int WIDTH = 3 == SystemProperties.getInt(FACEID_VER_PROP, 1) ? 1440 : 960;
    private static final int HEIGHT = 3 == SystemProperties.getInt(FACEID_VER_PROP, 1) ? 1080 : 720;
    private FaceEnrollEnrolling.StartedListener mStartedListener;

    // View used to contain the circular cutout and enrollment animation drawable
    private ImageView mCircleView;

    // Drawable containing the circular cutout and enrollment animations
    private FaceEnrollAnimationDrawable mAnimationDrawable;

    // Texture used for showing the camera preview
    private FaceSquareTextureView mTextureView;

    // Listener sent to the animation drawable
    private final ParticleCollection.Listener mAnimationListener
            = new ParticleCollection.Listener() {
        @Override
        public void onEnrolled() {
            mListener.onEnrolled();
        }
    };

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener =
            new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(
                SurfaceTexture surfaceTexture, int width, int height) {
            Log.d(TAG, "onSurfaceTextureAvailable");
            if (USING_UNISOC_FACEID) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
                mStartedListener.onEnrollStarted();
            } else {
                openCamera(width, height);
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(
                SurfaceTexture surfaceTexture, int width, int height) {
            // Shouldn't be called, but do this for completeness.
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            Log.d(TAG, "onSurfaceTextureDestroyed");
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    private final CameraDevice.StateCallback mCameraStateCallback =
            new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            try {
                // Configure the size of default buffer
                SurfaceTexture texture = mTextureView.getSurfaceTexture();
                texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

                // This is the output Surface we need to start preview
                Surface surface = new Surface(texture);

                // Set up a CaptureRequest.Builder with the output Surface
                mPreviewRequestBuilder =
                        mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mPreviewRequestBuilder.addTarget(surface);

                // Create a CameraCaptureSession for camera preview
                mCameraDevice.createCaptureSession(Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }
                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        null /* listener */, mHandler);
                            } catch (CameraAccessException e) {
                                Log.e(TAG, "Unable to access camera", e);
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG, "Unable to configure camera");
                        }
                    }, null /* handler */);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            mCameraDevice = null;
        }
    };

    public Surface getPreviewSurface() {
        Log.d(TAG, "startPreviewEnrollment begin");
        try {
            final SurfaceTexture texture = mTextureView.getSurfaceTexture();

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(WIDTH, HEIGHT);

            // This is the output Surface we need to start preview.
            final Surface surface = new Surface(texture);
            return surface;
        } catch (Exception e) {
            Log.d(TAG, "startEnrollment Exception", e);
        }
        return null;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.FACE_ENROLL_PREVIEW;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTextureView = getActivity().findViewById(R.id.texture_view);
        mCircleView = getActivity().findViewById(R.id.circle_view);

        // Must disable hardware acceleration for this view, otherwise transparency breaks
        mCircleView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        mAnimationDrawable = new FaceEnrollAnimationDrawable(getContext(), mAnimationListener);
        mCircleView.setImageDrawable(mAnimationDrawable);

        mCameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
    }

    @Override
    public void onResume() {
        super.onResume();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            if (USING_UNISOC_FACEID) {
                mStartedListener.onEnrollStarted();
            } else {
                openCamera(mTextureView.getWidth(), mTextureView.getHeight());
            }
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        closeCamera();
    }

    @Override
    public void onEnrollmentError(int errMsgId, CharSequence errString) {
        mAnimationDrawable.onEnrollmentError(errMsgId, errString);
    }

    @Override
    public void onEnrollmentHelp(int helpMsgId, CharSequence helpString) {
        mAnimationDrawable.onEnrollmentHelp(helpMsgId, helpString);
    }

    @Override
    public void onEnrollmentProgressChange(int steps, int remaining) {
        mAnimationDrawable.onEnrollmentProgressChange(steps, remaining);
        if (mTextureView != null) {
            setCameraRotation(mTextureView.getWidth(),mTextureView.getHeight());
        }
    }

    public void setListener(ParticleCollection.Listener listener) {
        mListener = listener;
    }

    public void setStartedListener(FaceEnrollEnrolling.StartedListener listener) {
        mStartedListener = listener;
    }

    /**
     * Sets up member variables related to camera.
     */
    private void setUpCameraOutputs() {
        try {
            for (String cameraId : mCameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics =
                        mCameraManager.getCameraCharacteristics(cameraId);

                // Find front facing camera
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                mCameraId = cameraId;

                // Get the stream configurations
                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class));
                break;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Unable to access camera", e);
        }
    }

    /**
     * Opens the camera specified by mCameraId.
     * @param width  The width of the texture view
     * @param height The height of the texture view
     */
    private void openCamera(int width, int height) {
        try {
            setUpCameraOutputs();
            mCameraManager.openCamera(mCameraId, mCameraStateCallback, mHandler);
            configureTransform(width, height);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Unable to open camera", e);
        }
    }

    /**
     * Chooses the optimal resolution for the camera to open.
     */
    private Size chooseOptimalSize(Size[] choices) {
        for (int i = 0; i < choices.length; i++) {
            if (choices[i].getHeight() == MAX_PREVIEW_HEIGHT
                    && choices[i].getWidth() == MAX_PREVIEW_WIDTH) {
                return choices[i];
            }
        }
        Log.w(TAG, "Unable to find a good resolution");
        return choices[0];
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (mTextureView == null) {
            return;
        }

        // Fix the aspect ratio
        float scaleX = (float) viewWidth / (USING_UNISOC_FACEID ? WIDTH : mPreviewSize.getWidth());
        float scaleY = (float) viewHeight / (USING_UNISOC_FACEID ? HEIGHT : mPreviewSize.getHeight());

        // Now divide by smaller one so it fills up the original space.
        float smaller = Math.min(scaleX, scaleY);
        scaleX = scaleX / smaller;
        scaleY = scaleY / smaller;

        // Apply the transformation/scale
        mTextureView.setTranslationX(getResources().getDimension(R.dimen.face_preview_translate_x));
        mTextureView.setTranslationY(getResources().getDimension(R.dimen.face_preview_translate_y));

        final TypedValue scale = new TypedValue();
        getResources().getValue(R.dimen.face_preview_scale, scale, true /* resolveRefs */);
        mTextureView.setScaleX(scaleX * scale.getFloat());
        mTextureView.setScaleY(scaleY * scale.getFloat());

        setCameraRotation(viewWidth, viewHeight);
    }

    private int mDeviceRotation = 0;

    private void setCameraRotation(int viewWidth, int viewHeight) {
        int rotation = getPrefContext().getDisplay().getOrientation();
        if (mDeviceRotation != rotation) {
            Log.d(TAG, "setCameraRotation rotation =" + rotation);
            mDeviceRotation = rotation;
            Matrix matrix = new Matrix();
            RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
            RectF bufferRect = new RectF(0, 0, HEIGHT, WIDTH);
            float centerX = viewRect.centerX();
            float centerY = viewRect.centerY();
            if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
                bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
                matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
                float scaled = Math.max(
                        (float) viewHeight / HEIGHT,
                        (float) viewWidth / WIDTH);
                matrix.postScale(scaled, scaled, centerX, centerY);
                matrix.postRotate(90 * (rotation - 2), centerX, centerY);
            } else if (Surface.ROTATION_180 == rotation) {
                matrix.postRotate(180, centerX, centerY);
            }
            mTextureView.setTransform(matrix);
        }
    }

    private void closeCamera() {
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }
}
