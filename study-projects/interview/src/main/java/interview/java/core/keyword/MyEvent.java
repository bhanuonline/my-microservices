package interview.java.core.keyword;

public class MyEvent {
    // without volatile, visibility problems can occur
    private volatile boolean fromCluster = false;
    private static final long serialVersionUID = 1L;

    private transient volatile boolean fromNetwork;

    public boolean isFromNetwork() {
        return fromNetwork;
    }

    public void setFromNetwork(boolean fromNetwork) {
        this.fromNetwork = fromNetwork;
    }

    @Override
    public String toString() {
        return "MyEvent{fromNetwork=" + fromNetwork + '}';
    }

    public boolean isFromCluster() {
        return fromCluster;
    }

    public void setFromCluster(boolean fromCluster) {
        this.fromCluster = fromCluster;
    }
}