/**
 * The wallet backends this module ships, each one an implementation of {@link
 * com.uxplima.uxmlib.condition.Wallet} and nothing more.
 *
 * <p>A backend answers two questions: what a player's balance is, and whether a whole amount can be taken.
 * It fixes no price, no cost table, no currency name and no way of writing a number for a player to read.
 * Those are the game a plugin plays, and they stay in the plugin.
 *
 * <p>Three of them talk to another plugin, and the first of those covers most servers:
 *
 * <ul>
 *   <li>{@link com.uxplima.uxmlib.condition.wallet.BridgedWallet}: one economy plugin, described by an
 *       {@link com.uxplima.uxmlib.condition.wallet.EconomyBinding} rather than compiled against. {@link
 *       com.uxplima.uxmlib.condition.wallet.Economies} holds the descriptions of Vault, VaultUnlocked,
 *       PlayerPoints and EcoBits, and an operator's own economy is one more description.
 *   <li>{@link com.uxplima.uxmlib.condition.wallet.TreasuryWallet}: Treasury, which answers a subscriber
 *       instead of returning and so fits in no description.
 *   <li>{@link com.uxplima.uxmlib.condition.wallet.PlaceholderWallet}: a balance a placeholder answers
 *       with, taken by a console line. It is the last resort for an economy that publishes no API.
 * </ul>
 *
 * <p>Every one of those three is soft. A typed reference to another plugin sits inside a method that runs
 * only once the plugin manager has been asked whether that plugin is here, the handle is resolved on first
 * use and kept, and any {@link java.lang.Throwable} out of a missing or renamed API is read as "absent". A
 * server with none of these plugins installed behaves as though they were not here.
 *
 * <p>The fourth is a different kind of thing, and the difference is written into the class rather than
 * smoothed over:
 *
 * <ul>
 *   <li>{@link com.uxplima.uxmlib.condition.wallet.ExperienceWallet}: the player's own experience, counted
 *       in points or in levels. There is no other plugin behind it, so there is no present-guard, no handle
 *       to resolve and no absent path. The balance is not in an economy but in the player, so it is read
 *       and written on the thread that owns them and never off it, and a player who is not online has none.
 *       {@link com.uxplima.uxmlib.condition.wallet.ExperiencePoints} holds the vanilla curve that keeps its
 *       two units apart: a level is worth seven points near the start and over a hundred past level thirty
 *       one, so no rate turns one into the other.
 * </ul>
 *
 * <p>None of them takes a part of a cost. Where the economy itself refuses an overdraft the refusal is
 * read straight back; where it does not, the balance is read first and the take is refused before
 * anything moves.
 */
@NullMarked
package com.uxplima.uxmlib.condition.wallet;

import org.jspecify.annotations.NullMarked;
