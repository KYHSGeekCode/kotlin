// FILE: Named.java

public interface Named {
    public String getName();
}

// FILE: test.kt

fun foo(n: Named) {
    n.name = "";
}
