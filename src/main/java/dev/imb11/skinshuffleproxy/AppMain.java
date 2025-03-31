package dev.imb11.skinshuffleproxy;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AppMain {
    public static void main(String[] args) {
        System.out.println("Launching SkinShuffle Proxy Server");

        Properties props = loadProgramProperties();
        if (props == null) return;
    }

    private static Properties loadProgramProperties() {
        Properties properties = new Properties();
        try (InputStream input = AppMain.class.getClassLoader().getResourceAsStream("local.properties")) {
            if (input == null) {
                System.out.println("Sorry, unable to find " + "local.properties");
                return null;
            }
            properties.load(input);
            return properties;
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }
}
