package com.example.codecsender;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import com.example.codecsender.encode.AvcEncoder;
import com.example.codecsender.rtp.RtpSenderWrapper;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback, Camera.PreviewCallback {
    private static final String TAG = "MainActivity";
    DatagramSocket socket;
    InetAddress address;

    AvcEncoder avcCodec;
    public Camera m_camera;
    SurfaceView m_prevewview;
    SurfaceHolder m_surfaceHolder;
    //屏幕分辨率，每个机型不一样，机器连上adb后输入wm size可获取
    int width = 1920;
    int height = 1080;
    int framerate = 30;//每秒帧率
    int bitrate = 2500000;//编码比特率，
    private RtpSenderWrapper mRtpSenderWrapper;

    byte[] h264 = new byte[width*height*3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectAll()   // or .detectAll() for all detectable problems
                .penaltyLog()
                .build());
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .penaltyDeath()
                .build());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestPermissions();
        mRtpSenderWrapper = new RtpSenderWrapper("192.168.1.13", 5004, false);
        avcCodec = new AvcEncoder(width,height,framerate,bitrate);

        m_prevewview = (SurfaceView) findViewById(R.id.SurfaceViewPlay);
        m_surfaceHolder = m_prevewview.getHolder(); // 绑定SurfaceView，取得SurfaceHolder对象
        m_surfaceHolder.setFixedSize(width, height); // 预览大小設置
        //m_surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        m_surfaceHolder.addCallback((SurfaceHolder.Callback) this);
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    1);
        }
    }


    @Override
    public void onPreviewFrame(byte[] data, Camera camera) {
        Log.d(TAG, "onPreviewFrame: data.length == " + data.length);
        int ret = avcCodec.offerEncoder(data, h264);
        if(ret > 0){
            //实时发送数据流
            Log.d(TAG, "onPreviewFrame: ret > 0");
            mRtpSenderWrapper.sendAvcPacket(h264, 0, ret, 0);
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            m_camera = Camera.open();
            m_camera.setPreviewDisplay(m_surfaceHolder);
            Camera.Parameters parameters = m_camera.getParameters();
            parameters.setPreviewSize(width, height);
            parameters.setPictureSize(width, height);
            parameters.setPreviewFormat(ImageFormat.YV12);
            m_camera.setParameters(parameters);
            m_camera.setPreviewCallback((Camera.PreviewCallback) this);
            m_camera.startPreview();
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        m_camera.setPreviewCallback(null);  //！！这个必须在前，不然退出出错
        m_camera.release();
        m_camera = null;
        avcCodec.close();
    }
}