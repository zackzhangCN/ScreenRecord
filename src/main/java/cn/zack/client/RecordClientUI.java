package cn.zack.client;

import cn.zack.service.WindowsScreenRecord;
import com.formdev.flatlaf.FlatLightLaf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.HashMap;

/**
 * @author zack
 * 客户端UI界面
 */
@Component("RecordClientUI")
public class RecordClientUI extends JFrame {

    @Autowired
    private WindowsScreenRecord windowsScreenRecord;

    private static RecordClientUI instance = null;

    /**
     * 录制的视频文件保存位置
     * eg: D:\init.bat.mp4
     */
    private static String videoSavePath = "";

    /**
     * 录制的音频文件保存位置
     * eg: D:\abc.wav
     */
    private static String audioSavePath = "";

    /**
     * 开始录制视频那一刻的毫秒值
     */
    private Long startRecordVideoMills = 0L;

    /**
     * 已录制的音频列表
     * key为音频文件位置
     * value为音频文件的开始时间比视频文件开始时间落后的时间差, 单位毫秒
     */
    private HashMap<String, Long> audioOffsetMap = new HashMap<>();

    private RecordClientUI() {
    }

    // 创建窗口对象
    public static RecordClientUI getInstance() {
        if (null == instance) {
            synchronized (RecordClientUI.class) {
                if (null == instance) {
                    instance = new RecordClientUI();
                }
            }
        }
        return instance;
    }

    /**
     * 初始化客户端GUI
     */
    public void initUI() {
        // 窗口标题
        this.setTitle("录屏工具plus++");
        // 窗口大小
        this.setSize(800, 310);
        // 不可调整窗口大小
        this.setResizable(false);
        // 窗口关闭时退出程序
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // 居中显示
        this.setLocationRelativeTo(null);
        // 设置窗口图标
        this.setIconImage(new ImageIcon(RecordClientUI.class.getResource("/icon.jpg")).getImage());

        // 流式布局
        FlowLayout flowLayout = new FlowLayout();
        this.setLayout(flowLayout);

        JLabel saveLabel = new JLabel("保存到");
        saveLabel.setFont(new Font("楷体", Font.BOLD, 25));
        saveLabel.setForeground(Color.BLUE);
        this.add(saveLabel);
        // 目录选择按钮
        JButton chooseDirButton = new JButton("选择目录");
        chooseDirButton.setBorderPainted(false);
        chooseDirButton.setForeground(Color.BLUE);
        chooseDirButton.setPreferredSize(new Dimension(180, 50));
        chooseDirButton.setFont(new Font("楷体", Font.BOLD, 20));
        chooseDirButton.setBackground(Color.LIGHT_GRAY);
        this.add(chooseDirButton);

        // 显示选择的目录路径
        JTextField pathTextField = new JTextField(40);
        pathTextField.setFont(new Font("微软雅黑", Font.PLAIN, 20));
        pathTextField.setPreferredSize(new Dimension(600, 40));
        this.add(pathTextField);

        // 录制按钮
        JButton startButton = new JButton("开始录制");
        startButton.setBorderPainted(false);
        startButton.setForeground(Color.BLUE);
        startButton.setPreferredSize(new Dimension(180, 50));
        startButton.setFont(new Font("楷体", Font.BOLD, 25));
        startButton.setBackground(Color.LIGHT_GRAY);
        // 默认禁用
        startButton.setEnabled(false);
        this.add(startButton);

        // 停止按钮
        JButton stopButton = new JButton("停止录制");
        stopButton.setBorderPainted(false);
        stopButton.setForeground(Color.BLUE);
        stopButton.setPreferredSize(new Dimension(180, 50));
        stopButton.setFont(new Font("楷体", Font.BOLD, 25));
        stopButton.setBackground(Color.LIGHT_GRAY);
        this.add(stopButton);
        // 默认禁用停止按钮
        stopButton.setEnabled(false);

        // 从接口获取麦克风设备列表，示例中使用模拟数据
        String[] micDevices = windowsScreenRecord.getAudioMicrophoneDevices();
        JComboBox<String> micComboBox = new JComboBox<>(micDevices);
        micComboBox.setFont(new Font("楷体", Font.BOLD, 20));
        micComboBox.setPreferredSize(new Dimension(200, 40));
        this.add(micComboBox);

        JCheckBox micCheckBox = new JCheckBox("");
        micCheckBox.setFont(new Font("楷体", Font.BOLD, 25));
        int iconWidth = 30;
        int iconHeight = 30;
        // 麦克风启用时的图标
        ImageIcon selectIcon = new ImageIcon(RecordClientUI.class.getResource("/select.png"));
        Image resizeSelectIcon = selectIcon.getImage().getScaledInstance(iconWidth, iconHeight, Image.SCALE_SMOOTH);
        micCheckBox.setSelectedIcon(new ImageIcon(resizeSelectIcon));
        // 麦克风禁用时的图标
        ImageIcon unSelectIcon = new ImageIcon(RecordClientUI.class.getResource("/unselect.png"));
        Image resizeUnSelectIcon = unSelectIcon.getImage().getScaledInstance(iconWidth, iconHeight, Image.SCALE_SMOOTH);
        micCheckBox.setIcon(new ImageIcon(resizeUnSelectIcon));
        micCheckBox.setEnabled(false);
        this.add(micCheckBox);

        // 生成按钮
        JButton finishButton = new JButton("生成视频");
        finishButton.setBorderPainted(false);
        finishButton.setForeground(Color.BLUE);
        finishButton.setPreferredSize(new Dimension(180, 50));
        finishButton.setFont(new Font("楷体", Font.BOLD, 25));
        finishButton.setBackground(Color.LIGHT_GRAY);
        this.add(finishButton);
        // 默认禁用停止按钮
        finishButton.setEnabled(false);


        JLabel space = new JLabel("                                                          " +
                "                                                                                 " +
                "                                                                                 ");
        this.add(space);
        // 创建显示文字的标签
        JLabel footerLabel = new JLabel("Designed By zhangyl07");
        footerLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        footerLabel.setForeground(Color.GRAY);
        this.add(footerLabel);
        // 展示窗口
        this.setVisible(true);

        // 监听选择目录按钮
        chooseDirButton.addActionListener(e -> {
            try {
                // 弹窗选择现代化扁平风格
                UIManager.setLookAndFeel(new FlatLightLaf());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooser.setDialogTitle("选择保存目录");

            int returnVal = fileChooser.showOpenDialog(this);
            if (returnVal == JFileChooser.APPROVE_OPTION) {
                pathTextField.setText(fileChooser.getSelectedFile().getAbsolutePath() + "\\");
                // 选择完目录之后, 启用开始录制按钮, 禁用目录选择按钮
                startButton.setEnabled(true);
                chooseDirButton.setEnabled(false);
            }
        });

        // 监听开始按钮点击事件
        startButton.addActionListener(e -> {
            // 获取选择的路径
            videoSavePath = pathTextField.getText() + System.currentTimeMillis() + ".mp4";
            // 开启录制
            windowsScreenRecord.startVideoRecording(videoSavePath);
            // 记录视频文件开始录制的毫秒值
            startRecordVideoMills = System.currentTimeMillis();
            // 开始录制之后, 禁用开始按钮, 启用停止按钮和录音按钮
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            micCheckBox.setEnabled(true);
        });

        // 监听停止按钮点击事件
        stopButton.addActionListener(e -> {
            // 先停止录音
            windowsScreenRecord.stopAudioRecording();
            // 再停止录屏
            windowsScreenRecord.stopVideoRecording();

            // 停止录制之后, 关闭麦克风
            micCheckBox.setSelected(false);
            // 禁用停止按钮和录音按钮
            micCheckBox.setEnabled(false);
            stopButton.setEnabled(false);
            // 启用生成按钮
            finishButton.setEnabled(true);

            // 弹窗提醒
            JOptionPane.showMessageDialog(null, "录制结束", "录制视频", JOptionPane.INFORMATION_MESSAGE);
        });

        // 监听录音按钮的状态变化
        micCheckBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    // 开始录音的时间
                    long startRecordAudioTime = System.currentTimeMillis();
                    // 本次录音文件的保存位置
                    audioSavePath = pathTextField.getText() + startRecordAudioTime + ".wav";
                    // 开始录音
                    windowsScreenRecord.startAudioRecording(audioSavePath, (String) micComboBox.getSelectedItem());
                    // 记录本次录音的落后时间差
                    audioOffsetMap.put(pathTextField.getText() + startRecordAudioTime + ".wav", startRecordAudioTime - startRecordVideoMills);
                } else {
                    windowsScreenRecord.stopAudioRecording();
                }
            }
        });

        // 监听生成按钮点击事件
        finishButton.addActionListener(e -> {
            String resultPath = videoSavePath;
            // 有录音存在, 需要合并
            if (audioOffsetMap.size() > 0) {
                resultPath = pathTextField.getText() + System.currentTimeMillis() + ".mp4";
                // 合成音视频
                windowsScreenRecord.mergeVideoAndAudio(videoSavePath, resultPath, audioOffsetMap);
            }
            // 禁用生成按钮
            finishButton.setEnabled(false);
            // 启用开始录制按钮
            startButton.setEnabled(true);
            // 重置数据
            videoSavePath = "";
            audioSavePath = "";
            startRecordVideoMills = 0L;
            audioOffsetMap = new HashMap<>();

            // 复制最终结果到剪切板
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection stringSelection = new StringSelection(resultPath);
            clipboard.setContents(stringSelection, null);
            // 弹窗提醒
            UIManager.put("OptionPane.buttonFont", new FontUIResource(new Font("楷体", Font.BOLD, 20)));
            UIManager.put("OptionPane.messageFont", new FontUIResource(new Font("楷体", Font.BOLD, 20)));
            JOptionPane.showMessageDialog(null, "生成视频成功, 视频地址已复制到剪切板", "生成视频", JOptionPane.INFORMATION_MESSAGE);
        });
    }
}
