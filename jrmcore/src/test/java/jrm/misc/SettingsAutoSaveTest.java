package jrm.misc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for the debounced auto-save mechanism in {@link Settings}.
 */
@DisplayName("Settings auto-save")
class SettingsAutoSaveTest {

	@Test
	@DisplayName("flush runs the save handler after a change")
	void flushRunsSaveHandlerAfterChange() {
		final var settings = new ProfileSettings();
		final var saves = new AtomicInteger();
		settings.setSaveHandler(saves::incrementAndGet);

		settings.setProperty("key", "value");

		assertThat(saves.get()).isZero();
		settings.flush();
		assertThat(saves.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("flush without changes does not run the save handler")
	void flushWithoutChangesDoesNothing() {
		final var settings = new ProfileSettings();
		final var saves = new AtomicInteger();
		settings.setSaveHandler(saves::incrementAndGet);

		settings.flush();

		assertThat(saves.get()).isZero();
	}

	@Test
	@DisplayName("auto-saves after the debounce delay")
	void autoSavesAfterDebounceDelay() throws Exception {
		final var settings = new ProfileSettings();
		final var latch = new CountDownLatch(1);
		settings.setSaveHandler(latch::countDown);

		settings.setProperty("key", true);

		assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
	}

	@Test
	@DisplayName("null save handler disables auto-save")
	void nullSaveHandlerDisablesAutoSave() {
		final var settings = new ProfileSettings();
		final var saves = new AtomicInteger();
		settings.setSaveHandler(saves::incrementAndGet);
		settings.setSaveHandler(null);

		settings.setProperty("key", "value");
		settings.flush();

		assertThat(saves.get()).isZero();
	}

	@Test
	@DisplayName("boolean, int and string setters all mark dirty")
	void allSetterOverloadsMarkDirty() {
		final var settings = new ProfileSettings();
		final var saves = new AtomicInteger();
		settings.setSaveHandler(saves::incrementAndGet);

		settings.setProperty("bool", true);
		settings.setProperty("int", 42);
		settings.setProperty("str", "value");

		assertThat(saves.get()).isZero();
		settings.flush();
		assertThat(saves.get()).isEqualTo(1);
	}
}
