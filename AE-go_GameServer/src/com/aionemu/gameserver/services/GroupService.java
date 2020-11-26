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
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import javolution.util.FastMap;

import org.apache.log4j.Logger;

import com.aionemu.gameserver.configs.main.GroupConfig;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Monster;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.RequestResponseHandler;
import com.aionemu.gameserver.model.group.GroupEvent;
import com.aionemu.gameserver.model.group.LootGroupRules;
import com.aionemu.gameserver.model.group.LootRuleType;
import com.aionemu.gameserver.model.group.PlayerGroup;
import com.aionemu.gameserver.network.aion.clientpackets.CM_VIEW_PLAYER_DETAILS;
import com.aionemu.gameserver.network.aion.serverpackets.SM_GROUP_INFO;
import com.aionemu.gameserver.network.aion.serverpackets.SM_GROUP_MEMBER_INFO;
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SHOW_BRAND;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.restrictions.RestrictionsManager;
import com.aionemu.gameserver.utils.MathUtil;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.utils.idfactory.IDFactory;
import com.aionemu.gameserver.utils.idfactory.IDFactoryAionObject;
import com.aionemu.gameserver.utils.stats.StatFunctions;
import com.google.inject.Inject;

/**
 * @author Simple
 */
public class GroupService
{
	/**
	 * Definition for logging
	 */
	private static final Logger						log				= Logger.getLogger(CM_VIEW_PLAYER_DETAILS.class);

	/**
	 * Caching group members
	 */
	private final FastMap<Integer, PlayerGroup>		groupMembers	= new FastMap<Integer, PlayerGroup>();

	/**
	 * Caching remove group member schedule
	 */
	private FastMap<Integer, ScheduledFuture<?>>	playerGroup		= new FastMap<Integer, ScheduledFuture<?>>();

	/**
	 * Injections
	 */
	@Inject
	@IDFactoryAionObject
	private IDFactory								aionObjectsIDFactory;

	@Inject
	QuestEngine										questEngine;

	/**
	 * This method will add a member to the group member cache
	 * 
	 * @param player
	 */
	public void addGroupMemberToCache(Player player)
	{
		if(!groupMembers.containsKey(player.getObjectId()))
			groupMembers.put(player.getObjectId(), player.getPlayerGroup());
	}

	public void removeGroupMemberFromCache(int playerObjId)
	{
		if(groupMembers.containsKey(playerObjId))
			groupMembers.remove(playerObjId);
	}

	/**
	 * @param playerObjId
	 * @return returns true if player is in the cache
	 */
	public boolean isGroupMember(int playerObjId)
	{
		return groupMembers.containsKey(playerObjId);
	}

	/**
	 * Returns the player's group
	 * 
	 * @param playerObjId
	 * @return PlayerGroup
	 */
	public PlayerGroup getGroup(int playerObjId)
	{
		return groupMembers.get(playerObjId);
	}

	/**
	 * This method will handle everything to a player that is invited for a group
	 * 
	 * @param inviter
	 * @param invited
	 */
	public void invitePlayerToGroup(final Player inviter, final Player invited)
	{
		if(RestrictionsManager.canInviteToGroup(inviter, invited))
		{
			final PlayerGroup group = inviter.getPlayerGroup();
			RequestResponseHandler responseHandler = new RequestResponseHandler(inviter){
				@Override
				public void acceptRequest(Creature requester, Player responder)
				{
					if(group != null && group.isFull())
						return;

					PacketSendUtility.sendPacket(inviter, SM_SYSTEM_MESSAGE.REQUEST_GROUP_INVITE(invited.getName()));
					if(group != null)
					{
						inviter.getPlayerGroup().addPlayerToGroup(invited);
						addGroupMemberToCache(invited);
					}
					else
					{
						new PlayerGroup(aionObjectsIDFactory.nextId(), inviter);
						inviter.getPlayerGroup().addPlayerToGroup(invited);
						addGroupMemberToCache(inviter);
						addGroupMemberToCache(invited);
					}
				}

				@Override
				public void denyRequest(Creature requester, Player responder)
				{
					PacketSendUtility.sendPacket(inviter, SM_SYSTEM_MESSAGE.REJECT_GROUP_INVITE(responder.getName()));
				}
			};

			boolean result = invited.getResponseRequester().putRequest(SM_QUESTION_WINDOW.STR_REQUEST_GROUP_INVITE,
				responseHandler);
			if(result)
			{
				PacketSendUtility.sendPacket(invited, new SM_QUESTION_WINDOW(
					SM_QUESTION_WINDOW.STR_REQUEST_GROUP_INVITE, 0, inviter.getName()));
			}
		}
	}

	/**
	 * @param player
	 */
	public void removePlayerFromGroup(Player player)
	{
		if(player.isInGroup())
		{
			final PlayerGroup group = player.getPlayerGroup();

			group.removePlayerFromGroup(player);
			removeGroupMemberFromCache(player.getObjectId());

			if(group.size() < 2)
				disbandGroup(group);
		}
	}

	/**
	 * @param player
	 */
	public void setGroupLeader(Player player)
	{
		final PlayerGroup group = player.getPlayerGroup();

		group.setGroupLeader(player);
		group.updateGroupUIToEvent(player.getPlayerGroup().getGroupLeader(), GroupEvent.CHANGELEADER);
	}

	/**
	 * @param status
	 * @param playerObjId
	 * @param player
	 */
	public void playerStatusInfo(int status, Player player)
	{
		switch(status)
		{
			case 2:
				removePlayerFromGroup(player);
				break;
			case 3:
				setGroupLeader(player);
				break;
			case 6:
				removePlayerFromGroup(player);
				break;
		}

		log.info(String.valueOf(status));
	}

	/**
	 * @param player
	 * @param amount
	 */
	public void groupDistribution(Player player, int amount)
	{
		PlayerGroup pg = player.getPlayerGroup();
		if(pg == null)
			return;

		int availableKinah = player.getInventory().getKinahItem().getItemCount();
		if(availableKinah < amount)
		{
			// TODO retail message ?
			return;
		}

		int rewardcount = pg.size() - 1;
		if(rewardcount <= amount)
		{
			int reward = amount / rewardcount;

			for(Player groupMember : pg.getMembers())
			{
				if(groupMember.equals(player))
					groupMember.getInventory().decreaseKinah(amount);
				else
					groupMember.getInventory().increaseKinah(reward);
			}
		}
	}

	/**
	 * This method will send a reward if a player is in a group
	 * 
	 * @param player
	 */
	public void doReward(Player player, Monster owner)
	{
		long xpReward = StatFunctions.calculateGroupExperienceReward(player, owner);
		
		List<Player> players = new ArrayList<Player>();
		int partyLvlSum = 0;
		for(Player member : player.getPlayerGroup().getMembers())
		{
			if(MathUtil.isInRange(member, player, GroupConfig.GROUP_MAX_DISTANCE))
			{
				players.add(member);
				partyLvlSum += member.getLevel();
			}
		}
		double mod = 1;
		if (players.size() == 0)
			return;
		else if (players.size() > 1)
			mod = 1+(((players.size()-1)*10)/100);
		
		xpReward *= mod; 

		for(Player member : players)
		{
			long currentExp = member.getCommonData().getExp();
			long reward = (xpReward * member.getLevel())/partyLvlSum;
			reward *= member.getRates().getGroupXpRate();
			member.getCommonData().setExp(currentExp + reward);

			PacketSendUtility.sendPacket(member, SM_SYSTEM_MESSAGE.EXP(Long.toString(reward)));

				// DPreward
			int currentDp = member.getCommonData().getDp();
			int dpReward = StatFunctions.calculateGroupDPReward(member, owner);
			member.getCommonData().setDp(dpReward + currentDp);
			questEngine.onKill(new QuestEnv(owner, member, 0 , 0));
		}
	}

	/**
	 * This method will send the show brand to every groupmember
	 * 
	 * @param playerGroup
	 * @param brandId
	 * @param targetObjectId
	 */
	public void showBrand(PlayerGroup playerGroup, int brandId, int targetObjectId)
	{
		for(Player member : playerGroup.getMembers())
		{
			PacketSendUtility.sendPacket(member, new SM_SHOW_BRAND(brandId, targetObjectId));
		}
	}

	/**
	 * This method is called when a group is disbanded
	 */
	private void disbandGroup(PlayerGroup group)
	{
		aionObjectsIDFactory.releaseId(group.getGroupId());
		group.getGroupLeader().setPlayerGroup(null);
		PacketSendUtility.sendPacket(group.getGroupLeader(), SM_SYSTEM_MESSAGE.DISBAND_GROUP());
	}

	/**
	 * @param player
	 */
	public void onLogin(Player activePlayer)
	{
		final PlayerGroup group = activePlayer.getPlayerGroup();

		// Send legion info packets
		PacketSendUtility.sendPacket(activePlayer, new SM_GROUP_INFO(group));
		for(Player member : group.getMembers())
		{
			if(!activePlayer.equals(member))
				PacketSendUtility.sendPacket(activePlayer, new SM_GROUP_MEMBER_INFO(group, member, GroupEvent.ENTER));
		}
	}

	/**
	 * @param playerGroupCache
	 *            the playerGroupCache to set
	 */
	public void addPlayerGroupCache(int playerObjId, ScheduledFuture<?> future)
	{
		if(!playerGroup.containsKey(playerObjId))
			playerGroup.put(playerObjId, future);
	}

	/**
	 * This method will remove a schedule to remove a player from a group
	 * 
	 * @param playerObjId
	 */
	public void cancelScheduleRemove(int playerObjId)
	{
		if(playerGroup.containsKey(playerObjId))
		{
			playerGroup.get(playerObjId).cancel(true);
			playerGroup.remove(playerObjId);
		}
	}

	/**
	 * This method will create a schedule to remove a player from a group
	 * 
	 * @param player
	 */
	public void scheduleRemove(final Player player)
	{
		ScheduledFuture<?> future = ThreadPoolManager.getInstance().schedule(new Runnable(){
			@Override
			public void run()
			{
				removePlayerFromGroup(player);
				playerGroup.remove(player.getObjectId());
			}
		}, GroupConfig.GROUP_REMOVE_TIME * 1000);
		addPlayerGroupCache(player.getObjectId(), future);
		player.getPlayerGroup().getMembers().remove(player.getObjectId());
		
		for(Player groupMember : player.getPlayerGroup().getMembers())
		{
			// TODO: MISSING SEND PARTY MEMBER PACKETS
			PacketSendUtility.sendPacket(groupMember, SM_SYSTEM_MESSAGE.PARTY_HE_BECOME_OFFLINE(player.getName()));
		}
	}

	/**
	 * @param player
	 */
	public void setGroup(Player player)
	{
		if(!isGroupMember(player.getObjectId()))
			return;

		final PlayerGroup group = getGroup(player.getObjectId());
		if(group.size() < 2)
		{
			removeGroupMemberFromCache(player.getObjectId());
			cancelScheduleRemove(player.getObjectId());
			return;
		}
		player.setPlayerGroup(group);
		group.onGroupMemberLogIn(player);
		cancelScheduleRemove(player.getObjectId());
		if(group.getGroupLeader().getObjectId() == player.getObjectId())
			group.setGroupLeader(player);
	}

	/**
	 * @return FastMap<Integer, Boolean>
	 */
	public List<Integer> getMembersToRegistrateByRules(final Player player, final PlayerGroup group)
	{
		final LootGroupRules lootRules = group.getLootGroupRules();
		final LootRuleType lootRule = lootRules.getLootRule();
		List<Integer> luckyMembers = new ArrayList<Integer>();

		switch(lootRule)
		{
			case FREEFORALL:
				luckyMembers = getGroupMembers(group, false);
				break;
			case ROUNDROBIN:
				luckyMembers.add(group.getRandomMember());
				break;
			case LEADER:
				luckyMembers.add(group.getGroupLeader().getObjectId());
				break;
		}
		return luckyMembers;
	}

	/**
	 * This method will get all group members
	 * 
	 * @param group
	 * @param except
	 * @return list of group members
	 */
	public List<Integer> getGroupMembers(final PlayerGroup group, boolean except)
	{
		List<Integer> luckyMembers = new ArrayList<Integer>();
		for(int memberObjId : group.getMemberObjIds())
		{
			if(except)
			{
				if(group.getGroupLeader().getObjectId() != memberObjId)
					luckyMembers.add(memberObjId);
			}
			else
				luckyMembers.add(memberObjId);
		}
		return luckyMembers;
	}

	/**
	 * @param player
	 */
	public Player getLuckyPlayer(Player player)
	{
		final PlayerGroup group = player.getPlayerGroup();
		switch(group.getLootGroupRules().getAutodistribution())
		{
			case NORMAL:
				return player;
			case ROLL_DICE:
				// NOT FINISHED YET
				return player;
			case BID:
				// NOT FINISHED YET
				return player;
		}
		return player;
	}
}
