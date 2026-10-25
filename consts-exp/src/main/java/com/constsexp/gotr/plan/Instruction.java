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

import com.constsexp.gotr.model.Altar;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.runelite.api.coords.WorldPoint;

public final class Instruction
{
	private final Step step;
	private final String headline;
	private final String detail;
	private final Target target;
	@Nullable
	private final Altar altar;
	@Nullable
	private final WorldPoint location;
	private final Set<Integer> items;
	private final Urgency urgency;

	private Instruction(Builder b)
	{
		this.step = b.step;
		this.headline = b.headline;
		this.detail = b.detail;
		this.target = b.target;
		this.altar = b.altar;
		this.location = b.location;
		this.items = b.items;
		this.urgency = b.urgency;
	}

	public static Builder builder()
	{
		return new Builder();
	}

	public Builder toBuilder()
	{
		Builder b = new Builder();
		b.step = step;
		b.headline = headline;
		b.detail = detail;
		b.target = target;
		b.altar = altar;
		b.location = location;
		b.items = items;
		b.urgency = urgency;
		return b;
	}

	public Step getStep()
	{
		return step;
	}

	public String getHeadline()
	{
		return headline;
	}

	public String getDetail()
	{
		return detail;
	}

	public Target getTarget()
	{
		return target;
	}

	@Nullable
	public Altar getAltar()
	{
		return altar;
	}

	@Nullable
	public WorldPoint getLocation()
	{
		return location;
	}

	public Set<Integer> getItems()
	{
		return items;
	}

	public Urgency getUrgency()
	{
		return urgency;
	}

	public static final class Builder
	{
		private Step step;
		private String headline;
		private String detail = "";
		private Target target = Target.NONE;
		private Altar altar;
		private WorldPoint location;
		private Set<Integer> items = ImmutableSet.of();
		private Urgency urgency = Urgency.NORMAL;

		public Builder step(Step step)
		{
			this.step = step;
			return this;
		}

		public Builder headline(String headline)
		{
			this.headline = headline;
			return this;
		}

		public Builder detail(String detail)
		{
			this.detail = detail;
			return this;
		}

		public Builder target(Target target)
		{
			this.target = target;
			return this;
		}

		public Builder altar(@Nullable Altar altar)
		{
			this.altar = altar;
			return this;
		}

		public Builder location(@Nullable WorldPoint location)
		{
			this.location = location;
			return this;
		}

		public Builder items(Set<Integer> items)
		{
			this.items = items;
			return this;
		}

		public Builder urgency(Urgency urgency)
		{
			this.urgency = urgency;
			return this;
		}

		public Instruction build()
		{
			return new Instruction(this);
		}
	}
}
