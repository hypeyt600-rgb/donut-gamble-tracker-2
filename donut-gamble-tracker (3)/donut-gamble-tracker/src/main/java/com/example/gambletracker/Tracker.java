package com.example.gambletracker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Tracker {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("gambletracker.json");

    // ---- saved state ----
    public double profit, biggestWin, biggestLoss, totalWon, totalLost;
    public int wins, losses, streak; // streak: +n win streak, -n loss streak
    public boolean hudVisible = true;
    public int hudMode = 0; // 0 full, 1 compact, 2 minimal
    public int hudX = 6, hudY = 6;
    // !! Guesses. Edit these in config/gambletracker.json to match the server's real chat lines.
    // Group 1 must capture the amount (supports $, commas, and k/m/b/t suffixes).
    public String winRegex  = "(?i)you (?:won|win|received|collected)\\s*\\$?([\\d.,]+\\s?[kmbt]?)";
    public String lossRegex = "(?i)you (?:lost|lose)\\s*\\$?([\\d.,]+\\s?[kmbt]?)";

    // ---- session only ----
    public transient long sessionStart = System.currentTimeMillis();
    public transient double sessionProfit;
    public transient int sessionWins, sessionLosses;
    private transient Pattern winP, lossP;

    public static Tracker load() {
        try {
            if (Files.exists(FILE)) {
                Tracker t = GSON.fromJson(Files.readString(FILE), Tracker.class);
                t.compile();
                return t;
            }
        } catch (Exception e) { e.printStackTrace(); }
        Tracker t = new Tracker();
        t.compile();
        t.save();
        return t;
    }

    public void save() {
        try { Files.writeString(FILE, GSON.toJson(this)); } catch (Exception e) { e.printStackTrace(); }
    }

    public void compile() {
        winP = Pattern.compile(winRegex);
        lossP = Pattern.compile(lossRegex);
    }

    /** Returns true if the line was recognised as a win/loss. */
    public boolean onChat(String line) {
        Matcher m = lossP.matcher(line);
        if (m.find()) { Double v = parse(m.group(1)); if (v != null) { record(-v); return true; } }
        m = winP.matcher(line);
        if (m.find()) { Double v = parse(m.group(1)); if (v != null) { record(v); return true; } }
        return false;
    }

    public void record(double delta) {
        profit += delta; sessionProfit += delta;
        if (delta >= 0) {
            wins++; sessionWins++; totalWon += delta; biggestWin = Math.max(biggestWin, delta);
            streak = streak > 0 ? streak + 1 : 1;
        } else {
            losses++; sessionLosses++; totalLost += -delta; biggestLoss = Math.max(biggestLoss, -delta);
            streak = streak < 0 ? streak - 1 : -1;
        }
        save();
    }

    public void resetAll() {
        profit = biggestWin = biggestLoss = totalWon = totalLost = 0;
        wins = losses = streak = 0;
        resetSession();
        save();
    }

    public void resetSession() {
        sessionStart = System.currentTimeMillis();
        sessionProfit = 0; sessionWins = sessionLosses = 0;
    }

    public double winRate() {
        int n = wins + losses;
        return n == 0 ? 0 : 100.0 * wins / n;
    }

    public double perHour() {
        double hrs = (System.currentTimeMillis() - sessionStart) / 3_600_000.0;
        return hrs < 0.01 ? 0 : sessionProfit / hrs;
    }

    /** "1.5k", "2m", "$3,200" -> number */
    public static Double parse(String s) {
        try {
            s = s.trim().toLowerCase().replace("$", "").replace(",", "").replace(" ", "");
            double mult = 1;
            if (!s.isEmpty()) {
                char c = s.charAt(s.length() - 1);
                switch (c) {
                    case 'k' -> mult = 1e3;
                    case 'm' -> mult = 1e6;
                    case 'b' -> mult = 1e9;
                    case 't' -> mult = 1e12;
                }
                if (mult != 1) s = s.substring(0, s.length() - 1);
            }
            return Double.parseDouble(s) * mult;
        } catch (Exception e) { return null; }
    }

    public static String fmt(double v) {
        String sign = v < 0 ? "-" : "";
        double a = Math.abs(v);
        String out;
        if (a >= 1e12) out = String.format("%.2fT", a / 1e12);
        else if (a >= 1e9) out = String.format("%.2fB", a / 1e9);
        else if (a >= 1e6) out = String.format("%.2fM", a / 1e6);
        else if (a >= 1e3) out = String.format("%.1fK", a / 1e3);
        else out = String.format("%.0f", a);
        return sign + "$" + out;
    }
}
