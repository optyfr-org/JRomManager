package jrm.fx.ui;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javafx.scene.control.TextField;
import jrm.locale.Messages;

final class MemoryStatusMonitor {
	private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
	private final TextField status;

	MemoryStatusMonitor(TextField status) {
		this.status = status;
	}

	void start() {
		scheduler.scheduleAtFixedRate(this::updateMemory, 0, 20, TimeUnit.SECONDS);
	}

	void updateMemory() {
		final Runtime rt = Runtime.getRuntime();
		status.setText(String.format(Messages.getString("MainFrame.MemoryUsage"), String.format(XX_MIB, rt.totalMemory() / 1048576.0),
				String.format(XX_MIB, (rt.totalMemory() - rt.freeMemory()) / 1048576.0), String.format(XX_MIB, rt.freeMemory() / 1048576.0),
				String.format(XX_MIB, rt.maxMemory() / 1048576.0)));
	}

	void shutdown() {
		scheduler.shutdown();
	}

	private static final String XX_MIB = "%.2f MiB";
}
