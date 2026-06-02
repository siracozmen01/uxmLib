package com.uxplima.uxmlib.update;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;

import com.uxplima.uxmlib.text.Text;
import org.junit.jupiter.api.Test;

class UpdateMessagesTest {

    private static final Release RELEASE = new Release("1.5.0", "https://github.com/o/r/releases/latest");

    @Test
    void notificationMentionsBothVersionsAsPlainText() {
        Component message = UpdateMessages.notification("uxmLib", "1.4.0", RELEASE);
        String plain = Text.plain(message);
        assertThat(plain).contains("uxmLib", "1.4.0", "1.5.0");
    }

    @Test
    void notificationCarriesAClickableOpenUrlToTheRelease() {
        Component message = UpdateMessages.notification("uxmLib", "1.4.0", RELEASE);
        assertThat(clickUrlOf(message)).contains(RELEASE.url());
    }

    // ClickEvent#value() is the URL accessor on the compile-time Adventure; a transitively-newer Adventure on the
    // test runtime marks it deprecated, so suppress the warning here rather than depend on a version-specific payload
    // API.
    @SuppressWarnings("deprecation")
    private static Optional<String> clickUrlOf(Component component) {
        ClickEvent click = component.clickEvent();
        if (click != null && click.action() == ClickEvent.Action.OPEN_URL) {
            return Optional.of(click.value());
        }
        for (Component child : component.children()) {
            Optional<String> found = clickUrlOf(child);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }
}
