/*
 * Copyright (c) 2026, FabulousOtter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.constsexp.gotr.state;

import com.constsexp.gotr.model.Alignment;
import com.constsexp.gotr.model.Altar;
import com.constsexp.gotr.model.CellTier;
import com.constsexp.gotr.plan.Step;
import com.constsexp.gotr.plan.Target;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;

/**
 * Planner input captured once per game tick. Times are seconds; -1 means unknown.
 */
public final class Snapshot
{
	public static final Snapshot OUTSIDE = Snapshot.builder().build();

	private final Location location;
	@Nullable
	private final Altar altarRoom;
	@Nullable
	private final WorldPoint playerLocation;
	private final GamePhase phase;
	private final int secondsToStart;
	private final int secondsSinceStart;
	private final int secondsToNextGame;
	private final int elementalEnergy;
	private final int catalyticEnergy;
	private final int power;
	private final int maxPower;
	@Nullable
	private final Altar activeElemental;
	@Nullable
	private final Altar activeCatalytic;
	private final int altarSecondsRemaining;
	private final boolean portalOpen;
	private final int portalSecondsRemaining;
	@Nullable
	private final String portalDirection;
	private final int secondsSinceLastPortal;
	private final int secondsToNextPortal;
	private final boolean anyPortalThisGame;
	// Whether the player has been inside an altar this game: the opening run is over.
	private final boolean altarVisitedThisGame;
	private final int guardiansActive;
	private final int guardiansMax;
	private final int fragments;
	private final int essence;
	private final int elementalStones;
	private final int catalyticStones;
	private final int polyElementalStones;
	private final int polyCatalyticStones;
	@Nullable
	private final CellTier chargedCell;
	private final int unchargedCells;
	private final boolean chisel;
	private final boolean pickaxe;
	private final int freeSlots;
	private final Set<Altar> talismans;
	@Nullable
	private final Altar baseRune;
	private final int baseRuneCount;
	private final List<PouchState> pouches;
	private final boolean bindingNecklaceWorn;
	private final int necklaceCharges;
	private final boolean magicImbueActive;
	private final boolean lunarSpellbook;
	private final boolean runePouch;
	private final int runecraftLevel;
	private final int agilityLevel;
	private final int magicLevel;
	private final Set<Altar> questLockedAltars;
	private final List<BarrierState> barriers;
	private final List<WorldPoint> emptyTiles;
	private final List<WorldPoint> brokenTiles;
	private final Map<Altar, WorldPoint> portalGuardians;
	// Retain the previous target to avoid switching as distances change.
	@Nullable
	private final WorldPoint chosenTile;
	// Preserve in-progress actions when timing estimates fluctuate.
	@Nullable
	private final Step lastStep;
	private final boolean weakCellTablePresent;
	private final int savedElementalPoints;
	private final int savedCatalyticPoints;
	private final boolean pointsCredited;
	// Measured throughput (per game tick) and the crowd's projected rift close.
	private final double fragmentsPerTick;
	private final double partsPerTick;
	private final double craftPerTick;
	private final double hugePerTick;
	private final int secondsToClose;
	// Walking distances are in tiles; absent entries are unknown.
	private final Map<Target, Integer> travelTicks;
	private final Map<Altar, Integer> guardianTravelTicks;
	// Remains-to-workbench distance in tiles.
	private final Map<Target, Integer> returnTicks;
	// Distance in tiles from each errand to the open portal.
	private final Map<Target, Integer> portalTicks;

	private Snapshot(Builder b)
	{
		this.location = b.location;
		this.altarRoom = b.altarRoom;
		this.playerLocation = b.playerLocation;
		this.phase = b.phase;
		this.secondsToStart = b.secondsToStart;
		this.secondsSinceStart = b.secondsSinceStart;
		this.secondsToNextGame = b.secondsToNextGame;
		this.elementalEnergy = b.elementalEnergy;
		this.catalyticEnergy = b.catalyticEnergy;
		this.power = b.power;
		this.maxPower = b.maxPower;
		this.activeElemental = b.activeElemental;
		this.activeCatalytic = b.activeCatalytic;
		this.altarSecondsRemaining = b.altarSecondsRemaining;
		this.portalOpen = b.portalOpen;
		this.portalSecondsRemaining = b.portalSecondsRemaining;
		this.portalDirection = b.portalDirection;
		this.secondsSinceLastPortal = b.secondsSinceLastPortal;
		this.secondsToNextPortal = b.secondsToNextPortal;
		this.anyPortalThisGame = b.anyPortalThisGame;
		this.altarVisitedThisGame = b.altarVisitedThisGame;
		this.guardiansActive = b.guardiansActive;
		this.guardiansMax = b.guardiansMax;
		this.fragments = b.fragments;
		this.essence = b.essence;
		this.elementalStones = b.elementalStones;
		this.catalyticStones = b.catalyticStones;
		this.polyElementalStones = b.polyElementalStones;
		this.polyCatalyticStones = b.polyCatalyticStones;
		this.chargedCell = b.chargedCell;
		this.unchargedCells = b.unchargedCells;
		this.chisel = b.chisel;
		this.pickaxe = b.pickaxe;
		this.freeSlots = b.freeSlots;
		this.talismans = b.talismans;
		this.baseRune = b.baseRune;
		this.baseRuneCount = b.baseRuneCount;
		this.pouches = b.pouches;
		this.bindingNecklaceWorn = b.bindingNecklaceWorn;
		this.necklaceCharges = b.necklaceCharges;
		this.magicImbueActive = b.magicImbueActive;
		this.lunarSpellbook = b.lunarSpellbook;
		this.runePouch = b.runePouch;
		this.runecraftLevel = b.runecraftLevel;
		this.agilityLevel = b.agilityLevel;
		this.magicLevel = b.magicLevel;
		this.questLockedAltars = b.questLockedAltars;
		this.barriers = b.barriers;
		this.emptyTiles = b.emptyTiles;
		this.brokenTiles = b.brokenTiles;
		this.portalGuardians = b.portalGuardians;
		this.chosenTile = b.chosenTile;
		this.lastStep = b.lastStep;
		this.weakCellTablePresent = b.weakCellTablePresent;
		this.savedElementalPoints = b.savedElementalPoints;
		this.savedCatalyticPoints = b.savedCatalyticPoints;
		this.pointsCredited = b.pointsCredited;
		this.fragmentsPerTick = b.fragmentsPerTick;
		this.partsPerTick = b.partsPerTick;
		this.craftPerTick = b.craftPerTick;
		this.hugePerTick = b.hugePerTick;
		this.secondsToClose = b.secondsToClose;
		this.travelTicks = b.travelTicks;
		this.guardianTravelTicks = b.guardianTravelTicks;
		this.returnTicks = b.returnTicks;
		this.portalTicks = b.portalTicks;
	}

	public static Builder builder()
	{
		return new Builder();
	}

	public Builder toBuilder()
	{
		Builder b = new Builder();
		b.location = location;
		b.altarRoom = altarRoom;
		b.playerLocation = playerLocation;
		b.phase = phase;
		b.secondsToStart = secondsToStart;
		b.secondsSinceStart = secondsSinceStart;
		b.secondsToNextGame = secondsToNextGame;
		b.elementalEnergy = elementalEnergy;
		b.catalyticEnergy = catalyticEnergy;
		b.power = power;
		b.maxPower = maxPower;
		b.activeElemental = activeElemental;
		b.activeCatalytic = activeCatalytic;
		b.altarSecondsRemaining = altarSecondsRemaining;
		b.portalOpen = portalOpen;
		b.portalSecondsRemaining = portalSecondsRemaining;
		b.portalDirection = portalDirection;
		b.secondsSinceLastPortal = secondsSinceLastPortal;
		b.secondsToNextPortal = secondsToNextPortal;
		b.anyPortalThisGame = anyPortalThisGame;
		b.altarVisitedThisGame = altarVisitedThisGame;
		b.guardiansActive = guardiansActive;
		b.guardiansMax = guardiansMax;
		b.fragments = fragments;
		b.essence = essence;
		b.elementalStones = elementalStones;
		b.catalyticStones = catalyticStones;
		b.polyElementalStones = polyElementalStones;
		b.polyCatalyticStones = polyCatalyticStones;
		b.chargedCell = chargedCell;
		b.unchargedCells = unchargedCells;
		b.chisel = chisel;
		b.pickaxe = pickaxe;
		b.freeSlots = freeSlots;
		b.talismans = talismans;
		b.baseRune = baseRune;
		b.baseRuneCount = baseRuneCount;
		b.pouches = pouches;
		b.bindingNecklaceWorn = bindingNecklaceWorn;
		b.necklaceCharges = necklaceCharges;
		b.magicImbueActive = magicImbueActive;
		b.lunarSpellbook = lunarSpellbook;
		b.runePouch = runePouch;
		b.runecraftLevel = runecraftLevel;
		b.agilityLevel = agilityLevel;
		b.magicLevel = magicLevel;
		b.questLockedAltars = questLockedAltars;
		b.barriers = barriers;
		b.emptyTiles = emptyTiles;
		b.brokenTiles = brokenTiles;
		b.portalGuardians = portalGuardians;
		b.chosenTile = chosenTile;
		b.lastStep = lastStep;
		b.weakCellTablePresent = weakCellTablePresent;
		b.savedElementalPoints = savedElementalPoints;
		b.savedCatalyticPoints = savedCatalyticPoints;
		b.pointsCredited = pointsCredited;
		b.fragmentsPerTick = fragmentsPerTick;
		b.partsPerTick = partsPerTick;
		b.craftPerTick = craftPerTick;
		b.hugePerTick = hugePerTick;
		b.secondsToClose = secondsToClose;
		b.travelTicks = travelTicks;
		b.guardianTravelTicks = guardianTravelTicks;
		b.returnTicks = returnTicks;
		b.portalTicks = portalTicks;
		return b;
	}

	public Location getLocation()
	{
		return location;
	}

	@Nullable
	public Altar getAltarRoom()
	{
		return altarRoom;
	}

	@Nullable
	public WorldPoint getPlayerLocation()
	{
		return playerLocation;
	}

	public GamePhase getPhase()
	{
		return phase;
	}

	public int getSecondsToStart()
	{
		return secondsToStart;
	}

	public int getSecondsSinceStart()
	{
		return secondsSinceStart;
	}

	public int getSecondsToNextGame()
	{
		return secondsToNextGame;
	}

	public int getElementalEnergy()
	{
		return elementalEnergy;
	}

	public int getCatalyticEnergy()
	{
		return catalyticEnergy;
	}

	public int getPower()
	{
		return power;
	}

	public int getMaxPower()
	{
		return maxPower;
	}

	@Nullable
	public Altar getActiveElemental()
	{
		return activeElemental;
	}

	@Nullable
	public Altar getActiveCatalytic()
	{
		return activeCatalytic;
	}

	public int getAltarSecondsRemaining()
	{
		return altarSecondsRemaining;
	}

	public boolean isPortalOpen()
	{
		return portalOpen;
	}

	public int getPortalSecondsRemaining()
	{
		return portalSecondsRemaining;
	}

	@Nullable
	public String getPortalDirection()
	{
		return portalDirection;
	}

	public int getSecondsSinceLastPortal()
	{
		return secondsSinceLastPortal;
	}

	public int getSecondsToNextPortal()
	{
		return secondsToNextPortal;
	}

	public boolean isAnyPortalThisGame()
	{
		return anyPortalThisGame;
	}

	public boolean isAltarVisitedThisGame()
	{
		return altarVisitedThisGame;
	}

	public int getGuardiansActive()
	{
		return guardiansActive;
	}

	public int getGuardiansMax()
	{
		return guardiansMax;
	}

	public int getFragments()
	{
		return fragments;
	}

	public int getEssence()
	{
		return essence;
	}

	public int getElementalStones()
	{
		return elementalStones;
	}

	public int getCatalyticStones()
	{
		return catalyticStones;
	}

	public int getPolyElementalStones()
	{
		return polyElementalStones;
	}

	public int getPolyCatalyticStones()
	{
		return polyCatalyticStones;
	}

	@Nullable
	public CellTier getChargedCell()
	{
		return chargedCell;
	}

	public int getUnchargedCells()
	{
		return unchargedCells;
	}

	public boolean isChisel()
	{
		return chisel;
	}

	public boolean isPickaxe()
	{
		return pickaxe;
	}

	public int getFreeSlots()
	{
		return freeSlots;
	}

	public Set<Altar> getTalismans()
	{
		return talismans;
	}

	@Nullable
	public Altar getBaseRune()
	{
		return baseRune;
	}

	public int getBaseRuneCount()
	{
		return baseRuneCount;
	}

	public List<PouchState> getPouches()
	{
		return pouches;
	}

	public boolean isBindingNecklaceWorn()
	{
		return bindingNecklaceWorn;
	}

	public int getNecklaceCharges()
	{
		return necklaceCharges;
	}

	public boolean isMagicImbueActive()
	{
		return magicImbueActive;
	}

	public boolean isLunarSpellbook()
	{
		return lunarSpellbook;
	}

	public boolean isRunePouch()
	{
		return runePouch;
	}

	public int getRunecraftLevel()
	{
		return runecraftLevel;
	}

	public int getAgilityLevel()
	{
		return agilityLevel;
	}

	public int getMagicLevel()
	{
		return magicLevel;
	}

	public Set<Altar> getQuestLockedAltars()
	{
		return questLockedAltars;
	}

	public List<BarrierState> getBarriers()
	{
		return barriers;
	}

	public List<WorldPoint> getEmptyTiles()
	{
		return emptyTiles;
	}

	public List<WorldPoint> getBrokenTiles()
	{
		return brokenTiles;
	}

	public Map<Altar, WorldPoint> getPortalGuardians()
	{
		return portalGuardians;
	}

	@Nullable
	public WorldPoint getChosenTile()
	{
		return chosenTile;
	}

	@Nullable
	public Step getLastStep()
	{
		return lastStep;
	}

	public boolean isWeakCellTablePresent()
	{
		return weakCellTablePresent;
	}

	public int getSavedElementalPoints()
	{
		return savedElementalPoints;
	}

	public int getSavedCatalyticPoints()
	{
		return savedCatalyticPoints;
	}

	public boolean isPointsCredited()
	{
		return pointsCredited;
	}

	public double getFragmentsPerTick()
	{
		return fragmentsPerTick;
	}

	public double getPartsPerTick()
	{
		return partsPerTick;
	}

	public double getCraftPerTick()
	{
		return craftPerTick;
	}

	public double getHugePerTick()
	{
		return hugePerTick;
	}

	public int getSecondsToClose()
	{
		return secondsToClose;
	}

	public Map<Target, Integer> getTravelTicks()
	{
		return travelTicks;
	}

	public Map<Altar, Integer> getGuardianTravelTicks()
	{
		return guardianTravelTicks;
	}

	public Map<Target, Integer> getReturnTicks()
	{
		return returnTicks;
	}

	public Map<Target, Integer> getPortalTicks()
	{
		return portalTicks;
	}

	public int pouchStored()
	{
		int n = 0;
		for (PouchState pouch : pouches)
		{
			n += pouch.getStored();
		}
		return n;
	}

	public int pouchSpace()
	{
		int n = 0;
		for (PouchState pouch : pouches)
		{
			n += pouch.space();
		}
		return n;
	}

	public boolean anyPouchDegraded()
	{
		for (PouchState pouch : pouches)
		{
			if (pouch.isDegraded())
			{
				return true;
			}
		}
		return false;
	}

	public int essenceTotal()
	{
		return essence + pouchStored();
	}

	public int capacity()
	{
		return freeSlots + pouchSpace();
	}

	public int stonesTotal()
	{
		return elementalStones + catalyticStones + polyElementalStones + polyCatalyticStones;
	}

	public boolean hasStones()
	{
		return stonesTotal() > 0;
	}

	public int energy(Alignment alignment)
	{
		return alignment == Alignment.ELEMENTAL ? elementalEnergy : catalyticEnergy;
	}

	public int savedPoints(Alignment alignment)
	{
		return alignment == Alignment.ELEMENTAL ? savedElementalPoints : savedCatalyticPoints;
	}

	public int powerPercent()
	{
		return maxPower <= 0 ? 0 : (int) Math.round(100.0 * power / maxPower);
	}

	public boolean energyCapped()
	{
		return (elementalEnergy >= 1000 && catalyticEnergy >= 1000) || elementalEnergy + catalyticEnergy >= 1200;
	}

	public static final class Builder
	{
		private Location location = Location.OUTSIDE;
		private Altar altarRoom;
		private WorldPoint playerLocation;
		private GamePhase phase = GamePhase.WAITING;
		private int secondsToStart = -1;
		private int secondsSinceStart = -1;
		private int secondsToNextGame = -1;
		private int elementalEnergy;
		private int catalyticEnergy;
		private int power;
		private int maxPower;
		private Altar activeElemental;
		private Altar activeCatalytic;
		private int altarSecondsRemaining = -1;
		private boolean portalOpen;
		private int portalSecondsRemaining = -1;
		private String portalDirection;
		private int secondsSinceLastPortal = -1;
		private int secondsToNextPortal = -1;
		private boolean anyPortalThisGame;
		private boolean altarVisitedThisGame;
		private int guardiansActive;
		private int guardiansMax = 10;
		private int fragments;
		private int essence;
		private int elementalStones;
		private int catalyticStones;
		private int polyElementalStones;
		private int polyCatalyticStones;
		private CellTier chargedCell;
		private int unchargedCells;
		private boolean chisel;
		private boolean pickaxe;
		private int freeSlots;
		private Set<Altar> talismans = ImmutableSet.of();
		private Altar baseRune;
		private int baseRuneCount;
		private List<PouchState> pouches = ImmutableList.of();
		private boolean bindingNecklaceWorn;
		private int necklaceCharges;
		private boolean magicImbueActive;
		private boolean lunarSpellbook;
		private boolean runePouch;
		private int runecraftLevel = 1;
		private int agilityLevel = 1;
		private int magicLevel = 1;
		private Set<Altar> questLockedAltars = ImmutableSet.of();
		private List<BarrierState> barriers = ImmutableList.of();
		private List<WorldPoint> emptyTiles = ImmutableList.of();
		private List<WorldPoint> brokenTiles = ImmutableList.of();
		private Map<Altar, WorldPoint> portalGuardians = ImmutableMap.of();
		private WorldPoint chosenTile;
		private Step lastStep;
		private boolean weakCellTablePresent;
		private int savedElementalPoints = -1;
		private int savedCatalyticPoints = -1;
		private boolean pointsCredited;
		private double fragmentsPerTick = 0.6;
		private double partsPerTick = 0.4;
		private double craftPerTick = 1.0;
		private double hugePerTick = 0.6;
		private int secondsToClose = -1;
		private Map<Target, Integer> travelTicks = ImmutableMap.of();
		private Map<Altar, Integer> guardianTravelTicks = ImmutableMap.of();
		private Map<Target, Integer> returnTicks = ImmutableMap.of();
		private Map<Target, Integer> portalTicks = ImmutableMap.of();

		public Builder location(Location location)
		{
			this.location = location;
			return this;
		}

		public Builder altarRoom(@Nullable Altar altarRoom)
		{
			this.altarRoom = altarRoom;
			return this;
		}

		public Builder playerLocation(@Nullable WorldPoint playerLocation)
		{
			this.playerLocation = playerLocation;
			return this;
		}

		public Builder phase(GamePhase phase)
		{
			this.phase = phase;
			return this;
		}

		public Builder secondsToStart(int secondsToStart)
		{
			this.secondsToStart = secondsToStart;
			return this;
		}

		public Builder secondsSinceStart(int secondsSinceStart)
		{
			this.secondsSinceStart = secondsSinceStart;
			return this;
		}

		public Builder secondsToNextGame(int secondsToNextGame)
		{
			this.secondsToNextGame = secondsToNextGame;
			return this;
		}

		public Builder elementalEnergy(int elementalEnergy)
		{
			this.elementalEnergy = elementalEnergy;
			return this;
		}

		public Builder catalyticEnergy(int catalyticEnergy)
		{
			this.catalyticEnergy = catalyticEnergy;
			return this;
		}

		public Builder power(int power)
		{
			this.power = power;
			return this;
		}

		public Builder maxPower(int maxPower)
		{
			this.maxPower = maxPower;
			return this;
		}

		public Builder activeElemental(@Nullable Altar activeElemental)
		{
			this.activeElemental = activeElemental;
			return this;
		}

		public Builder activeCatalytic(@Nullable Altar activeCatalytic)
		{
			this.activeCatalytic = activeCatalytic;
			return this;
		}

		public Builder altarSecondsRemaining(int altarSecondsRemaining)
		{
			this.altarSecondsRemaining = altarSecondsRemaining;
			return this;
		}

		public Builder portalOpen(boolean portalOpen)
		{
			this.portalOpen = portalOpen;
			return this;
		}

		public Builder portalSecondsRemaining(int portalSecondsRemaining)
		{
			this.portalSecondsRemaining = portalSecondsRemaining;
			return this;
		}

		public Builder portalDirection(@Nullable String portalDirection)
		{
			this.portalDirection = portalDirection;
			return this;
		}

		public Builder secondsSinceLastPortal(int secondsSinceLastPortal)
		{
			this.secondsSinceLastPortal = secondsSinceLastPortal;
			return this;
		}

		public Builder secondsToNextPortal(int secondsToNextPortal)
		{
			this.secondsToNextPortal = secondsToNextPortal;
			return this;
		}

		public Builder anyPortalThisGame(boolean anyPortalThisGame)
		{
			this.anyPortalThisGame = anyPortalThisGame;
			return this;
		}

		public Builder altarVisitedThisGame(boolean altarVisitedThisGame)
		{
			this.altarVisitedThisGame = altarVisitedThisGame;
			return this;
		}

		public Builder guardiansActive(int guardiansActive)
		{
			this.guardiansActive = guardiansActive;
			return this;
		}

		public Builder guardiansMax(int guardiansMax)
		{
			this.guardiansMax = guardiansMax;
			return this;
		}

		public Builder fragments(int fragments)
		{
			this.fragments = fragments;
			return this;
		}

		public Builder essence(int essence)
		{
			this.essence = essence;
			return this;
		}

		public Builder elementalStones(int elementalStones)
		{
			this.elementalStones = elementalStones;
			return this;
		}

		public Builder catalyticStones(int catalyticStones)
		{
			this.catalyticStones = catalyticStones;
			return this;
		}

		public Builder polyElementalStones(int polyElementalStones)
		{
			this.polyElementalStones = polyElementalStones;
			return this;
		}

		public Builder polyCatalyticStones(int polyCatalyticStones)
		{
			this.polyCatalyticStones = polyCatalyticStones;
			return this;
		}

		public Builder chargedCell(@Nullable CellTier chargedCell)
		{
			this.chargedCell = chargedCell;
			return this;
		}

		public Builder unchargedCells(int unchargedCells)
		{
			this.unchargedCells = unchargedCells;
			return this;
		}

		public Builder chisel(boolean chisel)
		{
			this.chisel = chisel;
			return this;
		}

		public Builder pickaxe(boolean pickaxe)
		{
			this.pickaxe = pickaxe;
			return this;
		}

		public Builder freeSlots(int freeSlots)
		{
			this.freeSlots = freeSlots;
			return this;
		}

		public Builder talismans(Set<Altar> talismans)
		{
			this.talismans = talismans;
			return this;
		}

		public Builder baseRune(@Nullable Altar baseRune)
		{
			this.baseRune = baseRune;
			return this;
		}

		public Builder baseRuneCount(int baseRuneCount)
		{
			this.baseRuneCount = baseRuneCount;
			return this;
		}

		public Builder pouches(List<PouchState> pouches)
		{
			this.pouches = pouches;
			return this;
		}

		public Builder bindingNecklaceWorn(boolean bindingNecklaceWorn)
		{
			this.bindingNecklaceWorn = bindingNecklaceWorn;
			return this;
		}

		public Builder necklaceCharges(int necklaceCharges)
		{
			this.necklaceCharges = necklaceCharges;
			return this;
		}

		public Builder magicImbueActive(boolean magicImbueActive)
		{
			this.magicImbueActive = magicImbueActive;
			return this;
		}

		public Builder lunarSpellbook(boolean lunarSpellbook)
		{
			this.lunarSpellbook = lunarSpellbook;
			return this;
		}

		public Builder runePouch(boolean runePouch)
		{
			this.runePouch = runePouch;
			return this;
		}

		public Builder runecraftLevel(int runecraftLevel)
		{
			this.runecraftLevel = runecraftLevel;
			return this;
		}

		public Builder agilityLevel(int agilityLevel)
		{
			this.agilityLevel = agilityLevel;
			return this;
		}

		public Builder magicLevel(int magicLevel)
		{
			this.magicLevel = magicLevel;
			return this;
		}

		public Builder questLockedAltars(Set<Altar> questLockedAltars)
		{
			this.questLockedAltars = questLockedAltars;
			return this;
		}

		public Builder barriers(List<BarrierState> barriers)
		{
			this.barriers = barriers;
			return this;
		}

		public Builder emptyTiles(List<WorldPoint> emptyTiles)
		{
			this.emptyTiles = emptyTiles;
			return this;
		}

		public Builder brokenTiles(List<WorldPoint> brokenTiles)
		{
			this.brokenTiles = brokenTiles;
			return this;
		}

		public Builder portalGuardians(Map<Altar, WorldPoint> portalGuardians)
		{
			this.portalGuardians = portalGuardians;
			return this;
		}

		public Builder chosenTile(@Nullable WorldPoint chosenTile)
		{
			this.chosenTile = chosenTile;
			return this;
		}

		public Builder lastStep(@Nullable Step lastStep)
		{
			this.lastStep = lastStep;
			return this;
		}

		public Builder weakCellTablePresent(boolean weakCellTablePresent)
		{
			this.weakCellTablePresent = weakCellTablePresent;
			return this;
		}

		public Builder savedElementalPoints(int savedElementalPoints)
		{
			this.savedElementalPoints = savedElementalPoints;
			return this;
		}

		public Builder savedCatalyticPoints(int savedCatalyticPoints)
		{
			this.savedCatalyticPoints = savedCatalyticPoints;
			return this;
		}

		public Builder pointsCredited(boolean pointsCredited)
		{
			this.pointsCredited = pointsCredited;
			return this;
		}

		public Builder fragmentsPerTick(double fragmentsPerTick)
		{
			this.fragmentsPerTick = fragmentsPerTick;
			return this;
		}

		public Builder partsPerTick(double partsPerTick)
		{
			this.partsPerTick = partsPerTick;
			return this;
		}

		public Builder craftPerTick(double craftPerTick)
		{
			this.craftPerTick = craftPerTick;
			return this;
		}

		public Builder hugePerTick(double hugePerTick)
		{
			this.hugePerTick = hugePerTick;
			return this;
		}

		public Builder secondsToClose(int secondsToClose)
		{
			this.secondsToClose = secondsToClose;
			return this;
		}

		public Builder travelTicks(Map<Target, Integer> travelTicks)
		{
			this.travelTicks = travelTicks;
			return this;
		}

		public Builder guardianTravelTicks(Map<Altar, Integer> guardianTravelTicks)
		{
			this.guardianTravelTicks = guardianTravelTicks;
			return this;
		}

		public Builder returnTicks(Map<Target, Integer> returnTicks)
		{
			this.returnTicks = returnTicks;
			return this;
		}

		public Builder portalTicks(Map<Target, Integer> portalTicks)
		{
			this.portalTicks = portalTicks;
			return this;
		}

		public Snapshot build()
		{
			return new Snapshot(this);
		}
	}
}
