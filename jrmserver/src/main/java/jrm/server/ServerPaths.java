package jrm.server;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;

final class ServerPaths {
	static Path getWorkPath() {
		String base = System.getProperty("jrommanager.dir");
		if (base == null)
			base = System.getProperty("user.dir");
		return Paths.get(base);
	}

	static String getLogPath() throws IOException {
		final var path = getWorkPath().resolve("logs");
		Files.createDirectories(path);
		return path.toString();
	}

	static Resource getClientPath(ResourceFactory resourceFactory, String path) throws IOException, URISyntaxException {
		return ServerResourceLocator.locateClient(resourceFactory, path);
	}

	static Resource getCertsPath(String path) throws URISyntaxException, IOException {
		return ServerResourceLocator.locateCerts(path);
	}

	static Path getPath(String path) {
		return ServerResourceLocator.resolve(path);
	}
}
