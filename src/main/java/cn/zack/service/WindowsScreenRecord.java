package cn.zack.service;

import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Windows 桌面屏幕画面和系统声音混合录制
 *
 * @author 张云龙
 */
@Component
public class WindowsScreenRecord {

    private Process videoRecordFfmpegProcess;
    private Process audioRecordFfmpegProcess;
    private Thread videoRecordProcessThread;
    private Thread audioRecordProcessThread;

    /**
     * 开始录制桌面屏幕和系统声音
     */
    public void startVideoRecording(String output) {
        String prefix = output.split("\\.")[0];
        String suffix = output.split("\\.")[1];

        // 在后台线程中启动录制
        videoRecordProcessThread = new Thread(() -> {
            String ffmpegCommand = "./bin/ffmpeg -f dshow -i video=\"screen-capture-recorder\" -f dshow -i audio=\"virtual-audio-capturer\" " +
                    "-vcodec libx264 -preset:v fast -crf 23 -acodec aac -b:a 128k -pix_fmt yuv420p -r 30 " +
                    "-segment_time 60 -f segment -reset_timestamps 1 " + prefix + "_%03d." + suffix;

            ProcessBuilder processBuilder = new ProcessBuilder(ffmpegCommand.split(" "));
            // 合并输出，便于日志调试
            processBuilder.redirectErrorStream(true);
            // 这里可以重定向到文件或继承当前进程的输出
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

            try {
                videoRecordFfmpegProcess = processBuilder.start();
                System.out.println("开始录屏...");
                // 等待进程结束（这行代码只会在录制结束后才返回，不要在 EDT 中调用）
                int exitCode = videoRecordFfmpegProcess.waitFor();
                System.out.println("完成录屏: " + exitCode);
                if (exitCode == 0) {
                    // 列出分片文件
                    List<String> shardingFiles = getShardFiles(prefix + "_");
                    List<String> fileNames = shardingFiles
                            .stream().map(c -> "file '" + c + "'").collect(Collectors.toList());
                    // 有分片, 需要合并
                    if (!fileNames.isEmpty()) {
                        // 写入txt文件
                        Path filePath = Paths.get("videoSharding.txt");
                        Files.write(filePath, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        Files.write(filePath, fileNames, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        // 合并视频文件
                        String mergeCommand = "ffmpeg -f concat -safe 0 -i videoSharding.txt -c copy " + output;
                        processBuilder = new ProcessBuilder(mergeCommand.split(" "));
                        processBuilder.redirectErrorStream(true);
                        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        videoRecordFfmpegProcess = processBuilder.start();
                        System.out.println("开始合并视频分片...");
                        int shardingCode = videoRecordFfmpegProcess.waitFor();
                        System.out.println("完成合并视频分片: " + shardingCode);
                        // 删除分片文件
                        if (shardingCode == 0) {
                            for (String file : shardingFiles) {
                                Files.deleteIfExists(Paths.get(file));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("录屏异常");
                ;
            }
        });
        videoRecordProcessThread.start();
    }

    /**
     * 停止录制视频和系统声音
     */
    public void stopVideoRecording() {
        new Thread(() -> {
            if (videoRecordFfmpegProcess != null) {
                try {
                    OutputStream os = videoRecordFfmpegProcess.getOutputStream();
                    os.write("q".getBytes());
                    os.flush();
                    System.out.println("停止录屏...");
                } catch (Exception e) {
                    System.out.println("停止录屏异常");
                }
            }
        }).start();
    }

    /**
     * 获取系统中所有的音频设备（麦克风）名称
     *
     * @return 音频设备名称数组
     */
    public static String[] getAudioMicrophoneDevices() {
        System.out.println("尝试获取音频设备列表");
        List<String> devices = new ArrayList<>();
        try {
            // 构造 ffmpeg 命令：列出所有 DirectShow 设备
            ProcessBuilder pb = new ProcessBuilder(
                    "./bin/ffmpeg", "-list_devices", "true", "-f", "dshow", "-i", "dummy"
            );
            // ffmpeg 的设备列表信息通常输出到标准错误流
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8)
            );

            String line;
            while ((line = reader.readLine()) != null) {
                // 只取音频设备, 且排除dshow虚拟驱动
                if (line.contains("(audio)") && !line.contains("(video)") && !line.contains("virtual-audio-capturer")) {
                    // 设备名称一般位于双引号内
                    int firstQuote = line.indexOf("\"");
                    int lastQuote = line.indexOf("\"", firstQuote + 1);
                    if (firstQuote != -1 && lastQuote != -1 && lastQuote > firstQuote) {
                        String deviceName = line.substring(firstQuote + 1, lastQuote);
                        System.out.println("获取到音频设备: " + deviceName);
                        devices.add(deviceName);
                    }
                }
            }
            reader.close();
            process.waitFor();
        } catch (Exception ex) {
            System.out.println("获取音频设备异常");
        }
        return devices.toArray(new String[0]);
    }

    /**
     * 开始录制音频
     *
     * @param audioSavePath        保存音频文件的路径（例如：D:\record.wav）
     * @param microphoneDeviceName 麦克风设备名称（例如："本机麦克风 (适用于数字麦克风的英特尔® 智音技术)"）
     */
    public void startAudioRecording(String audioSavePath, String microphoneDeviceName) {
        String prefix = audioSavePath.split("\\.")[0];
        String suffix = audioSavePath.split("\\.")[1];
        // 在后台线程中启动录制
        audioRecordProcessThread = new Thread(() -> {
            String command = "./bin/ffmpeg -f dshow -i audio=\"" + microphoneDeviceName +
                    "\" -acodec pcm_s16le -ar 44100 -ac 2 -y " +
                    "-segment_time 60 -f segment -reset_timestamps 1 " + prefix + "_%03d." + suffix;

            ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
            // 合并输出，便于日志调试
            processBuilder.redirectErrorStream(true);
            // 这里可以重定向到文件或继承当前进程的输出
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

            try {
                audioRecordFfmpegProcess = processBuilder.start();
                System.out.println("开始录音...");
                // 等待进程结束（这行代码只会在录制结束后才返回，不要在 EDT 中调用）
                int exitCode = audioRecordFfmpegProcess.waitFor();
                System.out.println("录音完成: " + exitCode);

                if (exitCode == 0) {
                    // 列出分片文件
                    List<String> shardingFiles = getShardFiles(prefix + "_");
                    List<String> fileNames = shardingFiles
                            .stream().map(c -> "file '" + c + "'").collect(Collectors.toList());
                    // 有分片, 需要合并
                    if (!fileNames.isEmpty()) {
                        // 写入txt文件
                        Path filePath = Paths.get("audioSharding.txt");
                        Files.write(filePath, new byte[0], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        Files.write(filePath, fileNames, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                        // 合并音频文件
                        String mergeCommand = "ffmpeg -f concat -safe 0 -i audioSharding.txt -c copy " + audioSavePath;
                        processBuilder = new ProcessBuilder(mergeCommand.split(" "));
                        processBuilder.redirectErrorStream(true);
                        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        audioRecordFfmpegProcess = processBuilder.start();
                        System.out.println("开始合并音频分片...");
                        int shardingCode = audioRecordFfmpegProcess.waitFor();
                        System.out.println("完成合并音频分片: " + shardingCode);
                        // 删除分片文件
                        if (shardingCode == 0) {
                            for (String file : shardingFiles) {
                                Files.deleteIfExists(Paths.get(file));
                            }
                        }
                    }
                }

            } catch (IOException | InterruptedException e) {
                System.out.println("录音异常");
                e.printStackTrace();
            }
        });
        audioRecordProcessThread.start();
    }

    /**
     * 停止录制音频
     */
    public void stopAudioRecording() {
        new Thread(() -> {
            if (audioRecordFfmpegProcess != null) {
                try {
                    OutputStream os = audioRecordFfmpegProcess.getOutputStream();
                    os.write("q".getBytes());
                    os.flush();
                    System.out.println("停止录音");
                } catch (Exception e) {
                    System.out.println("停止录音异常");
                }
            }
        }).start();
    }

    /**
     * 合并一批音频到视频文件中
     *
     * @param videoPath  原视频文件
     * @param outPutPath 新生成的视频文件
     * @param audioMap   音频文件以及音轨开始位置
     */
    public void mergeVideoAndAudio(String videoPath, String outPutPath, HashMap<String, Long> audioMap) {
        List<String> keyList = new ArrayList<>();
        keyList.addAll(audioMap.keySet());

        String source = "";
        String adelay = "";
        String num = "";
        int size = keyList.size() + 1;
        for (int i = 0; i < keyList.size(); i++) {
            source += "-i " + keyList.get(i) + " ";
            adelay += "[" + (i + 1) + "]adelay=" + audioMap.get(keyList.get(i)) + "|" + audioMap.get(keyList.get(i)) + "[a" + (i + 1) + "]; ";
            num += "[a" + (i + 1) + "]";
        }

        String command = String.format(
                "./bin/ffmpeg -i " +
                        videoPath + " " +
                        source +
                        "-filter_complex \"[0:a]adelay=0|0[original]; " +
                        adelay +
                        "[original]" +
                        num +
                        "amix=inputs=" +
                        size +
                        ":normalize=1[mixed_audio]\" -map 0:v -map \"[mixed_audio]\" -c:v copy -c:a aac " +
                        outPutPath
        );

        System.out.println(command);

        ProcessBuilder processBuilder = new ProcessBuilder(command.split(" "));
        // 合并输出，便于日志调试
        processBuilder.redirectErrorStream(true);
        // 这里可以重定向到文件或继承当前进程的输出
        processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);

        try {
            audioRecordFfmpegProcess = processBuilder.start();
            System.out.println("开始合成...");
            // 等待进程结束（这行代码只会在录制结束后才返回，不要在 EDT 中调用）
            int exitCode = audioRecordFfmpegProcess.waitFor();
            // 正常退出, 说明合成完毕, 清理旧文件
            if (exitCode == 0) {
                Files.deleteIfExists(Paths.get(videoPath));
                for (String audio : keyList) {
                    Files.deleteIfExists(Paths.get(audio));
                }
            }
            System.out.println("合成完成: " + exitCode);
        } catch (IOException | InterruptedException e) {
            System.out.println("合成异常");
        }
    }

    /**
     * 获取指定相似文件, 用于查找指定文件的分片文件列表
     *
     * @param searchPath 要搜索的文件名    eg: D:\abc\def\1234
     * @return 相似文件名   eg: D:\abc\def\12345.txt, D:\abc\def\1234.pdf
     */
    public List<String> getShardFiles(String searchPath) {
        // 获取目录 D:\abc\def
        Path parentDir = Paths.get(searchPath).getParent();
        // 获取前缀 1740444994434
        String prefix = Paths.get(searchPath).getFileName().toString();

        if (parentDir == null || !Files.isDirectory(parentDir)) {
            System.out.println("目录不存在");
            return new ArrayList<>();
        }

        try {
            List<String> matchingFiles = Files.list(parentDir)
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().startsWith(prefix))
                    .map(c -> c.toString())
                    .collect(Collectors.toList());
            return matchingFiles;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }
}
