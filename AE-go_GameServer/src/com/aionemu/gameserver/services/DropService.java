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
package com.aionemu.gameserver.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javolution.util.FastMap;

import org.apache.log4j.Logger;

import com.aionemu.commons.database.dao.DAOManager;
import com.aionemu.gameserver.dao.DropListDAO;
import com.aionemu.gameserver.model.drop.DropItem;
import com.aionemu.gameserver.model.drop.DropList;
import com.aionemu.gameserver.model.drop.DropTemplate;
import com.aionemu.gameserver.model.gameobjects.DropNpc;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LOOT_ITEMLIST;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LOOT_STATUS;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.world.World;
import com.google.inject.Inject;

/**
 * @author ATracer
 */
public class DropService
{
	private static final Logger			log					= Logger.getLogger(DropService.class);

	private DropList					dropList;

	private Map<Integer, Set<DropItem>>	currentDropMap		= Collections
																.synchronizedMap(new HashMap<Integer, Set<DropItem>>());
	private Map<Integer, DropNpc>		dropRegistrationMap	= new FastMap<Integer, DropNpc>();

	private ItemService					itemService;
	private GroupService				groupService;
	private QuestService				questService;
	private World						world;

	@Inject
	public DropService(ItemService itemService, World world, GroupService groupService, QuestService questService)
	{
		this.itemService = itemService;
		this.world = world;
		this.groupService = groupService;
		this.questService = questService;
		dropList = DAOManager.getDAO(DropListDAO.class).load();
		log.info(dropList.getSize() + " npc drops loaded");
	}

	/**
	 * @return the dropList
	 */
	public DropList getDropList()
	{
		return dropList;
	}

	/**
	 * After NPC dies - it can register arbitrary drop
	 * 
	 * @param npc
	 */
	public void registerDrop(Npc npc, Player player)
	{
		int npcUniqueId = npc.getObjectId();
		int npcTemplateId = npc.getObjectTemplate().getTemplateId();

		Set<DropItem> droppedItems = new HashSet<DropItem>();
		Set<DropTemplate> templates = dropList.getDropsFor(npcTemplateId);
		int index = 1;
		if(templates != null)
		{
			for(DropTemplate dropTemplate : templates)
			{
				DropItem dropItem = new DropItem(dropTemplate);
				dropItem.calculateCount(player.getRates().getDropRate());

				if(dropItem.getCount() > 0)
				{
					dropItem.setIndex(index++);
					droppedItems.add(dropItem);
				}
			}
		}
		
		questService.getQuestDrop(droppedItems, index, npc, player);
		currentDropMap.put(npcUniqueId, droppedItems);

		// TODO player should not be null
		if(player != null)
		{
			if(player.isInGroup())
			{
				dropRegistrationMap.put(npcUniqueId, new DropNpc(groupService.getMembersToRegistrateByRules(player,
					player.getPlayerGroup())));
			}
			else
			{
				List<Integer> singlePlayer = new ArrayList<Integer>();
				singlePlayer.add(player.getObjectId());
				dropRegistrationMap.put(npcUniqueId, new DropNpc(singlePlayer));
			}
		}
	}

	/**
	 * After NPC respawns - drop should be unregistered //TODO more correct - on despawn
	 * 
	 * @param npc
	 */
	public void unregisterDrop(Npc npc)
	{
		int npcUniqueId = npc.getObjectId();
		currentDropMap.remove(npcUniqueId);
		if(dropRegistrationMap.containsKey(npcUniqueId))
		{
			dropRegistrationMap.remove(npcUniqueId);
		}
	}

	/**
	 * When player clicks on dead NPC to request drop list
	 * 
	 * @param player
	 * @param npcId
	 */
	public void requestDropList(Player player, int npcId)
	{
		if(player == null || !dropRegistrationMap.containsKey(npcId))
			return;

		DropNpc dropNpc = dropRegistrationMap.get(npcId);
		if(!dropNpc.containsKey(player.getObjectId()))
		{
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_LOOT_NO_RIGHT());
			return;
		}

		if(dropNpc.isBeingLooted())
		{
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_LOOT_FAIL_ONLOOTING());
			return;
		}

		dropNpc.setBeingLooted(player.getObjectId());

		Set<DropItem> dropItems = currentDropMap.get(npcId);

		if(dropItems == null)
		{
			dropItems = Collections.emptySet();
		}

		PacketSendUtility.sendPacket(player, new SM_LOOT_ITEMLIST(npcId, dropItems, player));
		// PacketSendUtility.sendPacket(player, new SM_LOOT_STATUS(npcId, size > 0 ? size - 1 : size));
		PacketSendUtility.sendPacket(player, new SM_LOOT_STATUS(npcId, 2));
		player.unsetState(CreatureState.ACTIVE);
		player.setState(CreatureState.LOOTING);
		PacketSendUtility.broadcastPacket(player, new SM_EMOTION(player, 35, 0, npcId), true);

		// if dropitems is empty, resend droplist for close loot
		if(dropItems.size() == 0)
			resendDropList(player, npcId, dropItems);
	}

	/**
	 * This method will change looted corpse to not in use
	 * @param player
	 * @param npcId
	 * @param close
	 */
	public void requestDropList(Player player, int npcId, boolean close)
	{
		if(!dropRegistrationMap.containsKey(npcId))
			return;

		DropNpc dropNpc = dropRegistrationMap.get(npcId);
		dropNpc.setBeingLooted(0);
		
		if(player.isInGroup())
		{
			if(player.getPlayerGroup().getGroupLeader().getObjectId() == player.getObjectId())
				dropRegistrationMap.put(npcId, new DropNpc(groupService.getGroupMembers(player.getPlayerGroup(), true)));
		}
		player.unsetState(CreatureState.LOOTING);
		player.setState(CreatureState.ACTIVE);
		PacketSendUtility.broadcastPacket(player, new SM_EMOTION(player, 36, 0, npcId), true);
	}

	public void requestDropItem(Player player, int npcId, int itemIndex)
	{
		Set<DropItem> dropItems = currentDropMap.get(npcId);

		// drop was unregistered
		if(dropItems == null)
		{
			return;
		}

		// TODO prevent possible exploits

		DropItem requestedItem = null;

		synchronized(dropItems)
		{
			for(DropItem dropItem : dropItems)
			{
				if(dropItem.getIndex() == itemIndex)
				{
					requestedItem = dropItem;
					break;
				}
			}
		}

		if(requestedItem != null)
		{
			int currentDropItemCount = requestedItem.getCount();
			int itemId = requestedItem.getDropTemplate().getItemId();
			
			currentDropItemCount = itemService.addItem(player, itemId, currentDropItemCount);

			if(currentDropItemCount == 0)
			{
				dropItems.remove(requestedItem);
			}
			else
			{
				// If player didnt got all item stack
				requestedItem.setCount(currentDropItemCount);
			}

			// show updated droplist
			resendDropList(player, npcId, dropItems);
		}
	}

	private void resendDropList(Player player, int npcId, Set<DropItem> dropItems)
	{
		if(dropItems.size() != 0)
		{
			PacketSendUtility.sendPacket(player, new SM_LOOT_ITEMLIST(npcId, dropItems, player));
			PacketSendUtility.sendPacket(player, new SM_LOOT_STATUS(npcId, 0));
		}
		else
		{
			PacketSendUtility.sendPacket(player, new SM_LOOT_STATUS(npcId, 3));
			player.unsetState(CreatureState.LOOTING);
			player.setState(CreatureState.ACTIVE);
			PacketSendUtility.broadcastPacket(player, new SM_EMOTION(player, 36, 0, npcId), true);
			Npc npc = (Npc) world.findAionObject(npcId);
			if(npc != null)
			{
				npc.getController().onDespawn(true);
			}
		}
	}
}