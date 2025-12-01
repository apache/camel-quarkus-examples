package org.acme.cxf.soap.utils;

import io.quarkus.runtime.LaunchMode;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

public final class CxfServerUtils {
    private CxfServerUtils() {
    }

    public static String getServerUrl() {
        Config config = ConfigProvider.getConfig();
        final int port = LaunchMode.current().equals(LaunchMode.TEST) ? config.getValue("quarkus.http.test-port", Integer.class)
                : config.getValue("quarkus.http.port", Integer.class);
        return "http://localhost:%d%s".formatted(port, config.getValue("quarkus.cxf.path", String.class));
    }
}
