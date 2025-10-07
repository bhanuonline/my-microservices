package interview.spring.annotation;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app")
@Profile("preprod") // This bean will only be created if 'preprod' profile is active
public class AppProperties {

    private String name;
    private String version;
    private DataSource datasource;

    private Map<String, String> settings; // can be any type for value

    public Map<String, String> getSettings() {
        return settings;
    }
    public void setSettings(Map<String, String> settings) {
        this.settings = settings;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }
    public void setVersion(String version) {
        this.version = version;
    }

    public DataSource getDatasource() {
        return datasource;
    }
    public void setDatasource(DataSource datasource) {
        this.datasource = datasource;
    }

    public static class DataSource {
        private String url;
        private String username;
        private String password;

        public String getUrl() {
            return url;
        }
        public void setUrl(String url) {
            this.url = url;
        }

        public String getUsername() {
            return username;
        }
        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }
        public void setPassword(String password) {
            this.password = password;
        }
    }
}