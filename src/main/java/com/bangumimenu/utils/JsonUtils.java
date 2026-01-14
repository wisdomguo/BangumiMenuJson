package com.bangumimenu.utils;

import com.bangumimenu.entity.Bangumi;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.JsonSyntaxException;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * JSON工具类，用于处理Bangumi数据的读写
 */
public class JsonUtils {

    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new JsonSerializer<LocalDateTime>() {
                @Override
                public JsonElement serialize(LocalDateTime localDateTime, Type srcType, JsonSerializationContext context) {
                    if (localDateTime == null) {
                        return null;
                    }
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    return new JsonPrimitive(localDateTime.format(formatter));
                }
            })
            .registerTypeAdapter(LocalDateTime.class, new JsonDeserializer<LocalDateTime>() {
                @Override
                public LocalDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
                        throws JsonParseException {
                    try {
                        if (json == null || json.getAsString() == null || json.getAsString().isEmpty()) {
                            return null;
                        }
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        return LocalDateTime.parse(json.getAsString(), formatter);
                    } catch (DateTimeParseException e) {
                        return null; // 如果解析失败，返回null
                    }
                }
            })
            .setPrettyPrinting() // 格式化输出，便于阅读
            .create();

    /**
     * 从JSON文件读取Bangumi列表
     * @param filePath 文件路径
     * @return Bangumi对象列表
     */
    public static List<Bangumi> readBangumiList(String filePath) {
        try {
            InputStream inputStream = null;
            
            // 首先尝试从用户数据目录读取
            String userDataPath = GitUtils.getUserDataDir() + "/" + filePath.replaceFirst("^/", "");
            File userFile = new File(userDataPath);
            if (userFile.exists()) {
                inputStream = new FileInputStream(userFile);
            } else {
                // 如果用户目录中不存在，则从资源文件读取
                inputStream = JsonUtils.class.getResourceAsStream(filePath);
            }
            
            if (inputStream != null) {
                try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                    Type listType = new TypeToken<List<Bangumi>>(){}.getType();
                    return gson.fromJson(reader, listType);
                }
            } else {
                System.err.println("无法找到文件: " + filePath);
                // 返回空列表而不是null，以确保程序可以正常启动
                return new java.util.ArrayList<>();
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new java.util.ArrayList<>();
        } catch (JsonSyntaxException e) {
            System.err.println("JSON格式错误: " + e.getMessage());
            return new java.util.ArrayList<>();
        }
    }

    /**
     * 将Bangumi列表写入JSON文件
     * @param bangumis Bangumi对象列表
     * @param filePath 文件路径（绝对路径或相对路径）
     */
    public static void writeBangumiList(List<Bangumi> bangumis, String filePath) {
        String json = gson.toJson(bangumis);
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(filePath), StandardCharsets.UTF_8)) {
            writer.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // 如果写入的是用户目录中的文件，也需要确保同步到项目资源目录（仅在开发环境）
        String userDataDir = GitUtils.getUserDataDir();
        if (filePath.startsWith(userDataDir)) {
            // 这是写入用户目录的文件，如果是开发环境，也要同步到项目目录
            String fileName = new File(filePath).getName();
            UserDataSync.syncFromUserToProjectFile(fileName);
        }
    }
    
    /**
     * 将Bangumi列表写入用户数据目录下的JSON文件
     * @param bangumis Bangumi对象列表
     * @param fileName 文件名（相对于用户数据目录）
     */
    public static void writeBangumiListToUserDir(List<Bangumi> bangumis, String fileName) {
        String userDataPath = GitUtils.getUserDataDir() + "/" + fileName;
        writeBangumiList(bangumis, userDataPath);
    }
    
    /**
     * 将Bangumi列表写入项目资源目录下的JSON文件（仅在开发环境使用）
     * @param bangumis Bangumi对象列表
     * @param fileName 文件名（相对于项目资源目录）
     */
    public static void writeBangumiListToProjectResources(List<Bangumi> bangumis, String fileName) {
        // 检查是否在开发环境中运行（即存在src/main/java目录）
        String projectResourcesPath = System.getProperty("user.dir") + "/src/main/resources/" + fileName;
        File resourcesDir = new File(System.getProperty("user.dir"), "src/main/resources");
        
        // 仅在开发环境（存在src/main/resources目录）下写入项目目录
        if (resourcesDir.exists()) {
            writeBangumiList(bangumis, projectResourcesPath);
        }
    }

    /**
     * 解析日期时间字符串
     * @param dateTimeStr 日期时间字符串
     * @return LocalDateTime对象
     */
    public static LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(dateTimeStr, formatter);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 格式化日期时间
     * @param dateTime LocalDateTime对象
     * @return 日期时间字符串
     */
    public static String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return dateTime.format(formatter);
    }
}