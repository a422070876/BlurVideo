package com.hyq.hm.test.blur.video.activity;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.opengl.GLES20;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelection;
import com.google.android.exoplayer2.trackselection.TrackSelector;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.hyq.hm.test.blur.video.R;
import com.hyq.hm.test.blur.video.glsl.EGLUtils;
import com.hyq.hm.test.blur.video.glsl.GLBlurRenderer;
import com.hyq.hm.test.blur.video.glsl.GLRenderer;
import com.hyq.hm.test.blur.video.glsl.GLScaleRenderer;
import com.hyq.hm.test.blur.video.glsl.GLVideoRenderer;

public class MainActivity extends AppCompatActivity {
    private Handler videoHandler;
    private HandlerThread videoThread;
    private SurfaceView surfaceView;
    private int screenWidth = 0,screenHeight = 0;
    private Rect viewRect;
    private EGLUtils eglUtils = new EGLUtils();
    private GLVideoRenderer videoRenderer = new GLVideoRenderer();
    private GLRenderer renderer = new GLRenderer();
    private GLBlurRenderer blurRenderer = new GLBlurRenderer();
    private GLScaleRenderer scaleRenderer = new GLScaleRenderer();
    private GLRenderer scaleBlurRenderer = new GLRenderer();

    private float blurScale = 1.0f;
    private int sigma = 3;
    private int radius = 2;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        videoThread = new HandlerThread("videoThread");
        videoThread.start();
        videoHandler = new Handler(videoThread.getLooper());

        surfaceView = findViewById(R.id.surface_view);
        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            private boolean isDestroyed = true;
            @Override
            public void surfaceCreated(final SurfaceHolder holder) {
                videoHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        eglUtils.initEGL(holder.getSurface());
                        renderer.initShader();
                        blurRenderer.initShader();
                        scaleRenderer.initShader();
                        scaleBlurRenderer.initShader();
                        videoRenderer.initShader(new SurfaceTexture.OnFrameAvailableListener() {
                            @Override
                            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                                if(player != null && player.getVideoFormat() != null){


                                    int width = player.getVideoFormat().width;
                                    int height = player.getVideoFormat().height;
                                    int blurScaleWidth = (int) (width*blurScale);
                                    int blurScaleHeight = (int) (height*blurScale);
                                    blurRenderer.setBlurRadius(radius);
                                    blurRenderer.setSigma(sigma);
                                    blurRenderer.setScaleSize(blurScaleWidth,blurScaleHeight);
                                    blurRenderer.gaussianWeights();


                                    if(viewRect == null && screenWidth != 0 && screenHeight != 0){
                                        viewRect = viewportSize(screenWidth,screenHeight,width,height);
                                        videoRenderer.setSize(width,height);
                                    }
                                    videoRenderer.drawFrame();
                                    scaleRenderer.setScaleSize(blurScaleWidth,blurScaleHeight);
                                    scaleRenderer.drawFrame(videoRenderer.getTexture());
                                    blurRenderer.drawFrame(scaleRenderer.getTexture());

                                    GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
                                    GLES20.glClearColor(0.0f,0.0f,0.0f,1.0f);
                                    GLES20.glEnable(GLES20.GL_BLEND);
                                    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
                                    GLES20.glViewport(0,0,screenWidth,screenHeight);
                                    scaleBlurRenderer.drawFrame(blurRenderer.getTexture());
                                    GLES20.glViewport(viewRect.left,viewRect.top,viewRect.width(),viewRect.height());
                                    renderer.drawFrame(videoRenderer.getTexture());
                                    GLES20.glDisable(GLES20.GL_BLEND);
                                    eglUtils.swap();

                                }
                            }
                        });
                    }
                });
            }

            @Override
            public void surfaceChanged(final SurfaceHolder holder, int format, int width, int height) {
                screenWidth = width;
                screenHeight = height;
                videoHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(player == null){
                            initPlayer();
                        }
                        if(isDestroyed){
                            player.setVideoSurface(videoRenderer.getSurface());
                            player.setPlayWhenReady(true);
                            isDestroyed = false;
                        }
                    }
                });

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                screenWidth = 0;
                screenHeight = 0;
                videoHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        scaleRenderer.release();
                        blurRenderer.release();
                        videoRenderer.release();
                        renderer.release();
                        scaleBlurRenderer.release();
                        eglUtils.release();
                        player.setPlayWhenReady(false);
                        isDestroyed = true;
                    }
                });
            }
        });

        final TextView sigmaTextView = findViewById(R.id.sigma_text_view);
        final TextView radiusTextView = findViewById(R.id.radius_text_view);
        final TextView sizeTextView = findViewById(R.id.size_text_view);
        sigmaTextView.setText("sigma:"+sigma);
        radiusTextView.setText("radius:"+radius);
        sizeTextView.setText("size:"+(int)(blurScale*100)+"%");

        SeekBar sigmaSeekBar = findViewById(R.id.sigma_seek_bar);
        sigmaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, final int progress, boolean fromUser) {
                if(fromUser){
                    sigma = progress;
                    sigmaTextView.setText("sigma:"+sigma);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        SeekBar radiusSeekBar = findViewById(R.id.radius_seek_bar);
        radiusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, final int progress, boolean fromUser) {
                if(fromUser){
                    radius = progress;
                    radiusTextView.setText("radius:"+radius);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });



        SeekBar sizeSeekBar = findViewById(R.id.size_seek_bar);
        sizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser){
                    if(progress < 1){
                        progress = 1;
                        seekBar.setProgress(progress);
                    }
                    blurScale = progress/100.0f;
                    sizeTextView.setText("size:"+(int)(blurScale*100)+"%");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
    }

    private SimpleExoPlayer player;

    private String videoPath = Environment.getExternalStorageDirectory().getAbsolutePath()+"/xxx/video.mp4";
    private void initPlayer(){
        
        Log.d("==============","videoPath = "+videoPath);
        Uri url = Uri.parse(videoPath);
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();


        TrackSelection.Factory videoTrackSelectionFactory = new AdaptiveTrackSelection.Factory(bandwidthMeter);
        TrackSelector trackSelector = new DefaultTrackSelector(videoTrackSelectionFactory);

        player = ExoPlayerFactory.newSimpleInstance(this, trackSelector);
        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
                Util.getUserAgent(this, "ExoPlayer"), bandwidthMeter);
        MediaSource videoSource = new ExtractorMediaSource.Factory(dataSourceFactory)
                .createMediaSource(url, null, null);
        player.prepare(videoSource);
    }
    public static Rect viewportSize(int screenWidth, int screenHeight, int videoWidth, int videoHeight) {
        int left, top, viewWidth, viewHeight;
        float sh = screenWidth * 1.0f / screenHeight;
        float vh = videoWidth * 1.0f / videoHeight;
        if (sh < vh) {
            left = 0;
            viewWidth = screenWidth;
            viewHeight = (int) (videoHeight * 1.0f / videoWidth * viewWidth);
            top = (screenHeight - viewHeight) / 2;
        } else {
            top = 0;
            viewHeight = screenHeight;
            viewWidth = (int) (videoWidth * 1.0f / videoHeight * viewHeight);
            left = (screenWidth - viewWidth) / 2;
        }
        Rect rect = new Rect();
        rect.left = left;
        rect.top = top;
        rect.right = left + viewWidth;
        rect.bottom = top + viewHeight;
        return rect;
    }
}
