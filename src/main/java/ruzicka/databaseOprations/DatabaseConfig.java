package ruzicka.databaseOprations;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Loads database configuration from a properties file.
 * Ensures properties are loaded only once.
 */
public class DatabaseConfig {

    private static final Properties properties = new Properties();
    private static final String PROPERTIES_FILE = "/database.properties";

    // Static initializer block to load the properties file when the class is first used.
    static {
        try (InputStream input = DatabaseConfig.class.getResourceAsStream(PROPERTIES_FILE)) {
            if (input == null) {
                throw new IOException("Unable to find " + PROPERTIES_FILE);
            }
            properties.load(input);
        } catch (IOException ex) {
            // If the config file can't be read, the application cannot run.
            // Throw a runtime exception to fail fast.
            throw new RuntimeException("Error loading database configuration", ex);
        }
    }

    public static String getUrl() {
        return properties.getProperty("db.url");
    }

    public static String getUser() {
        return properties.getProperty("db.user");
    }

    public static String getPassword() {
        return properties.getProperty("db.password");
    }
}