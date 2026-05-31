import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.util.Locale

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val iconPackCompileSdk = providers.gradleProperty("ICONPACK_COMPILE_SDK").orNull?.toIntOrNull() ?: 37
val iconPackTargetSdk = providers.gradleProperty("ICONPACK_TARGET_SDK").orNull?.toIntOrNull() ?: iconPackCompileSdk
val iconPackStoreFile = providers.gradleProperty("ICONPACK_STORE_FILE").orNull?.let { rootProject.file(it) }
val iconPackStorePassword = providers.gradleProperty("ICONPACK_STORE_PASSWORD").orNull
val iconPackKeyAlias = providers.gradleProperty("ICONPACK_KEY_ALIAS").orNull
val iconPackKeyPassword = providers.gradleProperty("ICONPACK_KEY_PASSWORD").orNull
val iconPackHasSigningConfig = iconPackStoreFile != null &&
    iconPackStorePassword != null &&
    iconPackKeyAlias != null &&
    iconPackKeyPassword != null

fun md5(value: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

fun toResourceBase(name: String): String {
    val normalized = name.lowercase(Locale.US).replace("-", "_").replace(" ", "_")
    val asciiOnly = buildString {
        normalized.forEach { ch ->
            when {
                ch in 'a'..'z' || ch in '0'..'9' -> append(ch)
                ch == '_' -> append('_')
                else -> append('_')
            }
        }
    }
        .replace(Regex("_+"), "_")
        .trim('_')

    return if (asciiOnly.isBlank()) {
        "icon_${md5(name).take(12)}"
    } else {
        asciiOnly
    }
}

fun xmlEscape(value: String): String {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

fun jsonEscape(value: String): String {
    val sb = StringBuilder(value.length + 16)
    value.forEach { ch ->
        when (ch) {
            '\\' -> sb.append("\\\\")
            '"' -> sb.append("\\\"")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

fun normalizeMatchKey(value: String): String {
    return value.lowercase(Locale.US).replace(Regex("[^\\p{L}\\p{Nd}]"), "")
}

fun dimensionXml(value: String): String {
    return "${value}dp"
}

val generateIconPackResources by tasks.registering {
    group = "iconpack"
    description = "Generate drawable/asset resources from ../GlobalIconPack"

    val sourceDir = rootProject.projectDir.parentFile.resolve("GlobalIconPack")
    val componentMapFile = rootProject.projectDir.resolve("component-map.json")
    val generatedRoot = layout.buildDirectory.dir("generated/iconpack").get().asFile

    inputs.dir(sourceDir)
    inputs.file(componentMapFile).optional()
    outputs.dir(generatedRoot)

    doLast {
        if (!sourceDir.exists()) {
            throw GradleException("Icon source folder not found: ${sourceDir.absolutePath}")
        }

        val drawableDir = generatedRoot.resolve("res/drawable-nodpi")
        val launcherDrawableDir = generatedRoot.resolve("res/drawable")
        val adaptiveXmlDir = generatedRoot.resolve("res/drawable-anydpi-v26")
        val xmlDir = generatedRoot.resolve("res/xml")
        val assetsDir = generatedRoot.resolve("assets")

        delete(generatedRoot)
        drawableDir.mkdirs()
        launcherDrawableDir.mkdirs()
        adaptiveXmlDir.mkdirs()
        xmlDir.mkdirs()
        assetsDir.mkdirs()

        val sourcePngs = sourceDir
            .listFiles { f -> f.isFile && f.extension.equals("png", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase(Locale.US) }
            ?: emptyList()

        val usedNames = mutableSetOf<String>()
        data class GeneratedIconEntry(
            val name: String,
            val drawable: String,
            val launcherDrawable: String,
            val components: List<String>
        )

        val iconEntries = mutableListOf<GeneratedIconEntry>()
        val componentMapByKey = mutableMapOf<String, MutableSet<String>>()

        if (componentMapFile.exists()) {
            @Suppress("UNCHECKED_CAST")
            val parsed = JsonSlurper().parse(componentMapFile) as? Map<String, Any?>
            parsed?.forEach { (rawName, rawComponents) ->
                val key = normalizeMatchKey(rawName)
                if (key.isBlank()) {
                    return@forEach
                }
                val target = componentMapByKey.getOrPut(key) { linkedSetOf() }
                if (rawComponents is Iterable<*>) {
                    rawComponents.forEach { value ->
                        val text = value?.toString()?.trim().orEmpty()
                        if (text.isNotEmpty()) {
                            target += text
                        }
                    }
                }
            }
        }

        sourcePngs.forEach { file ->
            val displayName = file.nameWithoutExtension
            var resourceName = if (displayName.equals("AOSPPerfectIcon", ignoreCase = true)) {
                "ic_launcher"
            } else {
                "ic_${toResourceBase(displayName)}"
            }
            if (!resourceName.first().isLetter()) {
                resourceName = "ic_${md5(displayName).take(12)}"
            }

            if (!resourceName.matches(Regex("[a-z][a-z0-9_]*"))) {
                resourceName = "ic_${md5(displayName).take(12)}"
            }

            if (resourceName.length > 60) {
                resourceName = resourceName.take(48) + "_" + md5(displayName).take(11)
            }

            var finalName = resourceName
            var collisionIndex = 1
            while (usedNames.contains(finalName)) {
                finalName = "${resourceName}_${collisionIndex}"
                collisionIndex += 1
            }
            usedNames += finalName

            val components = componentMapByKey[normalizeMatchKey(displayName)]?.toList().orEmpty()
            val previewDrawableName = "${finalName}_preview"
            val launcherForegroundName = "${finalName}_foreground"

            file.copyTo(drawableDir.resolve("${previewDrawableName}.png"), overwrite = true)

            launcherDrawableDir.resolve("${launcherForegroundName}.xml").writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <inset xmlns:android="http://schemas.android.com/apk/res/android"
                    android:insetLeft="12dp"
                    android:insetTop="12dp"
                    android:insetRight="12dp"
                    android:insetBottom="12dp"
                    android:drawable="@drawable/${previewDrawableName}" />
                """.trimIndent() + "\n"
            )

            adaptiveXmlDir.resolve("${finalName}.xml").writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
                    <background android:drawable="@android:color/transparent" />
                    <foreground android:drawable="@drawable/${launcherForegroundName}" />
                </adaptive-icon>
                """.trimIndent() + "\n"
            )
            iconEntries += GeneratedIconEntry(
                name = displayName,
                drawable = previewDrawableName,
                launcherDrawable = finalName,
                components = components
            )
        }

        val jsonContent = buildString {
            append("[\n")
            iconEntries.forEachIndexed { index, entry ->
                append("  {\"name\":\"")
                append(jsonEscape(entry.name))
                append("\",\"drawable\":\"")
                append(jsonEscape(entry.drawable))
                append("\",\"launcherDrawable\":\"")
                append(jsonEscape(entry.launcherDrawable))
                append("\",\"components\":[")
                entry.components.forEachIndexed { componentIndex, component ->
                    append("\"")
                    append(jsonEscape(component))
                    append("\"")
                    if (componentIndex != entry.components.lastIndex) {
                        append(',')
                    }
                }
                append("]}")
                if (index != iconEntries.lastIndex) {
                    append(',')
                }
                append("\n")
            }
            append("]\n")
        }
        assetsDir.resolve("icon_pack_index.json").writeText(jsonContent)

        val drawableXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            append("<resources>\n")
            iconEntries.forEach { entry ->
                append("    <item name=\"")
                append(xmlEscape(entry.name))
                append("\" drawable=\"")
                append(xmlEscape(entry.drawable))
                append("\"/>\n")
            }
            append("</resources>\n")
        }
        xmlDir.resolve("drawable.xml").writeText(drawableXml)

        val appfilterXml = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
            append("<resources>\n")
            append("    <!-- Auto-generated. Component mapping source: component-map.json -->\n")
            append("    <scale factor=\"1.0\"/>\n")
            var mappedIconCount = 0
            var mappedComponentCount = 0
            iconEntries.forEach { entry ->
                val launcherDrawable = if (entry.launcherDrawable.isNotBlank()) entry.launcherDrawable else entry.drawable
                val launcherComponents = entry.components.filter { component ->
                    val start = component.indexOf('{')
                    val slash = component.indexOf('/')
                    val end = component.indexOf('}')
                    component.startsWith("ComponentInfo{") &&
                        start >= 0 && slash > start + 1 && end > slash + 1
                }
                append("    <item drawable=\"")
                append(xmlEscape(launcherDrawable))
                append("\"/>\n")

                if (launcherComponents.isNotEmpty()) {
                    mappedIconCount += 1
                }
                launcherComponents.forEach { component ->
                    append("    <item component=\"")
                    append(xmlEscape(component))
                    append("\" drawable=\"")
                    append(xmlEscape(launcherDrawable))
                    append("\"/>\n")
                    mappedComponentCount += 1
                }
            }
            append("</resources>\n")

            logger.lifecycle(
                "Component mapping: matched ${mappedIconCount} icons, ${mappedComponentCount} component entries"
            )
        }
        assetsDir.resolve("appfilter.xml").writeText(appfilterXml)

        logger.lifecycle("Generated ${iconEntries.size} icons into ${generatedRoot.absolutePath}")
    }
}

android {
    namespace = "com.hsukqi.aospperfecticons"
    compileSdk = iconPackCompileSdk

    signingConfigs {
        if (iconPackHasSigningConfig) {
            create("release") {
                storeFile = iconPackStoreFile
                storePassword = iconPackStorePassword
                keyAlias = iconPackKeyAlias
                keyPassword = iconPackKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.hsukqi.aospperfecticons"
        minSdk = 26
        targetSdk = iconPackTargetSdk
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (iconPackHasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            res.srcDir(layout.buildDirectory.dir("generated/iconpack/res"))
            assets.srcDir(layout.buildDirectory.dir("generated/iconpack/assets"))
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateIconPackResources)
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.activity:activity-ktx:1.10.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
