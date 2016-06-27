package com.eulerspace.eel.udprx;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaFormat;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.util.Date;

public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private static String TAG = "UDP";

    static {

        System.loadLibrary("JniTest");
    }

    Handler handler;
    WifiManager wm;
    WifiManager.MulticastLock lock;
    private UdpReceiverDecoderThread mRec = null;

    public native String getHello();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        //window settings
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        wm = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        lock = wm.createMulticastLock("broadcast_lock");

        SurfaceView sv = new SurfaceView(this);
        sv.getHolder().addCallback(this);
        setContentView(sv);
//        MediaRecorder mediaRecorder;
//        File f;
        handler = new Handler() {
            @Override
            public void handleMessage(Message m) {
                Toast.makeText(MainActivity.this, "m:" + m.arg1, Toast.LENGTH_SHORT).show();

            }
        };

    }

    protected void onPause() {
        super.onPause();
        Log.i("UDP", "on pause" + getHello());

        //ViewRootImpl l;
        //finish();

    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i("UDP", "onresume");
        Log.e(TAG, "onresume");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mRec != null) {
            mRec.stopSocket();
        }

        Log.i("UDP", "ondestroyed");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d("UDP", "created");
        mRec = new UdpReceiverDecoderThread(holder.getSurface(), 5000);
        mRec.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d("UDP", "surface changed");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("UDP", "surface destroyed");
        mRec.interrupt();
        mRec = null;
    }


    private class UdpReceiverDecoderThread extends Thread {
        final boolean isMulticast = true;
        int port;
        int nalu_search_state = 0;
        byte[] nalu_data;
        int nalu_data_position;
        int NALU_MAXLEN = 1024 * 1024;
        long dbg = 0;
        Handler h = null;
        DatagramSocket s = null;
        boolean stop = false;
        private MediaCodec decoder = null;
        private MediaFormat format = null;
        private File file = null;
        private FileOutputStream fos = null;

        public UdpReceiverDecoderThread(Surface surface, int port) {

            this.port = port;

            nalu_data = new byte[NALU_MAXLEN];
            nalu_data_position = 0;
//            byte[] sps = {0, 0, 0, 1, 103, 100, 0, 40, -84, 52, -59, 1, -32, 17, 31, 120, 11, 80, 16, 16, 31
//                    , 0, 0, 3, 3, -23, 0, 0, -22, 96, -108};
//            byte[] pps = {0, 0, 0, 1, 104, -18, 60, -128};
            try {
                decoder = MediaCodec.createDecoderByType("video/avc");
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }

            format = MediaFormat.createVideoFormat("video/avc", 1920, 1080);
//            format.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
//            format.setByteBuffer("csd-1", ByteBuffer.wrap(pps));
            decoder.configure(format, surface, null, 0);
            //decoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);

            decoder.start();
            h = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    Log.i(TAG, "handle stop");
                    stop = true;
                }
            };

        }

        public void stopSocket() {
            if (h != null) {
                h.sendMessage(new Message());
            }
        }

        public void run() {
            int server_port = this.port;
            byte[] message = new byte[1024 * 64];
            setPriority(Thread.MAX_PRIORITY);
            DatagramPacket p = new DatagramPacket(message, message.length);

            if (isMulticast) {
                try {
                    s = new MulticastSocket(server_port);
                    String BROADCAST_IP = "239.10.0.0";
                    //IP协议多点广播地址范围:224.0.0.0---239.255.255.255,其中224.0.0.0为系统自用
                    InetAddress serverAddress = InetAddress.getByName(BROADCAST_IP);
                    //((MulticastSocket) s).joinGroup(serverAddress);
                    if (NetworkInterface.getByName("wlan0") == null)
                        Log.e(TAG, "wlan0 is null");
                    ((MulticastSocket) s).joinGroup(new InetSocketAddress(serverAddress, server_port), NetworkInterface.getByName("wlan0"));
                    //s.receive(p);
                    Log.i(TAG, "receive from mm socket");
                } catch (Exception e) {
                    //// TODO: 2016-06-21

                    Log.e(TAG, "HH mm Exception" + e.toString());

                }
            } else {
                try {
                    s = new DatagramSocket(server_port);
                    Log.i(TAG, "create Socket");
                    //s.setReuseAddress(true);
                    //s.setSoTimeout(20000);
                } catch (SocketException e) {
                    Log.e(TAG, e.getMessage());
                    e.printStackTrace();
                } catch (Exception e) {
                    Log.e(TAG, "HH Exception");
                    e.printStackTrace();
                }
            }
            Log.i(TAG, "begin rcv acquire ");
            lock.acquire();
            long t1 = new Date().getTime();
            long t2 = new Date().getTime();
            long rcv_len = 0;
            Log.i(TAG, "begin rcv  ");
            while (!Thread.interrupted() && s != null && !stop) {
                try {
                    s.receive(p);
                    t2 = new Date().getTime();
                    parseDatagram(p.getData(), p.getLength());
                    writeToFile(p.getData(), p.getLength());
                    rcv_len += p.getLength();
                    Log.i(TAG, "rcv len " + p.getLength() + " " + rcv_len / (t2 - t1) * 1000 / 1024 + "KB/S " + (isMulticast ? "MC" : "UDP"));

                } catch (IOException e) {
                    Log.e(TAG, "IOException");
                    //e.printStackTrace();
                }
            }

            Log.i(TAG, "stop ,close the socket and release resource");
            releaseResource();

        }

        private void writeToFile(byte[] data, int len) {
            Log.e(TAG, "writeToFile" + data[0] + data[1] + data[2] + data[3] + " --" + len);
            if (file == null) {
                file = new File("/sdcard/h264rx.bin");
                if (!file.exists())
                    try {
                        file.createNewFile();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                try {
                    fos = new FileOutputStream(file);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    Log.e(TAG, "file open fail");
                    return;
                }
            }
            try {
                fos.write(data, 0, len);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "file write fail");
            }


        }

        private void releaseResource() {
            if (s != null) {
                s.close();
                s = null;
            }
            if (lock != null) {
                lock.release();
                lock = null;
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
                decoder = null;
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            file = null;
        }

        private void feedDecoder(byte[] n, int len) {
            Log.i(TAG, "feedDecoder " + n[0] + n[1] + n[2] + n[3] + "  len =" + len);
            ByteBuffer[] inputBuffers = decoder.getInputBuffers();
            ByteBuffer[] outputBuffers = decoder.getOutputBuffers();

            int inputBufferIndex = decoder.dequeueInputBuffer(0);
            if (inputBufferIndex >= 0) {
                // fill inputBuffers[inputBufferIndex] with valid data
                //Log.i(TAG, "in index:" + inputBufferIndex);
                inputBuffers[inputBufferIndex].clear();
                inputBuffers[inputBufferIndex].put(n);
                decoder.queueInputBuffer(inputBufferIndex, 0, len, 0, 0);
            }

            BufferInfo bi = new MediaCodec.BufferInfo();
            int outputBufferIndex = decoder.dequeueOutputBuffer(bi, 0);
            //Log.i(TAG, "out index1 :" + outputBufferIndex);


            while (outputBufferIndex >= 0) {
                // outputBuffer is ready to be processed or rendered.
                //Log.i(TAG, "out index:" + outputBufferIndex);
                decoder.releaseOutputBuffer(outputBufferIndex, true);
                outputBufferIndex = decoder.dequeueOutputBuffer(bi, 0);
            }
            if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = decoder.getOutputBuffers();
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // Subsequent data will conform to new format.
                MediaFormat format = decoder.getOutputFormat();
            }

        }

        private void interpretNalu(byte[] n, int len) {
            //int id = n[4] & 0xff;
            feedDecoder(n, len);
        }

        private void parseDatagram(byte[] p, int plen) {
            int i;
            // Log.e(TAG, "parseDatagram" + p[0]  + p[1] + p[2] + p[3] + " --" + plen);
            //Log.i(TAG, new String(p));
            if (true) {
                if ((p[0] == 0 && p[1] == 0 && p[2] == 0 && p[3] == 1)
                        || (p[0] == 0 && p[1] == 0 && p[2] == 1))
                    ;
                else
                    Log.e(TAG, "header error , not nalu,maybe should drop");
                feedDecoder(p, plen);
                return;
            }
            for (i = 0; i < plen; ++i) {
                nalu_data[nalu_data_position++] = p[i];
                if (nalu_data_position == NALU_MAXLEN - 1) {
                    Log.i("UDP", "Nalu overflow");

                    nalu_data_position = 0;
                }

                switch (nalu_search_state) {
                    case 0:
                    case 1:
                    case 2:
                        if (p[i] == 0)
                            nalu_search_state++;
                        else
                            nalu_search_state = 0;
                        break;

                    case 3:
                        if (p[i] == 1) {
                            //nalupacket found
                            nalu_data[0] = 0;
                            nalu_data[1] = 0;
                            nalu_data[2] = 0;
                            nalu_data[3] = 1;
                            //interpretNalu(nalu_data,nalu_data_position-4);
                            interpretNalu(p, plen);
                            nalu_data_position = 4;
                        }
                        nalu_search_state = 0;

                        break;

                    default:
                        Log.e(TAG, "not nalu:" + p.toString());
                        Message m = new Message();
                        m.arg1 = p[0];
                        handler.sendMessage(m);
                        break;


                }
            }
        }
    }
}