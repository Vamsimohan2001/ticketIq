package com.ticketiq.app.ticket;

import com.ticketiq.core.model.Category;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Suggested owning team for each AI/manual ticket category. */
public final class TicketTeams {

    private static final Map<Category, String> TEAMS;

    static {
        EnumMap<Category, String> teams = new EnumMap<>(Category.class);
        teams.put(Category.ACCESS, "Identity and Access");
        teams.put(Category.BILLING, "Billing and Payments");
        teams.put(Category.BUG, "Application Engineering");
        teams.put(Category.HOW_TO, "Product Support");
        teams.put(Category.INTEGRATION, "Integrations");
        teams.put(Category.FEATURE_REQUEST, "Product");
        teams.put(Category.OTHER, "General Support");
        TEAMS = Collections.unmodifiableMap(teams);
    }

    private TicketTeams() {
    }

    public static String forCategory(Category category) {
        return TEAMS.getOrDefault(category, TEAMS.get(Category.OTHER));
    }

    public static Map<String, String> asMap() {
        Map<String, String> names = new LinkedHashMap<>();
        for (Category category : Category.values()) {
            names.put(category.name(), forCategory(category));
        }
        return Collections.unmodifiableMap(names);
    }
}
