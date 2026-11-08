package com.meitheal.Main;

import com.meitheal.cli.ConsoleApp;
import com.meitheal.service.TimeBankService;

public class Main {
    public static void main(String[] args) {
        TimeBankService service = new TimeBankService();
        ConsoleApp app = new ConsoleApp(service);
        app.start();
    }
}
