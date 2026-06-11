FROM debian:12-slim

ENV DEBIAN_FRONTEND=noninteractive

# 安装运行时所需软件包：
# - openssh-server：SSH + SFTP（SFTP 是 OpenSSH 内置子系统，无需额外配置）
# - openjdk-17-jre-headless：Java 17 运行时，向下兼容 Java 8 编译的 jar
# - nginx：前端部署目标
# - unzip：deploy-platform 部署前端 zip 包后解压到站点目录
# - supervisor：进程管理，同时拉起 sshd 与 nginx
# - procps / sudo / ca-certificates：常用工具
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openssh-server \
        openjdk-17-jre-headless \
        nginx \
        unzip \
        supervisor \
        procps \
        sudo \
        ca-certificates && \
    rm -rf /var/lib/apt/lists/*

# 生成 SSH host key —— Debian 容器构建时 apt 触发器不会自动跑，
# 不显式生成的话 sshd 启动后会立即退出，登录端口空着
RUN ssh-keygen -A

# 移除 PAM 中 pam_loginuid 行 —— 容器没有 audit 子系统，
# 该模块会让密码正确的用户也被踢出，是 docker + sshd 的经典坑
RUN sed -i '/pam_loginuid/d' /etc/pam.d/sshd

# 账号：root/root 与 deploy/deploy
# deploy 加入 sudo 组并配置 NOPASSWD —— 普通用户 SSH 非交互场景下 sudo 拿不到密码会卡住，
# 测试容器直接放开。生产环境请改为限定命令列表（仅 /usr/sbin/nginx 之类）
RUN echo 'root:root' | chpasswd && \
    useradd -m -s /bin/bash deploy && \
    echo 'deploy:deploy' | chpasswd && \
    usermod -aG sudo deploy && \
    echo 'deploy ALL=(ALL) NOPASSWD: ALL' > /etc/sudoers.d/deploy-nopasswd && \
    chmod 440 /etc/sudoers.d/deploy-nopasswd

# 修复非 root 用户 SSH 非交互 shell 的 PATH —— Debian 默认给普通用户的 PATH 不含 /usr/sbin /sbin，
# 直接 `nginx -v` / `nginx -t` 会 command not found。
# OpenSSH 非交互不读 .bashrc/.profile，但 PAM 会读 /etc/environment，写这里最稳。
RUN echo 'PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"' > /etc/environment

# 允许密码认证 + 允许 root 直接登录（仅测试环境，请勿沿用至生产）
RUN sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication yes/' /etc/ssh/sshd_config && \
    sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin yes/' /etc/ssh/sshd_config

# sshd 运行所需目录
RUN mkdir -p /run/sshd

# supervisord 配置
COPY supervisord.conf /etc/supervisor/conf.d/supervisord.conf

# 22 SSH/SFTP；80 nginx；8080 是被部署的 jar 常用默认端口（按需自行调整）
EXPOSE 22 80 8080

CMD ["/usr/bin/supervisord", "-n", "-c", "/etc/supervisor/conf.d/supervisord.conf"]
