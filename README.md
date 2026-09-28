# Yami Delight

<p align="center">为东方女仆（Touhou Little Maid）与 Farmer's Delight 添加酒狐女仆相关内容的 NeoForge 模组。</p>

<p align="center">
    <img src="https://img.shields.io/badge/Minecraft-1.21.1-blue" alt="Minecraft 1.21.1">
    <img src="https://img.shields.io/badge/NeoForge-21.1-orange" alt="NeoForge 21.1">
    <img src="https://img.shields.io/badge/version-1.0.0-informational" alt="版本 1.0.0">
    <img src="https://img.shields.io/badge/license-MIT-green" alt="MIT License">
</p>

<hr>

## 项目简介

**Yami Delight** 是一个面向 Minecraft 1.21.1 的 NeoForge 模组，围绕东方女仆中的酒狐女仆扩展头颅收藏、遗体处理、料理和展示内容。模组可以读取 Yes Steve Model（YSM）模型中的头部骨骼数据，让掉落的头颅保留对应女仆的模型外观；也添加了解剖台、肉钩、酒狐料理和宴席方块，并与 Farmer's Delight 的烹饪系统配合。

## 功能

### 女仆头颅与遗体

- 击败符合条件的女仆后可获得她的头颅；头颅物品保留模型和贴图信息，可作为方块展示。
- 支持读取 YSM 模型的头部及附属部件。客户端尚未安装对应模型时，会使用默认外观显示。
- 添加酒狐遗体、肉钩和解剖台，用于展示遗体并处理不同阶段的部位。
- 包含女仆悬吊死亡表现、相关动画和音效，以及可配置的死亡表现参数。

### 酒狐料理与宴席

- 添加酒狐肉、内脏和其他食材对应的料理与加工配方。
- 加入酒狐脑料理、酿酒狐、海陆风酒狐等食物或展示方块。
- 提供与 Farmer's Delight 烹饪锅的配方集成。

### 展示与模型兼容

- 头颅和遗体展示支持不同朝向，并提供对应模型资源。
- 对 YSM 模型进行客户端读取与渲染；YSM 为可选依赖，缺少相应模型时会使用默认外观。

## 依赖

| 模组 | 版本要求 | 必需 | 用途 |
|---|---|---:|---|
| Minecraft | 1.21.1 | 是 | 游戏版本 |
| NeoForge | 21.1.0 或更高的 21.1.x | 是 | 模组加载器 |
| [东方女仆（Touhou Little Maid）](https://github.com/TartaricAcid/TouhouLittleMaid) | 1.5.3 或更高 | 是 | 女仆实体、模型与相关事件 |
| [Farmer's Delight](https://github.com/vectorwing/FarmersDelight) | 1.3.4 或更高 | 是 | 食材、烹饪锅与料理集成 |
| Yes Steve Model（YSM） | 2.3.3 或更高 | 否 | 酒狐女仆模型读取与对应外观 |

游戏中请为客户端和服务端安装所有必需依赖。YSM 是可选依赖；没有 YSM 或对应模型文件时，相关头颅会使用默认外观。

## 安装

1. 安装 Minecraft 1.21.1 和 NeoForge 21.1.x。
2. 将东方女仆和 Farmer's Delight 放入实例的 `mods/` 文件夹。
3. 将 `Yami Delight` 的模组 JAR 放入同一 `mods/` 文件夹。
4. 如需使用 YSM 模型外观，再安装 Yes Steve Model 和对应模型文件。
5. 启动游戏。

## 从源码构建

需要 **JDK 21**。构建脚本使用 Gradle Wrapper；Windows PowerShell 下可运行：

```powershell
.\gradlew.bat compileJava
.\gradlew.bat build
.\gradlew.bat runClient
```

首次构建需要联网下载 Gradle 插件和 NeoForge 开发依赖。此外，项目源码直接使用东方女仆和 Farmer's Delight 的类，构建前需要将对应版本的依赖 JAR 放入项目根目录的 `libs/` 文件夹。当前开发环境使用的文件为：

```text
libs/FarmersDelight-1.21.1-1.3.4.jar
libs/touhoulittlemaid-1.5.3-neoforge+mc1.21.1.jar
libs/ysm-2.6.5-neoforge+mc1.21.1-release.jar
```

`build.gradle` 会自动读取 `libs/` 中的 JAR。依赖 JAR 不包含在源码仓库中；仓库的 `.gitignore` 规则也会忽略 `*.jar`。因此，克隆源码后需要自行获取匹配版本的依赖并放进 `libs/`，否则 `compileJava` 会因为找不到外部类而失败。请勿将运行环境中的整套 `mods/` 文件夹当作编译依赖。

请将 `gradle.properties`、Gradle Wrapper 脚本以及 `gradle/wrapper/` 配置一并纳入源码仓库，确保新克隆的项目具备 README 中所列的构建入口。

构建完成后，模组 JAR 位于：

```text
build/libs/
```

## 项目结构

```text
.
├── build.gradle                       # NeoForge ModDev 构建配置与本地依赖
├── gradle.properties                  # Minecraft、NeoForge 和模组版本
├── settings.gradle                   # Gradle 项目配置
├── gradlew.bat                        # Windows Gradle Wrapper
├── gradle/wrapper/                    # Gradle Wrapper 配置
├── libs/                              # 本地编译依赖 JAR（不随源码提交）
├── LICENSE                            # MIT 许可证
└── src/main/
    ├── java/com/yami/yamidelight/     # 模组逻辑、方块、物品、事件与渲染代码
    └── resources/                     # 模型、贴图、动画、配方、战利品表与语言文件
```

## 致谢

- 感谢 **Touhou Little Maid** 提供女仆实体、模型和相关扩展接口。
- 感谢 **Farmer's Delight** 提供料理与烹饪锅系统。
- 感谢 **Yes Steve Model** 及其模型作者，为女仆模型外观扩展提供支持。
- 感谢 NeoForge 及所有相关开源项目的开发者。

## 许可证

本项目使用 [MIT License](LICENSE)。第三方模组、模型及其他资源仍遵循各自作者的许可证；使用或再分发这些内容时，请遵守对应项目的许可要求。

## 声明

Yami Delight 是非官方的社区模组，与 Touhou Little Maid、Farmer's Delight、NeoForge、Yes Steve Model 及其作者没有隶属、背书或官方支持关系。相关名称、商标和第三方资源归其各自所有者所有。
