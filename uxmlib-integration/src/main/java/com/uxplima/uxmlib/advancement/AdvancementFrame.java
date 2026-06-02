package com.uxplima.uxmlib.advancement;

/**
 * The three vanilla advancement frame shapes. The frame controls the toast's border and the verb the
 * client narrates ("Task complete", "Goal reached", "Challenge complete"), so it is the visual register of
 * a toast. The JSON value is the lower-case name the vanilla advancement loader expects under
 * {@code display.frame}.
 */
public enum AdvancementFrame {

    /** The plain square frame — the default for ordinary advancements. */
    TASK("task"),

    /** The rounded frame used for milestone goals. */
    GOAL("goal"),

    /** The spiked frame used for hard challenges; the toast plays the challenge-complete sound. */
    CHALLENGE("challenge");

    private final String json;

    AdvancementFrame(String json) {
        this.json = json;
    }

    /** The lower-case token the advancement JSON's {@code display.frame} field uses. */
    public String json() {
        return json;
    }
}
