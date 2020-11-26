/*
 * This file is part of aion-unique <aion-unique.org>.
 *
 *  aion-unique is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  aion-unique is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with aion-unique.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aionemu.gameserver.taskmanager;

import org.apache.log4j.Logger;

import com.aionemu.commons.utils.AEFastSet;
import com.aionemu.commons.utils.concurrent.RunnableStatsManager;

/**
 * @author lord_rex and MrPoke 
 * 	based on l2j-free engines.
 */
public abstract class AbstractFIFOPeriodicTaskManager<T> extends AbstractPeriodicTaskManager
{
	protected static final Logger	log		= Logger.getLogger(AbstractFIFOPeriodicTaskManager.class);
	private final AEFastSet<T>		queue	= new AEFastSet<T>();

	public AbstractFIFOPeriodicTaskManager(int period)
	{
		super(period);
	}

	public final void add(T t)
	{
		writeLock();
		try
		{
			queue.add(t);
		}
		finally
		{
			writeUnlock();
		}
	}

	private final T getFirst()
	{
		writeLock();
		try
		{
			return queue.getFirst();
		}
		finally
		{
			writeUnlock();
		}
	}

	private final void remove(T t)
	{
		writeLock();
		try
		{
			queue.remove(t);
		}
		finally
		{
			writeUnlock();
		}
	}

	@Override
	public final void run()
	{
		for(T task; (task = getFirst()) != null;)
		{
			final long begin = System.nanoTime();

			try
			{
				callTask(task);
			}
			catch(RuntimeException e)
			{
				log.error("", e);
			}
			finally
			{
				RunnableStatsManager.handleStats(task.getClass(), getCalledMethodName(), System.nanoTime() - begin);

				remove(task);
			}
		}
	}

	protected abstract void callTask(T task);

	protected abstract String getCalledMethodName();
}
