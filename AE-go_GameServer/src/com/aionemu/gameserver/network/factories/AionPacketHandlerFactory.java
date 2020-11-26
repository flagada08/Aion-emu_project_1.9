/*
 * This file is part of aion-emu <aion-emu.com>.
 *
 *  aion-emu is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  aion-emu is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with aion-emu.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aionemu.gameserver.network.factories;

import com.aionemu.gameserver.network.aion.AionClientPacket;
import com.aionemu.gameserver.network.aion.AionPacketHandler;
import com.aionemu.gameserver.network.aion.AionConnection.State;
import com.aionemu.gameserver.network.aion.clientpackets.*;
import com.google.inject.Injector;

/**
 * This factory is responsible for creating {@link AionPacketHandler} object. It also initializes created handler with a
 * set of packet prototypes.<br>
 * Object of this classes uses <tt>Injector</tt> for injecting dependencies into prototype objects.<br>
 * <br>
 * 
 * @author Luno
 * 
 */
public class AionPacketHandlerFactory
{
	private Injector			injector;
	private AionPacketHandler	handler	= new AionPacketHandler();

	/**
	 * Creates new instance of <tt>AionPacketHandlerFactory</tt><br>
	 * 
	 * @param injector
	 */
	public AionPacketHandlerFactory(Injector injector)
	{
		this.injector = injector;
		addPacket(new CM_START_LOOT(0x00), State.IN_GAME);
		addPacket(new CM_LOOT_ITEM(0x01), State.IN_GAME);
		addPacket(new CM_MOVE_ITEM(0x02), State.IN_GAME);
		addPacket(new CM_SPLIT_ITEM(0x03), State.IN_GAME);
		addPacket(new CM_CLIENT_COMMAND_LOC(0x04), State.IN_GAME);
		addPacket(new CM_CHARACTER_LIST(0x0C), State.AUTHED);
		addPacket(new CM_CREATE_CHARACTER(0x0D), State.AUTHED);// 1.5.x
		addPacket(new CM_DELETE_CHARACTER(0x0E), State.AUTHED);// 1.5.x
		addPacket(new CM_RESTORE_CHARACTER(0x0F), State.AUTHED);
		addPacket(new CM_FRIEND_STATUS(0x10), State.IN_GAME);// 1.5.x
		addPacket(new CM_CHANGE_CHANNEL(0x12), State.IN_GAME);
		addPacket(new CM_PLAYER_SEARCH(0x15), State.IN_GAME);
		addPacket(new CM_LEGION_UPLOAD_INFO(0x16), State.IN_GAME);
		addPacket(new CM_LEGION_UPLOAD_EMBLEM(0x17), State.IN_GAME);
		addPacket(new CM_REPLACE_ITEM(0x18), State.IN_GAME);
		addPacket(new CM_BLOCK_SET_REASON(0x19), State.IN_GAME);
		addPacket(new CM_SHOW_BRAND(0x1B), State.IN_GAME);
		addPacket(new CM_BLOCK_ADD(0x1C), State.IN_GAME);
		addPacket(new CM_BLOCK_DEL(0x1D), State.IN_GAME);
		addPacket(new CM_SHOW_BLOCKLIST(0x1E), State.IN_GAME);
		addPacket(new CM_MAY_LOGIN_INTO_GAME(0x20), State.AUTHED);
		addPacket(new CM_MAC_ADDRESS(0x23), State.CONNECTED, State.AUTHED, State.IN_GAME);
		addPacket(new CM_MAC_ADDRESS2(0x24), State.IN_GAME);
		addPacket(new CM_MACRO_CREATE(0x25), State.IN_GAME);// 1.5.x
		addPacket(new CM_MACRO_DELETE(0x26), State.IN_GAME);// 1.5.x
		addPacket(new CM_CHECK_NICKNAME(0x27), State.AUTHED);// 1.5.x
		addPacket(new CM_SHOW_MAP(0x2A), State.IN_GAME);
		addPacket(new CM_RECONNECT_AUTH(0x2D), State.AUTHED);
		addPacket(new CM_DISTRIBUTION_SETTINGS(0x2F), State.IN_GAME);
		addPacket(new CM_GROUP_RESPONSE(0x36), State.IN_GAME);
		addPacket(new CM_GODSTONE_SOCKET(0x41), State.IN_GAME);
		addPacket(new CM_DELETE_QUEST(0x46), State.IN_GAME);
		addPacket(new CM_PLAY_MOVIE_END(0x47), State.IN_GAME);
		addPacket(new CM_VIEW_PLAYER_DETAILS(0x4A), State.IN_GAME);
		addPacket(new CM_MANASTONE_REMOVE(0x4F), State.IN_GAME);
		addPacket(new CM_GROUP_DISTRIBUTION(0x52), State.IN_GAME);
		addPacket(new CM_PLAYER_STATUS_INFO(0x56), State.IN_GAME);
		addPacket(new CM_INVITE_TO_GROUP(0x57), State.IN_GAME);
		addPacket(new CM_DUEL_REQUEST(0x58), State.IN_GAME);
		addPacket(new CM_DELETE_ITEM(0x5A), State.IN_GAME);
		addPacket(new CM_PING_REQUEST(0x5D), State.IN_GAME); // 1.5.x
		addPacket(new CM_SHOW_FRIENDLIST(0x64), State.IN_GAME);
		addPacket(new CM_FRIEND_ADD(0x65), State.IN_GAME);// 1.5.x
		addPacket(new CM_FRIEND_DEL(0x66), State.IN_GAME);
		addPacket(new CM_SEND_MAIL(0x6A), State.IN_GAME);
		addPacket(new CM_PRIVATE_STORE(0x6D), State.IN_GAME);
		addPacket(new CM_PRIVATE_STORE_NAME(0x6E), State.IN_GAME);
		addPacket(new CM_TITLE_SET(0x71), State.IN_GAME);
		addPacket(new CM_CRAFT(0x73), State.IN_GAME);
		addPacket(new CM_TELEPORT_SELECT(0x7A), State.IN_GAME);
		addPacket(new CM_L2AUTH_LOGIN_CHECK(0x7B), State.CONNECTED);
		addPacket(new CM_READ_MAIL(0x7C), State.IN_GAME);
		addPacket(new CM_GET_MAIL_ATTACHMENT(0x7E), State.IN_GAME);
		addPacket(new CM_DELETE_MAIL(0x7F),State.IN_GAME);
		addPacket(new CM_CHAT_MESSAGE_PUBLIC(0x81), State.IN_GAME);
		addPacket(new CM_CHAT_MESSAGE_WHISPER(0x82), State.IN_GAME);// 1.5.x
		addPacket(new CM_LEGION_SEND_EMBLEM(0x86), State.IN_GAME);
		addPacket(new CM_SKILL_DEACTIVATE(0x88), State.IN_GAME);
		addPacket(new CM_REMOVE_ALTERED_STATE(0x89), State.IN_GAME);
		addPacket(new CM_USE_ITEM(0x8B), State.IN_GAME);
		addPacket(new CM_EMOTION(0x91), State.IN_GAME);
		addPacket(new CM_PING(0x92), State.AUTHED, State.IN_GAME);
		addPacket(new CM_LEGION(0x93), State.IN_GAME);
		addPacket(new CM_TARGET_SELECT(0x95), State.IN_GAME);
		addPacket(new CM_ATTACK(0x96), State.IN_GAME);
		addPacket(new CM_CASTSPELL(0x97), State.IN_GAME);
		addPacket(new CM_QUESTION_RESPONSE(0x98), State.IN_GAME);
		addPacket(new CM_BUY_ITEM(0x99), State.IN_GAME);
		addPacket(new CM_SHOW_DIALOG(0x9A), State.IN_GAME);
		addPacket(new CM_CLOSE_DIALOG(0x9B), State.IN_GAME);
		addPacket(new CM_EQUIP_ITEM(0x9C), State.IN_GAME);
		addPacket(new CM_SET_NOTE(0xA0), State.IN_GAME);
		addPacket(new CM_LEGION_MODIFY_EMBLEM(0xA1), State.IN_GAME);
		addPacket(new CM_MOVE(0xA6), State.IN_GAME);
		addPacket(new CM_FLIGHT_TELEPORT(0xA7), State.IN_GAME);
		addPacket(new CM_EXCHANGE_ADD_KINAH(0xA8), State.IN_GAME);
		addPacket(new CM_EXCHANGE_LOCK(0xA9), State.IN_GAME);
		addPacket(new CM_EXCHANGE_OK(0xAA), State.IN_GAME);
		addPacket(new CM_EXCHANGE_CANCEL(0xAB), State.IN_GAME);
		addPacket(new CM_DIALOG_SELECT(0xAC), State.IN_GAME);
		addPacket(new CM_LEGION_TABS(0xAD), State.IN_GAME);
		addPacket(new CM_EXCHANGE_REQUEST(0xB5), State.IN_GAME);
		addPacket(new CM_EXCHANGE_ADD_ITEM(0xB6), State.IN_GAME);
		addPacket(new CM_DISCONNECT(0xE8), State.IN_GAME);
		addPacket(new CM_QUIT(0xE9), State.AUTHED, State.IN_GAME);
		addPacket(new CM_MAY_QUIT(0xEA), State.AUTHED, State.IN_GAME);
		addPacket(new CM_REVIVE(0xEB), State.IN_GAME);
		addPacket(new CM_UI_SETTINGS(0xF0), State.IN_GAME);
		addPacket(new CM_OBJECT_SEARCH(0xF1), State.IN_GAME);
		addPacket(new CM_CUSTOM_SETTINGS(0xF2), State.IN_GAME);
		addPacket(new CM_VERSION_CHECK(0xF6), State.CONNECTED);
		addPacket(new CM_TIME_CHECK(0xF8), State.CONNECTED, State.AUTHED, State.IN_GAME);
		addPacket(new CM_GATHER(0xF9), State.IN_GAME);// 1.5
		addPacket(new CM_ENTER_WORLD(0xFE), State.AUTHED);
		addPacket(new CM_LEVEL_READY(0xFF), State.IN_GAME);
		addPacket(new CM_SUMMON_MOVE(0x3F), State.IN_GAME);
		addPacket(new CM_SUMMON_COMMAND(0x6F), State.IN_GAME);
		addPacket(new CM_SUMMON_EMOTION(0x30), State.IN_GAME);
		addPacket(new CM_SUMMON_ATTACK(0x31), State.IN_GAME);
	}

	public AionPacketHandler getPacketHandler()
	{
		return handler;
	}

	private void addPacket(AionClientPacket prototype, State... states)
	{
		injector.injectMembers(prototype);
		handler.addPacketPrototype(prototype, states);
	}

}
