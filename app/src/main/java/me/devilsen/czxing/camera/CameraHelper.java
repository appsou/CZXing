package me.devilsen.czxing.camera;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.view.SurfaceHolder;

import java.io.IOException;
import java.util.Collections;

import me.devilsen.czxing.BarCodeUtil;

/**
 * @author : dongSen
 * date : 2019-06-29 13:59
 * desc :
 */
final class CameraHelper implements ICamera, SurfaceHolder.Callback {

    private Context context;
    private Camera camera;
    private Camera.Parameters params;
    private SurfaceHolder surfaceHolder;

    private CameraConfigurationManager mCameraConfiguration;

    private boolean mPreviewing = true;
    private boolean mSurfaceCreated = false;

    CameraHelper(Context context) {
        this.context = context;
    }

    void setCamera(Camera camera, SurfaceHolder holder) {
        if (camera == null) {
            return;
        }

        this.camera = camera;
        this.surfaceHolder = holder;
        this.params = camera.getParameters();

        mCameraConfiguration = new CameraConfigurationManager(context);
        mCameraConfiguration.initFromCameraParameters(camera, params);
        surfaceHolder.addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        mSurfaceCreated = true;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (holder.getSurface() == null) {
            return;
        }
        stopCameraPreview();
        startCameraPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mSurfaceCreated = false;
        stopCameraPreview();
    }

    @Override
    public void startCameraPreview() {
        if (camera == null) {
            return;
        }
        try {
            mPreviewing = true;
            surfaceHolder.setKeepScreenOn(true);

            camera.reconnect();
            if (surfaceHolder.isCreating()) {
                camera.setPreviewDisplay(surfaceHolder);
            }
            mCameraConfiguration.setDesiredCameraParameters(camera);
            camera.startPreview();
            startContinuousAutoFocus();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void stopCameraPreview() {
        if (camera == null) {
            return;
        }
        try {
            mPreviewing = false;
            if (surfaceHolder.isCreating()) {
                camera.cancelAutoFocus();
            }
            camera.stopPreview();
            camera.setOneShotPreviewCallback(null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean flashLightAvailable() {
        return isPreviewing() && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    @Override
    public void openFlashlight() {
        if (flashLightAvailable()) {
            mCameraConfiguration.openFlashlight(camera);
        }
    }

    @Override
    public void closeFlashlight() {
        if (flashLightAvailable()) {
            mCameraConfiguration.closeFlashlight(camera);
        }
    }

    @Override
    public void zoomIn() {
        handleZoom(true);
    }

    @Override
    public void zoomOut() {
        handleZoom(false);
    }

    @Override
    public void handleFocusMetering(float originFocusCenterX, float originFocusCenterY, int originFocusWidth, int originFocusHeight) {
        boolean isNeedUpdate = false;
        Camera.Parameters focusMeteringParameters = params;
        Camera.Size size = focusMeteringParameters.getPreviewSize();
        if (focusMeteringParameters.getMaxNumFocusAreas() > 0) {
            BarCodeUtil.d("支持设置对焦区域");
            isNeedUpdate = true;
            Rect focusRect = CameraUtil.calculateFocusMeteringArea(1f,
                    originFocusCenterX, originFocusCenterY,
                    originFocusWidth, originFocusHeight,
                    size.width, size.height);
            CameraUtil.printRect("对焦区域", focusRect);
            focusMeteringParameters.setFocusAreas(Collections.singletonList(new Camera.Area(focusRect, 1000)));
            focusMeteringParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
        } else {
            BarCodeUtil.d("不支持设置对焦区域");
        }

        if (focusMeteringParameters.getMaxNumMeteringAreas() > 0) {
            BarCodeUtil.d("支持设置测光区域");
            isNeedUpdate = true;
            Rect meteringRect = CameraUtil.calculateFocusMeteringArea(1.5f,
                    originFocusCenterX, originFocusCenterY,
                    originFocusWidth, originFocusHeight,
                    size.width, size.height);
            CameraUtil.printRect("测光区域", meteringRect);
            focusMeteringParameters.setMeteringAreas(Collections.singletonList(new Camera.Area(meteringRect, 1000)));
        } else {
            BarCodeUtil.d("不支持设置测光区域");
        }


        try {
            if (isNeedUpdate) {
                if (surfaceHolder.isCreating())
                    camera.cancelAutoFocus();
                camera.setParameters(focusMeteringParameters);
                camera.autoFocus((success, camera) -> {
                    if (success) {
                        BarCodeUtil.d("对焦测光成功");
                    } else {
                        BarCodeUtil.e("对焦测光失败");
                    }
                    startContinuousAutoFocus();
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 连续对焦
     */
    private void startContinuousAutoFocus() {
        if (camera == null) {
            return;
        }

        try {
            Camera.Parameters parameters = camera.getParameters();
            // 连续对焦
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            camera.setParameters(parameters);
            // 要实现连续的自动对焦，这一句必须加上
            camera.cancelAutoFocus();
        } catch (Exception e) {
            BarCodeUtil.e("连续对焦失败");
        }
    }

    /**
     * 放大缩小
     *
     * @param isZoomIn true：缩小
     */
    private void handleZoom(boolean isZoomIn) {
        if (params.isZoomSupported()) {
            BarCodeUtil.d("不支持缩放");
            return;
        }
        int zoom = params.getZoom();
        if (isZoomIn && zoom < params.getMaxZoom()) {
            BarCodeUtil.d("Zoom Out");
            zoom++;
        } else if (!isZoomIn && zoom > 0) {
            BarCodeUtil.d("Zoom In");
            zoom--;
        } else {
            return;
        }
        params.setZoom(zoom);
        camera.setParameters(params);
    }

    Point getCameraResolution() {
        return mCameraConfiguration.getCameraResolution();
    }

    boolean isPreviewing() {
        return camera != null && mPreviewing && mSurfaceCreated;
    }
}