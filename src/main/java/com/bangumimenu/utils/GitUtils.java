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
    
    private static final String REPO_PATH = System.getProperty("user.dir");
    private static Git git;
    
    /**
     * 初始化Git仓库
     */
    public static boolean initRepo() {
        try {
            File repoDir = new File(REPO_PATH);
            File gitDir = new File(repoDir, ".git");
            
            if (!gitDir.exists()) {
                // 如果不存在.git目录，则初始化一个新的仓库
                git = Git.init().setDirectory(repoDir).call();
                System.out.println("Git仓库初始化完成");
            } else {
                // 如果存在.git目录，则打开现有仓库
                git = Git.open(repoDir);
                System.out.println("已连接到现有Git仓库");
            }
            
            // 设置远程仓库URL
            String remoteUrl = AppConfig.getProperty("git.remote.url", "");
            if (!remoteUrl.isEmpty()) {
                // 确保使用HTTPS协议，防止JGit错误地尝试使用SSH
                if (!remoteUrl.startsWith("https://")) {
                    System.err.println("远程仓库URL应使用HTTPS协议");
                    return false;
                }
                
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
        if (git == null) {
            System.err.println("Git仓库未初始化");
            return false;
        }
        
        try {
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
            
            // 尝试拉取默认分支
            try {
                PullResult result = git.pull()
                    .setCredentialsProvider(credentialsProvider)
                    .call();
                    
                if (result.isSuccessful()) {
                    System.out.println("成功拉取最新更改");
                    return true;
                } else {
                    System.out.println("拉取失败: " + result.getMergeResult());
                    return false;
                }
            } catch (org.eclipse.jgit.api.errors.RefNotAdvertisedException e) {
                // 如果默认拉取失败，尝试先获取远程分支信息
                System.out.println("检测到默认分支拉取失败，正在获取远程分支信息...");
                
                // 先 fetch 远程分支信息
                git.fetch()
                    .setCredentialsProvider(credentialsProvider)
                    .setRemote("origin")
                    .call();
                
                // 尝试 checkout 并 pull master 分支
                String remoteBranch = "master";
                try {
                    // 检出远程分支
                    git.checkout()
                        .setName(remoteBranch)
                        .setCreateBranch(true)
                        .setUpstreamMode(org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
                        .setStartPoint("origin/" + remoteBranch)
                        .call();
                    
                    // 拉取更新
                    PullResult result = git.pull()
                        .setCredentialsProvider(credentialsProvider)
                        .call();
                        
                    if (result.isSuccessful()) {
                        System.out.println("成功拉取最新更改（从 " + remoteBranch + " 分支）");
                        return true;
                    } else {
                        System.out.println("拉取失败: " + result.getMergeResult());
                        return false;
                    }
                } catch (Exception ex) {
                    // 如果 master 分支失败，尝试 main 分支
                    remoteBranch = "main";
                    try {
                        // 检出远程分支
                        git.checkout()
                            .setName(remoteBranch)
                            .setCreateBranch(true)
                            .setUpstreamMode(org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.SET_UPSTREAM)
                            .setStartPoint("origin/" + remoteBranch)
                            .call();
                        
                        // 拉取更新
                        PullResult result = git.pull()
                            .setCredentialsProvider(credentialsProvider)
                            .call();
                            
                        if (result.isSuccessful()) {
                            System.out.println("成功拉取最新更改（从 " + remoteBranch + " 分支）");
                            return true;
                        } else {
                            System.out.println("拉取失败: " + result.getMergeResult());
                            return false;
                        }
                    } catch (Exception mainEx) {
                        System.err.println("无法检出 master 或 main 分支: " + mainEx.getMessage());
                        return false;
                    }
                }
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
        if (git == null) {
            System.err.println("Git仓库未初始化");
            return false;
        }
        
        try {
            // 只添加JSON数据文件，而不是所有文件
            String[] dataFiles = AppConfig.getProperty("git.data.files", "bangumi.json,current_bangumi.json").split(",");
            for (String file : dataFiles) {
                file = file.trim();
                if (!file.isEmpty()) {
                    // 检查文件是否存在
                    File dataFile = new File(System.getProperty("user.dir"), "src/main/resources/" + file);
                    if (dataFile.exists()) {
                        git.add().addFilepattern("src/main/resources/" + file).call();
                    } else {
                        // 检查根目录下的文件
                        dataFile = new File(System.getProperty("user.dir"), file);
                        if (dataFile.exists()) {
                            git.add().addFilepattern(file).call();
                        }
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
            
            Iterable<org.eclipse.jgit.transport.PushResult> pushResults = pushCommand.call();
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
        }
    }
    
    /**
     * 检查是否有本地更改
     */
    public static boolean hasLocalChanges() {
        if (git == null) {
            return false;
        }
        
        try {
            return !git.status().call().isClean();
        } catch (GitAPIException e) {
            System.err.println("检查更改状态失败: " + e.getMessage());
            return false;
        }
    }
}