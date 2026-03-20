package designpattern.builderpattren;


class Order {

    private String id;
    private double amount;
    private String status;

    private Order(String id, double amount, String status) {
        this.id = id;
        this.amount = amount;
        this.status = status;
    }

    interface IdStep {
        AmountStep id(String id);
    }

    interface AmountStep {
        StatusStep amount(double amount);
    }

    interface StatusStep {
        BuildStep status(String status);
    }

    interface BuildStep {
        Order build();
    }

    public static IdStep builder() {
        return new Builder();
    }

    static class Builder implements IdStep, AmountStep, StatusStep, BuildStep {
        private String id;
        private double amount;
        private String status;

        public AmountStep id(String id) {
            this.id = id;
            return this;
        }

        public StatusStep amount(double amount) {
            this.amount = amount;
            return this;
        }

        public BuildStep status(String status) {
            this.status = status;
            return this;
        }

        public Order build() {
            return new Order(id, amount, status);
        }
    }
}

/**
 * Forces correct order
 * Mandatory fields must be set
 * Prevents incomplete object creation
 */
public class StepBuilderPattern {
    public static void main(String[] args) {
        Order order = Order.builder()
                .id("ORD1")
                .amount(1000)
                .status("CREATED")
                .build();

    }
}
