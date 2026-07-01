package designpattern.builderDesignPattern;

class User {

    // fields
    private final String name;
    private final int age;
    private final String city;
    private final boolean active;

    // private constructor (only builder can create)
    private User(Builder builder) {
        this.name = builder.name;
        this.age = builder.age;
        this.city = builder.city;
        this.active = builder.active;
    }

    // static builder accessor
    public static Builder builder() {
        return new Builder();
    }

    // INNER STATIC BUILDER CLASS
    public static class Builder {

        private String name;
        private int age;
        private String city;
        private boolean active;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public Builder city(String city) {
            this.city = city;
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public User build() {
            return new User(this);
        }
    }

    @Override
    public String toString() {
        return name + " " + age + " " + city + " " + active;
    }
}
public class BuilderDesignPattern {
    public static void main(String[] args) {
        User user=User.builder().name("Hi").build();
        System.out.println(user);
    }
}
