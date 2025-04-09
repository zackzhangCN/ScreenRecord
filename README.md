# ScreenRecord

录屏神器

注意:

- 需要ffmpeg和dshow插件(已提供)

启动说明:

- 先运行mvn clean package构建出ScreenRecord.jar
- 将ScreenRecord.jar, ffmpeg.exe, Setup.Screen.Capturer.Recorder.v0.13.3.exe, init.bat和start.bat放到同一目录下
- 运行init.bat(仅首次使用需要运行)
- 运行start.bat

操作说明:

- 先选择保存目录, 再点击开始录制
- 录屏过程中会混合录制画面和音频
- 录屏过程中可随时启用/停用麦克风录音
- 停止录屏后, 点击生成视频, 最终录屏视频地址会复制到剪切板