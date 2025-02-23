package cn.zack.client;

import cn.zack.service.WindowsScreenRecord;
import com.formdev.flatlaf.FlatLightLaf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author zack
 * 客户端UI界面
 */
@Component("RecordClientUI")
public class RecordClientUI extends JFrame {

    @Autowired
    private WindowsScreenRecord windowsScreenRecord;

    private static RecordClientUI instance = null;

    private static String videoSavePath = "";
    private static String audioSavePath = "";

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
        this.setTitle("刘总专用录屏工具plus++");
        // 窗口大小
        this.setSize(800, 300);
        // 不可调整窗口大小
        this.setResizable(true);
        // 窗口关闭时退出程序
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // 居中显示
        this.setLocationRelativeTo(null);

        // 流式布局
        FlowLayout flowLayout = new FlowLayout();
        this.setLayout(flowLayout);

        JLabel newsJlabel = new JLabel("保存到");
        newsJlabel.setFont(new Font("楷体", Font.BOLD, 25));
        newsJlabel.setForeground(Color.BLUE);
        this.add(newsJlabel);
        // 目录选择按钮
        JButton chooseDirButton = new JButton("选择");
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

        JCheckBox micCheckBox = new JCheckBox("麦克风已禁用");
        micCheckBox.setFont(new Font("楷体", Font.BOLD, 25));
        int iconWidth = 30;
        int iconHeight = 30;
        // 启用时的图标
        ImageIcon selectIcon = new ImageIcon(RecordClientUI.class.getResource("/mic_select.png"));
        Image resizeSelectIcon = selectIcon.getImage().getScaledInstance(iconWidth, iconHeight, Image.SCALE_SMOOTH);
        micCheckBox.setSelectedIcon(new ImageIcon(resizeSelectIcon));
        // 未启用时的图标
        ImageIcon unSelectIcon = new ImageIcon(RecordClientUI.class.getResource("/mic_unselect.png"));
        Image resizeUnSelectIcon = unSelectIcon.getImage().getScaledInstance(iconWidth, iconHeight, Image.SCALE_SMOOTH);
        micCheckBox.setIcon(new ImageIcon(resizeUnSelectIcon));
        this.add(micCheckBox);

        // 展示窗口
        this.setVisible(true);

        // 选择目录按钮事件
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
                pathTextField.setText(fileChooser.getSelectedFile().getAbsolutePath());
            }
        });

        // 开始按钮点击事件
        startButton.addActionListener(e -> {
            // 获取选择的路径
            videoSavePath = pathTextField.getText() + System.currentTimeMillis() + ".mp4";
            // 开启录制
            windowsScreenRecord.startVideoRecording(videoSavePath);
            // 禁用开始按钮
            startButton.setEnabled(false);
            // 启用停止按钮
            stopButton.setEnabled(true);
        });

        // 停止按钮点击事件
        stopButton.addActionListener(e -> {
            windowsScreenRecord.stopVideoRecording();
            // 弹窗提醒
            UIManager.put("OptionPane.buttonFont", new FontUIResource(new Font("楷体", Font.BOLD, 20)));
            UIManager.put("OptionPane.messageFont", new FontUIResource(new Font("楷体", Font.BOLD, 20)));
            // 启用开始按钮
            startButton.setEnabled(true);
            // 禁用停止按钮
            stopButton.setEnabled(false);
            JOptionPane.showMessageDialog(null, "已保存到" + videoSavePath, "完成", JOptionPane.INFORMATION_MESSAGE);
        });

        // 监听复选框的选中状态变化
        micCheckBox.addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    micCheckBox.setText("麦克风已启用");
                    audioSavePath = pathTextField.getText() + System.currentTimeMillis() + ".wav";
                    windowsScreenRecord.startAudioRecording(audioSavePath);
                } else {
                    micCheckBox.setText("麦克风已禁用");
                    windowsScreenRecord.stopAudioRecording();
                }
            }
        });

    }
}
