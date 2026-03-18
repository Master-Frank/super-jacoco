package com.frank.superjacoco.util;

import java.net.SocketException;

public class LocalIpUtils {

    public static String getTomcatBaseUrl() {
        String os = System.getProperty("os.name");
        if (os != null && os.toLowerCase().contains("windows")) {
            return "http://127.0.0.1:8899/";
        }
        String localIp = "";
        try {
            localIp = GetIPAddress.getLinuxLocalIp();
        } catch (SocketException ignore) {
        }
        if (localIp == null || localIp.trim().isEmpty()) {
            localIp = "127.0.0.1";
        }
        return "http://" + localIp + ":8899/";

    }

}
