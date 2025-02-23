package cn.zack.service;

import org.bytedeco.ffmpeg.global.avcodec;
import org.bytedeco.javacv.*;
import org.springframework.stereotype.Component;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Windows 桌面屏幕画面和系统声音混合录制
 */
@Component
public class WindowsScreenRecord {

    private static FFmpegFrameGrabber videoGrabber;
    private static FFmpegFrameRecorder videoRecorder;

    /**
     * 是否开启视频和系统音频录制
     */
    private static AtomicBoolean videoIsRecording = new AtomicBoolean(false);

    /**
     * 是否开启麦克风录制
     */
    private static AtomicBoolean audioIsRecording = new AtomicBoolean(false);
    private TargetDataLine targetDataLine;

    /**
     * 开始录制桌面屏幕和系统声音
     */
    public void startVideoRecording(String output) {
        videoGrabber = new FFmpegFrameGrabber("video=screen-capture-recorder:audio=virtual-audio-capturer");
        videoGrabber.setFormat("dshow");

        try {
            videoGrabber.start();

            videoRecorder = new FFmpegFrameRecorder(output, videoGrabber.getImageWidth(), videoGrabber.getImageHeight(), videoGrabber.getAudioChannels());
            videoRecorder.setFormat("mp4");
            videoRecorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            videoRecorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);

            // 5Mbps 高码率
            videoRecorder.setVideoBitrate(5_000_000);
            // FPS 30
            videoRecorder.setFrameRate(30);
            // 避免颜色失真
            videoRecorder.setPixelFormat(0);

            // 侧重编码速度
            videoRecorder.setVideoOption("preset", "ultrafast");
            // 侧重低延迟
            videoRecorder.setVideoOption("tune", "zerolatency");
            // GOP 大小, 提升质量
            videoRecorder.setGopSize(30);

            // 192Kbps, 提高音频质量
            videoRecorder.setAudioBitrate(192_000);
            // 音频
            videoRecorder.setSampleRate(44100);
            videoRecorder.start();
            System.out.println("视频录制已开始");

            videoIsRecording.set(true);
            new Thread(() -> {
                try {
                    Frame frame;
                    while (videoIsRecording.get() && (frame = videoGrabber.grab()) != null) {
                        videoRecorder.record(frame);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    stopVideoRecording();
                }
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止录制视频和系统声音
     */
    public void stopVideoRecording() {
        videoIsRecording.set(false);
        try {
            videoRecorder.stop();
            videoRecorder.release();
            videoGrabber.stop();
            videoGrabber.release();
            System.out.println("视频录制已停止");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 混合音视频
     *
     * @param videoPath
     * @param outputPath
     */
    public void mixVideoAndAudio(String videoPath, String outputPath) {
        String[] audioPaths = {"123.mp3", "345.mp3"}; // 多个音频路径

        try {
            // 初始化视频抓取器
            FFmpegFrameGrabber videoGrabber = new FFmpegFrameGrabber(videoPath);
            videoGrabber.start();

            // 获取视频参数
            int videoWidth = videoGrabber.getImageWidth();
            int videoHeight = videoGrabber.getImageHeight();
            int videoChannels = videoGrabber.getAudioChannels();

            // 初始化音频抓取器
            FFmpegFrameGrabber[] audioGrabbers = new FFmpegFrameGrabber[audioPaths.length];
            for (int i = 0; i < audioPaths.length; i++) {
                audioGrabbers[i] = new FFmpegFrameGrabber(audioPaths[i]);
                audioGrabbers[i].start();
            }

            // 创建输出录制器
            FFmpegFrameRecorder recorder = new FFmpegFrameRecorder(outputPath, videoWidth, videoHeight, videoChannels);
            recorder.setFormat("mp4");
            recorder.setVideoCodec(avcodec.AV_CODEC_ID_H264);
            recorder.setAudioCodec(avcodec.AV_CODEC_ID_AAC);
            recorder.setVideoBitrate(5_000_000);
            recorder.setAudioBitrate(192_000);
            recorder.setFrameRate(30);
            recorder.setSampleRate(44100);

            recorder.start();

            // 记录视频流
            Frame videoFrame;
            while ((videoFrame = videoGrabber.grab()) != null) {
                recorder.record(videoFrame);
            }

            // 设置音频的开始时间（按分钟计算）
            int[] audioStartTimes = {180, 300}; // 音频1从第3分钟开始，音频2从第5分钟开始

            // 合并音频到视频
            for (int i = 0; i < audioGrabbers.length; i++) {
                Frame audioFrame;
                long audioStartTimestamp = audioStartTimes[i] * 1000000L; // 转为微秒

                while ((audioFrame = audioGrabbers[i].grab()) != null) {
                    if (audioGrabbers[i].getTimestamp() >= audioStartTimestamp) {
                        recorder.record(audioFrame);
                    }
                }
            }

            // 完成录制
            recorder.stop();
            recorder.release();
            videoGrabber.stop();
            for (FFmpegFrameGrabber audioGrabber : audioGrabbers) {
                audioGrabber.stop();
            }
            System.out.println("音频和视频合成完成，输出文件: " + outputPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 开启录制麦克风音频
     */
    public void startAudioRecording(String output) {
        // 设置音频格式
        AudioFormat audioFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,   // 使用 PCM 编码格式
                44100,                            // 设置采样率为 44100 Hz
                16,                               // 设置位深度为 16 位
                2,                                // 立体声
                4,                                // 每帧的字节数，立体声需要 4 字节
                44100,                            // 设定帧速率
                false                              // 小端字节序
        );
        DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);

        // 获取音频输入设备
        try {
            targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
            targetDataLine.open(audioFormat);
        } catch (LineUnavailableException e) {
            System.err.println("无法获取音频设备：" + e.getMessage());
            return;
        }

        // 输出到音频文件
        File outputFile = new File(output);

        // 创建文件输出流和音频输入流
        try {
            AudioFileFormat.Type fileType = AudioFileFormat.Type.WAVE;

            // 创建一个音频输入流，传递给 AudioSystem.write 方法
            AudioInputStream audioInputStream = new AudioInputStream(targetDataLine);
            // 启动录音线程
            audioIsRecording.set(true);
            targetDataLine.start();
            System.out.println("录音已开始...");

            // 在另一个线程中录音
            Thread recordingThread = new Thread(() -> {
                try {
                    AudioSystem.write(audioInputStream, fileType, outputFile);
                    System.out.println("录音已保存到: " + outputFile.getAbsolutePath());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            recordingThread.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止录制麦克风音频
     */
    public void stopAudioRecording() {
        if (audioIsRecording.get() && targetDataLine != null) {
            // 停止录音
            targetDataLine.stop();
            targetDataLine.close();
            audioIsRecording.set(false);
            System.out.println("录音已停止");
        } else {
            System.out.println("没有正在进行的录音");
        }
    }
}
