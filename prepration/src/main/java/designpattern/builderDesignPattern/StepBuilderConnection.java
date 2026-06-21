package designpattern.builderDesignPattern;


// Interfaces enforce each step
interface HostStep   { PortStep host(String host); }
interface PortStep   { DatabaseStep port(int port); }
interface DatabaseStep { BuildStep database(String db); }
interface BuildStep  {
    BuildStep username(String u);
    BuildStep password(String p);
    Connection build();
}


class ConnectionBuilder
    implements HostStep, PortStep, DatabaseStep, BuildStep {

    private String host, database, username, password;
    private int port;

    public static HostStep builder() { return new ConnectionBuilder(); }

    public PortStep host(String h)     { this.host = h; return this; }
    public DatabaseStep port(int p)   { this.port = p; return this; }
    public BuildStep database(String d){ this.database = d; return this; }
    public BuildStep username(String u){ this.username = u; return this; }
    public BuildStep password(String p){ this.password = p; return this; }
    public Connection build() { return new Connection(host, port, database, username, password); }
}

// The product class — holds the final values
class Connection {
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;

    // Package-private or public constructor — called by builder's build()
    public Connection(String host, int port, String database,
                      String username, String password) {
        this.host     = host;
        this.port     = port;
        this.database = database;
        this.username = username;
        this.password = password;
    }

    // Getters
    public String getHost()     { return host; }
    public int    getPort()     { return port; }
    public String getDatabase() { return database; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }

    // Use this connection to actually connect somewhere
    public void connect() {
        System.out.println("Connecting to " + host + ":" + port + "/" + database);
        // real DB connection logic here
    }
}

public class StepBuilderConnection{
    public static void main(String[] args) {
        Connection connection=ConnectionBuilder.builder().host("mysql").port(3306).database("dbname").username("bhanu").password("123").build();
        connection.connect();
    }

}

