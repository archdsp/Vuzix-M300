/*
2017-10-31 Jisu Choi created.
this example is for simple camview
* */
package kr.co.kornic.smart.myapplication;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.SurfaceTexture;
import android.os.Bundle;

import android.util.Log;
import android.view.TextureView;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener
{
    private final String LOG_TAG = "CameraActivity";
    private BroadcastReceiver mVoiceCmdReceiver;
    private Camera2BasicFragment camfrag;
    TextureView camView;
    private Activity mActivity;
    VuzixCam vuzixCam;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mActivity = this;

        camView = (TextureView) findViewById(R.id.cam_view);
        camView.setSurfaceTextureListener(this);
        vuzixCam = new VuzixCam(this, camView.getWidth(), camView.getHeight());

        mVoiceCmdReceiver = new VoiceCmdReceiver();
        registerReceiver(mVoiceCmdReceiver, new IntentFilter(VuzixSpeechClient.ACTION_VOICE_COMMAND));

        try
        {
            com.vuzix.sdk.vuzixspeechclient.VuzixSpeechClient sc = new com.vuzix.sdk.vuzixspeechclient.VuzixSpeechClient(this);
            sc.deletePhrase("flashlight on");
            sc.deletePhrase("flashlight off");
            sc.defineIntent("picture", new Intent(VuzixSpeechClient.ACTION_VOICE_COMMAND).putExtra("click", true));
            sc.defineIntent("getinfo", new Intent(VuzixSpeechClient.ACTION_VOICE_COMMAND).putExtra("info", 97));
            sc.defineIntent("hello", new Intent(VuzixSpeechClient.ACTION_VOICE_COMMAND).putExtra("hello", "hello"));
            sc.insertPhrase("hello");
            sc.insertPhrase("photo", "s:snapit");
            sc.insertPhrase("do dialog");
            sc.insertPhrase("okay", "s:&K_ENTER");
            sc.insertIntentPhrase("pic", "picture");
            sc.insertIntentPhrase("give me information", "getinfo");
            Log.i(LOG_TAG, sc.dump());
        }
        catch (Exception e)
        {
            Log.e(LOG_TAG, "Error setting custom vocabulary: " + e.getMessage());
        }

    }

    @Override
    protected void onResume()
    {
        super.onResume();

        if (camView.isAvailable())
        {
            vuzixCam.openCamera();
        }
        else
        {
            camView.setSurfaceTextureListener(this);
        }
    }


    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        unregisterReceiver(mVoiceCmdReceiver);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height)
    {
        vuzixCam.setPreview(surface);
        vuzixCam.openCamera();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    public class VoiceCmdReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String phrase = intent.getStringExtra("phrase");
            Log.d("jisu", phrase) ;
            if (intent.getAction().equals(VuzixSpeechClient.ACTION_VOICE_COMMAND))
            {
                phrase = intent.getStringExtra("phrase");
                if (phrase != null && phrase.equals("snapit"))
                    camfrag.takePicture();

                else if (phrase != null && phrase.equals("do_dialog"))
                {
                    new AlertDialog.Builder(mActivity)
                            .setMessage(R.string.intro_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                }

                else if (intent.getBooleanExtra("click", false))
                    camfrag.takePicture();

                else if (intent.getIntExtra("info", 0) == 97)
                    new AlertDialog.Builder(mActivity)
                            .setMessage(R.string.intro_message)
                            .setPositiveButton(android.R.string.ok, null)
                            .show();

            }
        }
    }
}

