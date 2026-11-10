package com.meitheal.cli;

import com.meitheal.model.*;
import com.meitheal.service.TimeBankService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class ConsoleApp {
    private final TimeBankService service;
    private final Scanner scanner;
    private User actingUser;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public ConsoleApp(TimeBankService service) {
        this.service = service;
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        System.out.println("==================================================");
        System.out.println("   Welcome to Meitheal Community Time-Bank CLI    ");
        System.out.println("==================================================");

        actingUser = selectIdentity();
        System.out.println("Acting as: " + actingUser.getDisplayName() + " (" + actingUser.getLoginId() + ")");

        boolean running = true;
        while (running) {
            printMenu();
            String choice = scanner.nextLine().trim();
            try {
                switch (choice) {
                    case "1" -> registerMember();
                    case "2" -> suspendOrReinstateMember();
                    case "3" -> publishOffer();
                    case "4" -> raiseRequest();
                    case "5" -> proposeEngagement();
                    case "6" -> confirmSession();
                    case "7" -> cancelEngagement();
                    case "8" -> applyForGrant();
                    case "9" -> queryBalancesAndHistory();
                    case "10" -> printSystemReport();
                    case "0" -> {
                        running = false;
                        System.out.println("Exiting Meitheal Time-Bank. Goodbye!");
                    }
                    default -> System.out.println("Invalid option. Please choose between 0 and 10.");
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
            System.out.println();
        }
    }

    private void printMenu() {
        System.out.println("\n--- MEITHEAL MAIN MENU ---");
        System.out.println("1. Register a member");
        System.out.println("2. Suspend / Reinstate a member (Admin)");
        System.out.println("3. Publish an offer");
        System.out.println("4. Raise a request");
        System.out.println("5. Propose an engagement");
        System.out.println("6. Confirm a session");
        System.out.println("7. Cancel an engagement");
        System.out.println("8. Apply for a community fund grant");
        System.out.println("9. Query balances and history");
        System.out.println("10. System Report (Credits & Fund)");
        System.out.println("0. Exit");
        System.out.print("Enter choice: ");
    }

    private User selectIdentity() {
        List<Member> members = new ArrayList<>(service.getMembers().values());
        System.out.println("\n--- SELECT YOUR IDENTITY ---");
        for (int i = 0; i < members.size(); i++) {
            Member m = members.get(i);
            System.out.println((i + 1) + ". Member: " + m.getName() + " (" + m.getId() + ")");
        }
        int adminChoice = members.size() + 1;
        System.out.println(adminChoice + ". Administrator");
        while (true) {
            System.out.print("Enter choice: ");
            String input = scanner.nextLine().trim();
            int choice;
            try {
                choice = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a number.");
                continue;
            }
            if (choice >= 1 && choice <= members.size()) {
                Member m = members.get(choice - 1);
                return new CommunityUser(m.getId(), m.getName(), m);
            }
            if (choice == adminChoice) {
                return new AdminUser("ADMIN", "System Administrator");
            }
            System.out.println("Invalid choice. Please choose between 1 and " + adminChoice + ".");
        }
    }

    private void registerMember() {
        System.out.print("Enter name: ");
        String name = scanner.nextLine();
        System.out.print("Enter email: ");
        String email = scanner.nextLine();
        Member m = service.registerMember(name, email);
        System.out.println("Member registered successfully! Assigned ID: " + m.getId() + " with 120 welcome credits.");
    }

    private void suspendOrReinstateMember() {
        if (!actingUser.canSuspendMembers()) {
            System.out.println("Refused: only a System Admin can suspend or reinstate members.");
            return;
        }
        System.out.print("Enter Member ID: ");
        String id = scanner.nextLine();
        System.out.print("Suspend (true/false): ");
        boolean suspend = Boolean.parseBoolean(scanner.nextLine());
        service.suspendMember(actingUser, id, suspend);
        System.out.println("Member " + id + " suspension status updated to: " + suspend);
    }

    private void publishOffer() {
        System.out.print("Enter Member ID: ");
        String memberId = scanner.nextLine();
        System.out.println("Valid Categories: " + service.getValidSkillCategories());
        System.out.print("Enter skill category: ");
        String category = scanner.nextLine();
        Offering off = service.publishOffer(memberId, category);
        System.out.println("Offer published successfully! ID: " + off.getId());
    }

    private void raiseRequest() {
        System.out.print("Enter Member ID: ");
        String memberId = scanner.nextLine();
        System.out.println("Valid Categories: " + service.getValidSkillCategories());
        System.out.print("Enter skill category: ");
        String category = scanner.nextLine();
        System.out.print("Enter earliest date (yyyy-MM-dd): ");
        LocalDate earliest = LocalDate.parse(scanner.nextLine(), dateFormatter);
        System.out.print("Enter latest date (yyyy-MM-dd): ");
        LocalDate latest = LocalDate.parse(scanner.nextLine(), dateFormatter);
        Request req = service.raiseRequest(memberId, category, earliest, latest);
        System.out.println("Request raised successfully! ID: " + req.getId());
    }

    private void proposeEngagement() {
        System.out.print("Enter Offering ID: ");
        String offId = scanner.nextLine();
        System.out.print("Enter Request ID: ");
        String reqId = scanner.nextLine();
        System.out.print("Enter session date & time (yyyy-MM-dd HH:mm): ");
        LocalDateTime dt = LocalDateTime.parse(scanner.nextLine(), dateTimeFormatter);
        System.out.print("Enter duration in minutes (max 480): ");
        int duration = Integer.parseInt(scanner.nextLine());
        Engagement eng = service.proposeEngagement(offId, reqId, dt, duration);
        System.out.println("Engagement proposed successfully! ID: " + eng.getId());
    }

    private void confirmSession() {
        System.out.print("Enter Engagement ID: ");
        String engId = scanner.nextLine();
        System.out.print("Enter Member ID confirming: ");
        String memberId = scanner.nextLine();
        System.out.print("Is this member the provider? (true/false): ");
        boolean isProvider = Boolean.parseBoolean(scanner.nextLine());
        service.confirmEngagementSession(engId, memberId, isProvider);
        System.out.println("Confirmation recorded successfully.");
    }

    private void cancelEngagement() {
        System.out.print("Enter Engagement ID: ");
        String engId = scanner.nextLine();
        System.out.print("Enter Cancelling Member ID: ");
        String memberId = scanner.nextLine();
        service.cancelEngagement(engId, memberId);
        System.out.println("Engagement cancelled.");
    }

    private void applyForGrant() {
        System.out.print("Enter Member ID: ");
        String memberId = scanner.nextLine();
        System.out.print("Enter grant amount: ");
        int amount = Integer.parseInt(scanner.nextLine());
        YearMonth currentMonth = YearMonth.now();
        service.applyCommunityFundGrant(memberId, amount, currentMonth);
        System.out.println("Community fund grant applied successfully!");
    }

    private void queryBalancesAndHistory() {
        System.out.println("\n--- MEMBERS & BALANCES ---");
        for (Member m : service.getMembers().values()) {
            if (actingUser.canInspect(m)) {
                System.out.println(m);
            }
        }
        System.out.println("\n--- LEDGER ENTRIES ---");
        for (LedgerEntry entry : service.getLedger().getEntries()) {
            if (canInspectLedgerEntry(entry)) {
                System.out.println(entry);
            }
        }
    }

    private boolean canInspectLedgerEntry(LedgerEntry entry) {
        Member sender = entry.getSenderId() == null ? null : service.getMembers().get(entry.getSenderId());
        Member recipient = entry.getRecipientId() == null ? null : service.getMembers().get(entry.getRecipientId());
        return (sender == null || actingUser.canInspect(sender))
                && (recipient == null || actingUser.canInspect(recipient));
    }

    private void printSystemReport() {
        System.out.println("\n--- SYSTEM REPORT ---");
        System.out.println("Total Credits in Circulation (Members + Fund): " + service.getTotalCreditsInCirculation());
        System.out.println("Community Fund Balance: " + service.getCommunityFund().getBalance() + " credits");
        System.out.println("Total Members Registered: " + service.getMembers().size());
        System.out.println("Total Active Offerings: " + service.getOfferings().size());
        System.out.println("Total Active Requests: " + service.getRequests().size());
    }
}
