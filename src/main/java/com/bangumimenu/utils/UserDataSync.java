package com.bangumimenu.utils;

import com.bangumimenu.config.AppConfig;

import java.io.File;
import java.nio.file.Files;

/**
 * 用户数据同步工具类
 * 负责在用户目录和项目目录之间同步数据文件
 */
public class UserDataSync {
    
    public static final String USER_NAME = System.getProperty("user.name");
    private static final String USER_HOME_DIR = System.getProperty("user.home");
    private static final String APP_DATA_DIR = USER_HOME_DIR + "/.bangumi-menu";
    
    /**
     * 同步数据文件到用户目录（如果用户目录不存在文件）
     */
    public static void initializeUserData() {
        try {
            // 确保用户数据目录存在
            File appDataDir = new File(APP_DATA_DIR);
            if (!appDataDir.exists()) {
                appDataDir.mkdirs();
                System.out.println("创建用户数据目录: " + APP_DATA_DIR);
            }
            
            // 获取配置中定义的数据文件列表
            String[] dataFiles = AppConfig.getProperty("git.data.files", "bangumi.json,current_bangumi.json").split(",");
            
            for (String fileName : dataFiles) {
                fileName = fileName.trim();
                if (!fileName.isEmpty()) {
                    // 检查用户目录中是否已存在该文件
                    File userFile = new File(APP_DATA_DIR, fileName);
                    if (!userFile.exists()) {
                        // 如果用户目录中不存在该文件，则从资源文件复制
                        java.io.InputStream resourceStream = UserDataSync.class.getResourceAsStream("/" + fileName);
                        if (resourceStream != null) {
                            Files.copy(resourceStream, userFile.toPath(), 
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            System.out.println("已复制初始数据文件到用户目录: " + fileName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("初始化用户数据失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 从用户目录同步文件到项目资源目录
     * 注意：仅在安全范围内使用此方法，例如只同步特定的JSON文件
     */
    public static void syncFromUserToProject() {
        try {
            String[] dataFiles = AppConfig.getProperty("git.data.files", "bangumi.json,current_bangumi.json").split(",");
            
            for (String fileName : dataFiles) {
                fileName = fileName.trim();
                if (!fileName.isEmpty()) {
                    syncFromUserToProjectFile(fileName);
                }
            }
        } catch (Exception e) {
            System.err.println("从用户目录同步到项目目录失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 同步单个文件从用户目录到项目资源目录
     */
    public static void syncFromUserToProjectFile(String fileName) {
        try {
            // 从用户数据目录复制到项目资源目录
            File userFile = new File(APP_DATA_DIR, fileName);
            if (userFile.exists()) {
                // 获取项目资源目录路径 - 仅在开发环境下才同步到项目目录
                // 在JAR运行时，不创建src/main/resources目录
                String projectResourcesPath = getProjectResourcesPath();
                if (projectResourcesPath != null) {
                    File destFile = new File(projectResourcesPath, fileName);
                    
                    // 确保目标目录存在
                    if (!destFile.getParentFile().exists()) {
                        destFile.getParentFile().mkdirs();
                    }
                    
                    // 检查文件是否允许同步（仅限JSON文件）
                    if (isValidSyncFile(fileName)) {
                        Files.copy(userFile.toPath(), destFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("已同步文件到项目目录: " + fileName);
                    } else {
                        System.err.println("不允许同步非JSON文件: " + fileName);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("同步文件到项目目录失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 获取项目资源目录路径 - 仅在开发环境有效
     */
    private static String getProjectResourcesPath() {
        // 检查是否在开发环境中运行（即存在src/main/java目录）
        String currentDir = System.getProperty("user.dir");
        File resourcesDir = new File(currentDir, "src/main/resources");
        
        // 仅当资源目录存在时才返回路径，否则返回null（表示在JAR环境中运行）
        if (resourcesDir.exists()) {
            return resourcesDir.getAbsolutePath();
        }
        
        // 在JAR环境中，我们不应该创建src/main/resources目录
        // 所以返回null，这样就不会同步到不存在的目录
        System.out.println("检测到JAR环境，跳过项目目录同步");
        return null;
    }
    
    /**
     * 从项目资源目录同步文件到用户目录
     */
    public static void syncFromProjectToUser() {
        try {
            String[] dataFiles = AppConfig.getProperty("git.data.files", "bangumi.json,current_bangumi.json").split(",");
            
            for (String fileName : dataFiles) {
                fileName = fileName.trim();
                if (!fileName.isEmpty()) {
                    // 从项目资源目录复制到用户数据目录
                    String projectResourcesPath = getProjectResourcesPath();
                    if (projectResourcesPath != null) {
                        File sourceFile = new File(projectResourcesPath, fileName);
                        if (sourceFile.exists()) {
                            File destFile = new File(APP_DATA_DIR, fileName);
                            
                            // 确保目标目录存在
                            if (!destFile.getParentFile().exists()) {
                                destFile.getParentFile().mkdirs();
                            }
                            
                            // 检查文件是否允许同步（仅限JSON文件）
                            if (isValidSyncFile(fileName)) {
                                Files.copy(sourceFile.toPath(), destFile.toPath(),
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                System.out.println("已同步文件从项目目录: " + fileName);
                            } else {
                                System.err.println("不允许同步非JSON文件: " + fileName);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("从项目目录同步到用户目录失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 验证文件是否允许同步（仅允许JSON文件）
     */
    private static boolean isValidSyncFile(String fileName) {
        // 仅允许同步bangumi.json和settings.json文件，根据项目规范
        return fileName.equals("bangumi.json") || fileName.equals("current_bangumi.json") || fileName.equals("settings.json");
    }
    
    /**
     * 获取用户数据目录路径
     */
    public static String getUserDataDir() {
        return APP_DATA_DIR;
    }
}