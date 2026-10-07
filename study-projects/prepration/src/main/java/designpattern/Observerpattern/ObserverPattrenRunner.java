package designpattern.Observerpattern;

import java.util.ArrayList;
import java.util.List;

// ─────────────────────────────────────────
// STEP 1: Observer interface
// WHO watches → Viewer
// ─────────────────────────────────────────
interface Observer {
    void update(String channelName, String videoTitle);
}

// ─────────────────────────────────────────
// STEP 2: Subject interface
// WHO is watched → YouTube Channel
// ─────────────────────────────────────────
interface YoutubeChannel {
    void subscribe(Observer o);         // register a viewer
    void unSubscribe(Observer o);       // remove a viewer
    void notifyAllSubscribers();        // tell all viewers
}

// ─────────────────────────────────────────
// STEP 3: ConcreteSubject
// YouTube Channel holds the list + state
// ─────────────────────────────────────────
class Channel implements YoutubeChannel {
    String channelName;
    String latestVideo;
    List<Observer> subscribers = new ArrayList<>();

    public Channel(String channelName) {
        this.channelName = channelName;
    }

    @Override
    public void subscribe(Observer o) {
        subscribers.add(o);
        o.update(channelName, latestVideo);
        System.out.println("✅ New subscriber added! Total: " + subscribers.size());
    }

    @Override
    public void unSubscribe(Observer o) {
        subscribers.remove(o);
        o.update(channelName, latestVideo);
        System.out.println("❌ Subscriber removed! Total: " + subscribers.size());
    }

    @Override
    public void notifyAllSubscribers() {
        for (Observer o : subscribers) {
            o.update(channelName, latestVideo);   // push data to each viewer
        }
    }

    // When a video is uploaded → notify everyone automatically
    public void uploadVideo(String videoTitle) {
        this.latestVideo = videoTitle;
        System.out.println("\n📹 [" + channelName + "] uploaded: " + videoTitle);
        notifyAllSubscribers();   // ← this is the trigger
    }
}

// ─────────────────────────────────────────
// STEP 4: ConcreteObserver
// Viewer reacts when notified
// ─────────────────────────────────────────
class Viewer implements Observer {

    private String viewerName;

    public Viewer(String viewerName) {
        this.viewerName = viewerName;
    }

    @Override
    public void update(String channelName, String videoTitle) {
        System.out.println("🔔 " + viewerName + " got notified → " + channelName + " uploaded: " + videoTitle);
    }
}

public class ObserverPattrenRunner {
    public static void main(String[] args) {
// Create the Subject (YouTube Channel)
        Channel channel = new Channel("CodeWithMe");
        // Create Observers (Viewers)
        Viewer alice = new Viewer("Alice");
        Viewer bob   = new Viewer("Bob");
        Viewer carol = new Viewer("Carol");

        // Register observers to subject
        channel.subscribe(alice);
        channel.subscribe(bob);
        channel.subscribe(carol);

        // Trigger — all 3 get notified
        channel.uploadVideo("Observer Pattern Explained");

        channel.unSubscribe(bob);
        channel.uploadVideo("Strategy Pattern Explained");

    }
}
