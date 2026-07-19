package io.matrix.minecraft;

import io.matrix.io.MinecraftBotSensor;

import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Snapshot of headless-bot state returned to API consumers (HTTP/WS/test).
 *
 * <p>This is a pure-data record — no live references to the agent or world
 * — so it can be serialised to JSON without circular references.
 */
public record HeadlessBotSnapshot(
        String botId,
        boolean alive,
        int stepsSurvived,
        int blocksMined,
        int itemsCrafted,
        int health,
        int hunger,
        String toolTier,
        int x,
        int y,
        String lastAction,
        long timestampMs) {

    public static HeadlessBotSnapshot from(BotCoordinator.Outcome outcome, String botId) {
        return new HeadlessBotSnapshot(
                botId,
                outcome.alive(),
                outcome.agent().stepsSurvived(),
                outcome.agent().blocksMined(),
                outcome.agent().itemsCrafted(),
                outcome.agent().health(),
                outcome.agent().hunger(),
                outcome.agent().toolTier().name(),
                outcome.agent().position().x(),
                outcome.agent().position().y(),
                outcome.label(),
                System.currentTimeMillis());
    }
}
