# JumpBlocker 签名配置

签名配置保存在 `signing/signing.properties`，私钥文件保存在 `signing/jumpblocker-release.jks`。

## 当前证书

- 别名：`jumpblocker`
- DN：`CN=Operit JumpBlocker, O=Operit, C=CN`
- 算法：RSA 2048 / SHA256withRSA
- APK 签名方案：v3
- SHA-256：`1b:f7:a4:c6:3a:79:49:f1:b1:07:f5:44:7e:00:7b:cc:2e:0e:0a:8a:15:a5:67:44:bf:d7:dd:7c:18:96:30:fc`

## 后续签名规则

1. 构建新版本时必须使用 `signing/signing.properties` 中指定的 keystore、alias 和密码。
2. 签名后使用 `apksigner verify --verbose --print-certs` 校验证书指纹必须与上面一致。
3. 不得重新生成 keystore；重新生成会导致 Android 无法覆盖安装已有版本。
4. APK 必须包含 `assets/xposed_init`，并且内容必须是 `io.operit.jumpblocker.JumpBlocker`。
5. 源码发布包不得包含 `signing/` 目录或任何 keystore 文件。

> `signing.properties` 和 `.jks` 都包含敏感签名信息，仅限本地工作区使用，不要上传到公开仓库或发送给他人。