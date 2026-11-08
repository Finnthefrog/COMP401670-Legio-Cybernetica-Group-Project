package com.meitheal.service;

import com.meitheal.model.*;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

public class TimeBankService {
    // R3: one-off welcome grant given to every new member
    private static final int WELCOME_GRANT = 120;
    // R2: lowest balance a requester may reach
    private static final int OVERDRAFT_FLOOR = -180;
    // R10: community levy rate, paid by the requester on top of the session cost
    private static final double LEVY_RATE = 0.05;

    private final Map<String, Member> members = new LinkedHashMap<>();
    private final Map<String, Offering> offerings = new LinkedHashMap<>();
    private final Map<String, Request> requests = new LinkedHashMap<>();
    private final Map<String, Engagement> engagements = new LinkedHashMap<>();
    private final Ledger ledger = new Ledger();
    private final CommunityFund communityFund = new CommunityFund(0);
    private final Set<String> validSkillCategories = new HashSet<>();
    private int idCounter = 1;

    public TimeBankService() {
        loadSkillCategories();
    }

    private void loadSkillCategories() {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("skills.properties")) {
            Properties prop = new Properties();
            if (input != null) {
                prop.load(input);
                String skillsStr = prop.getProperty("skills", "");
                for (String s : skillsStr.split(",")) {
                    validSkillCategories.add(s.trim());
                }
            } else {
                // Fallback default skills
                validSkillCategories.addAll(Arrays.asList("Legal Advice", "Furniture Moving", "Gardening", "Computer Repair", "Childcare", "Carpentry", "Tutor", "Cooking"));
            }
        } catch (Exception e) {
            validSkillCategories.addAll(Arrays.asList("Legal Advice", "Furniture Moving", "Gardening", "Computer Repair", "Childcare", "Carpentry", "Tutor", "Cooking"));
        }
    }

    public Set<String> getValidSkillCategories() {
        return Collections.unmodifiableSet(validSkillCategories);
    }

    public Member registerMember(String name, String email) {
        String memberId = "M" + (idCounter++);
        // R3: One-off welcome grant of 120 credits when member joins
        Member member = new Member(memberId, name, email, WELCOME_GRANT);
        members.put(memberId, member);

        LedgerEntry entry = new LedgerEntry(
                "L" + (idCounter++),
                LocalDateTime.now(),
                "Welcome Grant for " + name,
                null,
                memberId,
                WELCOME_GRANT,
                0
        );
        ledger.addEntry(entry);
        return member;
    }

    public void suspendMember(String memberId, boolean suspend) {
        Member member = members.get(memberId);
        if (member == null) {
            throw new IllegalArgumentException("Member not found: " + memberId);
        }
        member.setStatus(suspend ? MemberStatus.SUSPENDED : MemberStatus.ACTIVE);
    }

    public Offering publishOffer(String memberId, String skillCategory) {
        Member member = getActiveMember(memberId);
        if (!validSkillCategories.contains(skillCategory)) {
            throw new IllegalArgumentException("Invalid skill category (R13): " + skillCategory);
        }
        String offId = "OFF" + (idCounter++);
        Offering offering = new Offering(offId, member, skillCategory);
        offerings.put(offId, offering);
        return offering;
    }

    public Request raiseRequest(String memberId, String skillCategory, LocalDate earliestDate, LocalDate latestDate) {
        Member member = getActiveMember(memberId);
        if (!validSkillCategories.contains(skillCategory)) {
            throw new IllegalArgumentException("Invalid skill category (R13): " + skillCategory);
        }
        String reqId = "REQ" + (idCounter++);
        Request request = new Request(reqId, member, skillCategory, earliestDate, latestDate);
        requests.put(reqId, request);
        return request;
    }

    public Engagement proposeEngagement(String offeringId, String requestId, LocalDateTime sessionDateTime, int durationMinutes) {
        Offering offering = offerings.get(offeringId);
        Request request = requests.get(requestId);
        if (offering == null || !offering.isActive()) {
            throw new IllegalArgumentException("Offering not found or inactive.");
        }
        if (request == null || !request.isActive()) {
            throw new IllegalArgumentException("Request not found or inactive.");
        }

        // R6: Suspended members cannot enter new engagements
        if (offering.getProvider().getStatus() == MemberStatus.SUSPENDED ||
                request.getRequester().getStatus() == MemberStatus.SUSPENDED) {
            throw new IllegalArgumentException("Suspended members cannot enter new engagements (R6).");
        }

        String engId = "ENG" + (idCounter++);
        Engagement engagement = new Engagement(engId, offering, request, sessionDateTime, durationMinutes);
        engagements.put(engId, engagement);
        return engagement;
    }

    public void confirmEngagementSession(String engagementId, String memberId, boolean isProvider) {
        Engagement engagement = engagements.get(engagementId);
        if (engagement == null || engagement.getStatus() == EngagementStatus.CANCELLED) {
            throw new IllegalArgumentException("Engagement not found or already cancelled.");
        }

        Member member = members.get(memberId);
        if (member == null) {
            throw new IllegalArgumentException("Member not found.");
        }

        if (isProvider) {
            if (!engagement.getOffering().getProvider().equals(member)) {
                throw new IllegalArgumentException("Member is not the provider for this engagement.");
            }
            engagement.setProviderConfirmed(true);
        } else {
            if (!engagement.getRequest().getRequester().equals(member)) {
                throw new IllegalArgumentException("Member is not the requester for this engagement.");
            }
            engagement.setRequesterConfirmed(true);
        }

        // R4: Transfer happens only once BOTH provider and requester have confirmed
        if (engagement.isProviderConfirmed() && engagement.isRequesterConfirmed() && engagement.getStatus() == EngagementStatus.PROPOSED) {
            executeEngagementTransfer(engagement);
            engagement.setStatus(EngagementStatus.CONFIRMED);
        }
    }

    private void executeEngagementTransfer(Engagement engagement) {
        Member provider = engagement.getOffering().getProvider();
        Member requester = engagement.getRequest().getRequester();

        // R1: 1 hour = 60 credits. Cost proportional to duration minutes.
        // E.g. 60 min = 60 credits. durationMinutes credits.
        int sessionCost = engagement.getDurationMinutes();

        // R10: 5% community levy taken from transfer, rounded down to whole credits, paid by requester on top
        int levy = (int) Math.floor(sessionCost * LEVY_RATE);
        int totalRequesterDeduction = sessionCost + levy;

        // Check R2 & R10: Requester balance cannot drop below -180 floor
        if (requester.getBalance() - totalRequesterDeduction < OVERDRAFT_FLOOR) {
            throw new IllegalStateException("Transfer rejected: Requester would breach the overdraft floor of -180 credits (R2, R10).");
        }

        // Perform updates
        requester.updateBalance(-totalRequesterDeduction);
        provider.updateBalance(sessionCost);
        communityFund.addCredits(levy);

        // Record to Ledger (R7)
        LedgerEntry entry = new LedgerEntry(
                "L" + (idCounter++),
                LocalDateTime.now(),
                "Engagement transfer: " + engagement.getOffering().getSkillCategory() + " (" + engagement.getDurationMinutes() + " mins)",
                requester.getId(),
                provider.getId(),
                sessionCost,
                levy
        );
        ledger.addEntry(entry);
    }

    public void cancelEngagement(String engagementId, String cancellingMemberId) {
        Engagement engagement = engagements.get(engagementId);
        if (engagement == null || engagement.getStatus() == EngagementStatus.CANCELLED) {
            throw new IllegalArgumentException("Engagement not found or already cancelled.");
        }
        Member member = members.get(cancellingMemberId);
        if (member == null) {
            throw new IllegalArgumentException("Member not found.");
        }

        engagement.setStatus(EngagementStatus.CANCELLED);

        // R14: Cancelling < 24 hours before agreed start transfers no credits, but is recorded against cancelling member
        LocalDateTime now = LocalDateTime.now();
        if (now.plusHours(24).isAfter(engagement.getSessionDateTime())) {
            member.incrementLateCancellationCount();
            LedgerEntry entry = new LedgerEntry(
                    "L" + (idCounter++),
                    LocalDateTime.now(),
                    "Late cancellation recorded for member " + member.getId(),
                    null,
                    member.getId(),
                    0,
                    0
            );
            ledger.addEntry(entry);
        }
    }

    public void applyCommunityFundGrant(String memberId, int grantAmount, YearMonth month) {
        Member member = members.get(memberId);
        if (member == null) {
            throw new IllegalArgumentException("Member not found.");
        }

        if (!communityFund.canReceiveGrant(member, month)) {
            throw new IllegalStateException("Member is not eligible for a community fund grant this month or balance is > 0 (R11).");
        }

        if (grantAmount <= 0 || grantAmount > communityFund.getBalance()) {
            throw new IllegalArgumentException("Invalid grant amount or community fund insufficient balance (R11).");
        }

        communityFund.deductCredits(grantAmount);
        member.updateBalance(grantAmount);
        communityFund.recordGrant(member, month);

        LedgerEntry entry = new LedgerEntry(
                "L" + (idCounter++),
                LocalDateTime.now(),
                "Community Fund Grant",
                null,
                memberId,
                grantAmount,
                0
        );
        ledger.addEntry(entry);
    }

    private Member getActiveMember(String memberId) {
        Member member = members.get(memberId);
        if (member == null) {
            throw new IllegalArgumentException("Member not found: " + memberId);
        }
        if (member.getStatus() == MemberStatus.SUSPENDED) {
            throw new IllegalStateException("Member is suspended and cannot perform this operation (R6).");
        }
        return member;
    }

    // R12: Read-only accessors
    public Map<String, Member> getMembers() {
        Map<String, Member> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Member> e : members.entrySet()) {
            copy.put(e.getKey(), e.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, Offering> getOfferings() {
        return Collections.unmodifiableMap(offerings);
    }

    public Map<String, Request> getRequests() {
        return Collections.unmodifiableMap(requests);
    }

    public Map<String, Engagement> getEngagements() {
        return Collections.unmodifiableMap(engagements);
    }

    public Ledger getLedger() {
        return ledger;
    }

    public CommunityFund getCommunityFund() {
        return communityFund;
    }

    public int getTotalCreditsInCirculation() {
        int total = 0;
        for (Member m : members.values()) {
            total += m.getBalance();
        }
        return total + communityFund.getBalance();
    }
}