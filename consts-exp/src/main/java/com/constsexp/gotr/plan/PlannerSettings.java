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
package com.constsexp.gotr.plan;

/**
 * consts-EXP has no GOTR settings but its switch, so every one is fixed at upstream's default and the planner
 * plays only upstream's default strategy, Mass (its General and Solo plans are cut); only what the player carries
 * changes.
 */
public final class PlannerSettings
{
	private final boolean combinationRunes;

	public PlannerSettings(boolean combinationRunes)
	{
		this.combinationRunes = combinationRunes;
	}

	public int getOpeningFragmentTarget()
	{
		return 120;
	}

	// Seconds before the altars change at which a run past the opening stops mining to craft.
	public int getCraftWindowSeconds()
	{
		return 50;
	}

	public int getMinFragmentsToCraft()
	{
		return 30;
	}

	public int getDesiredGuardians()
	{
		return 2;
	}

	public boolean isCombinationRunes()
	{
		return combinationRunes;
	}

	public boolean isBalanceWithSavedPoints()
	{
		return true;
	}

	public int getMaxImbalance()
	{
		return 200;
	}

	public int getPortalMinCapacity()
	{
		return 10;
	}

	public boolean isTakeWeakCell()
	{
		return true;
	}

	// Off: once the opening run is done, fragments come only from what is left and the portals.
	public boolean isMineAfterOpening()
	{
		return true;
	}

	public int getGracePortalSeconds()
	{
		return 95;
	}

	// How long to wait for a better altar when only a weak one is usable; 0 never waits. Every
	// altar pays two energy per essence, so the wait only buys a better cell, and points come
	// first: a weak altar is an altar.
	public int getWeakAltarWaitSeconds()
	{
		return 0;
	}

	public int getPortalWarningSeconds()
	{
		return 10;
	}
}
