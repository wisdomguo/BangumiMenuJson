package com.bangumimenu;

import com.bangumimenu.gui.MainWindow;

import javax.swing.*;

/**
 * Bangumi Menu 主应用程序类
 */
public class BangumiMenuApp {
    public static void main(String[] args) {
        // 设置系统外观
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 在事件调度线程中启动GUI
        SwingUtilities.invokeLater(() -> new MainWindow().setVisible(true));
    }
}