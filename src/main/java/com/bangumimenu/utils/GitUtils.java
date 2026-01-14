package com.bangumimenu.utils;

import com.bangumimenu.config.AppConfig;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;

/**
 * Git工具类，用于处理Git版本控制和同步操作
 */
public class GitUtils {
    
    /**
     * 初始化Git仓库
     */
    public static boolean initRepo() {
        try {
            File repoDir = new File(UserDataSync.getUserDataDir());
            File gitDir = new File(repoDir, ".git");
            
            if (!repoDir.exists()) {
                repoDir.mkdirs(); // 创建用户数据目录
            }
            
            if (!gitDir.exists()) {
                // 如果不存在.git目录，则初始化一个新的仓库
                Git git = Git.init().setDirectory(repoDir).call();
                System.out.println("Git仓库初始化完成，位置: " + UserDataSync.getUserDataDir());
                
                // 复制初始数据文件
                UserDataSync.initializeUserData();
            } else {
                // 如果存在.git目录，则打开现有仓库
                Git git = Git.open(repoDir);
                System.out.println("已连接到现有Git仓库，位置: " + UserDataSync.getUserDataDir());
            }
            
            // 设置远程仓库URL
            String remoteUrl = AppConfig.getProperty("git.remote.url", "");
            if (!remoteUrl.isEmpty()) {
                // 确保使用HTTPS协议，防止JGit错误地尝试使用SSH
                if (!remoteUrl.startsWith("https://")) {
                    System.err.println("远程仓库URL应使用HTTPS协议");
                    return false;
                }
                
                Repository repository = Git.open(repoDir).getRepository();
                repository.getConfig().setString("remote", "origin", "url", remoteUrl);
                repository.getConfig().setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
                try {
                    repository.getConfig().save();
                } catch (IOException e) {
                    System.err.println("保存Git配置失败: " + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            System.err.println("Git仓库初始化失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 拉取最新更改
     */
    public static boolean pullChanges() {
        try {
            File repoDir = new File(UserDataSync.getUserDataDir());
            if (!repoDir.exists()) {
                System.err.println("用户数据目录不存在: " + UserDataSync.getUserDataDir());
                return false;
            }
            
            Git git = Git.open(repoDir);
            
            String username = AppConfig.getProperty("git.username", "");
            String password = AppConfig.getProperty("git.password", "");
            
            if (username.isEmpty() || password.isEmpty()) {
                System.err.println("Git认证信息未配置");
                return false;
            }
            
            UsernamePasswordCredentialsProvider credentialsProvider = 
                new UsernamePasswordCredentialsProvider(username, password);
            
            String remoteUrl = AppConfig.getProperty("git.remote.url", "");
            
            // 确保使用HTTPS协议，防止JGit错误地尝试使用SSH
            if (!remoteUrl.startsWith("https://")) {
                System.err.println("远程仓库URL应使用HTTPS协议");
                return false;
            }
            
            // 设置Git配置以改善网络连接
            git.getRepository().getConfig().setInt("http", null, "postBuffer", 524288000); // 设置POST缓冲区为500MB
            git.getRepository().getConfig().setInt("http", null, "timeout", 60); // 设置HTTP超时为60秒
            try {
                git.getRepository().getConfig().save();
            } catch (IOException e) {
                System.err.println("保存Git配置失败: " + e.getMessage());
            }
            
            // 先尝试拉取默认分支
            org.eclipse.jgit.api.PullResult result = null;
            try {
                result = git.pull()
                    .setCredentialsProvider(credentialsProvider)
                    .setTimeout(120) // 设置120秒超时
                    .call();
            } catch (org.eclipse.jgit.api.errors.RefNotAdvertisedException e) {
                System.err.println("RefNotAdvertisedException: " + e.getMessage());
                System.err.println("远程仓库未公布分支引用，尝试显式指定分支");
                
                // 先获取远程分支信息
                try {
                    org.eclipse.jgit.api.FetchCommand fetch = git.fetch();
                    fetch.setCredentialsProvider(credentialsProvider);
                    fetch.call();
                    
                    // 尝试获取远程仓库的默认分支信息
                    java.util.List<org.eclipse.jgit.lib.Ref> remoteRefs = git.branchList()
                        .setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE)
                        .call();
                    
                    // 检查远程仓库的默认分支
                    java.util.List<String> possibleDefaultBranches = java.util.Arrays.asList(
                        "main", "master", "develop", "trunk", "default"
                    );
                    
                    String actualDefaultBranch = null;
                    for (String branch : possibleDefaultBranches) {
                        for (org.eclipse.jgit.lib.Ref ref : remoteRefs) {
                            String refName = ref.getName();
                            if (refName.endsWith("/" + branch)) { // 检查是否以分支名结尾
                                actualDefaultBranch = branch;
                                break;
                            }
                        }
                        if (actualDefaultBranch != null) {
                            break;
                        }
                    }
                    
                    if (actualDefaultBranch != null) {
                        System.out.println("检测到远程默认分支: " + actualDefaultBranch + ", 正在拉取...");
                        result = git.pull()
                            .setCredentialsProvider(credentialsProvider)
                            .setRemote("origin")
                            .setRemoteBranchName(actualDefaultBranch)
                            .setTimeout(120)
                            .call();
                        System.out.println("成功拉取最新更改（" + actualDefaultBranch + "分支）");
                        
                        // 拉取成功后，同步到项目目录
                        UserDataSync.syncFromUserToProject();
                    } else {
                        // 如果以上方法都失败，尝试获取远程仓库的第一个分支
                        if (!remoteRefs.isEmpty()) {
                            // 获取第一个有效的远程分支
                            String firstBranch = null;
                            for (org.eclipse.jgit.lib.Ref ref : remoteRefs) {
                                String branchName = ref.getName();
                                if (branchName.startsWith("refs/remotes/origin/")) {
                                    firstBranch = branchName.substring("refs/remotes/origin/".length());
                                    // 排除HEAD引用，通常形如 refs/remotes/origin/HEAD -> origin/main
                                    if (!firstBranch.equals("HEAD")) {
                                        break;
                                    }
                                }
                            }
                            
                            if (firstBranch != null) {
                                System.out.println("检测到远程分支: " + firstBranch + ", 正在拉取...");
                                result = git.pull()
                                    .setCredentialsProvider(credentialsProvider)
                                    .setRemote("origin")
                                    .setRemoteBranchName(firstBranch)
                                    .setTimeout(120)
                                    .call();
                                System.out.println("成功拉取最新更改（" + firstBranch + "分支）");
                                
                                // 拉取成功后，同步到项目目录
                                UserDataSync.syncFromUserToProject();
                            } else {
                                System.err.println("无法找到任何远程分支");
                                return false;
                            }
                        } else {
                            System.err.println("远程仓库没有任何分支");
                            return false;
                        }
                    }
                } catch (Exception branchEx) {
                    System.err.println("无法从远程仓库拉取分支信息: " + branchEx.getMessage());
                    System.err.println("错误类型: " + branchEx.getClass().getSimpleName());
                    branchEx.printStackTrace();
                    return false;
                }
            } catch (org.eclipse.jgit.api.errors.TransportException e) {
                System.err.println("Git传输异常: " + e.getMessage());
                System.err.println("这通常是由于网络连接问题或认证失败导致的");
                // 尝试提供更具体的解决方案
                String remoteUrlCheck = AppConfig.getProperty("git.remote.url", "");
                if (remoteUrlCheck.contains("github.com")) {
                    System.err.println("GitHub连接问题可能的原因:");
                    System.err.println("1. 网络连接问题");
                    System.err.println("2. GitHub访问限制（特别是在中国地区）");
                    System.err.println("3. 认证凭据不正确");
                    System.err.println("4. 需要配置代理服务器");
                }
                return false;
            }
                
            if (result != null && result.isSuccessful()) {
                System.out.println("成功拉取最新更改");
                // 拉取成功后，同步到项目目录
                UserDataSync.syncFromUserToProject();
                return true;
            } else if (result != null) {
                System.out.println("拉取失败: " + result.getMergeResult());
                return false;
            } else {
                System.err.println("拉取操作返回null结果");
                return false;
            }
        } catch (Exception e) {
            System.err.println("拉取更改失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 推送更改 - 仅推送JSON数据文件
     */
    public static boolean pushChanges(String commitMessage) {
        try {
            File repoDir = new File(UserDataSync.getUserDataDir());
            if (!repoDir.exists()) {
                System.err.println("用户数据目录不存在: " + UserDataSync.getUserDataDir());
                return false;
            }
            
            Git git = Git.open(repoDir);
            
            // 先从项目目录同步文件到用户目录，以确保推送最新的数据
            UserDataSync.syncFromProjectToUser();
            
            // 只添加JSON数据文件，而不是所有文件
            String[] dataFiles = AppConfig.getProperty("git.data.files", "bangumi.json,current_bangumi.json").split(",");
            for (String file : dataFiles) {
                file = file.trim();
                if (!file.isEmpty()) {
                    // 检查用户目录下的文件是否存在
                    File dataFile = new File(UserDataSync.getUserDataDir(), file);
                    if (dataFile.exists()) {
                        git.add().addFilepattern(file).call();
                    }
                }
            }
            
            // 检查是否有更改需要提交
            if (git.status().call().getUncommittedChanges().isEmpty()) {
                System.out.println("没有JSON数据更改需要推送");
                return true;
            }
            
            // 提交更改
            git.commit()
                .setMessage(commitMessage)
                .call();
            
            // 获取配置信息
            String username = AppConfig.getProperty("git.username", "");
            String password = AppConfig.getProperty("git.password", "");
            String remoteUrl = AppConfig.getProperty("git.remote.url", "");
            
            if (username.isEmpty() || password.isEmpty() || remoteUrl.isEmpty()) {
                System.err.println("Git配置信息不完整");
                return false;
            }
            
            // 确保使用HTTPS协议，防止JGit错误地尝试使用SSH
            if (!remoteUrl.startsWith("https://")) {
                System.err.println("远程仓库URL应使用HTTPS协议");
                return false;
            }
            
            UsernamePasswordCredentialsProvider credentialsProvider = 
                new UsernamePasswordCredentialsProvider(username, password);
            
            PushCommand pushCommand = git.push();
            pushCommand.setCredentialsProvider(credentialsProvider);
            
            // 确保远程URL已设置
            if (git.getRepository().getConfig().getString("remote", "origin", "url") == null || 
                !git.getRepository().getConfig().getString("remote", "origin", "url").equals(remoteUrl)) {
                git.getRepository().getConfig().setString("remote", "origin", "url", remoteUrl);
                git.getRepository().getConfig().setString("remote", "origin", "fetch", "+refs/heads/*:refs/remotes/origin/*");
                try {
                    git.getRepository().getConfig().save();
                } catch (IOException e) {
                    System.err.println("保存Git配置失败: " + e.getMessage());
                    e.printStackTrace();
                    return false;
                }
            }
            
            Iterable<org.eclipse.jgit.transport.PushResult> pushResults = pushCommand.setTimeout(120).call();
            for (org.eclipse.jgit.transport.PushResult pushResult : pushResults) {
                for (org.eclipse.jgit.transport.RemoteRefUpdate refUpdate : pushResult.getRemoteUpdates()) {
                    if (refUpdate.getStatus() != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK) {
                        if (refUpdate.getStatus() == org.eclipse.jgit.transport.RemoteRefUpdate.Status.REJECTED_OTHER_REASON) {
                            System.err.println("推送被拒绝: " + refUpdate.getMessage());
                            System.err.println("这通常是因为远程仓库的保护规则，你可能需要:");
                            System.err.println("1. 检查你是否有推送权限");
                            System.err.println("2. 确认你推送的是自己的仓库（而不是他人的仓库）");
                            System.err.println("3. 检查仓库是否有分支保护规则");
                            return false;
                        } else {
                            System.err.println("推送失败: " + refUpdate.getStatus() + " - " + refUpdate.getMessage());
                            return false;
                        }
                    }
                }
            }
            
            System.out.println("成功推送JSON数据更改");
            return true;
        } catch (GitAPIException e) {
            System.err.println("推送更改失败: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.err.println("推送更改时发生未知错误: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 检查是否有本地更改
     */
    public static boolean hasLocalChanges() {
        try {
            File repoDir = new File(UserDataSync.getUserDataDir());
            if (!repoDir.exists()) {
                return false;
            }
            
            Git git = Git.open(repoDir);
            return !git.status().call().isClean();
        } catch (Exception e) {
            System.err.println("检查更改状态失败: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * 获取用户数据目录路径
     */
    public static String getUserDataDir() {
        return UserDataSync.getUserDataDir();
    }
}