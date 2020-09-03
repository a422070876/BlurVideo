package com.hyq.hm.test.blur.video.glsl;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Created by 海米 on 2017/8/16.
 */

public class GLVideoRenderer {
    private int programId = -1;
    private int aPositionHandle;
    private int uTextureSamplerHandle;
    private int aTextureCoordHandle;
    private int uSTMatrixHandle;


    private int[] bos = new int[2];
    private int[] textures = new int[2];

    private int[] frameBuffers = new int[1];


    private final float[] mSTMatrix = new float[16];

    private SurfaceTexture surfaceTexture;
    private Surface surface;
    public void initShader(SurfaceTexture.OnFrameAvailableListener listener) {
        String fragmentShader = "#extension GL_OES_EGL_image_external : require\n" +
                "varying highp vec2 vTexCoord;\n" +
                "uniform samplerExternalOES sTexture;\n" +
                "uniform highp mat4 uSTMatrix;\n" +
                "void main() {\n" +
                "   highp vec2 tx_transformed = (uSTMatrix * vec4(vTexCoord, 0, 1.0)).xy;" +
                "   highp vec4 rgba = texture2D(sTexture , tx_transformed);\n" +
                "   gl_FragColor = rgba;\n" +
                "}";
        String vertexShader = "attribute vec4 aPosition;\n" +
                "attribute vec2 aTexCoord;\n" +
                "varying vec2 vTexCoord;\n" +
                "void main() {\n" +
                "  vTexCoord = aTexCoord;\n" +
                "  gl_Position = aPosition;\n" +
                "}";
        programId = ShaderUtils.createProgram(vertexShader, fragmentShader);
        aPositionHandle = GLES20.glGetAttribLocation(programId, "aPosition");
        uTextureSamplerHandle = GLES20.glGetUniformLocation(programId, "sTexture");
        aTextureCoordHandle = GLES20.glGetAttribLocation(programId, "aTexCoord");
        uSTMatrixHandle = GLES20.glGetUniformLocation(programId,"uSTMatrix");

        float[] vertexData = {
                1f, -1f, 0f,
                -1f, -1f, 0f,
                1f, 1f, 0f,
                -1f, 1f, 0f
        };


        float[] textureVertexData = {
                1f, 0f,
                0f, 0f,
                1f, 1f,
                0f, 1f
        };
        FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(vertexData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexData);
        vertexBuffer.position(0);

        FloatBuffer textureVertexBuffer = ByteBuffer.allocateDirect(textureVertexData.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(textureVertexData);
        textureVertexBuffer.position(0);

        GLES20.glGenBuffers(2, bos, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bos[0]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexData.length * 4, vertexBuffer, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bos[1]);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, textureVertexData.length * 4, textureVertexBuffer, GLES20.GL_STATIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glGenTextures(textures.length, textures, 0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);


        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1]);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);


        GLES20.glGenFramebuffers(frameBuffers.length,frameBuffers,0);


        if(surfaceTexture != null){
            surfaceTexture.release();
        }
        surfaceTexture = new SurfaceTexture(textures[0]);
        if(listener != null){
            surfaceTexture.setOnFrameAvailableListener(listener);
        }

        if(surface != null){
            surface.release();
        }
        surface = new Surface(surfaceTexture);
    }
    public int getTexture(){
        return textures[1];
    }
    public Surface getSurface() {
        if(surfaceTexture == null){
            return null;
        }
        return surface;
    }
    private int width,height;
    public void setSize(int width,int height){
        if(surfaceTexture == null){
            return;
        }
        this.width = width;
        this.height = height;
        surfaceTexture.setDefaultBufferSize(width,height);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[0]);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[1]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textures[1], 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    public void drawFrame() {
        if(surfaceTexture == null){
            return;
        }
        surfaceTexture.updateTexImage();
        surfaceTexture.getTransformMatrix(mSTMatrix);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[0]);
        GLES20.glViewport(0,0,width,height);
        GLES20.glUseProgram(programId);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,textures[0]);
        GLES20.glUniform1i(uTextureSamplerHandle,0);

        GLES20.glUniformMatrix4fv(uSTMatrixHandle,1,false,mSTMatrix,0);


        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bos[0]);
        GLES20.glEnableVertexAttribArray(aPositionHandle);
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
                0, 0);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, bos[1]);
        GLES20.glEnableVertexAttribArray(aTextureCoordHandle);
        GLES20.glVertexAttribPointer(aTextureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    public void release() {
        GLES20.glDeleteProgram(programId);
        GLES20.glDeleteFramebuffers(frameBuffers.length,frameBuffers,0);
        GLES20.glDeleteTextures(textures.length, textures, 0);
        GLES20.glDeleteBuffers(bos.length, bos, 0);
        if(surfaceTexture != null){
            surfaceTexture.release();
            surfaceTexture = null;
        }
        if(surface != null){
            surface.release();
            surface = null;
        }
    }
}
