package com.bangumimenu.utils;

import com.bangumimenu.config.AppConfig;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
     * 从远程仓库获取最新文件内容并覆盖本地文件
     */
    private static void overwriteLocalFileWithRemoteContent(String fileName) {
        try {
            File repoDir = new File(UserDataSync.getUserDataDir());
            Git git = Git.open(repoDir);
            
            // 先执行fetch获取远程最新内容
            String username = AppConfig.getProperty("git.username", "");
            String password = AppConfig.getProperty("git.password", "");
            UsernamePasswordCredentialsProvider credentialsProvider = 
                new UsernamePasswordCredentialsProvider(username, password);
            
            git.fetch()
                .setCredentialsProvider(credentialsProvider)
                .setTimeout(120)
                .call();
            
            // 尝试获取远程分支列表以确定默认分支
            java.util.List<org.eclipse.jgit.lib.Ref> remoteRefs = git.branchList()
                .setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE)
                .call();
            
            String targetBranch = null;
            // 优先查找main分支，然后是master分支
            for (org.eclipse.jgit.lib.Ref ref : remoteRefs) {
                String refName = ref.getName();
                if (refName.endsWith("/main")) {
                    targetBranch = refName;
                    break;
                } else if (refName.endsWith("/master")) {
                    targetBranch = refName;
                }
            }
            
            if (targetBranch != null) {
                // 获取指定远程分支的最新提交
                Iterable<RevCommit> commits = git.log()
                    .add(git.getRepository().resolve(targetBranch))
                    .setMaxCount(1)
                    .call();
                
                RevCommit latestCommit = commits.iterator().next();
                
                // 使用TreeWalk查找特定文件的内容
                try (TreeWalk treeWalk = new TreeWalk(git.getRepository())) {
                    treeWalk.addTree(latestCommit.getTree());
                    treeWalk.setRecursive(true);
                    treeWalk.setFilter(PathFilter.create(fileName));
                    
                    if (treeWalk.next()) {
                        org.eclipse.jgit.lib.ObjectId objectId = treeWalk.getObjectId(0);
                        org.eclipse.jgit.lib.ObjectLoader loader = git.getRepository().open(objectId);
                        
                        // 获取远程文件内容
                        byte[] content = loader.getBytes();
                        String fileContent = new String(content, StandardCharsets.UTF_8);
                        
                        // 覆盖本地文件
                        File localFile = new File(UserDataSync.getUserDataDir(), fileName);
                        java.nio.file.Files.write(localFile.toPath(), content, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);
                        
                        System.out.println("已从远程分支 " + targetBranch + " 覆盖本地文件: " + fileName);
                    } else {
                        System.out.println("在远程分支 " + targetBranch + " 中未找到文件: " + fileName);
                    }
                }
            } else {
                System.out.println("未找到合适的远程分支来获取文件: " + fileName);
            }
        } catch (Exception e) {
            System.err.println("覆盖本地文件时发生错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * 强制从远程仓库拉取最新内容进行覆盖
     */
    public static boolean forcePullChanges() {
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
            
            // 执行fetch操作获取远程更新
            try {
                git.fetch()
                    .setCredentialsProvider(credentialsProvider)
                    .setTimeout(120)
                    .call();
                System.out.println("成功获取远程更新信息");
            } catch (Exception e) {
                System.err.println("获取远程更新信息失败: " + e.getMessage());
                e.printStackTrace();
            }
            
            // 设置Git配置以改善网络连接
            git.getRepository().getConfig().setInt("http", null, "postBuffer", 524288000); // 设置POST缓冲区为500MB
            git.getRepository().getConfig().setInt("http", null, "timeout", 60); // 设置HTTP超时为60秒
            try {
                git.getRepository().getConfig().save();
            } catch (IOException e) {
                System.err.println("保存Git配置失败: " + e.getMessage());
            }
            
            // 获取所有需要同步的数据文件
            String[] dataFiles = AppConfig.getProperty("git.data.files", "bangumi.json,current_bangumi.json").split(",");
            
            // 无论本地是否有更改，都强制从远程获取最新内容并覆盖本地文件
            for (String fileName : dataFiles) {
                fileName = fileName.trim();
                if (!fileName.isEmpty()) {
                    overwriteLocalFileWithRemoteContent(fileName);
                }
            }
            
            // 将覆盖的文件添加到git并提交
            for (String fileName : dataFiles) {
                fileName = fileName.trim();
                if (!fileName.isEmpty()) {
                    File dataFile = new File(UserDataSync.getUserDataDir(), fileName);
                    if (dataFile.exists()) {
                        git.add().addFilepattern(fileName).call();
                    }
                }
            }
            
            // 提交强制更新的文件
            if (!git.status().call().getUncommittedChanges().isEmpty()) {
                git.commit()
                    .setMessage("强制同步远程最新内容 " + java.time.LocalDateTime.now())
                    .call();
            }
            
            // 尝试拉取，以防还有其他更新
            org.eclipse.jgit.api.PullResult result = null;
            try {
                result = git.pull()
                    .setCredentialsProvider(credentialsProvider)
                    .setTimeout(120) // 设置120秒超时
                    .call();
            } catch (org.eclipse.jgit.api.errors.RefNotAdvertisedException e) {
                System.err.println("RefNotAdvertisedException: " + e.getMessage());
                System.err.println("远程仓库未公布分支引用，尝试显式指定分支");
                
                // 获取远程分支信息
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
                } else {
                    System.err.println("无法找到合适的远程分支");
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
            } else if (result != null) {
                System.out.println("拉取部分成功或有警告: " + result.getMergeResult());
            } else {
                System.out.println("拉取操作完成，可能没有新更新");
            }
            
            // 强制拉取成功后，同步到项目目录
            UserDataSync.syncFromUserToProject();
            return true;
        } catch (Exception e) {
            System.err.println("强制拉取更改失败: " + e.getMessage());
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
            
            // 执行fetch操作获取远程更新
            try {
                git.fetch()
                    .setCredentialsProvider(credentialsProvider)
                    .setTimeout(120)
                    .call();
                System.out.println("成功获取远程更新信息");
            } catch (Exception e) {
                System.err.println("获取远程更新信息失败: " + e.getMessage());
                e.printStackTrace();
            }
            
            // 设置Git配置以改善网络连接
            git.getRepository().getConfig().setInt("http", null, "postBuffer", 524288000); // 设置POST缓冲区为500MB
            git.getRepository().getConfig().setInt("http", null, "timeout", 60); // 设置HTTP超时为60秒
            try {
                git.getRepository().getConfig().save();
            } catch (IOException e) {
                System.err.println("保存Git配置失败: " + e.getMessage());
            }
            
            // 拉取之前，先处理可能存在的冲突
            // 获取所有需要同步的数据文件
            String[] dataFiles = AppConfig.getProperty("git.data.files", "bangumi.json,current_bangumi.json").split(",");
            
            // 检查是否本地有未提交的更改
            boolean hasLocalChanges = !git.status().call().isClean();
            
            // 如果有本地更改，但在拉取时发生冲突，我们先直接获取远程内容覆盖
            if (hasLocalChanges) {
                System.out.println("检测到本地有更改，准备同步远程最新内容...");
            }
            
            // 先直接获取远程最新内容并覆盖本地文件（这是最可靠的同步方式）
            for (String fileName : dataFiles) {
                fileName = fileName.trim();
                if (!fileName.isEmpty()) {
                    overwriteLocalFileWithRemoteContent(fileName);
                }
            }
            
            // 现在本地文件已经是远程最新的内容，可以直接添加到git并提交
            for (String fileName : dataFiles) {
                fileName = fileName.trim();
                if (!fileName.isEmpty()) {
                    File dataFile = new File(UserDataSync.getUserDataDir(), fileName);
                    if (dataFile.exists()) {
                        git.add().addFilepattern(fileName).call();
                    }
                }
            }
            
            // 提交更新的文件
            if (!git.status().call().getUncommittedChanges().isEmpty()) {
                git.commit()
                    .setMessage("同步远程更新 " + java.time.LocalDateTime.now())
                    .call();
            }
            
            // 再次尝试拉取，以确保与远程仓库状态同步
            org.eclipse.jgit.api.PullResult result = null;
            try {
                result = git.pull()
                    .setCredentialsProvider(credentialsProvider)
                    .setTimeout(120) // 设置120秒超时
                    .call();
            } catch (org.eclipse.jgit.api.errors.RefNotAdvertisedException e) {
                System.err.println("RefNotAdvertisedException: " + e.getMessage());
                System.err.println("远程仓库未公布分支引用，尝试显式指定分支");
                
                // 获取远程分支信息
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
                } else {
                    System.err.println("无法找到合适的远程分支");
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
            } else if (result != null) {
                System.out.println("拉取部分成功或有警告: " + result.getMergeResult());
            } else {
                System.out.println("拉取操作完成，可能没有新更新");
            }
            
            // 拉取成功后，同步到项目目录
            UserDataSync.syncFromUserToProject();
            return true;
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
            
            // 获取配置中定义的数据文件列表
            String[] dataFiles = AppConfig.getProperty("git.data.files", "bangumi.json,current_bangumi.json").split(",");
            
            // 确保用户目录中的所有数据文件都被添加到Git
            for (String file : dataFiles) {
                file = file.trim();
                if (!file.isEmpty()) {
                    // 检查用户目录下的文件是否存在
                    File dataFile = new File(UserDataSync.getUserDataDir(), file);
                    if (dataFile.exists()) {
                        // 添加文件到Git索引
                        git.add().addFilepattern(file).call();
                        System.out.println("已添加文件到Git索引: " + file);
                    } else {
                        System.out.println("用户目录中未找到文件: " + file);
                    }
                }
            }
            
            // 检查是否有更改需要提交
            org.eclipse.jgit.api.Status status = git.status().call();
            java.util.Set<String> uncommittedChanges = status.getUncommittedChanges();
            java.util.Set<String> changedFiles = status.getChanged();
            java.util.Set<String> untracked = status.getUntracked();
            
            System.out.println("Git状态检查:");
            System.out.println("- 未提交的更改: " + uncommittedChanges);
            System.out.println("- 已修改的文件: " + changedFiles);
            System.out.println("- 未跟踪的文件: " + untracked);
            
            // 如果没有更改，就不需要提交和推送
            if (uncommittedChanges.isEmpty() && changedFiles.isEmpty() && untracked.isEmpty()) {
                System.out.println("没有JSON数据更改需要推送");
                return true;
            }
            
            // 提交更改
            git.commit()
                .setMessage(commitMessage)
                .call();
            System.out.println("已提交更改: " + commitMessage);
            
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