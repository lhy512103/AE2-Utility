# NeoForge 26.1.2 自动发布

## 一次性设置

- 仓库：`lhy512103/AE2-Utility`。
- Repository Secrets：`MODRINTH_TOKEN`、`CURSEFORGE_TOKEN`（已检测到这两个名称；不会读取密钥内容）。
- 平台项目：Modrinth `kJXRtOkZ`，CurseForge `1521605`。
- GitHub Release 使用 Actions 自带 `GITHUB_TOKEN`，工作流请求 `contents: write`，无需 PAT。
- `.github/workflows/release-sync-26.yml` 同时部署到默认分支 `main`，处理手工更新 Release 的事件；目标分支维护 `_publish-26.yml` 和脚本。

## 发布新版本

1. 修改 `gradle.properties` 中的 `mod_version`，例如 `1.8.2`。
2. 在 `CHANGELOG.md` 添加唯一的 `## 1.8.2` 节，内容保持中英文有序列表；下一版本标题前的全部正文将写入 Release。
3. 提交并推送目标分支，再创建专用 tag：

```sh
git push origin neoforge-26.1.2
git tag neoforge-26.1.2-v1.8.2
git push origin neoforge-26.1.2-v1.8.2
```

不要用 `v1.8.2`：该仓库的旧版本线仍使用普通 `v*` tag。Tag 中的模组版本必须与属性一致，Minecraft 版本不是 mod_version。

工作流运行发布脚本测试、Java 25 构建及模组测试，校验主 JAR，先创建草稿 Release，上传 `ae2utility-neoforge-26.1.2-<版本>.jar` 并下载回验 SHA-256，然后公开 Release。已公开的相同附件直接复用，不删除重传。

随后显式调用双平台发布工作流，因为 `GITHUB_TOKEN` 创建的 Release 不会自动触发另一条 release 事件工作流。每个平台在写操作前重新下载 GitHub Release 的实际主 JAR和实时正文，首次上传以同一 multipart 请求提交 JAR与 Release 文本。

## 更新 Release / 重试

- 手工修改已公开 Release 的正文（`edited`）会经默认分支路由同步 MR/CF。
- 已有相同文件时只更新对应平台版本的说明，不重复上传二进制；仍要求 GitHub Release 当前存在可验证主 JAR。
- 平台回执 `publish-modrinth.json` / `publish-curseforge.json` 附在 Release，记录平台文件 ID 和 SHA-256；Actions artifacts 另保存90天备份。
- 附件上传本身不保证产生 `release.edited`。手工补齐附件后，运行 **Sync Release - NeoForge 26.1.2** 的 `workflow_dispatch` 并填写 tag，或再保存一次 Release 描述。
- 可重跑 **Tag Release - NeoForge 26.1.2**，但必须是已存在的真实 tag；会重新按该 tag 的 CHANGELOG 更新正文，而非保留手工修改文本。
- 已公开版本若二进制发生改变会阻断；增加 mod_version 并推新 tag，不覆盖既有平台文件。强制移动既有 tag 不是升级方案。
- 平台 API 网络失败不自动重试上传，避免成功响应丢失导致重复发布；先检查平台实际文件及回执。CF 公共文件目录可能有缓存，无法承诺跨服务 exactly-once。
- GitHub Release、MR、CF 不支持跨平台原子事务；一个平台失败不会取消另一个，修复失败平台后重跑即可。

## 强制门禁

缺主 JAR、空文件、sources/javadoc/dev 包、错误 ZIP、modId/mod_version 不符、Minecraft 依赖不为 required `[26.1.2]`、SHA-256 不符、空正文、草稿 Release，均拒绝平台发布。已有回执也不能绕过文件校验。

只读 CI 在目标分支 push / PR 上执行，保存构建 JAR 和测试报告，不读取平台 Token。发布流程只接受 `neoforge-26.1.2-v*`，其他版本线不走本配置。

```sh
python -B -m unittest discover -s .github/scripts/tests -v
```

配置工作流不等于发布版本；部署时不创建 tag、不触发平台写入。
