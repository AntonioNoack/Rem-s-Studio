//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.newdawn.slick.openal;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.lwjgl.LWJGLUtil;

public class WaveData {
    public final ByteBuffer data;
    public final int format;
    public final int samplerate;
    // public -> can't change it

    private WaveData(ByteBuffer data, int format, int sampleRate) {
        this.data = data;
        this.format = format;
        this.samplerate = sampleRate;
    }

    public void dispose() {
        this.data.clear();
    }

    public static WaveData create(URL path) {
        try {
            return create(AudioSystem.getAudioInputStream(new BufferedInputStream(path.openStream())));
        } catch (Exception var2) {
            LWJGLUtil.log("Unable to create from: " + path);
            var2.printStackTrace();
            return null;
        }
    }

    public static WaveData create(String path) {
        return create(WaveData.class.getClassLoader().getResource(path));
    }

    public static WaveData create(InputStream is, int frameCount) {
        try {
            return create(AudioSystem.getAudioInputStream(is), frameCount);
        } catch (Exception var2) {
            LWJGLUtil.log("Unable to create from inputstream");
            var2.printStackTrace();
            return null;
        }
    }

    public static WaveData create(InputStream is) {
        try {
            return create(AudioSystem.getAudioInputStream(is), -1);
        } catch (Exception var2) {
            LWJGLUtil.log("Unable to create from inputstream");
            var2.printStackTrace();
            return null;
        }
    }

    public static WaveData create(byte[] buffer) {
        try {
            return create(AudioSystem.getAudioInputStream(new BufferedInputStream(new ByteArrayInputStream(buffer))));
        } catch (Exception var2) {
            var2.printStackTrace();
            return null;
        }
    }

    public static WaveData create(ByteBuffer buffer) {
        try {
            byte[] bytes;
            if (buffer.hasArray()) {
                bytes = buffer.array();
            } else {
                bytes = new byte[buffer.capacity()];
                buffer.get(bytes);
            }

            return create(bytes);
        } catch (Exception var2) {
            var2.printStackTrace();
            return null;
        }
    }

    public static WaveData create(AudioInputStream ais, int frameCount) {
        AudioFormat audioformat = ais.getFormat();
        int format;
        switch(audioformat.getChannels()){
            case 1:
                switch(audioformat.getSampleSizeInBits()){
                    case 8: format = 4352;break;
                    case 16:format = 4353;break;
                    default:throw new RuntimeException("Illegal sample size");
                }
                break;
            case 2:
                switch(audioformat.getSampleSizeInBits()){
                    case 8: format = 4354;break;
                    case 16:format = 4355;break;
                    default:throw new RuntimeException("Illegal sample size");
                }
                break;
            default:
                throw new RuntimeException("Only mono or stereo is supported");
        }

        // FFMPEG writes the wrong amount of frames into the file
        // we have to correct that; luckily we know the amount of frames
        int frameLength = frameCount > -1 ? frameCount : (int) ais.getFrameLength();
        int byteLength = audioformat.getChannels() * frameLength * audioformat.getSampleSizeInBits() / 8;
        byte[] buf = new byte[byteLength];

        int targetLength = 0;

        try {
            int length;
            while((length = ais.read(buf, targetLength, buf.length - targetLength)) != -1 && targetLength < buf.length) {
                targetLength += length;
            }
        } catch (IOException var10) {
            return null;
        }

        ByteBuffer buffer = convertAudioBytes(buf, audioformat.getSampleSizeInBits() == 16);
        WaveData wavedata = new WaveData(buffer, format, (int)audioformat.getSampleRate());

        try {
            ais.close();
        } catch (IOException e) { }

        return wavedata;
    }

    private static ByteBuffer convertAudioBytes(byte[] audio_bytes, boolean two_bytes_data) {
        ByteBuffer dest = ByteBuffer.allocateDirect(audio_bytes.length);
        dest.order(ByteOrder.nativeOrder());
        ByteBuffer src = ByteBuffer.wrap(audio_bytes);
        src.order(ByteOrder.LITTLE_ENDIAN);
        if (two_bytes_data) {
            ShortBuffer dest_short = dest.asShortBuffer();
            ShortBuffer src_short = src.asShortBuffer();

            while(src_short.hasRemaining()) {
                dest_short.put(src_short.get());
            }
        } else {
            while(src.hasRemaining()) {
                dest.put(src.get());
            }
        }

        dest.rewind();
        return dest;
    }
}
