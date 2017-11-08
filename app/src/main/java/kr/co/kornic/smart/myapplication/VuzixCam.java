package kr.co.kornic.smart.myapplication;

/**
 * Created by jisu choi on 2017-10-31.
 * 뷰직스 카메라 사용을 쉽게 하기 위한 클래스입니다.
 * 안드로이드 5.0 이상부터 camera2로 API 사용이 권장되기 때문에 기존의 camera와 매우 접근 방법이 다릅니다.
 * 따라서 사용하기 간편한 클래스를 따로 제작합니다.
 */

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
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
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentCompat;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import java.util.Arrays;


import static android.Manifest.permission.CAMERA;

public class VuzixCam
{
    // 로깅을 위한 태그 지정
    private final String LOG_TAG = "jisu";

    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraManager camManager;
    private CameraCaptureSession captureSession;

    // 화면 뒤집었을 때, 카메라 로테이션 전환
    private int sensorOrientation;

    // 플래시 지원 여부
    private boolean flashSupported;

    // 카메라 속성
    private CameraCharacteristics characteristics;

    // 카메라 속성 중에서도 스트림 관련 설정
    private StreamConfigurationMap map;

    // 카메라 해상도
    private Size camSize;

    // camera2 API 보장 최대 해상도
    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    // 카메라 호출한 액티비티
    private Activity camActivity;

    // 카메라 퍼미션 코드
    private static final int REQUEST_CAMERA_PERMISSION = 1;

    // 카메라 디바이스 콜백
    private CameraDevice.StateCallback camDeviceStateCallback = new CameraDevice.StateCallback()
    {
        @Override
        public void onOpened(@NonNull CameraDevice camera)
        {
            cameraDevice = camera;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    // 카메라 캡쳐 콜백
    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback()
    {
        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult)
        {
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {

            super.onCaptureCompleted(session, request, result);
        }
    };

    private Surface camSurface;
    private CaptureRequest.Builder previewRequestBuilder;
    private CaptureRequest previewRequest;

    /**
     * 생성자
     * */
    public VuzixCam(Activity camAtivity, int width, int height)
    {
        this.camActivity = camAtivity;
        camManager = (CameraManager) camAtivity.getSystemService(Context.CAMERA_SERVICE);
    }

    private void initCam() throws CameraAccessException
    {
        for (String cameraId : camManager.getCameraIdList())
        {
            characteristics = camManager.getCameraCharacteristics(cameraId);

            // 후면캠 사용(뷰직스 M300은 전면캠 없음)
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)
            {   continue;   }

            // fps 률, 비디오 사이즈 등등 스트림 관련 설정
            map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null)
            {   continue;   }

            // 최고해상도로 출력 설정
            Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
            camSize = sizes[0];
            for (Size size : sizes)
                if (size.getWidth() > camSize.getWidth())
                    camSize = size;

            this.cameraId = cameraId;
            return;
        }
    }

    public void openCamera()
    {
        // 카메라 권한 확인
        if (camActivity.checkSelfPermission(CAMERA) == PackageManager.PERMISSION_DENIED)
        {
            // 확인 후 권한 없으면 퍼미션 요청 다이얼로그 생성
            if(camActivity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))
                new ConfirmationDialog().show(camActivity.getFragmentManager(), "카메라 퍼미션");

            else
                camActivity.requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }

        try
        {
            initCam();
            camManager.openCamera(this.cameraId, camDeviceStateCallback, null);
        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
        }
    }

    public boolean setPreview(SurfaceTexture surface)
    {
        if(surface == null)
        {
            Log.d(LOG_TAG, "surface null!!");
            return false;
        }

        this.camSurface = new Surface(surface);
        return  true;
    }

    private boolean createCameraPreviewSession()
    {
        if(camSurface == null)
            return false;

        try
        {
            // 캡쳐 요청 빌드
            previewRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(camSurface);

            // 캡처 세션 생성
            cameraDevice.createCaptureSession(Arrays.asList(camSurface),
                    new CameraCaptureSession.StateCallback()
                    {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession)
                        {
                            if (null == cameraDevice)
                                return;

                            captureSession = cameraCaptureSession;
                            try
                            {
                                // 오토 포커스 모드 ON
                                previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                // 프리뷰 출력.
                                previewRequest = previewRequestBuilder.build();
                                captureSession.setRepeatingRequest(previewRequest, captureCallback, null);
                            }
                            catch (CameraAccessException e)
                            {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession)
                        {

                        }
                    }, null
            );
            return true;
        }
        catch (CameraAccessException e)
        {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Shows OK/Cancel confirmation dialog about camera permission.
     */
    public static class ConfirmationDialog extends DialogFragment
    {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState)
        {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.request_permission)
                    .setPositiveButton(android.R.string.ok,
                            new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialog, int which)
                                {
                                    FragmentCompat.requestPermissions(parent, new String[]{Manifest.permission.CAMERA},
                                            REQUEST_CAMERA_PERMISSION);
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener()
                            {
                                @Override
                                public void onClick(DialogInterface dialog, int which)
                                {
                                    Activity activity = parent.getActivity();
                                    if (activity != null)
                                        activity.finish();
                                }
                            })
                    .create();
        }
    }
} // end of class
