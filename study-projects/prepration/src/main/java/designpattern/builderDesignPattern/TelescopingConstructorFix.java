package designpattern.builderDesignPattern;

class Pizza{
    // BEFORE — telescoping constructors (avoid this)

    /*public Pizza(String size) {  }
    public Pizza(String size, boolean cheese) {  }
    public Pizza(String size, boolean cheese, boolean pepperoni) {  }
    public Pizza(String size, boolean cheese, boolean pepperoni, boolean mushrooms) {  }

    // Caller has no idea what true, false, true means:
    Pizza p = new Pizza("large", true, false, true);*/

    private final String size;
    private final boolean cheese, pepperoni, mushrooms, olives;

    private Pizza(Builder b) {
        size = b.size;
        cheese = b.cheese;
        pepperoni = b.pepperoni;
        mushrooms = b.mushrooms;
        olives = b.olives;
    }

    public static class Builder {
        private final String size;
        private boolean cheese, pepperoni, mushrooms, olives;

        public Builder(String size) { this.size = size; }
        public Builder cheese()    { cheese = true; return this; }
        public Builder pepperoni() { pepperoni = true; return this; }
        public Builder mushrooms() { mushrooms = true; return this; }
        public Builder olives()    { olives = true; return this; }
        public Pizza build() { return new Pizza(this); }
    }
}
public class TelescopingConstructorFix {

}
