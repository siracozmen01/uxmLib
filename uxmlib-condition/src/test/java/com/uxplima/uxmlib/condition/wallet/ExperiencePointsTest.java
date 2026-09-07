package com.uxplima.uxmlib.condition.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** The vanilla curve, proved at the two levels where it bends and on either side of each. */
class ExperiencePointsTest {

    @ParameterizedTest
    @CsvSource({"0,0", "1,7", "15,315", "16,352", "17,394", "30,1395", "31,1507", "32,1628", "50,5345"})
    @DisplayName("the total at a level is the one the game itself uses")
    void countsTheTotalAtAlevel(int level, int total) {
        assertThat(ExperiencePoints.totalAt(level)).isEqualTo(total);
    }

    @ParameterizedTest
    @CsvSource({"0,7", "15,37", "16,42", "30,112", "31,121"})
    @DisplayName("the step to the next level changes at sixteen and at thirty one")
    void countsTheStepToTheNextLevel(int level, int step) {
        assertThat(ExperiencePoints.toNextFrom(level)).isEqualTo(step);
    }

    @ParameterizedTest
    @CsvSource({"0", "1", "14", "15", "16", "17", "29", "30", "31", "32", "33", "60"})
    @DisplayName("one step up from a level lands exactly on the total of the next one")
    void joinsTheThreeFormulasWithoutAgap(int level) {
        assertThat(ExperiencePoints.totalAt(level) + ExperiencePoints.toNextFrom(level))
                .isEqualTo(ExperiencePoints.totalAt(level + 1));
    }

    @Test
    @DisplayName("the curve is not a line, so a level is not worth a fixed number of points")
    void bendsRatherThanScales() {
        assertThat(ExperiencePoints.toNextFrom(0)).isEqualTo(7);
        assertThat(ExperiencePoints.toNextFrom(31)).isEqualTo(121);
        // Thirty levels and thirty points are the two ends of the same misunderstanding.
        assertThat(ExperiencePoints.totalAt(30)).isEqualTo(1395);
    }

    @Test
    @DisplayName("a player halfway through a level keeps their half")
    void countsTheFractionOfAlevel() {
        int total = ExperiencePoints.totalOf(10, 0.5f);

        assertThat(total).isEqualTo(ExperiencePoints.totalAt(10) + Math.round(0.5f * ExperiencePoints.toNextFrom(10)));
    }

    @ParameterizedTest
    @CsvSource({"0", "1", "6", "100", "351", "352", "353", "1000", "1507", "1628", "5344", "100000"})
    @DisplayName("a total turns back into the level and the fraction it came from")
    void roundTripsAtotal(int total) {
        ExperiencePoints.Standing standing = ExperiencePoints.standingOf(total);

        assertThat(ExperiencePoints.totalOf(standing.level(), standing.progress()))
                .isEqualTo(total);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "6,0", "7,1", "351,15", "352,16", "393,16", "394,17", "1506,30", "1507,31", "1627,31"})
    @DisplayName("a total buys the level it reaches and not the one after it")
    void buysTheLevelItReaches(int total, int level) {
        assertThat(ExperiencePoints.standingOf(total).level()).isEqualTo(level);
    }

    @Test
    @DisplayName("a level or a total below zero is not a number the curve answers for")
    void refusesWhatIsBelowZero() {
        assertThatThrownBy(() -> ExperiencePoints.totalAt(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExperiencePoints.toNextFrom(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ExperiencePoints.standingOf(-1)).isInstanceOf(IllegalArgumentException.class);
    }
}
