package cn.zack.service;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Windows 桌面屏幕画面和系统声音混合录制
 */
@Component
public class WindowsScreenRecord {

    private static FFmpegFrameGrabber grabber;
    private static FFmpegFrameRecorder recorder;
    private static AtomicBoolean isRecording = new AtomicBoolean(false);

    /**
     * 开始录制桌面屏幕和系统声音
     */
    public void startRecording(String output) {
        grabber = new FFmpegFrameGrabber("video=screen-capture-recorder:audio=virtual-audio-capturer");
        grabber.setFormat("dshow");

        try {
            grabber.start();

            recorder = new FFmpegFrameRecorder(output, grabber.getImageWidth(), grabber.getImageHeight(), grabber.getAudioChannels());
            recorder.setFormat("mp4");
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);

            // 5Mbps 高码率
            recorder.setVideoBitrate(5_000_000);
            // FPS 30
            recorder.setFrameRate(30);
            // 避免颜色失真
            recorder.setPixelFormat(0);

            // 侧重编码速度
            recorder.setVideoOption("preset", "ultrafast");
            // 侧重低延迟
            recorder.setVideoOption("tune", "zerolatency");
            // GOP 大小, 提升质量
            recorder.setGopSize(30);

            // 192Kbps, 提高音频质量
            recorder.setAudioBitrate(192_000);
            // 音频
            recorder.setSampleRate(44100);
            recorder.start();

            isRecording.set(true);
            new Thread(() -> {
                try {
                    Frame frame;
                    while (isRecording.get() && (frame = grabber.grab()) != null) {
                        recorder.record(frame);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    stopRecording();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止录制
     */
    public void stopRecording() {
        isRecording.set(false);
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
                recorder = null;
            }
            if (grabber != null) {
                grabber.stop();
                grabber.release();
                grabber = null;
            }
            System.out.println("录制已停止！");
        } catch (FFmpegFrameRecorder.Exception | FFmpegFrameGrabber.Exception e) {
            e.printStackTrace();
        }
    }
}
