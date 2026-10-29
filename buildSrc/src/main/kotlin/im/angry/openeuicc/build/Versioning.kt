package im.angry.openeuicc.build

import com.android.build.gradle.internal.api.ApkVariantOutputImpl
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Versioning also works from the complete source ZIP, without a .git directory. */
class MyVersioningPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.configure<BaseAppModuleExtension> {
            defaultConfig {
                versionCode = 4
                versionName = "0.1.3"
            }
            applicationVariants.all {
                if (name == "debug") {
                    val suffix = mergedFlavor.versionNameSuffix.orEmpty()
                    outputs.forEach {
                        with(it as ApkVariantOutputImpl) {
                            versionCodeOverride = 4
                            versionNameOverride = "0.1.3-debug$suffix"
                        }
                    }
                }
            }
        }
    }
}
