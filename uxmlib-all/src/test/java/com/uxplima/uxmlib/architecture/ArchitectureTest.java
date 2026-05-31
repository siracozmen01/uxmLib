package com.uxplima.uxmlib.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture guards over the whole library. They fail the build if a module reaches "upward" into one
 * that should depend on it, or if any class touches the platform escape hatches the library exists to
 * replace (the legacy scheduler and the legacy command interfaces).
 */
@AnalyzeClasses(packages = "com.uxplima.uxmlib", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** Folia-safety: nothing may use the legacy BukkitScheduler family. */
    @ArchTest
    static final ArchRule noLegacyScheduler = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.bukkit.scheduler..")
            .because("scheduling goes through the uxmlib scheduler abstraction, never BukkitScheduler");

    /** Commands are Brigadier-only; the legacy command interfaces are forbidden. */
    @ArchTest
    static final ArchRule noLegacyCommandApi = noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.bukkit.command.CommandExecutor")
            .orShould()
            .dependOnClassesThat()
            .haveFullyQualifiedName("org.bukkit.command.TabCompleter")
            .because("commands use Brigadier via the command module, not CommandExecutor/TabCompleter");

    /** The common module is the root: it must not depend on any feature module. */
    @ArchTest
    static final ArchRule commonDependsOnNoFeatureModule = noClasses()
            .that()
            .resideInAnyPackage(
                    "com.uxplima.uxmlib.common..",
                    "com.uxplima.uxmlib.scheduler..",
                    "com.uxplima.uxmlib.text..",
                    "com.uxplima.uxmlib.config..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "com.uxplima.uxmlib.item..",
                    "com.uxplima.uxmlib.command..",
                    "com.uxplima.uxmlib.gui..",
                    "com.uxplima.uxmlib.storage..",
                    "com.uxplima.uxmlib.hook..",
                    "com.uxplima.uxmlib.hologram..",
                    "com.uxplima.uxmlib.discord..")
            .because("common is the foundation; features build on it, not the other way around");

    /** The item module must not depend on the GUI that is built on top of it. */
    @ArchTest
    static final ArchRule itemDoesNotDependOnGui = noClasses()
            .that()
            .resideInAPackage("com.uxplima.uxmlib.item..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("com.uxplima.uxmlib.gui..")
            .because("gui depends on item, never the reverse");
}
