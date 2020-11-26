/*
 * This file is part of aion-unique <aion-unique.smfnew.com>.
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

import org.apache.log4j.Logger;

import com.aionemu.gameserver.dataholders.BindPointData;
import com.aionemu.gameserver.dataholders.PlayerInitialData;
import com.aionemu.gameserver.dataholders.PortalData;
import com.aionemu.gameserver.dataholders.TeleLocationData;
import com.aionemu.gameserver.dataholders.TeleporterData;
import com.aionemu.gameserver.dataholders.PlayerInitialData.LocationData;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.Storage;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.templates.BindPointTemplate;
import com.aionemu.gameserver.model.templates.portal.ExitPoint;
import com.aionemu.gameserver.model.templates.portal.PortalTemplate;
import com.aionemu.gameserver.model.templates.teleport.TelelocationTemplate;
import com.aionemu.gameserver.model.templates.teleport.TeleportLocation;
import com.aionemu.gameserver.model.templates.teleport.TeleporterTemplate;
import com.aionemu.gameserver.network.aion.serverpackets.SM_CHANNEL_INFO;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_ITEM_USAGE_ANIMATION;
import com.aionemu.gameserver.network.aion.serverpackets.SM_PLAYER_INFO;
import com.aionemu.gameserver.network.aion.serverpackets.SM_PLAYER_SPAWN;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SET_BIND_POINT;
import com.aionemu.gameserver.network.aion.serverpackets.SM_STATS_INFO;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.network.aion.serverpackets.SM_TELEPORT_LOC;
import com.aionemu.gameserver.network.aion.serverpackets.SM_TELEPORT_MAP;
import com.aionemu.gameserver.services.ZoneService.ZoneUpdateMode;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.World;
import com.google.inject.Inject;

/**
 * @author ATracer , orz, Simple
 * 
 */
public class TeleportService
{
	private static final Logger	log						= Logger.getLogger(TeleportService.class);

	private static final int	TELEPORT_DEFAULT_DELAY	= 2200;

	@Inject
	private World				world;
	@Inject
	private PlayerService		playerService;
	@Inject
	private DuelService			duelService;
	@Inject
	private TeleLocationData	teleLocationData;
	@Inject
	private TeleporterData		teleporterData;
	@Inject
	private BindPointData		bindPointData;
	@Inject
	private PlayerInitialData	playerInitialData;
	@Inject
	private PortalData			portalData;

	/**
	 * Schedules teleport animation
	 * 
	 * @param activePlayer
	 * @param mapid
	 * @param x
	 * @param y
	 * @param z
	 */
	public void scheduleTeleportTask(final Player activePlayer, final int mapid, final float x, final float y,
		final float z)
	{
		teleportTo(activePlayer, mapid, x, y, z, TELEPORT_DEFAULT_DELAY);
	}

	/**
	 * Performs flight teleportation
	 * 
	 * @param template
	 * @param locId
	 * @param player
	 */
	public void flightTeleport(TeleporterTemplate template, int locId, Player player)
	{
		if(template.getTeleLocIdData() == null)
		{
			log.info(String.format("Missing locId for this teleporter at teleporter_templates.xml with locId: %d",
				locId));
			PacketSendUtility.sendMessage(player,
				"Missing locId for this teleporter at teleporter_templates.xml with locId: " + locId);
			return;
		}

		TeleportLocation location = template.getTeleLocIdData().getTeleportLocation(locId);
		if(location == null)
		{
			log.info(String.format("Missing locId for this teleporter at teleporter_templates.xml with locId: %d",
				locId));
			PacketSendUtility.sendMessage(player,
				"Missing locId for this teleporter at teleporter_templates.xml with locId: " + locId);
			return;
		}

		TelelocationTemplate locationTemplate = teleLocationData.getTelelocationTemplate(locId);
		if(locationTemplate == null)
		{
			log.info(String.format("Missing info at teleport_location.xml with locId: %d", locId));
			PacketSendUtility.sendMessage(player, "Missing info at teleport_location.xml with locId: " + locId);
			return;
		}

		if(!checkKinahForTransportation(location, player))
			return;
		player.setState(CreatureState.FLIGHT_TELEPORT);
		player.unsetState(CreatureState.ACTIVE);
		player.setFlightTeleportId(location.getTeleportId());
		PacketSendUtility.broadcastPacket(player, new SM_EMOTION(player, 6, location.getTeleportId(), 0), true);
	}

	/**
	 * Performs regular teleportation
	 * 
	 * @param template
	 * @param locId
	 * @param player
	 */
	public void regularTeleport(TeleporterTemplate template, int locId, Player player)
	{
		if(template.getTeleLocIdData() == null)
		{
			log.info(String.format("Missing locId for this teleporter at teleporter_templates.xml with locId: %d",
				locId));
			PacketSendUtility.sendMessage(player,
				"Missing locId for this teleporter at teleporter_templates.xml with locId: " + locId);
			return;
		}

		TeleportLocation location = template.getTeleLocIdData().getTeleportLocation(locId);
		if(location == null)
		{
			log.info(String.format("Missing locId for this teleporter at teleporter_templates.xml with locId: %d",
				locId));
			PacketSendUtility.sendMessage(player,
				"Missing locId for this teleporter at teleporter_templates.xml with locId: " + locId);
			return;
		}

		TelelocationTemplate locationTemplate = teleLocationData.getTelelocationTemplate(locId);
		if(locationTemplate == null)
		{
			log.info(String.format("Missing info at teleport_location.xml with locId: %d", locId));
			PacketSendUtility.sendMessage(player, "Missing info at teleport_location.xml with locId: " + locId);
			return;
		}

		if(!checkKinahForTransportation(location, player))
			return;

		PacketSendUtility.sendPacket(player, new SM_TELEPORT_LOC(locationTemplate.getMapId(), locationTemplate.getX(),
			locationTemplate.getY(), locationTemplate.getZ()));
		scheduleTeleportTask(player, locationTemplate.getMapId(), locationTemplate.getX(), locationTemplate.getY(),
			locationTemplate.getZ());
	}

	/**
	 * Check kinah in inventory for teleportation
	 * 
	 * @param location
	 * @param player
	 * @return
	 */
	private boolean checkKinahForTransportation(TeleportLocation location, Player player)
	{
		Storage inventory = player.getInventory();

		if(!inventory.decreaseKinah(location.getPrice()))
		{
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.NOT_ENOUGH_KINAH(location.getPrice()));
			return false;
		}
		return true;
	}

	/**
	 * @param player
	 * @param targetObjectId
	 */
	public void showMap(Player player, int targetObjectId, int npcId)
	{
		if(player.isInState(CreatureState.FLYING))
		{
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_CANNOT_USE_AIRPORT_WHEN_FLYING);
			return;
		}
		
		Npc object = (Npc) world.findAionObject(targetObjectId);
		Race npcRace = object.getObjectTemplate().getRace();
		if(npcRace != null && npcRace != player.getCommonData().getRace())
		{
			PacketSendUtility.sendPacket(player, SM_SYSTEM_MESSAGE.STR_CANNOT_MOVE_TO_AIRPORT_WRONG_NPC);//TODO retail message
			return;
		}
		
		PacketSendUtility.sendPacket(player, new SM_TELEPORT_MAP(player, targetObjectId, getTeleporterTemplate(npcId)));
	}

	/**
	 * Teleport Creature to the location using current heading and instanceId
	 * 
	 * @param worldId
	 * @param x
	 * @param y
	 * @param z
	 * @param delay
	 * @return true or false
	 */
	public boolean teleportTo(Player player, int worldId, float x, float y, float z, int delay)
	{
		int instanceId = 1;
		if(player.getWorldId() == worldId)
		{
			instanceId = player.getInstanceId();
		}
		return teleportTo(player, worldId, instanceId, x, y, z, delay);
	}

	/**
	 * 
	 * @param worldId
	 * @param instanceId
	 * @param x
	 * @param y
	 * @param z
	 * @param delay
	 * @return true or false
	 */
	public boolean teleportTo(Player player, int worldId, int instanceId, float x, float y, float z, int delay)
	{
		return teleportTo(player, worldId, instanceId, x, y, z, player.getHeading(), delay);
	}

	/**
	 * 
	 * @param player
	 * @param worldId
	 * @param instanceId
	 * @param x
	 * @param y
	 * @param z
	 * @param heading
	 * @param delay
	 * @return
	 */
	public boolean teleportTo(final Player player, final int worldId, final int instanceId, final float x,
		final float y, final float z, final byte heading, final int delay)
	{
		if(player.getLifeStats().isAlreadyDead() || !player.isSpawned())
			return false;

		if(duelService.isDueling(player.getObjectId()))
			duelService.loseDuel(player);

		if(delay == 0)
		{
			changePosition(player, worldId, instanceId, x, y, z, heading);
			return true;
		}

		PacketSendUtility.sendPacket(player, new SM_ITEM_USAGE_ANIMATION(player.getObjectId(), 0, 0, delay, 0, 0));
		ThreadPoolManager.getInstance().schedule(new Runnable(){
			@Override
			public void run()
			{
				if(player.getLifeStats().isAlreadyDead() || !player.isSpawned())
					return;

				PacketSendUtility.sendPacket(player, new SM_ITEM_USAGE_ANIMATION(0, 0, 0, 0, 1, 0));
				changePosition(player, worldId, instanceId, x, y, z, heading);
			}
		}, delay);

		return true;
	}

	/**
	 * 
	 * @param worldId
	 * @param instanceId
	 * @param x
	 * @param y
	 * @param z
	 * @param heading
	 */
	private void changePosition(Player player, int worldId, int instanceId, float x, float y, float z, byte heading)
	{
		player.getFlyController().endFly();
				
		world.despawn(player);
		
		int currentWorldId = player.getWorldId();
		world.setPosition(player, worldId, instanceId, x, y, z, heading);	
		
		/**
		 * instant teleport when map is the same
		 */
		if(currentWorldId == worldId)
		{
			PacketSendUtility.sendPacket(player, new SM_STATS_INFO(player));
			PacketSendUtility.sendPacket(player, new SM_PLAYER_INFO(player, false));
			world.spawn(player);
			player.getEffectController().updatePlayerEffectIcons();
			player.getController().addZoneUpdateMask(ZoneUpdateMode.ZONE_REFRESH);
		}
		/**
		 * teleport with full map reloading
		 */
		else
		{			
			player.getController().startProtectionActiveTask();
			PacketSendUtility.sendPacket(player, new SM_CHANNEL_INFO(player.getPosition()));
			PacketSendUtility.sendPacket(player, new SM_PLAYER_SPAWN(player));	
		}
		player.getController().startProtectionActiveTask();		
			
	}

	/**
	 * @return the teleporterData
	 */
	public TeleporterTemplate getTeleporterTemplate(int npcId)
	{
		return teleporterData.getTeleporterTemplate(npcId);
	}

	/**
	 * @return the bindPointData
	 */
	public BindPointTemplate getBindPointTemplate2(int bindPointId)
	{
		return bindPointData.getBindPointTemplate2(bindPointId);
	}

	/**
	 * @param channel
	 */
	public void changeChannel(Player player, int channel)
	{
		world.despawn(player);
		world.setPosition(player, player.getWorldId(), channel + 1, player.getX(), player.getY(), player.getZ(), player
			.getHeading());
		player.getController().startProtectionActiveTask();
		PacketSendUtility.sendPacket(player, new SM_CHANNEL_INFO(player.getPosition()));
		PacketSendUtility.sendPacket(player, new SM_PLAYER_SPAWN(player));
	}

	/**
	 * This method will move a player to their bind location with 0 delay
	 * 
	 * @param player
	 * @param useTeleport
	 */
	public void moveToBindLocation(Player player, boolean useTeleport)
	{
		this.moveToBindLocation(player, useTeleport, 0);
	}

	/**
	 * This method will move a player to their bind location
	 * 
	 * @param player
	 * @param useTeleport
	 * @param delay
	 */
	public void moveToBindLocation(Player player, boolean useTeleport, int delay)
	{
		float x, y, z;
		int worldId;

		int bindPointId = player.getCommonData().getBindPoint();

		if(bindPointId != 0)
		{
			BindPointTemplate bplist = getBindPointTemplate2(bindPointId);
			worldId = bplist.getZoneId();
			x = bplist.getX();
			y = bplist.getY();
			z = bplist.getZ();
		}
		else
		{
			LocationData locationData = playerService.getPlayerInitialData().getSpawnLocation(
				player.getCommonData().getRace());
			worldId = locationData.getMapId();
			x = locationData.getX();
			y = locationData.getY();
			z = locationData.getZ();
		}

		if(useTeleport)
		{
			teleportTo(player, worldId, x, y, z, delay);
		}
		else
		{
			world.setPosition(player, worldId, 1, x, y, z, player.getHeading());
		}
	}

	/**
	 * This method will send the set bind point packet
	 * 
	 * @param player
	 */
	public void sendSetBindPoint(Player player)
	{
		int worldId;
		float x, y, z;
		if(player.getCommonData().getBindPoint() != 0)
		{
			BindPointTemplate bplist = bindPointData.getBindPointTemplate2(player.getCommonData().getBindPoint());
			worldId = bplist.getZoneId();
			x = bplist.getX();
			y = bplist.getY();
			z = bplist.getZ();
		}
		else
		{
			LocationData locationData = playerInitialData.getSpawnLocation(player.getCommonData().getRace());
			worldId = locationData.getMapId();
			x = locationData.getX();
			y = locationData.getY();
			z = locationData.getZ();
		}
		PacketSendUtility.sendPacket(player, new SM_SET_BIND_POINT(worldId, x, y, z));
	}
	
	/**
	 * 
	 * @param portalName
	 */
	public void teleportToPortalExit(Player player, String portalName, int worldId, int delay)
	{
		PortalTemplate template = portalData.getTemplateByNameAndWorld(worldId, portalName);
		if(template == null)
		{
			log.warn("No portal template found for : " + portalName + " " + worldId);
			return;
		}
		
		ExitPoint exitPoint = template.getExitPoint();
		teleportTo(player, worldId, exitPoint.getX(), exitPoint.getY(), exitPoint.getZ(), delay);
	}
}
