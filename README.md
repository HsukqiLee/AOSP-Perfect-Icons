![image](https://github.com/elliana-wt/Pixel-Launcher-Icons/blob/main/otherimg/githubbanner.png)

# Pixel 启动器图标补全计划

专门整治那些与 Pixel Launcher 水土不服的、喜欢乱加营销信息的 App 图标。

Since apps on Google Play generally support icon specifications, this project generally only needs to include Chinese apps. If you are interested in this project, its language defaults to Chinese.

## 目录说明

- **PixelLauncherMods**: 用于 Pixel Launcher Mods 的图标，包含透明边框。
- **GlobalIconPack**: 用于 Global Icon Pack 的图标，去除了多余的透明边框以保证显示大小和正常图标一致。

## 批量处理脚本

本项目包含一个 Python 脚本 `process_icons.py`，用于批量处理图标。

### 功能
1.  **预处理**：如果源图片尺寸为 512x518（未知原因导致导出的图标底部有空白），脚本会自动裁剪底部 6 像素，将其恢复为 512x512。
3.  **源文件压缩**：检查源文件（`PixelLauncherMods` 目录）大小，如果超过 200KB，脚本会自动进行无损压缩并覆盖源文件，以减小体积。
4.  **去边框**：将图片统一缩放至 570x570，然后裁剪中心 512x512 区域。这可以有效去除原图中多余的透明边框，放大图标主体。

### 使用方法
1.  确保已安装 Python 和 Pillow 库 (`pip install Pillow`)。
2.  将原始图标放入 `PixelLauncherMods` 目录。
3.  运行脚本：`python process_icons.py`
4.  处理后的图标将生成在 `GlobalIconPack` 目录中。

## 前后对比

![image](https://github.com/elliana-wt/Pixel-Launcher-Icons/blob/main/otherimg/launcher.jpg)
