## 这个项目是什么

 这是一个Android图片编辑库。主体代码来自于
 -  https://github.com/burhanrashid52/PhotoEditor 
 -  https://github.com/jfeinstein10/DominantColors
 
 第一个项目是编辑的主体，第二个项目用于涂抹方法，即使用kmeans聚合算法取出涂抹时需要的颜色。因为第二个项目年久失修，主要工作是将项目的jni迁移到cmake，并完成了之前未完成的聚合出的颜色百分比统计功能。
 
## 关于rust分支

 这是将项目中C实现的部分改造为rust实现的分支。编译这个分支需要在你的电脑上配置一系列相关环境：
 
 ### how to build?
 1. Install rust from https://www.rust-lang.org/.
 2. Install the rust toolchains for your target platforms:
    ```
     rustup target add armv7-linux-androideabi   # for arm
     rustup target add i686-linux-android        # for x86
     rustup target add aarch64-linux-android     # for arm64
     rustup target add x86_64-linux-android      # for x86_64
     rustup target add x86_64-unknown-linux-gnu  # for linux-x86-64
     rustup target add x86_64-apple-darwin       # for darwin (macOS)
     rustup target add x86_64-pc-windows-gnu     # for win32-x86-64-gnu
     rustup target add x86_64-pc-windows-msvc    # for win32-x86-64-msvc
    ``` 
 3. Install Android studio,sdk and ndk toolchains.
 4. Build.
 
 ### More information about rust for android:
 + I use Rust Android Gradle Plugin from mozilla,see https://github.com/mozilla/rust-android-gradle
 + You can learn rust by Rust book. https://doc.rust-lang.org/stable/book/
 + Maybe you're interested in these knowlage:
   + jni
   + android-ndk
   + rust-ffi (https://doc.rust-lang.org/nomicon/ffi.html).
