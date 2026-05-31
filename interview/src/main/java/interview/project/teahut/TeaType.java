package interview.project.teahut;

public enum TeaType {
    SIMPLE("Simple Tea", 800,  2),
    KADAK ("Kadak Tea",  1200, 5);

    private final String displayName;
    private final int    brewTimeMs;
    private final int    strength;     // 1–5 scale

    TeaType(String displayName, int brewTimeMs, int strength) {
        this.displayName = displayName;
        this.brewTimeMs  = brewTimeMs;
        this.strength    = strength;
    }

    public String getDisplayName() { return displayName; }
    public int    getBrewTimeMs()  { return brewTimeMs; }
    public int    getStrength()    { return strength; }
}