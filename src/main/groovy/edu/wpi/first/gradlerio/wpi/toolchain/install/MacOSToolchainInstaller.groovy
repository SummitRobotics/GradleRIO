package edu.wpi.first.gradlerio.wpi.toolchain.install

import de.undercouch.gradle.tasks.download.DownloadAction
import edu.wpi.first.gradlerio.wpi.WPIExtension
import edu.wpi.first.gradlerio.wpi.toolchain.WPIToolchainPlugin
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.internal.os.OperatingSystem

@CompileStatic
class MacOSToolchainInstaller extends AbstractToolchainInstaller {
    private WPIExtension wpiExtension

    @Override
    void install(Project project) {
        wpiExtension = project.extensions.getByType(WPIExtension)
        List<String> desiredVersion = wpiExtension.toolchainVersion.split("-") as List<String>
        URL src = WPIToolchainPlugin.toolchainDownloadURL(wpiExtension.toolchainTag, "FRC-${desiredVersion.first()}-Mac-Toolchain-${desiredVersion.last()}.tar.gz")
        File dst = new File(WPIToolchainPlugin.toolchainDownloadDirectory(), "macOS-${desiredVersion.join("-")}.tar.gz")
        dst.parentFile.mkdirs()


        println "Downloading..."
        def da = new DownloadAction(project)
        da.with { DownloadAction d ->
            d.src src
            d.dest dst
            d.overwrite false
        }
        da.execute()
        if (da.upToDate) {
            println "Already Downloaded!"
        }

        println "Extracting..."
        File extrDir = new File(WPIToolchainPlugin.toolchainExtractDirectory(), "macOS")
        if (extrDir.exists()) extrDir.deleteDir()
        extrDir.mkdirs()

        project.copy { CopySpec c ->
            c.from(project.tarTree(project.resources.gzip(dst)))
            c.into(extrDir)
        }

        println "Copying..."
        File installDir = WPIToolchainPlugin.toolchainInstallDirectory(wpiExtension.frcYear)
        if (installDir.exists()) installDir.deleteDir()
        installDir.mkdirs()

        project.copy { CopySpec c ->
            c.from(new File(extrDir, "frc${desiredVersion.first()}/roborio"))
            c.into(installDir)
        }

        println "Done!"
    }

    @Override
    boolean targets(OperatingSystem os) {
        return os.isMacOsX()
    }

    @Override
    String installerPlatform() {
        return "MacOS"
    }

    @Override
    File sysrootLocation() {
        return WPIToolchainPlugin.toolchainInstallDirectory(wpiExtension.frcYear)
    }
}
