package dk.logos_code.redis_manager.config;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Configures Netty to disable use of sun.misc.Unsafe which is deprecated
 * and will be removed in future Java versions.
 * 
 * This must happen before Netty classes are loaded, so we use @Startup
 * to ensure this bean is initialized early.
 */
@ApplicationScoped
@Startup
public class NettyConfigurer {

    @PostConstruct
    public void configureNetty() {
        // Disable Netty's use of sun.misc.Unsafe to avoid deprecation warnings
        // This must be set before any Netty classes are loaded
        String property = "io.netty.noUnsafe";
        String currentValue = System.getProperty(property);
        
        if (currentValue == null) {
            System.setProperty(property, "true");
            Log.info("Set system property io.netty.noUnsafe=true to disable sun.misc.Unsafe usage");
        } else {
            Log.info("System property io.netty.noUnsafe already set to: " + currentValue);
        }
    }
}
