package designpattern.builderpattren;
class Pizza {
    String size;
    boolean cheese;
    boolean pepperoni;

    @Override
    public String toString() {
        return "Pizza{" +
                "size='" + size + '\'' +
                ", cheese=" + cheese +
                ", pepperoni=" + pepperoni +
                '}';
    }
}
interface PizzaBuilder {
    void setSize(String size);
    void addCheese();
    void addPepperoni();
    Pizza build();
}

/**
 * Concrete Builder
 */
class VegPizzaBuilder implements PizzaBuilder {
    private Pizza pizza = new Pizza();

    public void setSize(String size) {
        pizza.size = size;
    }

    public void addCheese() {
        pizza.cheese = true;
    }

    public void addPepperoni() {
        pizza.pepperoni = false;
    }

    public Pizza build() {
        return pizza;
    }
}
class PizzaDirector {
    public Pizza makePizza(PizzaBuilder builder) {
        builder.setSize("Large");
        builder.addCheese();
        return builder.build();
    }
}


/**
 *
 PizzaDirector> PizzaBuilder > Pizza
 */
public class ClassicBuilderPatternGOFStyle {
    public static void main(String[] args) {
        PizzaDirector director = new PizzaDirector();
        Pizza pizza = director.makePizza(new VegPizzaBuilder());
        System.out.println(pizza);
    }
}
