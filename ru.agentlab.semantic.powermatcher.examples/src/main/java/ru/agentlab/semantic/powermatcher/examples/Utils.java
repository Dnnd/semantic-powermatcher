package ru.agentlab.semantic.powermatcher.examples;

import java.io.InputStream;

public class Utils {

    static public InputStream openResourceStream(String resource) {
        return Utils.class.getClassLoader().getResourceAsStream(resource);
    }
}
