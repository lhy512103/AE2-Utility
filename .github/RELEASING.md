# Forge 1.20.1 自动构建与发布

## 启用条件

本分支发布到已有项目：Modrinth `kJXRtOkZ`（ae2utility）、CurseForge `1521605`（ae2-utility），运行平台固定 **Minecraft 1.20.1 / Forge / Java 17**。不发布 main 分支的 NeoForge 版本，也不抢占该版本线的 GitHub Latest 标记。

在 [仓库 Actions Secrets](https://github.com/lhy512103/AE2-Utility/settings/secrets/actions) 添加：

- `MODRINTH_TOKEN`：有本项目创建版本、修改版本权限的 Modrinth Token。
- `CURSEFORGE_TOKEN`：有本项目文件上传、更新权限的 CurseForge Upload API Token。

不要把 Token 发到聊天、写进代码、命令行参数或日志。两平台任务只获得各自的 Token；普通 PR/CI 不获得发布密钥。GitHub Release 使用自动提供的 `GITHUB_TOKEN`，工作流已声明 `contents: write`，不需要另建 GitHub PAT。

2026-11-10 最终检查已确认仓库 Actions 启用，且 `MODRINTH_TOKEN`、`CURSEFORGE_TOKEN` 两个 Secrets 名称均存在；未读取密钥内容，也未触发真实发布来验证平台权限。没有密钥时平台发布会明确失败，不会假装成功或跳过 JAR 校验。

## 正常发布

1. 更新 `gradle.properties` 中的 `mod_version`。
2. 在 `CHANGELOG.md` 写唯一的对应版本二级章节，例如 `## 1.20.1.4`；保留中英文有序列表，内容仅写用户可感知变更。
3. 提交并推送到 `forge-1.20.1`，确认 Build CI 通过。
4. 在该提交上创建并推送 `v1.20.1.4`（或 `1.20.1.4`）tag；版本必须和 `mod_version` 完全一致。
5. Tag Release 构建 tag 指向的源码，通过测试后提取该版本 changelog，不会混入其它版本或 Unreleased 内容。
6. 创建 GitHub Release 草稿，附加 **`ae2utility-1.20.1.4.jar` 主发布包**，从远端重新下载校验后才公开 Release。
7. 调用两平台同步流程，从已公开的 GitHub Release 实时读取正文和 JAR，再将 **JAR + Release 正文**一起上传到 CF/MR。

仅提交代码不会自动创建 tag 或对外发版。本次配置也不创建 tag，不发布任何新版本。

## Release 正文修改与重试

- `release-sync.yml` 在默认分支 main 和 Forge 分支各保留一份轻量路由，接收 `release.published` / `release.edited`；只有 Forge `1.20.1.*` tag 转发到本分支 `_publish.yml`，不会误发 NeoForge Release。
- 使用 `GITHUB_TOKEN` 创建的 Release 不会再次触发其它工作流，因此 tag 工作流直接调用 `_publish.yml`；人工修改公开 Release 正文则由事件路由自动同步。
- 也可以在 Actions 手动运行默认分支已注册的 `Forward Forge Release updates`，填写 Forge tag；它不会重建或替换附件，而是使用 Release 已有 JAR 和最新正文。`publish.yml` 也保留 Forge 分支的手动入口；若该入口尚未显示，直接使用默认分支路由即可。
- tag 构建失败时在对应 Actions run 使用 Re-run jobs 重试；无需移动 tag，也不要为触发同步而重新上传不同二进制。
- 已发布且二进制相同的 MR 版本会更新原版本说明；CF 使用保存于 Release 的 `publish-curseforge.json` 回执定位同一文件并更新说明，不创建重复版本。
- 每个平台成功后的回执单独保存为 Release 附件；一方失败不丢掉另一方的成功回执，重新执行可继续。
- 发布后不要修改 tag 指向或替换同版本二进制；需要变更 JAR 时应提升版本并推送新 tag。
- Gradle 归档文件顺序/时间已固定，tag 构建使用提交时间 `SOURCE_DATE_EPOCH`；若重新构建仍与公开附件哈希不一致，保持失败而不覆盖，改用同步工作流重试已有正确附件。

## 必须有 JAR 的硬门禁

任何 CF/MR 写入之前都要求：

1. GitHub Release 已公开、正文非空，tag 属于 Forge 分支历史。
2. Release 有且仅有一个名称精确匹配版本的主 JAR，附件状态为 uploaded，大小非零。
3. 实际下载附件并检查 ZIP/CRC、Forge `META-INF/mods.toml`、mod ID、版本、Minecraft 1.20.1 依赖范围、Java 17 类文件头和 SHA-256。
4. 不允许用 sources、slim、dev 或 Javadoc JAR 替代；缺少附件、错误版本、损坏包、正文为空、哈希冲突时直接阻止发布。
5. 发布脚本再次独立验证本地 bundle 的 JAR 和正文；没有 JAR 时不发送平台创建/更新请求。

正文同步保留 GitHub Markdown 原始格式；CF 使用 markdown changelogType，MR 使用 changelog 字段。

## 平台 API 的恢复边界

CF 上传 API 不提供严格的 exactly-once 保证；公开 CFWidget 文件列表可能缓存和滞后。发生超时或连接中断后，远端可能已经接收上传，必须先检查实际平台文件、恢复真实回执再重试，不能猜测文件 ID 或无限重发。历史上手动发布而没有回执/可验证哈希的同名版本会失败关闭，避免冒险重复上传。

工作流不自动重试非幂等上传。不同 JAR 哈希不能借用旧回执，只有同一文件的正文更新可以复用回执。

## 文件与验证

- `workflows/build.yml`：Forge 分支/PR 编译测试、发布安全脚本测试、CI JAR 和测试报告附件。
- `workflows/release.yml`：tag/手动 GitHub Release 构建、附件验证及后续平台调用。
- `workflows/_publish.yml`：校验实际 GitHub Release 并分别发布 CF/MR。
- `workflows/publish.yml`：手动重试/同步入口。
- `workflows/release-sync.yml`：默认分支及 Forge Release 事件转发。
- `scripts/release.py`、`scripts/platform_publish.py`：仅使用 Python 标准库和 gh CLI 的发布实现。

离线校验命令（不会访问平台或使用密钥）：

```text
python -B -m unittest discover -s .github/scripts/tests -v
```

工作流与脚本的开发变更不写用户 changelog；对应版本的正文仅从已有用户 changelog 章节提取。
