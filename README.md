# AE2 Utility — NeoForge 26.1.2 Migration

## English

This branch is the in-progress port of AE2 Utility to Minecraft 26.1.2 and NeoForge 26.1.2.126 (Java 25). Features from the 1.21.1 project are being migrated and validated against the 26.1 API. Third-party integrations remain disabled until matching 26.1.2 releases and API signatures are verified.

## 中文

本分支用于将 AE2 Utility 迁移至 Minecraft 26.1.2、NeoForge 26.1.2.126（Java 25）。正在迁移并基于 26.1 API 校验 1.21.1 项目中的功能。第三方模组集成待对应的 26.1.2 版本及 API 签名核验后再启用。

## Build / 构建

Run `./gradlew build` with JDK 25. Integration features depending on Applied Energistics 2, JEI, EMI, or other add-ons are not buildable until their 26.1.2 dependencies are added.
