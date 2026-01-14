package com.bangumimenu.gui;

import com.bangumimenu.config.AppConfig;
import com.bangumimenu.entity.Bangumi;
import com.bangumimenu.utils.JsonUtils;
import com.bangumimenu.utils.GitUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.Random;

/**
 * Bangumi Menu 主窗口类
 */
public class MainWindow extends JFrame {
    private JPanel mainPanel;
    private JLabel titleLabel;
    private JButton randomSelectButton;
    private JButton addBangumiButton;
    private JButton markAsWatchedButton;
    private JButton loginButton;
    private JButton syncDataButton; // 新增同步按钮
    private JList<String> unwatchedList;
    private JList<String> watchedList;
    private JTextArea currentBangumiDisplay;
    private JTextArea bangumiDetailsArea;
    private JSplitPane leftRightSplitPane;
    private JSplitPane topBottomSplitPane;
    private List<Bangumi> allBangumis;
    private List<Bangumi> currentBangumiList;
    private boolean isLoggedIn = false; // 登录状态标志

    public MainWindow() {
        initializeComponents();
        setupLayout();
        setupEventHandlers();
        setupWindow();
        
        // 启动时立即初始化用户数据并强制从远程仓库拉取最新内容进行覆盖
        initializeUserDataAndForcePull();
    }
    
    private void initializeUserDataAndForcePull() {
        // 首先初始化用户数据
        com.bangumimenu.utils.UserDataSync.initializeUserData();
        
        if (AppConfig.getBooleanProperty("git.enabled", true)) {
            // 初始化Git仓库
            SwingUtilities.invokeLater(() -> {
                if (GitUtils.initRepo()) {
                    // 立即强制从远程仓库拉取最新内容进行覆盖，无论是否有更新
                    forcePullFromRemote();
                }
            });
        }
    }
    
    /**
     * 强制从远程仓库拉取最新内容进行覆盖
     */
    private void forcePullFromRemote() {
        if (!AppConfig.getBooleanProperty("git.enabled", true)) {
            return;
        }
        
        // 在后台线程中执行强制拉取
        SwingUtilities.invokeLater(() -> {
            Thread syncThread = new Thread(() -> {
                System.out.println("正在强制从远程仓库拉取最新内容进行覆盖...");
                boolean success = GitUtils.forcePullChanges();
                if (success) {
                    // 重新加载数据
                    SwingUtilities.invokeLater(() -> {
                        allBangumis = JsonUtils.readBangumiList("/bangumi.json");
                        currentBangumiList = JsonUtils.readBangumiList("/current_bangumi.json");
                        updateBangumiLists(); // 刷新列表显示
                        updateCurrentBangumiDisplay(); // 刷新当前观看显示
                        System.out.println("强制拉取和数据更新成功！");
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        System.err.println("强制拉取失败，请检查网络连接和远程仓库设置");
                    });
                }
            });
            syncThread.start();
        });
    }
    
    private void syncWithRemote() {
        if (!AppConfig.getBooleanProperty("git.enabled", true)) {
            return;
        }
        
        // 在后台线程中执行同步
        SwingUtilities.invokeLater(() -> {
            Thread syncThread = new Thread(() -> {
                boolean success = GitUtils.pullChanges();
                if (success) {
                    // 重新加载数据
                    SwingUtilities.invokeLater(() -> {
                        allBangumis = JsonUtils.readBangumiList("/bangumi.json");
                        currentBangumiList = JsonUtils.readBangumiList("/current_bangumi.json");
                        updateBangumiLists(); // 刷新列表显示
                        updateCurrentBangumiDisplay(); // 刷新当前观看显示
                        JOptionPane.showMessageDialog(this, "数据同步成功！", "信息", JOptionPane.INFORMATION_MESSAGE);
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(this, "数据同步失败，请检查网络连接和远程仓库设置", "警告", JOptionPane.WARNING_MESSAGE);
                    });
                }
            });
            syncThread.start();
        });
    }
    
    private void pushToRemote() {
        if (!AppConfig.getBooleanProperty("git.enabled", true)) {
            return;
        }
        
        // 使用内置账户信息推送
        SwingUtilities.invokeLater(() -> {
            Thread pushThread = new Thread(() -> {
                String commitMessage = "数据更新 " + java.time.LocalDateTime.now();
                boolean success = GitUtils.pushChanges(commitMessage);
                if (success) {
                    JOptionPane.showMessageDialog(this, "数据推送成功！", "信息", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "数据推送失败，请检查网络连接和认证信息", "警告", JOptionPane.WARNING_MESSAGE);
                }
            });
            pushThread.start();
        });
    }

    private void initializeComponents() {
        mainPanel = new JPanel(new BorderLayout());
        titleLabel = new JLabel("Bangumi Menu 系统", SwingConstants.CENTER);
        
        // 创建菜单按钮
        createMenuButtons();
        
        // 加载数据
        allBangumis = JsonUtils.readBangumiList("/bangumi.json");
        currentBangumiList = JsonUtils.readBangumiList("/current_bangumi.json");
        
        // 创建显示区域
        currentBangumiDisplay = new JTextArea();
        currentBangumiDisplay.setEditable(false);
        currentBangumiDisplay.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        currentBangumiDisplay.setLineWrap(true); // 启用自动换行
        currentBangumiDisplay.setWrapStyleWord(true); // 设置换行方式
        currentBangumiDisplay.setMargin(new Insets(10, 10, 10, 10)); // 设置内边距，改善视觉效果
        
        bangumiDetailsArea = new JTextArea();
        bangumiDetailsArea.setEditable(false);
        bangumiDetailsArea.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        bangumiDetailsArea.setLineWrap(true); // 启用自动换行
        bangumiDetailsArea.setWrapStyleWord(true); // 设置换行方式
        
        // 创建左右列表
        unwatchedList = new JList<>(createUnwatchedModel());
        watchedList = new JList<>(createWatchedModel());
        
        // 设置列表样式
        unwatchedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        watchedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // 更新列表和显示
        updateCurrentBangumiDisplay();
        
        // 设置字体
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 18));
    }
    
    private void createMenuButtons() {
        randomSelectButton = new JButton("随机抽取未观看番剧");
        addBangumiButton = new JButton("添加未观看番剧");
        markAsWatchedButton = new JButton("标记为已观看");
        loginButton = new JButton("登录");
        syncDataButton = new JButton("同步数据"); // 新增同步按钮
        
        // 默认隐藏随机抽取和标记已观看按钮
        randomSelectButton.setVisible(false);
        markAsWatchedButton.setVisible(false);
        
        // 设置按钮样式
        randomSelectButton.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        addBangumiButton.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        markAsWatchedButton.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        syncDataButton.setFont(new Font("微软雅黑", Font.PLAIN, 12)); // 新增同步按钮样式
    }
    
    private DefaultListModel<String> createUnwatchedModel() {
        DefaultListModel<String> model = new DefaultListModel<>();
        if (allBangumis != null) {
            for (Bangumi bangumi : allBangumis) {
                if (!bangumi.isWatched()) {
                    model.addElement(bangumi.getTitle() + " (提议人: " + bangumi.getProposer() + ")");
                }
            }
        }
        return model;
    }
    
    private DefaultListModel<String> createWatchedModel() {
        DefaultListModel<String> model = new DefaultListModel<>();
        if (allBangumis != null) {
            for (Bangumi bangumi : allBangumis) {
                if (bangumi.isWatched()) {
                    model.addElement(bangumi.getTitle() + " (提议人: " + bangumi.getProposer() + ")");
                }
            }
        }
        return model;
    }
    
    private void updateBangumiLists() {
        // 更新列表显示
        if (unwatchedList != null) {
            unwatchedList.setModel(createUnwatchedModel());
            unwatchedList.clearSelection(); // 清除选择，避免选择不存在的项目
            unwatchedList.updateUI(); // 强制更新UI
        }
        if (watchedList != null) {
            watchedList.setModel(createWatchedModel());
            watchedList.clearSelection(); // 清除选择，避免选择不存在的项目
            watchedList.updateUI(); // 强制更新UI
        }
        
        // 清除详情显示区，避免显示已删除项目的残留信息
        bangumiDetailsArea.setText("");
        
        // 更新当前观看显示
        updateCurrentBangumiDisplay();
        
        // 强制UI组件重绘以确保更新
        if (mainPanel != null) {
            mainPanel.revalidate();
            mainPanel.repaint();
        }
    }
    
    private void updateCurrentBangumiDisplay() {
        currentBangumiDisplay.setText("");
        if (currentBangumiList != null && !currentBangumiList.isEmpty()) {
            Bangumi current = currentBangumiList.get(0); // 有且仅有一个当前观看
            if (current != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("当前观看:\n");
                sb.append("番剧名: ").append(current.getTitle() != null ? current.getTitle() : "未知").append("\n");
                sb.append("简介: ").append(current.getDescription() != null ? current.getDescription() : "无").append("\n");
                sb.append("编剧: ").append(current.getWriter() != null ? current.getWriter() : "未知").append("\n");
                sb.append("原作: ").append(current.getOriginal() != null ? current.getOriginal() : "未知").append("\n");
                sb.append("导演: ").append(current.getDirector() != null ? current.getDirector() : "未知").append("\n");
                sb.append("提议人: ").append(current.getProposer() != null ? current.getProposer() : "未知").append("\n");
                sb.append("观看时间: ").append(current.getWatchTime() != null ? current.getWatchTime().toString() : "未设定").append("\n");
                sb.append("观看人: ").append(current.getWatcher() != null ? current.getWatcher() : "未设定").append("\n");
                currentBangumiDisplay.setText(sb.toString());
            } else {
                currentBangumiDisplay.setText("当前没有正在观看的番剧");
            }
        } else {
            currentBangumiDisplay.setText("当前没有正在观看的番剧");
        }
    }

    private void setupLayout() {
        setLayout(new BorderLayout());
        
        // 菜单按钮面板
        JPanel menuPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        menuPanel.add(randomSelectButton);
        menuPanel.add(addBangumiButton);
        menuPanel.add(markAsWatchedButton);
        menuPanel.add(loginButton);
        menuPanel.add(syncDataButton); // 添加同步按钮到面板
        
        // 整体顶部面板（菜单+标题）
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(menuPanel, BorderLayout.NORTH);
        topPanel.add(titleLabel, BorderLayout.CENTER);
        
        add(topPanel, BorderLayout.NORTH);
        
        // 左侧 - 未观看列表
        JScrollPane unwatchedScrollPane = new JScrollPane(unwatchedList);
        unwatchedScrollPane.setBorder(BorderFactory.createTitledBorder("未观看的番剧"));
        
        // 中间上半部分 - 当前观看显示
        JScrollPane currentBangumiScrollPane = new JScrollPane(currentBangumiDisplay);
        currentBangumiScrollPane.setBorder(BorderFactory.createTitledBorder("当前观看"));
        currentBangumiScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        // 中间下半部分 - 详情显示
        JScrollPane detailsScrollPane = new JScrollPane(bangumiDetailsArea);
        detailsScrollPane.setBorder(BorderFactory.createTitledBorder("番剧详情"));
        
        // 中间垂直分割面板 - 包含当前观看和详情
        topBottomSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, currentBangumiScrollPane, detailsScrollPane);
        topBottomSplitPane.setDividerLocation(0.45); // 给当前观看区域稍多一点空间
        topBottomSplitPane.setResizeWeight(0.45); // 设置调整权重
        topBottomSplitPane.setDividerSize(3); // 设置分割条大小
        topBottomSplitPane.setEnabled(false); // 禁止用户拖动分割条

        // 右侧 - 已观看列表
        JScrollPane watchedScrollPane = new JScrollPane(watchedList);
        watchedScrollPane.setBorder(BorderFactory.createTitledBorder("已观看的番剧"));
        
        // 中右分割面板 - 中间内容和已观看列表
        JSplitPane middleRightSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, topBottomSplitPane, watchedScrollPane);
        middleRightSplitPane.setDividerLocation(0.8); // 平均分配中间和右侧空间
        middleRightSplitPane.setResizeWeight(0.8); // 设置调整权重
        middleRightSplitPane.setDividerSize(3); // 设置分割条大小
        middleRightSplitPane.setEnabled(false); // 禁止用户拖动分割条

        // 主分割面板 - 左侧未观看列表和右侧内容（中间+已观看）
        leftRightSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, unwatchedScrollPane, middleRightSplitPane);
        leftRightSplitPane.setDividerLocation(0.2); // 给左侧未观看列表更多空间
        leftRightSplitPane.setResizeWeight(0.2); // 设置调整权重
        leftRightSplitPane.setDividerSize(3); // 设置分割条大小
        leftRightSplitPane.setEnabled(false); // 禁止用户拖动分割条

        add(leftRightSplitPane, BorderLayout.CENTER);
    }

    private void setupEventHandlers() {
        // 未观看列表点击事件
        unwatchedList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                displaySelectedBangumiDetails(unwatchedList.getSelectedIndex());
            }
        });
        
        // 已观看列表点击事件
        watchedList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                displaySelectedBangedumiDetailsForWatched(watchedList.getSelectedIndex());
            }
        });
        
        // 随机抽取按钮事件
        randomSelectButton.addActionListener(e -> randomSelectUnwatchedBangumi());
        
        // 添加番剧按钮事件
        addBangumiButton.addActionListener(e -> openAddBangumiDialog());
        
        // 标记为已观看按钮事件
        markAsWatchedButton.addActionListener(e -> markCurrentAsWatched());
        
        // 登录按钮事件
        loginButton.addActionListener(e -> showLoginDialog());
        
        // 数据同步按钮事件
        syncDataButton.addActionListener(e -> syncWithRemote());
    }
    
    private void randomSelectUnwatchedBangumi() {
        if (allBangumis == null) return;
        
        // 获取所有未观看的番剧
        java.util.List<Bangumi> unwatchedBangumis = new java.util.ArrayList<>();
        for (Bangumi bangumi : allBangumis) {
            if (!bangumi.isWatched()) {
                unwatchedBangumis.add(bangumi);
            }
        }
        
        if (unwatchedBangumis.isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有未观看的番剧！", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // 随机选择一个
        Random random = new Random();
        Bangumi selected = unwatchedBangumis.get(random.nextInt(unwatchedBangumis.size()));
        
        // 设置为当前观看
        if (currentBangumiList == null) {
            currentBangumiList = new java.util.ArrayList<>();
        }
        currentBangumiList.clear();
        currentBangumiList.add(selected);
        
        // 保存当前观看到文件（使用用户目录）
        JsonUtils.writeBangumiListToUserDir(currentBangumiList, "current_bangumi.json");
        
        // 推送更改到远程仓库
        if (AppConfig.getBooleanProperty("git.enabled", true)) {
            pushToRemote();
        }
        
        // 更新显示
        updateCurrentBangumiDisplay();
        updateBangumiLists(); // 更新列表以反映更改
        
        JOptionPane.showMessageDialog(this, "已随机选择: " + selected.getTitle(), "随机选择结果", JOptionPane.INFORMATION_MESSAGE);
    }
    
    private void openAddBangumiDialog() {
        // 创建添加番剧对话框
        JDialog dialog = new JDialog(this, "添加未观看番剧", true);
        dialog.setSize(400, 500);
        dialog.setLayout(new BorderLayout());
        
        // 创建表单面板，使用GridBagLayout以更好地控制组件间距
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10); // 设置组件周围的间距
        
        JTextField titleField = new JTextField();
        JTextArea descArea = new JTextArea(5, 20); // 增加简介框大小
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        JScrollPane descScrollPane = new JScrollPane(descArea);
        
        JTextField writerField = new JTextField();
        JTextField originalField = new JTextField();
        JTextField directorField = new JTextField();
        JTextField proposerField = new JTextField();
        
        // 添加标签和输入框
        gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("番剧名:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(titleField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 2; gbc.weighty = 0; gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("编剧:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(writerField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("原作:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(originalField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("导演:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(directorField, gbc);
        
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("提议人:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.fill = GridBagConstraints.HORIZONTAL;
        formPanel.add(proposerField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("简介:"), gbc);
        gbc.gridx = 1; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        formPanel.add(descScrollPane, gbc);
        
        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout());
        JButton addButton = new JButton("添加");
        JButton cancelButton = new JButton("取消");
        
        buttonPanel.add(addButton);
        buttonPanel.add(cancelButton);
        
        dialog.add(formPanel, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);
        
        // 添加按钮事件
        addButton.addActionListener(e -> {
            // 获取表单数据
            String title = titleField.getText().trim();
            String description = descArea.getText().trim();
            String writer = writerField.getText().trim();
            String original = originalField.getText().trim();
            String director = directorField.getText().trim();
            String proposer = proposerField.getText().trim();
            
            if (title.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "请输入番剧名！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 检查是否已存在同名番剧
            if (isTitleExists(title)) {
                JOptionPane.showMessageDialog(dialog, "番剧《" + title + "》已存在，不能重复添加！", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            // 创建新的Bangumi对象
            Bangumi newBangumi = new Bangumi();
            newBangumi.setTitle(title);
            newBangumi.setDescription(description);
            newBangumi.setWriter(writer);
            newBangumi.setOriginal(original);
            newBangumi.setDirector(director);
            newBangumi.setProposer(proposer);
            newBangumi.setWatched(false); // 默认未观看
            newBangumi.setVotes(0);
            
            // 添加到总列表
            if (allBangumis == null) {
                allBangumis = new java.util.ArrayList<>();
            }
            allBangumis.add(newBangumi);
            
            // 保存到文件（使用用户目录）
            JsonUtils.writeBangumiListToUserDir(allBangumis, "bangumi.json");
            
            // 保存当前观看列表（以防万一）
            JsonUtils.writeBangumiListToUserDir(currentBangumiList, "current_bangumi.json");
            
            // 推送更改到远程仓库
            if (AppConfig.getBooleanProperty("git.enabled", true)) {
                pushToRemote();
            }
            
            // 更新列表显示
            updateBangumiLists();
            
            JOptionPane.showMessageDialog(dialog, "番剧添加成功！", "成功", JOptionPane.INFORMATION_MESSAGE);
            dialog.dispose();
        });
        
        // 取消按钮事件
        cancelButton.addActionListener(e -> dialog.dispose());
        
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
    
    private void markCurrentAsWatched() {
        if (currentBangumiList == null || currentBangumiList.isEmpty()) {
            JOptionPane.showMessageDialog(this, "当前没有正在观看的番剧！", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        Bangumi current = currentBangumiList.get(0);
        
        // 在allBangumis中找到并更新该番剧的状态
        for (int i = 0; i < allBangumis.size(); i++) {
            Bangumi bangumi = allBangumis.get(i);
            if (bangumi.getTitle().equals(current.getTitle())) {
                bangumi.setWatched(true);
                break;
            }
        }
        
        // 从当前观看列表中移除
        currentBangumiList.clear();
        
        // 保存到文件（使用用户目录）
        JsonUtils.writeBangumiListToUserDir(allBangumis, "bangumi.json");
        
        // 同时清空当前观看文件
        JsonUtils.writeBangumiListToUserDir(currentBangumiList, "current_bangumi.json");
        
        // 推送更改到远程仓库
        if (AppConfig.getBooleanProperty("git.enabled", true)) {
            pushToRemote();
        }
        
        // 更新显示
        updateCurrentBangumiDisplay();
        updateBangumiLists(); // 更新列表以反映更改        
        JOptionPane.showMessageDialog(this, "已将 '" + current.getTitle() + "' 标记为已观看！", "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    private void displaySelectedBangumiDetails(int index) {
        if (allBangumis != null && index >= 0) {
            // 找到未观看列表中的第index个元素
            int unwatchedIndex = 0;
            for (int i = 0; i < allBangumis.size(); i++) {
                if (!allBangumis.get(i).isWatched()) {
                    if (unwatchedIndex == index) {
                        Bangumi selected = allBangumis.get(i);
                        displayBangumiDetail(selected);
                        return;
                    }
                    unwatchedIndex++;
                }
            }
        }
    }
    
    private void displaySelectedBangedumiDetailsForWatched(int index) {
        if (allBangumis != null && index >= 0) {
            // 找到已观看列表中的第index个元素
            int watchedIndex = 0;
            for (int i = 0; i < allBangumis.size(); i++) {
                if (allBangumis.get(i).isWatched()) {
                    if (watchedIndex == index) {
                        Bangumi selected = allBangumis.get(i);
                        displayBangumiDetail(selected);
                        return;
                    }
                    watchedIndex++;
                }
            }
        }
    }
    
    private boolean isTitleExists(String title) {
        if (allBangumis == null || title == null || title.isEmpty()) {
            return false;
        }
        
        for (Bangumi bangumi : allBangumis) {
            if (title.equals(bangumi.getTitle())) {
                return true;
            }
        }
        
        return false;
    }
    
    private void displayBangumiDetail(Bangumi bangumi) {
        if (bangumi == null) {
            bangumiDetailsArea.setText("番剧信息为空");
            return;
        }
        
        StringBuilder detail = new StringBuilder();
        detail.append("番剧名: ").append(bangumi.getTitle() != null ? bangumi.getTitle() : "未知").append("\n");
        detail.append("简介: ").append(bangumi.getDescription() != null ? bangumi.getDescription() : "无").append("\n");
        detail.append("编剧: ").append(bangumi.getWriter() != null ? bangumi.getWriter() : "未知").append("\n");
        detail.append("原作: ").append(bangumi.getOriginal() != null ? bangumi.getOriginal() : "未知").append("\n");
        detail.append("导演: ").append(bangumi.getDirector() != null ? bangumi.getDirector() : "未知").append("\n");
        detail.append("提议人: ").append(bangumi.getProposer() != null ? bangumi.getProposer() : "未知").append("\n");
        detail.append("是否观看完: ").append(bangumi.isWatched() ? "是" : "否").append("\n");
        detail.append("票数: ").append(bangumi.getVotes()).append("\n");
        
        // 如果有观看时间和观看人信息，则显示
        if (bangumi.getWatchTime() != null) {
            detail.append("观看时间: ").append(bangumi.getWatchTime().toString()).append("\n");
        } else {
            detail.append("观看时间: 未设定\n");
        }
        if (bangumi.getWatcher() != null) {
            detail.append("观看人: ").append(bangumi.getWatcher()).append("\n");
        } else {
            detail.append("观看人: 未设定\n");
        }
        
        bangumiDetailsArea.setText(detail.toString());
    }

    private void showLoginDialog() {
        JPasswordField passwordField = new JPasswordField();
        Object[] message = {
            "请输入密码:",
            passwordField
        };

        int option = JOptionPane.showConfirmDialog(this, message, "登录", JOptionPane.OK_CANCEL_OPTION);
        if (option == JOptionPane.OK_OPTION) {
            char[] password = passwordField.getPassword();
            String passwordStr = new String(password);
            
            if ("wisdomGuo97".equals(passwordStr)) {
                // 登录成功，显示隐藏的按钮
                randomSelectButton.setVisible(true);
                markAsWatchedButton.setVisible(true);
                loginButton.setVisible(false); // 隐藏登录按钮
                isLoggedIn = true;
                JOptionPane.showMessageDialog(this, "登录成功！", "提示", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "密码错误！", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void setupWindow() {
        setTitle(AppConfig.getProperty("app.title", "Bangumi Menu 系统"));
        setSize(1200, 800); // 固定窗口大小
        setResizable(false); // 禁止调整窗口大小
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // 居中显示
    }

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