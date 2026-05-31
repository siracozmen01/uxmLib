package com.uxplima.uxmlib.command;

/**
 * A registerable command definition. The Brigadier node and execution wiring land with the command
 * module's first feature pass; this contract names the unit consumers register.
 */
public interface CommandSpec {

    /** The primary command label, without a leading slash. */
    String name();
}
