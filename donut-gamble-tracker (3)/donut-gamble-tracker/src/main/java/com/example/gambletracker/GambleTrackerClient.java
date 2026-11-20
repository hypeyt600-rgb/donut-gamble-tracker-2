package com.example.gambletracker;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class GambleTrackerClient implements ClientModInitializer {
    static Tracker tracker;
    static boolean debug = false;
    static KeyBinding kToggle, kMode, kSession, kReset;
    static final KeyBinding.Category CAT = KeyBinding.Category.create(Identifier.of("gambletracker", "main"));

    static KeyBinding key(String id, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(id, InputUtil.Type.KEYSYM, code, CAT));
    }

    @Override
    public void onInitializeClient() {
        tracker = Tracker.load();

        kToggle  = key("key.gambletracker.toggle",  GLFW.GLFW_KEY_H);
        kMode    = key("key.gambletracker.mode",    GLFW.GLFW_KEY_J);
        kSession = key("key.gambletracker.session", GLFW.GLFW_KEY_K);
        kReset   = key("key.gambletracker.reset",   GLFW.GLFW_KEY_UNKNOWN); // unbound by default, set in Controls

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (kToggle.wasPressed()) { tracker.hudVisible = !tracker.hudVisible; tracker.save(); }
            while (kMode.wasPressed()) {
                tracker.hudMode = (tracker.hudMode + 1) % 3; tracker.hudVisible = true; tracker.save();
            }
            while (kSession.wasPressed()) { tracker.resetSession(); say("Session reset"); }
            while (kReset.wasPressed()) { tracker.resetAll(); say("All stats reset"); }
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) return;
            String line = message.getString();
            if (debug) say("§7[raw] " + line);
            tracker.onChat(line);
        });

        HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, Identifier.of("gambletracker", "hud"), GambleTrackerClient::renderHud);

        ClientCommandRegistrationCallback.EVENT.register((d, access) -> d.register(
            ClientCommandManager.literal("gamble")
                .then(ClientCommandManager.literal("hud").executes(c -> {
                    tracker.hudVisible = !tracker.hudVisible; tracker.save();
                    say("HUD " + (tracker.hudVisible ? "on" : "off")); return 1; }))
                .then(ClientCommandManager.literal("debug").executes(c -> {
                    debug = !debug;
                    say("Debug " + (debug ? "on - every chat line is echoed so you can copy the exact format" : "off")); return 1; }))
                .then(ClientCommandManager.literal("win").then(ClientCommandManager.argument("amount", StringArgumentType.word()).executes(c -> {
                    Double v = Tracker.parse(StringArgumentType.getString(c, "amount"));
                    if (v == null) { say("§cBad amount"); return 0; }
                    tracker.record(v); say("Added win " + Tracker.fmt(v)); return 1; })))
                .then(ClientCommandManager.literal("loss").then(ClientCommandManager.argument("amount", StringArgumentType.word()).executes(c -> {
                    Double v = Tracker.parse(StringArgumentType.getString(c, "amount"));
                    if (v == null) { say("§cBad amount"); return 0; }
                    tracker.record(-v); say("Added loss " + Tracker.fmt(v)); return 1; })))
                .then(ClientCommandManager.literal("pos").then(ClientCommandManager.argument("x", StringArgumentType.word())
                    .then(ClientCommandManager.argument("y", StringArgumentType.word()).executes(c -> {
                        try {
                            tracker.hudX = Integer.parseInt(StringArgumentType.getString(c, "x"));
                            tracker.hudY = Integer.parseInt(StringArgumentType.getString(c, "y"));
                            tracker.save(); say("HUD moved"); return 1;
                        } catch (NumberFormatException e) { say("§cUse whole numbers"); return 0; } }))))
                .then(ClientCommandManager.literal("reload").executes(c -> {
                    tracker = Tracker.load(); say("Config reloaded"); return 1; }))
                .then(ClientCommandManager.literal("resetsession").executes(c -> {
                    tracker.resetSession(); say("Session reset"); return 1; }))
                .then(ClientCommandManager.literal("reset").executes(c -> {
                    tracker.resetAll(); say("All stats reset"); return 1; }))
                .executes(c -> {
                    say("/gamble hud | debug | win <amt> | loss <amt> | pos <x> <y> | reload | resetsession | reset");
                    return 1; })
        ));
    }

    static void say(String s) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("§6[Gamble] §f" + s), false);
    }

    static String color(double v) { return v > 0 ? "§a" : v < 0 ? "§c" : "§f"; }

    static void renderHud(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tick) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!tracker.hudVisible || mc.options.hudHidden || mc.player == null) return;

        List<String> lines = new ArrayList<>();
        lines.add("§6§lGamble Tracker §7[" + new String[]{"full","compact","minimal"}[tracker.hudMode] + "]");
        lines.add("Profit: " + color(tracker.profit) + Tracker.fmt(tracker.profit));
        if (tracker.hudMode <= 1) {
            lines.add("Session: " + color(tracker.sessionProfit) + Tracker.fmt(tracker.sessionProfit)
                    + " §7(" + color(tracker.perHour()) + Tracker.fmt(tracker.perHour()) + "§7/hr)");
            lines.add("W/L: §a" + tracker.wins + "§f/§c" + tracker.losses + " §7(" + String.format("%.1f", tracker.winRate()) + "%)");
        }
        if (tracker.hudMode == 0) {
            String st = tracker.streak == 0 ? "-" : (tracker.streak > 0 ? "§a" + tracker.streak + "W" : "§c" + (-tracker.streak) + "L");
            lines.add("Streak: " + st);
            lines.add("Best: §a" + Tracker.fmt(tracker.biggestWin) + " §fWorst: §c" + Tracker.fmt(tracker.biggestLoss));
            lines.add("Won: §a" + Tracker.fmt(tracker.totalWon) + " §fLost: §c" + Tracker.fmt(tracker.totalLost));
            lines.add("§8[" + kToggle.getBoundKeyLocalizedText().getString() + "] hide  ["
                    + kMode.getBoundKeyLocalizedText().getString() + "] mode  ["
                    + kSession.getBoundKeyLocalizedText().getString() + "] new session");
        }

        int y = tracker.hudY;
        for (String l : lines) {
            ctx.drawTextWithShadow(mc.textRenderer, l, tracker.hudX, y, 0xFFFFFFFF);
            y += 10;
        }
    }
}
