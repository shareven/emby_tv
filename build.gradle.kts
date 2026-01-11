// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    // 必须添加下面这一行，版本号通常与 kotlin 一致
    alias(libs.plugins.kotlin.compose) apply false
}