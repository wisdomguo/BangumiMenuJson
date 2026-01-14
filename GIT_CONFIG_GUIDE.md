# Git配置指南 - 解决网络连接问题

## 问题描述
当应用程序尝试连接到GitHub仓库时，可能会遇到以下错误：
```
org.eclipse.jgit.api.errors.TransportException: https://github.com/wisdomguo/BangumiMenuJson.git: connection failed
Caused by: java.net.ConnectException: Connection time out: github.com
```

## 解决方案

### 1. 基础解决方案
- 检查网络连接是否正常
- 确认可以正常访问GitHub网站
- 验证仓库URL是否正确

### 2. 使用代理服务器（适用于中国用户）
如果直接访问GitHub有问题，可以通过配置HTTP代理来解决：

```bash
# 临时设置Git代理
git config --global http.proxy http://代理服务器地址:端口
git config --global https.proxy https://代理服务器地址:端口

# 取消代理设置
git config --global --unset http.proxy
git config --global --unset https.proxy
```

### 3. 更换镜像源
可以考虑使用GitHub镜像源或国内代码托管服务：

1. 将远程仓库URL更改为GitHub代理地址
2. 使用Gitee等国内平台作为备份仓库

### 4. 应用程序级别配置
本应用程序支持以下配置项：

- `git.remote.url`: 远程仓库地址
- `git.username`: 用户名
- `git.password`: 访问令牌（推荐使用Personal Access Token）

### 5. GitHub Personal Access Token配置
由于GitHub不再支持用户名密码认证，请使用Personal Access Token：

1. 登录GitHub账户
2. 进入Settings > Developer settings > Personal access tokens
3. 生成新的token并赋予repo权限
4. 在应用中使用此token代替密码

### 6. 网络超时设置
应用程序已内置60秒超时设置，以避免长时间等待。