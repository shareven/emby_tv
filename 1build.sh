#!/bin/bash
# 2. 获取 pubspec.yaml 中的版本号 (例如 1.0.0)
VERSION=$(grep 'version: ' pubspec.yaml | awk '{print $2}' | cut -d'+' -f1)


# 3. 清理并打包
flutter clean
flutter pub get
APK_NAME="emby_tv-v${VERSION}.apk"

# 构建Release版本
echo "开始构建Emby TV Release版本..."
flutter build apk

# 检查构建是否成功
if [ $? -eq 0 ]; then
    echo "构建成功！"
   
    # 复制APK到桌面
    if [ -f "build/app/outputs/flutter-apk/app-release.apk" ]; then
        cp build/app/outputs/flutter-apk/app-release.apk ~/Desktop/${APK_NAME}
        echo "APK已复制到桌面: ~/Desktop/${APK_NAME}"
    
    else
        echo "错误：找不到Release APK文件"
        exit 1
    fi
else
    echo "构建失败！"
    exit 1
fi

echo "构建完成！"