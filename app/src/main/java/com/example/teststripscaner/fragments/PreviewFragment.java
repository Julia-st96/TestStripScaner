package com.example.teststripscaner.fragments;

import android.Manifest;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.teststripscaner.R;
import com.example.teststripscaner.models.ImageViewModel;
import com.example.teststripscaner.utils.ImageSaver;
import com.example.teststripscaner.utils.ViewfinderView;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class PreviewFragment extends Fragment {

    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT = 1;
    private ImageViewModel mImageViewModel;
    private HandlerThread mBackgroundHandlerThread;
    private Handler mBackgroundHandler;

    private CameraService mCameraService;

    private ImageButton mCaptureImageButton;
    private ViewfinderView mViewfinderView;

    private SurfaceView mPreviewSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private SurfaceHolder.Callback mSurfaceListener = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            if (holder != null) {
                mSurfaceHolder = holder;
                mCameraService.setupCamera(mSurfaceHolder.getSurfaceFrame().width(), mSurfaceHolder.getSurfaceFrame().height());
                mCameraService.connectCamera();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }
    };
    private File mImageFolder;
    private String mImageFileName;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        createImageFolder();

        CameraManager cameraManager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        mCameraService = new CameraService(cameraManager);

    }

    private void createImageFolder() {
        File imageFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        mImageFolder = new File(imageFile, "MyCamera");
        if (!mImageFolder.exists()) {
            mImageFolder.mkdirs();
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_preview, container, false);

        mImageViewModel = new ViewModelProvider(requireActivity()).get(ImageViewModel.class);

        mViewfinderView = view.findViewById(R.id.viewfinderView);
        mPreviewSurfaceView = view.findViewById(R.id.previewSurfaceView);
        mCaptureImageButton = view.findViewById(R.id.cameraImageButton);

        mCaptureImageButton.setOnClickListener(v -> {
            captureImage();
        });

        return view;
    }

    @AfterPermissionGranted(REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT)
    private void captureImage() {
        String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE};
        if (EasyPermissions.hasPermissions(getActivity(), perms)) {
            mCameraService.lockFocus();
        } else {
            EasyPermissions.requestPermissions(getActivity(), getString(R.string.write_external_starage_request_rationale),
                    REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT, perms);
        }
    }

    @Override
    public void onPause() {
        mCameraService.closeCamera();

        stopBackgroundThread();

        mPreviewSurfaceView.getHolder().removeCallback(mSurfaceListener);

        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();

        startBackgroundThread();

        if (mPreviewSurfaceView.isActivated()) {
            mCameraService.connectCamera();
        } else {
            mPreviewSurfaceView.getHolder().addCallback(mSurfaceListener);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void startBackgroundThread() {
        mBackgroundHandlerThread = new HandlerThread("");
        mBackgroundHandlerThread.start();
        mBackgroundHandler = new Handler(mBackgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        mBackgroundHandlerThread.quitSafely();
        try {
            mBackgroundHandlerThread.join();
            mBackgroundHandlerThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private File createImageFileName() throws IOException {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String prepend = "IMAGE_" + timestamp + "_";
        File imageFile = File.createTempFile(prepend, ".jpg", mImageFolder);
        mImageFileName = imageFile.getAbsolutePath();
        return imageFile;
    }

    private class CameraService {
        private static final int STATE_PREVIEW = 0;
        private static final int STATE_CAPTURE = 1;
        private static final int SENSOR_DEFAULT_ORIENTATION_DEGREES = 90;
        private static final int SENSOR_INVERSE_ORIENTATION_DEGREES = 270;
        private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new
                ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        Image image = reader.acquireLatestImage();
                        Bitmap bitmapPicture = convertImageToBitmap(image);

                        Bitmap croppedBitmap = null;

                        int degrees = 0;
                        Display display = ((WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
                        if (display.getRotation() == Surface.ROTATION_0) {
                            degrees = 90;
                        }

                        Matrix matrix = new Matrix();
                        matrix.postRotate(90);
                        Bitmap rotatedBitmap = Bitmap.createBitmap(bitmapPicture, 0, 0, bitmapPicture.getWidth(), bitmapPicture.getHeight(), matrix, true);

                        float koefX = (float) rotatedBitmap.getWidth() / (float) mPreviewSurfaceView.getWidth();
                        float koefY = (float) rotatedBitmap.getHeight() / (float) mPreviewSurfaceView.getHeight();

                        int x1 = mViewfinderView.getFrameLeft();
                        int y1 = mViewfinderView.getFrameTop();

                        int x2 = mViewfinderView.getFrameWidth();
                        int y2 = mViewfinderView.getFrameHeight();

                        int cropStartX = Math.round(x1 * koefX);
                        int cropStartY = Math.round(y1 * koefY);

                        int cropWidthX = Math.round(x2 * koefX);
                        int cropHeightY = Math.round(y2 * koefY);

                        if (cropStartX + cropWidthX <= rotatedBitmap.getWidth() && cropStartY + cropHeightY <= rotatedBitmap.getHeight()) {
                            croppedBitmap = Bitmap.createBitmap(rotatedBitmap, cropStartX, cropStartY, cropWidthX, cropHeightY);
                        } else {
                            croppedBitmap = null;
                        }

                        mImageViewModel.bitmap = croppedBitmap;
                        mImageViewModel.isNotCalculated = true;

                        mBackgroundHandler.post(new ImageSaver(image, mImageFileName));

                        getActivity().getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragment_container, new ResultFragment())
                                .addToBackStack(null)
                                .commit();

                    }
                };
        private int mCameraState = STATE_PREVIEW;
        private SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
        private SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
        private CameraManager mCameraManager;
        private String mCameraId;
        private CameraDevice mCamera;
        private Size mPreviewSize;
        private Size mImageSize;
        private int mTotalRotation;
        private CaptureRequest.Builder mCaptureRequestBuilder;
        private CameraCaptureSession mCaptureSession;
        private ImageReader mImageReader;
        private CameraDevice.StateCallback mCameraStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                mCamera = camera;
                startPreview();
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                camera.close();
                mCamera = null;
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                camera.close();
                mCamera = null;
            }
        };
        private CameraCaptureSession.CaptureCallback mCaptureCallback = new
                CameraCaptureSession.CaptureCallback() {

                    private void process(CaptureResult captureResult) {
                        switch (mCameraState) {
                            case STATE_PREVIEW:
                                break;
                            case STATE_CAPTURE:
                                mCameraState = STATE_PREVIEW;
                                Integer afState = captureResult.get(CaptureResult.CONTROL_AF_STATE);
                                if (afState == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                                        afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                                    startCaptureRequest();
                                }
                                break;
                        }
                    }

                    @Override
                    public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                        super.onCaptureCompleted(session, request, result);
                        process(result);
                    }
                };

        {
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
            DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
        }

        {
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
            INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
        }

        public CameraService(CameraManager cameraManager) {
            mCameraManager = cameraManager;
        }

        @AfterPermissionGranted(REQUEST_CAMERA_PERMISSION_RESULT)
        private void connectCamera() {
            String[] perms = {Manifest.permission.CAMERA};
            if (EasyPermissions.hasPermissions(getActivity(), perms)) {
                try {
                    mCameraManager.openCamera(mCameraId, mCameraStateCallback, mBackgroundHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            } else {
                EasyPermissions.requestPermissions(getActivity(), getString(R.string.camera_request_rationale),
                        REQUEST_CAMERA_PERMISSION_RESULT, perms);
            }
        }

        private void closeCamera() {
            if (mCamera != null) {
                mCamera.close();
                mCamera = null;
            }
        }

        private void lockFocus() {
            mCameraState = STATE_CAPTURE;
            mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            try {
                mCaptureSession.capture(mCaptureRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        private void setupCamera(int width, int height) {
            try {
                for (String cameraId : mCameraManager.getCameraIdList()) {
                    CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
                    if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) ==
                            CameraCharacteristics.LENS_FACING_FRONT) {
                        continue;
                    }
                    StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    int deviceOrientation = getActivity().getWindowManager().getDefaultDisplay().getRotation();
                    mTotalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
                    boolean swapRotation = mTotalRotation == 90 || mTotalRotation == 270;
                    int rotatedWidth = width;
                    int rotatedHeight = height;
                    if (swapRotation) {
                        rotatedWidth = height;
                        rotatedHeight = width;
                    }
                    mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);
                    mImageSize = chooseOptimalSize(map.getOutputSizes(ImageFormat.JPEG), rotatedWidth, rotatedHeight);
                    mImageReader = ImageReader.newInstance(mImageSize.getWidth(), mImageSize.getHeight(), ImageFormat.JPEG, 1);
                    mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                    mCameraId = cameraId;
                    return;
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        private void startPreview() {
            Surface previewSurface = mSurfaceHolder.getSurface();

            try {
                mCaptureRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                mCaptureRequestBuilder.addTarget(previewSurface);

                mCamera.createCaptureSession(Arrays.asList(previewSurface, mImageReader.getSurface()),
                        new CameraCaptureSession.StateCallback() {
                            @Override
                            public void onConfigured(CameraCaptureSession session) {
                                mCaptureSession = session;

                                try {
                                    mCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(),
                                            null, mBackgroundHandler);
                                } catch (CameraAccessException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onConfigureFailed(CameraCaptureSession session) {

                            }
                        }, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        private void startCaptureRequest() {
            try {
                mCaptureRequestBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                mCaptureRequestBuilder.addTarget(mImageReader.getSurface());
                mCaptureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, mTotalRotation);

                CameraCaptureSession.CaptureCallback stillCaptureCallback = new
                        CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                                super.onCaptureStarted(session, request, timestamp, frameNumber);

                                try {
                                    createImageFileName();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        };

                mCaptureSession.capture(mCaptureRequestBuilder.build(), stillCaptureCallback, null);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        private Bitmap convertImageToBitmap(Image image) {
            ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            return bitmap;
        }

        private int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int deviceOrientation) {
            int rotation = 0;
            int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            switch (sensorOrientation) {
                case SENSOR_DEFAULT_ORIENTATION_DEGREES:
                    rotation = DEFAULT_ORIENTATIONS.get(deviceOrientation);
                    break;
                case SENSOR_INVERSE_ORIENTATION_DEGREES:
                    rotation = INVERSE_ORIENTATIONS.get(deviceOrientation);
                    break;
                default:

            }
            return rotation;
        }

        private Size chooseOptimalSize(Size[] choices, int width, int height) {
            List<Size> bigEnough = new ArrayList<Size>();
            for (Size option : choices) {
                if (option.getHeight() == option.getWidth() * height / width &&
                        option.getWidth() >= width && option.getHeight() >= height) {
                    bigEnough.add(option);
                }
            }
            if (bigEnough.size() > 0) {
                return Collections.min(bigEnough, new CompareSizeByArea());
            } else {
                return choices[0];
            }
        }

        private class CompareSizeByArea implements Comparator<Size> {

            @Override
            public int compare(Size lhs, Size rhs) {
                return Long.signum((long) (lhs.getWidth() * lhs.getHeight()) -
                        (long) (rhs.getWidth() * rhs.getHeight()));
            }
        }
    }
}
