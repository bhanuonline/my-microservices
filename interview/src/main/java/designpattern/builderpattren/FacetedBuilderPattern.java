package designpattern.builderpattren;


class Employee {
    String name;
    String city;
    String role;
}


class EmployeeBuilder {
    protected Employee emp = new Employee();

    public EmployeeAddressBuilder address() {
        return new EmployeeAddressBuilder(emp);
    }

    public EmployeeJobBuilder job() {
        return new EmployeeJobBuilder(emp);
    }

    public Employee build() {
        return emp;
    }
}


class EmployeeAddressBuilder extends EmployeeBuilder {
    public EmployeeAddressBuilder(Employee emp) {
        this.emp = emp;
    }

    public EmployeeAddressBuilder city(String city) {
        emp.city = city;
        return this;
    }
}


class EmployeeJobBuilder extends EmployeeBuilder {
    public EmployeeJobBuilder(Employee emp) {
        this.emp = emp;
    }

    public EmployeeJobBuilder role(String role) {
        emp.role = role;
        return this;
    }
}

/**
 * Object has multiple independent parts
 *
 * Each part has its own builder
 */
public class FacetedBuilderPattern {
    public static void main(String[] args) {
        Employee emp = new EmployeeBuilder()
                .address().city("Bangalore")
                .job().role("Developer")
                .build();

    }
}
