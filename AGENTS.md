# 开发约定

- 每完成一个功能或修复，更新 `CHANGELOG.md` 并进行一次本地 Git 提交。
- 提交前检查当前分支，若处于 detached HEAD 或没有本地分支，先创建并切换到本地分支。
- Git author 和 committer 均使用 `lhy512103 <lhy512103@gmail.com>`，配置仅作用于本仓库。
- Changelog 沿用当前版本下的 `English`、`中文` 分节和有序列表，每项用一句话概括用户可感知的变化，开发内部调整无需记录。
- 中文 changelog 和提交标题使用简短的“新增：XXX，修复：XXX，移除：XXX，优化：XXX”格式，按需使用相应类别。
- 提交前完成与改动相关的验证，并检查暂存内容。
