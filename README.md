# Emaki Series

`Project` 目录现在作为独立 Git 仓库使用，源码推送到 [Emaki_Series](https://github.com/jiuwu02/Emaki_Series)。

## Maven 版本策略

- `Emaki_CoreLib/pom.xml` 维护 `CoreLib` 自己的 `<version>`
- `Emaki_Forge/pom.xml` 维护 `Forge` 自己的 `<version>`
- `Emaki_Forge/pom.xml` 里的 `emaki.corelib.version` 表示 Forge 当前依赖的 CoreLib 版本

示例：

- `CoreLib` 可以是 `1.1.0`
- `Forge` 可以保持 `1.0.0`
- 只要 `Forge` 的 `emaki.corelib.version` 指向正确的 `CoreLib` 版本，就可以继续构建

常用命令：

```powershell
mvn -pl Emaki_CoreLib clean package
mvn -pl Emaki_Forge -am clean package
```

## Git 上传

上传命令：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\upload-code.ps1
```

这个脚本会：

1. 同步 `..\Emaki Plugin Wiki\` 到仓库内的 `wiki\`
2. `git add -A`
3. 使用固定提交信息 `上传代码`
4. 推送到 `origin`

## GitHub Wiki 自动发布

- `Emaki Plugin Wiki\` 是本地 Wiki 源目录
- 同步后的内容会进入仓库内的 `wiki\`
- 推送到 `main` 后，GitHub Actions 会把 `wiki\` 的内容发布到仓库 Wiki

如果仓库 Wiki 尚未启用，先在 GitHub 仓库设置里开启 Wiki。

如果默认 `GITHUB_TOKEN` 无法推送 Wiki，可以在仓库 Secrets 中添加：

- `WIKI_PUSH_TOKEN`: 一个对当前仓库有写权限的 GitHub Token
