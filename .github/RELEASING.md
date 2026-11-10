# Automated Releases

## 中文操作说明

1. 在仓库 **Settings → Secrets and variables → Actions** 添加 `MODRINTH_TOKEN` 和 `CURSEFORGE_TOKEN`；不要把 Token 写进代码、日志、Release 或聊天消息。
2. 发布前更新 `gradle.properties` 的 `mod_version`，并将本次中英文有序更新条目整理到 `CHANGELOG.md` 的对应 `## X.Y.Z` 章节；不使用 `Unreleased` 作为发布内容。
3. 提交并推送版本提交后，推送匹配的版本 tag（如 `v1.8.1`）；可在 Actions 手动运行 `Tag Release` 并输入已有 tag 重试。
4. tag 工作流构建并测试该 tag，创建 Release 草稿，附加主 JAR 后回读校验，再公开 Release；随后从实际 Release 读取正文和附件，提交到 MR 与 CF。
5. 修改已公开 Release 正文会触发两平台同步；**没有正确主 JAR、附件未上传完成、正文为空、版本不符或 JAR 哈希冲突时均禁止发布**，不会拿源码/Javadoc JAR 代替。
6. 已公开版本的二进制发生变化必须升版本并发新 tag；同一 JAR 的重试通过每个平台的回执更新原版本正文，避免重复上传。
7. 此前已手动发布的 `1.8.0` 需要额外核实 CF 旧文件回执；通常从下一个新版本开始自动发布即可，不必重发旧 tag。
8. 两平台的 Token 目前未在仓库配置；工作流文件合入默认分支且配置密钥后才能真正自动上传；本配置本身不会自动创建新版本 tag。

以下英文部分说明校验、首次旧版本接管和故障恢复的具体边界。

## GitHub Actions Setup

Add these repository Actions secrets under **Settings → Secrets and variables → Actions → Repository secrets**:

- `MODRINTH_TOKEN`: a Modrinth token with permission to publish versions for project `kJXRtOkZ` (`ae2utility`).
- `CURSEFORGE_TOKEN`: a CurseForge API token with permission to upload files for project `1521605` (`ae2-utility`).

Never put either token in the repository, workflow files, release assets, or command-line arguments. The platform jobs receive only their own token. The ordinary build and GitHub Release build do not receive platform tokens.

The GitHub Actions repository setting **Allow GitHub Actions to create and approve pull requests** is not needed. The default `GITHUB_TOKEN` needs repository **Contents: write**; workflow permissions are declared in the workflows. The repository must permit Actions to run.

## Making a Release

1. Update `mod_version` in `gradle.properties` and move the corresponding user-facing English/Chinese ordered-list entries from `## Unreleased` into a matching `## X.Y.Z` section in `CHANGELOG.md`.
2. Commit the version and changelog update. CI must pass on the commit.
3. Push a version tag matching the version, for example `v1.8.1`. The tag must point at the commit containing the same `mod_version` and `## 1.8.1` changelog section.
4. The tag workflow builds/tests that tagged source, selects exactly `ae2utility-1.8.1.jar` (not source/Javadoc jars), and verifies that the ZIP, NeoForge descriptor, mod ID, version and size are valid.
5. It creates or updates the GitHub Release as a draft, uploads the JAR, downloads the actual Release asset again and verifies it, then publishes the Release with that version's bilingual changelog section. A missing JAR or changelog section fails before the Release is made public.
6. Only after the public GitHub Release has one uploaded, validated main JAR does the workflow fetch the Release asset and body and submit them to Modrinth and CurseForge. Editing the published GitHub Release body also syncs that text after verifying its JAR. `workflow_dispatch` can retry a tag, but does not bypass the asset or hash checks.

Do not reuse a published version tag for a different JAR. The workflow refuses to overwrite a released version with a different binary; increment `mod_version`, update the changelog and create a new tag instead.

## Existing Version Bootstrap

The currently published `1.8.0` CurseForge file exists already, while CurseForge's public file inventory does not expose a verifiable hash for it. The first automated CurseForge sync of this existing version therefore fails closed rather than risking a duplicate upload. Do not add a guessed receipt. An authorized maintainer must compare the actual platform JAR with the GitHub Release JAR, then create the verified `publish-curseforge.json` receipt and attach it to the GitHub Release, or leave the existing version untouched and publish the next version as a new tag. Modrinth exposes version file hashes and can verify/reuse a matching existing version automatically.

A receipt binds the platform, project ID, version, file ID and JAR SHA-256. It allows a retry to update only release text for that exact binary; a hash or identity mismatch is a hard failure.

CurseForge's public inventory service can be cached for up to an hour and may lag a successful upload. Writes are intentionally not retried automatically: if an upload request times out after the remote accepted it, reconcile the project file manually before rerunning. Workflow runs for a tag are serialized and save each successful platform receipt as a GitHub Release asset, but the external CurseForge API does not provide strict exactly-once upload semantics.

## Workflow Files

- `build.yml`: branch/PR compilation, tests and downloadable CI artifacts.
- `release.yml`: tag and manual GitHub Release build.
- `_publish.yml`: reusable, validated CurseForge/Modrinth publishing jobs.
- `publish.yml`: synchronizes a manually published or edited GitHub Release.

The publishing implementation and offline safety tests live under `scripts/`.
