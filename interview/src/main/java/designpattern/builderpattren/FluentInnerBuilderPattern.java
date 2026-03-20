package designpattern.builderpattren;


class User {
    private String name;
    private String email;
    private boolean active;

    private User(Builder builder) {
        this.name = builder.name;
        this.email = builder.email;
        this.active = builder.active;
    }

    public static class Builder {
        private String name;
        private String email;
        private boolean active;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder email(String email) {
            this.email = email;
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
}

/**
 * Builder inside the class
 * Method chaining
 * Very readable
 * Use when object has many optional fields
 */
public class FluentInnerBuilderPattern {

    User user = new User.Builder()
            .name("Bhanu")
            .email("bhanu@email.com")
            .active(true)
            .build();

}
