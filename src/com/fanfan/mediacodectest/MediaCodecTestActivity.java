package com.fanfan.mediacodectest;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.SystemClock;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.io.*;
import java.util.*;


public class MediaCodecTestActivity extends Activity {
    private static final String TAG = "MediaCodecTestActivity";
    private static final int VIDEO_WIDTH  = 1920;
    private static final int VIDEO_HEIGHT = 1088;
    private static final int VIDEO_FRATE  = 30;
    private static final int VIDEO_BITRATE= 10000000;

    private TextView         mTxtInfo;
    private Button           mBtnStartNand;
    private Button           mBtnStartExtsd;
    private Button           mBtnStartNoWrite;
    private Button           mBtnCpuTest;
    private Button           mBtnStop;
    private H264HwEncoder    mEncoder;
    private byte[]           mYuvData  = new byte[VIDEO_WIDTH * VIDEO_HEIGHT * 12 / 8];
    private byte[]           mH264Data = null;
    private FileOutputStream mH264FOS  = null;
    private long             mStartTime= 0;
    private Random           mRandom   = new Random();
    private CpuTestThread    mCpuTest1 = null;
    private CpuTestThread    mCpuTest2 = null;
    private CpuTestThread    mCpuTest3 = null;
    private CpuTestThread    mCpuTest4 = null;
    private int              mTestTypeN= 0;
    private String[]         mTestTypeS= new String[] {"test record to nand: ", "test record to extsd: ", "test encode only: ", "cpu test only: "};

    private static final int MSG_H264_ENCODE = 1;
    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_H264_ENCODE:
                mHandler.sendEmptyMessageDelayed(MSG_H264_ENCODE, 1000 / VIDEO_FRATE);
                if (mTestTypeN >= 0 && mTestTypeN <= 2) {
                    randVideoData(mYuvData);
                    if (mEncoder.enqueueInputBuffer(mYuvData, SystemClock.uptimeMillis() - mStartTime, 1000)) {
                        mH264Data = mEncoder.dequeueOutputBuffer(1000);
                        if (mH264Data != null && mH264FOS != null) {
                            try {
                                mH264FOS.write(mH264Data);
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    }
                }
                if (mTestTypeN >= 0 && mTestTypeN <= 3) {
                    SimpleDateFormat f = new SimpleDateFormat("mm:ss");
                    String time = f.format(SystemClock.uptimeMillis() - mStartTime - TimeZone.getDefault().getRawOffset());
                    mTxtInfo.setText(mTestTypeS[mTestTypeN] + time);
                }
                break;
            }
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        mTxtInfo         = (TextView)findViewById(R.id.txt_info         );
        mBtnStartNand    = (Button  )findViewById(R.id.btn_start_nand   );
        mBtnStartExtsd   = (Button  )findViewById(R.id.btn_start_extsd  );
        mBtnStartNoWrite = (Button  )findViewById(R.id.btn_start_nowrite);
        mBtnCpuTest      = (Button  )findViewById(R.id.btn_cpu_test     );
        mBtnStop         = (Button  )findViewById(R.id.btn_stop         );
        mBtnStartNand   .setOnClickListener(mOnClickListener);
        mBtnStartExtsd  .setOnClickListener(mOnClickListener);
        mBtnStartNoWrite.setOnClickListener(mOnClickListener);
        mBtnCpuTest     .setOnClickListener(mOnClickListener);
        mBtnStop        .setOnClickListener(mOnClickListener);
        mEncoder = new H264HwEncoder();
        mEncoder.init(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FRATE, VIDEO_BITRATE);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        mEncoder.free();
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private View.OnClickListener mOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
            case R.id.btn_start_nand:
                stopTest();
                mTestTypeN = 0;
                try {
                    mH264FOS = new FileOutputStream(new File("/mnt/sdcard/test.h264"));
                } catch (Exception e) { e.printStackTrace(); }
                mStartTime = SystemClock.uptimeMillis();
                mHandler.sendEmptyMessageDelayed(MSG_H264_ENCODE, 1000 / VIDEO_FRATE);
                break;
            case R.id.btn_start_extsd:
                stopTest();
                mTestTypeN = 1;
                try {
                    mH264FOS = new FileOutputStream(new File("/mnt/extsd/test.h264"));
                } catch (Exception e) { e.printStackTrace(); }
                mStartTime = SystemClock.uptimeMillis();
                mHandler.sendEmptyMessageDelayed(MSG_H264_ENCODE, 1000 / VIDEO_FRATE);
                break;
            case R.id.btn_start_nowrite:
                stopTest();
                mTestTypeN = 2;
                mStartTime = SystemClock.uptimeMillis();
                mHandler.sendEmptyMessageDelayed(MSG_H264_ENCODE, 1000 / VIDEO_FRATE);
                break;
            case R.id.btn_cpu_test:
                stopTest();
                mTestTypeN = 3;
                mCpuTest1 = new CpuTestThread();
                mCpuTest2 = new CpuTestThread();
                mCpuTest3 = new CpuTestThread();
                mCpuTest4 = new CpuTestThread();
                mCpuTest1.start();
                mCpuTest2.start();
                mCpuTest3.start();
                mCpuTest4.start();
                mStartTime = SystemClock.uptimeMillis();
                mHandler.sendEmptyMessageDelayed(MSG_H264_ENCODE, 1000 / VIDEO_FRATE);
                break;
            case R.id.btn_stop:
                stopTest();
                break;
            }
        }
    };

    private void stopTest() {
        mHandler.removeMessages(MSG_H264_ENCODE);
        try {
            mH264FOS.close();
        } catch (Exception e) { e.printStackTrace(); }
        mH264FOS = null;
        if (mCpuTest1 != null) mCpuTest1.exit();
        if (mCpuTest2 != null) mCpuTest2.exit();
        if (mCpuTest3 != null) mCpuTest3.exit();
        if (mCpuTest4 != null) mCpuTest4.exit();
        mTxtInfo.setText("stopped");
    }

    private void randVideoData(byte[] data) {
        byte[] temp = new byte[(VIDEO_WIDTH/16) * (VIDEO_HEIGHT/16) * 12 / 8];
        for (int i=0; i<temp.length; i++) {
            temp[i] = (byte)mRandom.nextInt(256);
        }
        for (int y=0; y<VIDEO_HEIGHT; y++) {
            for (int x=0; x<VIDEO_WIDTH; x++) {
                int dsty   = y * VIDEO_WIDTH + x;
                int srcy   = (y/16) * (VIDEO_WIDTH/16) + (x/16);
                data[dsty] = temp[srcy];
                if (y < VIDEO_HEIGHT/2) {
                    int dstuv   = VIDEO_WIDTH * VIDEO_HEIGHT + dsty;
                    int srcuv   = (VIDEO_WIDTH/16) * (VIDEO_HEIGHT/16) + srcy;
                    data[dstuv] = temp[srcuv];
                }
            }
        }
    }

    class CpuTestThread extends Thread {
        private boolean mExit = false;

        @Override
        public void run() {
            while (!mExit) {
                randVideoData(mYuvData);
            }
        }

        public void exit() {
            mExit = true;
        }
    }
}



