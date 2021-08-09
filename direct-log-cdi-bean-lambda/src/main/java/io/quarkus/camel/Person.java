package io.quarkus.camel;

public class Person {

    private String name;

    public String getName() {
        return name;
    }

    public Person setName(String name) {
        this.name = name;
        return this;
    }

    @Override
    public String toString() {
        return String.format("Person[name= %s]",this.name);
    }
}
