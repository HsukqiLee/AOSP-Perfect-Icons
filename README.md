![image](https://github.com/elliana-wt/Pixel-Launcher-Icons/blob/main/otherimg/githubbanner.png)

# Pixel 启动器图标补全计划

专门整治那些与 Pixel Launcher 水土不服的、喜欢乱加营销信息的 App 图标。

Since apps on Google Play generally support icon specifications, this project generally only needs to include Chinese apps. If you are interested in this project, its language defaults to Chinese.

## 目录说明

- **PixelLauncherMods**: 用于 Pixel Launcher Mods 的图标，包含透明边框。
- **GlobalIconPack**: 用于 Global Icon Pack 的图标，去除了多余的透明边框以保证显示大小和正常图标一致。
- **AndroidIconPack**: Android 图标包工程，构建时会自动读取 `GlobalIconPack` 并生成可发布 APK。

## 批量处理脚本

本项目包含一个 Python 脚本 `process_icons.py`，用于批量处理图标。

### 功能
1.  **预处理**：如果源图片尺寸为 512x518（未知原因导致导出的图标底部有空白），脚本会自动裁剪底部 6 像素，将其恢复为 512x512。
2.  **源文件压缩**：检查源文件（`PixelLauncherMods` 目录）大小，如果超过 200KB，脚本会自动进行无损压缩并覆盖源文件，以减小体积。
3.  **去透明边并贴边重排**：先按 alpha 通道裁掉透明边距，再把图标主体尽量撑满 512x512 画布，避免额外内边距。
4.  **避免黑边伪影**：通过预乘 alpha 缩放，减少“透明黑底参与插值”导致的黑圈/黑边问题。

### 使用方法
1.  确保已安装 Python 和 Pillow 库 (`pip install Pillow`)。
2.  将原始图标放入 `PixelLauncherMods` 目录。
3.  运行脚本：`python process_icons.py`
4.  处理后的图标将生成在 `GlobalIconPack` 目录中。

## Android 图标包工程

`AndroidIconPack` 是一个可直接构建的 Android 图标包应用（`compileSdk/targetSdk = 37`）。

### 自动资源生成

构建时会自动执行 `generateIconPackResources` 任务：
1. 扫描 `GlobalIconPack/*.png`。
2. 将文件名转换为合法 Android 资源名并复制到 `drawable-nodpi`。
3. 生成 `assets/icon_pack_index.json`（用于应用内展示）。
4. 基于 `AndroidIconPack/legacy-component-map.json` 自动写入 `component -> drawable` 对应关系（后续直接改这个 map 文件即可）。
5. 生成 `assets/appfilter.xml` 与 `res/xml/drawable.xml`（兼容常见 launcher 的图标包读取方式）。

生成的 `GlobalIconPack` 图标会尽量贴近正方形边框，供 launcher 自行加边框后保持正常视觉尺寸。

### 组件映射维护

组件映射文件：`AndroidIconPack/legacy-component-map.json`

后续直接修改这份 map 即可，构建时会自动注入到 `appfilter.xml`。

应用主页支持点击图标直接启动对应 app；未安装或无可启动组件时会给出提示。

### 本地构建

在 `AndroidIconPack` 目录执行：

```bash
gradle :app:assembleRelease
```

### 固定签名

如果你希望后续版本的 APK 保持同一签名，需要给 release 构建提供固定 keystore。

在 `AndroidIconPack/gradle.properties` 或命令行里设置这些属性：

```properties
ICONPACK_STORE_FILE=path/to/your-release.keystore
ICONPACK_STORE_PASSWORD=your-store-password
ICONPACK_KEY_ALIAS=your-key-alias
ICONPACK_KEY_PASSWORD=your-key-password
```

设置后，`assembleRelease` 会使用这组签名信息；不设置时会保持当前默认构建行为。

APK 输出路径：

`AndroidIconPack/app/build/outputs/apk/release/app-release.apk`

## Release 自动构建

新增工作流：`.github/workflows/release-icon-pack.yml`

触发方式：
1. 发布 GitHub Release（`published`）。
2. 手动触发（`workflow_dispatch`）。

工作流会：
1. 安装 Android SDK 37。
2. 构建图标包 release APK。
3. 上传到 Actions artifact。
4. 在 release 事件下自动把 APK 附加到 GitHub Release。

## 前后对比

![image](https://github.com/elliana-wt/Pixel-Launcher-Icons/blob/main/otherimg/launcher.jpg)
