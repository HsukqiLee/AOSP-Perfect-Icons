![image](https://github.com/elliana-wt/Pixel-Launcher-Icons/blob/main/otherimg/githubbanner.png)
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FHsukqiLee%2FAOSP-Perfect-Icons.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2FHsukqiLee%2FAOSP-Perfect-Icons?ref=badge_shield)

# AOSP Perfect Icons & Pixel 启动器图标补全计划

这是一个专为解决与 Pixel Launcher、系统 Launcher 或第三方启动器水土不服、样式不一的国内高频 App 图标而构建的图标包补全项目。
本项目基于原作者仓库 [elliana-wt/Pixel-Launcher-Icons](https://github.com/elliana-wt/Pixel-Launcher-Icons) 进行二次开发与润色，由 **Hsukqi Lee** 维护，集成了完整的 Material 3 设计规范、多语言切换、自适应图标渲染优化和自动化签名构建。

> [!NOTE]
> 本项目的 Android 图标包工程包名已升级为标准的个人域名包名：`com.tsinbei.aospperfecticons`。

---

## 目录结构说明

* **PixelLauncherMods**: 用于存放设计师绘制好的**带透明边框**的原始 PNG 图标。
* **GlobalIconPack**: 存放经 Python 脚本处理后的**贴边/无损压缩**图标，用于被 Android 图标包工程读取。
* **AndroidIconPack**: 图标包的 Android 工程源码，支持 Material 3 底栏导航、应用内/系统多语言切换以及自动生成图标包资源配置。
* **process_icons.py**: 用于自动化处理图标大小、剪裁透明边距并无损压缩的 Python 脚本。
* **component-map.json**: 图标与 App 组件（包名和 Activity 启动项）的映射配置文件，构建时会自动解析并生成对应的安卓资源文件。

---

## 开发与适配流程（一步步教你如何添加新图标）

如果你想为某个 App 添加图标适配，请按照以下流程操作：

### 第一步：绘制与准备图标
1. 绘制尺寸为 `512x512` 像素的 PNG 图标。
2. 将设计好的原始图标（可带有适当的透明外边距）放入 `PixelLauncherMods` 目录，命名为 `应用名称.png`（例如 `WeChat.png`）。

### 第二步：查找目标应用的 PackageName 与 Component 路径
Android 系统启动器是通过应用的组件信息来识别并替换图标的。你需要找到该应用的包名以及启动 Activity 类名。
* **推荐查找方法 1（使用 ADB 命令行）**：
  在手机上打开该 App，在电脑终端输入以下命令：
  ```bash
  adb shell dumpsys window | findstr mCurrentFocus
  # 或者
  adb shell dumpsys activity activities | findstr mFocusedApp
  ```
  你会得到类似输出：`mCurrentFocus=Window{... u0 com.tencent.mm/com.tencent.mm.ui.LauncherUI}`。
  其中 `com.tencent.mm` 即为包名，`com.tencent.mm.ui.LauncherUI` 即为启动 Activity。
* **推荐查找方法 2（使用手机端开发者工具）**：
  使用“创建快捷方式”、“当前 Activity”或“MT管理器”等工具，查看已安装应用的包名和主 Activity 路径。

### 第三步：添加组件映射
打开项目根目录下的 [component-map.json](file:///q:/Android/AOSPPerfectIcons/AndroidIconPack/component-map.json)，在对应的图标名称下添加你要适配的组件信息（支持添加多条组件映射，例如不同版本或分身应用的 Activity）：
```json
  "WeChat": [
    "ComponentInfo{com.tencent.mm/com.tencent.mm.ui.LauncherUI}"
  ]
```
> [!TIP]
> 如果无法确定具体的启动 Activity，也可以只使用包名进行模糊匹配，声明格式为：`PackageInfo{com.example.app}`。

### 第四步：运行 Python 脚本处理图标
1. 确保安装了 Python 3 和 Pillow 库：
   ```bash
   pip install Pillow
   ```
2. 在项目根目录下运行脚本：
   ```bash
   python process_icons.py
   ```
   *该脚本会自动剪裁 `PixelLauncherMods` 内图标的多余透明边距、转换格式、防止黑边伪影，并进行无损压缩后输出到 `GlobalIconPack`。*

### 第五步：编译 Android 应用
只要你修改了 [component-map.json](file:///q:/Android/AOSPPerfectIcons/AndroidIconPack/component-map.json) 或 `GlobalIconPack` 目录，下一次编译 Android 图标包工程时，Gradle 就会自动触发 `generateIconPackResources` 任务，自动为你生成 `assets/appfilter.xml`、`res/xml/drawable.xml` 以及应用内图标索引。

---

## 本地构建与签名配置

### 1. 普通编译（未签名包）
进入 `AndroidIconPack` 目录，运行以下命令（由于使用了 Android 35/37 SDK，构建时需要用 `-P` 传递编译 SDK 版本）：
* **Windows (PowerShell)**:
  ```powershell
  .\gradlew.bat :app:assembleRelease "-PICONPACK_COMPILE_SDK=35" "-PICONPACK_TARGET_SDK=35"
  ```
* **Linux / macOS**:
  ```bash
  ./gradlew :app:assembleRelease -PICONPACK_COMPILE_SDK=35 -PICONPACK_TARGET_SDK=35
  ```
编译成功后，未签名的 APK 将输出在：`AndroidIconPack/app/build/outputs/apk/release/app-release-unsigned.apk`。

### 2. 使用本地证书进行签名编译
如果你生成了自己的 `release.jks`（如我们刚才生成的签名），可以在本地进行签名打包：
```powershell
.\gradlew.bat :app:assembleRelease "-PICONPACK_COMPILE_SDK=35" "-PICONPACK_TARGET_SDK=35" "-PICONPACK_STORE_FILE=release.jks" "-PICONPACK_STORE_PASSWORD=aospperfecticons" "-PICONPACK_KEY_ALIAS=perfecticons" "-PICONPACK_KEY_PASSWORD=aospperfecticons"
```
签名后的正式包将输出在：`AndroidIconPack/app/build/outputs/apk/release/app-release.apk`。

> [!TIP]
> 如果不想每次都输入命令行长参数，也可以选择直接在 [gradle.properties](file:///q:/Android/AOSPPerfectIcons/AndroidIconPack/gradle.properties) 中硬编码这些配置：
> ```properties
> ICONPACK_STORE_FILE=release.jks
> ICONPACK_STORE_PASSWORD=aospperfecticons
> ICONPACK_KEY_ALIAS=perfecticons
> ICONPACK_KEY_PASSWORD=aospperfecticons
> ```

---

## GitHub Actions 持续集成与发布配置

本仓库已预配置了自动编译工作流 [.github/workflows/release-icon-pack.yml](file:///q:/Android/AOSPPerfectIcons/.github/workflows/release-icon-pack.yml)。每次你发布 Release 或手动触发 Actions 时，GitHub 会在云端自动编译并把 APK 挂载到发布附件中。

若要在云端进行**自动签名**，你需要为你的 GitHub 仓库添加以下几个 Secrets 变量：

### 1. 准备 Base64 格式的签名证书
因为 GitHub Actions 无法直接输入二进制的 `.jks` 文件，需要将它转化为 Base64 字符串形式输入。
在本地 PowerShell 中运行以下命令，将你的 `release.jks` 文件转换为 Base64 文本并存入 `keystore_base64.txt`：
```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks")) | Out-File -Encoding ascii keystore_base64.txt
```
复制 `keystore_base64.txt` 里的所有字符内容。

### 2. 在 GitHub 仓库设置 Secrets
打开你的 GitHub 仓库，进入 **Settings** -> **Secrets and variables** -> **Actions** -> **New repository secret**，添加以下四个变量：

* **`KEYSTORE_BASE64`**: 粘贴 `keystore_base64.txt` 里的全部 Base64 字符串。
* **`KEYSTORE_PASSWORD`**: `aospperfecticons` (你的 Key Store 密码)
* **`KEY_ALIAS`**: `perfecticons` (你的证书别名)
* **`KEY_PASSWORD`**: `aospperfecticons` (你的别名私钥密码)

*配置完成后，Actions 就会在云端拉取这些密文、自动解密为证书并完成构建，生成完美签名的 `app-release.apk` 挂载到 Release 页！*

---

## 核心设计与优化细节
为了确保作为图标包的主体具有一流的 Premium 质感，本项目已特别处理了如下细节：
* **关于页面防锯齿优化**：将 App 的大图预览资源 `ic_launcher_preview.png` 在编译期预加载到 `drawable-xxxhdpi` 文件夹中。系统解码时会自动进行高品质的双线性插值预缩放，消除了运行时直接在小 ImageView 强制下采样所产生的粗糙锯齿。
* **本体图标大黑框修复**：遵守 Android Adaptive Icon 规范，将图标包应用自身 `ic_launcher.xml` 的背景层设置为 `@android:color/white` 不透明背景，前景色缩放比设为 `18dp` 以将前景色缩放到安全绘制区，使得本体图标在桌面上显示为极其标准、高雅的白色圆形底座卡片，消除了透明背景导致的系统默认黑色底盒伪影。
* **M3 导航与多语言自适应**：底栏全面换装 Material 3 标准规范，支持无缝响应 Android 13+ 系统的“单应用语言首选项”切换。并且我们完美重写了 activity 重构时的生命周期状态保存与恢复机制，确保在 App 内切换语言重载后，导航栏高亮状态能与当前显示页面完美对应。

---

![image](https://github.com/elliana-wt/Pixel-Launcher-Icons/blob/main/otherimg/launcher.jpg)


## License
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2FHsukqiLee%2FAOSP-Perfect-Icons.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2FHsukqiLee%2FAOSP-Perfect-Icons?ref=badge_large)